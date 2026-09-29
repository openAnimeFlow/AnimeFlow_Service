package com.ligg.flowclient.service;

import com.ligg.api.themoviedbapi.TmdbClient;
import com.ligg.common.entity.BangumiEpisodeEntity;
import com.ligg.flowclient.mapper.BangumiDataItemMapper;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
    private final TmdbClient tmdbClient;
    private final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();

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

            try {
                String tmdbId = bangumiDataItemMapper.selectTmdbIdByBangumiId(String.valueOf(subjectId));
                if (!StringUtils.hasText(tmdbId)) {
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
            } catch (DataAccessException | TmdbException | NumberFormatException e) {
                log.debug("回填剧集封面失败, subjectId={}", subjectId, e);
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
