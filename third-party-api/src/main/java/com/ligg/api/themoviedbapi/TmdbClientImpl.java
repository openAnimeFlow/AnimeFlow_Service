package com.ligg.api.themoviedbapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.co.conoregan.themoviedbapi.TmdbApi;
import uk.co.conoregan.themoviedbapi.model.core.MovieResultsPage;
import uk.co.conoregan.themoviedbapi.model.core.TvSeriesResultsPage;
import uk.co.conoregan.themoviedbapi.model.movies.MovieDb;
import uk.co.conoregan.themoviedbapi.model.tv.series.TvSeriesDb;
import uk.co.conoregan.themoviedbapi.model.tv.episode.TvEpisodeDb;
import uk.co.conoregan.themoviedbapi.model.tv.season.TvSeasonDb;
import uk.co.conoregan.themoviedbapi.model.core.image.Artwork;
import java.util.Collections;
import java.util.List;
import uk.co.conoregan.themoviedbapi.tools.TmdbException;

@Service
public class TmdbClientImpl implements TmdbClient {

    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private final TmdbApi tmdbApi;

    public TmdbClientImpl(@Value("${anime-flow.tmdb.read-access-token:}") String readAccessToken) {
        this.tmdbApi = StringUtils.hasText(readAccessToken) ? new TmdbApi(readAccessToken) : null;
    }

    @Override
    public MovieDb getMovieDetails(int movieId, String language) throws TmdbException {
        requirePositiveId(movieId, "movieId");
        return requireClient().getMovies().getDetails(movieId, normalizeLanguage(language));
    }

    @Override
    public TvSeriesDb getTvSeriesDetails(int seriesId, String language) throws TmdbException {
        requirePositiveId(seriesId, "seriesId");
        return requireClient().getTvSeries().getDetails(seriesId, normalizeLanguage(language));
    }

    @Override
    public TvEpisodeDb getTvEpisodeDetails(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException {
        requirePositiveId(seriesId, "seriesId");
        if (seasonNumber < 0) {
            throw new IllegalArgumentException("seasonNumber 不能小于 0");
        }
        requirePositiveId(episodeNumber, "episodeNumber");
        return requireClient().getTvEpisodes().getDetails(
                seriesId, seasonNumber, episodeNumber, normalizeLanguage(language));
    }

    @Override
    public TvSeasonDb getTvSeasonDetails(int seriesId, int seasonNumber, String language) throws TmdbException {
        requirePositiveId(seriesId, "seriesId");
        if (seasonNumber < 0) {
            throw new IllegalArgumentException("seasonNumber 不能小于 0");
        }
        return requireClient().getTvSeasons().getDetails(seriesId, seasonNumber, normalizeLanguage(language));
    }

    @Override
    public String getImageUrl(String imagePath) {
        if (!StringUtils.hasText(imagePath)) {
            return null;
        }
        return IMAGE_BASE_URL + (imagePath.startsWith("/") ? imagePath : "/" + imagePath);
    }

    @Override
    public List<String> getTvSeriesBackdrops(int seriesId) throws TmdbException {
        return getTvSeriesImages(seriesId).backdrops();
    }

    @Override
    public List<String> getMovieBackdrops(int movieId) throws TmdbException {
        return getMovieImages(movieId).backdrops();
    }

    @Override
    public TmdbImageSet getTvSeriesImages(int seriesId) throws TmdbException {
        requirePositiveId(seriesId, "seriesId");
        var images = requireClient().getTvSeries().getImages(seriesId, null, "en", "ja", "zh", "null");
        return toImageSet(images == null ? null : images.getBackdrops(),
                images == null ? null : images.getLogos(), images == null ? null : images.getPosters());
    }

    @Override
    public TmdbImageSet getMovieImages(int movieId) throws TmdbException {
        requirePositiveId(movieId, "movieId");
        var images = requireClient().getMovies().getImages(movieId, null, "en", "ja", "zh", "null");
        return toImageSet(images == null ? null : images.getBackdrops(),
                images == null ? null : images.getLogos(), images == null ? null : images.getPosters());
    }

    private TmdbImageSet toImageSet(List<Artwork> backdrops, List<Artwork> logos, List<Artwork> posters) {
        return new TmdbImageSet(
                toImageUrls(backdrops), toImageUrls(logos), toImageUrls(posters));
    }

    private List<String> toImageUrls(List<Artwork> artworks) {
        if (artworks == null) {
            return Collections.emptyList();
        }
        return artworks.stream()
                .map(Artwork::getFilePath)
                .filter(StringUtils::hasText)
                .map(path -> "https://image.tmdb.org/t/p/w1280" + (path.startsWith("/") ? path : "/" + path))
                .toList();
    }

    @Override
    public String getTvEpisodeStillUrl(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException {
        TvEpisodeDb episode = getTvEpisodeDetails(seriesId, seasonNumber, episodeNumber, language);
        String stillPath = episode == null ? null : episode.getStillPath();
        return getImageUrl(stillPath);
    }

    @Override
    public MovieResultsPage searchMovies(String query, String language, int page) throws TmdbException {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page 必须大于等于 1");
        }
        return requireClient().getSearch().searchMovie(
                query.trim(), false, normalizeLanguage(language), null, page, null, null);
    }

    @Override
    public TvSeriesResultsPage searchTvs(String query, Integer firstAirDateYear, String language, int page, Integer year) throws TmdbException {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page 必须大于等于 1");
        }
        return requireClient().getSearch().searchTv(
                query.trim(), firstAirDateYear, false, normalizeLanguage(language), page, year
        );
    }

    private TmdbApi requireClient() {
        if (tmdbApi == null) {
            throw new IllegalStateException("未配置 TMDb Read Access Token: anime-flow.tmdb.read-access-token");
        }
        return tmdbApi;
    }

    private static String normalizeLanguage(String language) {
        return StringUtils.hasText(language) ? language.trim() : DEFAULT_LANGUAGE;
    }

    private static void requirePositiveId(int id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }
}
