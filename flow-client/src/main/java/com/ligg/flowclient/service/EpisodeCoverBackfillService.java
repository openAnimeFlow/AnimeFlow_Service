package com.ligg.flowclient.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.themoviedbapi.TmdbClient;
import com.ligg.api.themoviedbapi.TmdbImageSet;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.common.entity.BangumiSubjectBackdropCacheEntity;
import com.ligg.flowclient.mapper.BangumiDataItemMapper;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectBackdropCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.co.conoregan.themoviedbapi.model.movies.MovieDb;
import uk.co.conoregan.themoviedbapi.model.tv.season.TvSeasonDb;
import uk.co.conoregan.themoviedbapi.model.tv.season.TvSeasonEpisode;
import uk.co.conoregan.themoviedbapi.tools.TmdbException;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads episode covers from the database and backfills missing values from TMDb.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpisodeCoverBackfillService {

    private static final Pattern TMDB_TV_ID_PATTERN = Pattern.compile("^tv/(\\d+)(?:/season/(\\d+))?$");
    private static final Pattern TMDB_MOVIE_ID_PATTERN = Pattern.compile("^movie/(\\d+)$");
    private static final String DEFAULT_LANGUAGE = "zh-CN";

    private final BangumiEpisodeMapper episodeMapper;
    private final BangumiDataItemMapper bangumiDataItemMapper;
    private final BangumiSubjectBackdropCacheMapper backdropCacheMapper;
    private final TmdbClient tmdbClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();

    private static final long BACKDROP_CACHE_DAYS = 7;

    /** Gets the wide still/backdrop images associated with a Bangumi subject's TMDb entry. */
    public List<String> getSubjectStills(Integer subjectId) {
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("subjectId 必须大于 0");
        }
        Object lock = locks.computeIfAbsent(subjectId, ignored -> new Object());
        synchronized (lock) {
            BangumiSubjectBackdropCacheEntity cached = backdropCacheMapper.selectById(subjectId);
            if (cached != null && isFresh(cached.getFetchedAt())) {
                return readBackdrops(cached.getBackdrops());
            }

            try {
                String tmdbId = bangumiDataItemMapper.selectTmdbIdByBangumiId(String.valueOf(subjectId));
                if (!StringUtils.hasText(tmdbId)) {
                    return cached == null ? Collections.emptyList() : readBackdrops(cached.getBackdrops());
                }
                TmdbImageSet images = fetchSubjectImages(tmdbId.trim());
                saveBackdropCache(subjectId, tmdbId.trim(), images);
                return images.backdrops();
            } catch (TmdbException | RuntimeException e) {
                log.warn("获取 TMDb 剧照失败, subjectId={}", subjectId, e);
            }
            return cached == null ? Collections.emptyList() : readBackdrops(cached.getBackdrops());
        }
    }

    private boolean isFresh(LocalDateTime fetchedAt) {
        return fetchedAt != null && fetchedAt.isAfter(LocalDateTime.now().minusDays(BACKDROP_CACHE_DAYS));
    }

    private TmdbImageSet fetchSubjectImages(String tmdbId) throws TmdbException {
        Matcher tvMatcher = TMDB_TV_ID_PATTERN.matcher(tmdbId);
        if (tvMatcher.matches()) {
            return tmdbClient.getTvSeriesImages(Integer.parseInt(tvMatcher.group(1)));
        }
        Matcher movieMatcher = TMDB_MOVIE_ID_PATTERN.matcher(tmdbId);
        if (movieMatcher.matches()) {
            return tmdbClient.getMovieImages(Integer.parseInt(movieMatcher.group(1)));
        }
        return new TmdbImageSet(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private void saveBackdropCache(Integer subjectId, String tmdbId, TmdbImageSet images) {
        try {
            BangumiSubjectBackdropCacheEntity row = backdropCacheMapper.selectById(subjectId);
            boolean existing = row != null;
            if (row == null) {
                row = new BangumiSubjectBackdropCacheEntity();
                row.setSubjectId(subjectId);
            }
            row.setTmdbId(tmdbId);
            row.setBackdrops(objectMapper.writeValueAsString(images.backdrops()));
            row.setLogos(objectMapper.writeValueAsString(images.logos()));
            row.setPosters(objectMapper.writeValueAsString(images.posters()));
            row.setFetchedAt(LocalDateTime.now());
            if (!existing) {
                backdropCacheMapper.insert(row);
            } else {
                backdropCacheMapper.updateById(row);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化剧照缓存失败", e);
        }
    }

    private List<String> readBackdrops(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("读取剧照缓存失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns covers keyed by Bangumi episode ID, preferring values already stored in DB.
     */
    public Map<Integer, String> resolve(List<BangumiEpisodeEntity> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> covers = new LinkedHashMap<>();
        Map<Integer, List<BangumiEpisodeEntity>> missingBySubject = new LinkedHashMap<>();
        for (BangumiEpisodeEntity episode : episodes) {
            if (StringUtils.hasText(episode.getCover())) {
                covers.put(episode.getId(), episode.getCover());
            } else if (episode.getSubjectId() != null) {
                missingBySubject.computeIfAbsent(episode.getSubjectId(), ignored -> new java.util.ArrayList<>())
                        .add(episode);
            }
        }

        missingBySubject.forEach((subjectId, missing) -> backfillSubject(subjectId, missing, covers));
        return covers;
    }

    private void backfillSubject(
            Integer subjectId,
            List<BangumiEpisodeEntity> episodes,
            Map<Integer, String> covers) {
        Object lock = locks.computeIfAbsent(subjectId, ignored -> new Object());
        synchronized (lock) {
            try {
                List<BangumiEpisodeEntity> stillMissing = new java.util.ArrayList<>();
                for (BangumiEpisodeEntity episode : episodes) {
                    BangumiEpisodeEntity current = episodeMapper.selectById(episode.getId());
                    if (current != null && StringUtils.hasText(current.getCover())) {
                        episode.setCover(current.getCover());
                        covers.put(episode.getId(), current.getCover());
                    } else {
                        stillMissing.add(episode);
                    }
                }
                if (stillMissing.isEmpty()) {
                    return;
                }

                String tmdbId = bangumiDataItemMapper.selectTmdbIdByBangumiId(String.valueOf(subjectId));
                if (!StringUtils.hasText(tmdbId)) {
                    log.debug("条目 subjectId={} 没有 TMDb 映射，跳过剧集封面回填", subjectId);
                    return;
                }
                Map<Integer, String> fetched = fetchCovers(tmdbId.trim(), stillMissing);
                int backfilledCount = 0;
                for (BangumiEpisodeEntity episode : stillMissing) {
                    String cover = fetched.get(episode.getSort());
                    if (!StringUtils.hasText(cover)) {
                        continue;
                    }
                    BangumiEpisodeEntity update = new BangumiEpisodeEntity();
                    update.setId(episode.getId());
                    update.setCover(cover);
                    episodeMapper.updateById(update);
                    episode.setCover(cover);
                    covers.put(episode.getId(), cover);
                    backfilledCount++;
                }
                if (backfilledCount > 0) {
                    log.info("条目 subjectId={} 回填剧集封面数量={}", subjectId, backfilledCount);
                }
            } catch (TmdbException | RuntimeException e) {
                log.warn("条目 subjectId={} 回填剧集封面失败", subjectId, e);
            }
        }
    }

    private Map<Integer, String> fetchCovers(String tmdbId, List<BangumiEpisodeEntity> episodes)
            throws TmdbException {
        Matcher movieMatcher = TMDB_MOVIE_ID_PATTERN.matcher(tmdbId);
        if (movieMatcher.matches()) {
            MovieDb movie = tmdbClient.getMovieDetails(Integer.parseInt(movieMatcher.group(1)), DEFAULT_LANGUAGE);
            String cover = movie == null ? null : tmdbClient.getImageUrl(movie.getPosterPath());
            if (!StringUtils.hasText(cover)) {
                return Collections.emptyMap();
            }
            Map<Integer, String> covers = new HashMap<>();
            for (BangumiEpisodeEntity episode : episodes) {
                if (episode.getSort() != null) {
                    covers.put(episode.getSort(), cover);
                }
            }
            return covers;
        }

        Matcher tvMatcher = TMDB_TV_ID_PATTERN.matcher(tmdbId);
        if (!tvMatcher.matches()) {
            return Collections.emptyMap();
        }
        int seriesId = Integer.parseInt(tvMatcher.group(1));
        int seasonNumber = tvMatcher.group(2) == null ? 1 : Integer.parseInt(tvMatcher.group(2));
        TvSeasonDb season = tmdbClient.getTvSeasonDetails(seriesId, seasonNumber, DEFAULT_LANGUAGE);
        if (season == null || season.getEpisodes() == null) {
            return Collections.emptyMap();
        }

        Map<Integer, String> covers = new HashMap<>();
        for (TvSeasonEpisode episode : season.getEpisodes()) {
            if (episode.getEpisodeNumber() != null && StringUtils.hasText(episode.getStillPath())) {
                covers.put(episode.getEpisodeNumber(), tmdbClient.getImageUrl(episode.getStillPath()));
            }
        }
        return covers;
    }
}
