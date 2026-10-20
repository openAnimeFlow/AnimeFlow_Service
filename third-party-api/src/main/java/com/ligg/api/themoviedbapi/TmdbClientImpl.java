package com.ligg.api.themoviedbapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import uk.co.conoregan.themoviedbapi.model.core.MovieResultsPage;
import uk.co.conoregan.themoviedbapi.model.core.TvSeriesResultsPage;
import uk.co.conoregan.themoviedbapi.model.core.image.Artwork;
import uk.co.conoregan.themoviedbapi.model.movies.MovieDb;
import uk.co.conoregan.themoviedbapi.model.tv.episode.TvEpisodeDb;
import uk.co.conoregan.themoviedbapi.model.tv.season.TvSeasonDb;
import uk.co.conoregan.themoviedbapi.model.tv.series.TvSeriesDb;
import uk.co.conoregan.themoviedbapi.tools.TmdbException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TMDb HTTP transport using Jackson 3; the SDK supplies response models only. */
@Service
public class TmdbClientImpl implements TmdbClient {

    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient client;
    private final boolean configured;

    public TmdbClientImpl(@Value("${anime-flow.tmdb.read-access-token:}") String readAccessToken,
                          WebClient.Builder builder) {
        configured = StringUtils.hasText(readAccessToken);
        var mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        client = builder.clone().baseUrl("https://api.themoviedb.org/3")
                .defaultHeaders(headers -> {
                    if (configured) headers.setBearerAuth(readAccessToken);
                })
                .codecs(codecs -> codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper)))
                .build();
    }

    @Override
    public MovieDb getMovieDetails(int movieId, String language) throws TmdbException {
        requirePositiveId(movieId, "movieId");
        return get("/movie/" + movieId, languageParams(language), MovieDb.class);
    }

    @Override
    public TvSeriesDb getTvSeriesDetails(int seriesId, String language) throws TmdbException {
        requirePositiveId(seriesId, "seriesId");
        return get("/tv/" + seriesId, languageParams(language), TvSeriesDb.class);
    }

    @Override
    public TvEpisodeDb getTvEpisodeDetails(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException {
        validateSeason(seriesId, seasonNumber);
        requirePositiveId(episodeNumber, "episodeNumber");
        return get("/tv/" + seriesId + "/season/" + seasonNumber + "/episode/" + episodeNumber,
                languageParams(language), TvEpisodeDb.class);
    }

    @Override
    public TvSeasonDb getTvSeasonDetails(int seriesId, int seasonNumber, String language) throws TmdbException {
        validateSeason(seriesId, seasonNumber);
        return get("/tv/" + seriesId + "/season/" + seasonNumber, languageParams(language), TvSeasonDb.class);
    }

    @Override
    public String getImageUrl(String imagePath) {
        return StringUtils.hasText(imagePath) ? IMAGE_BASE_URL + imagePath(imagePath) : null;
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
        return getImages("/tv/" + seriesId + "/images");
    }

    @Override
    public TmdbImageSet getMovieImages(int movieId) throws TmdbException {
        requirePositiveId(movieId, "movieId");
        return getImages("/movie/" + movieId + "/images");
    }

    private TmdbImageSet getImages(String path) throws TmdbException {
        ImageResponse images = get(path, Map.of("include_image_language", "en,ja,zh,null"), ImageResponse.class);
        return images == null ? new TmdbImageSet(List.of(), List.of(), List.of())
                : new TmdbImageSet(toImageUrls(images.backdrops()), toImageUrls(images.logos()), toImageUrls(images.posters()));
    }

    private List<String> toImageUrls(List<Artwork> artworks) {
        if (artworks == null) return Collections.emptyList();
        return artworks.stream().map(Artwork::getFilePath).filter(StringUtils::hasText)
                .map(path -> "https://image.tmdb.org/t/p/w1280" + imagePath(path)).toList();
    }

    @Override
    public String getTvEpisodeStillUrl(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException {
        TvEpisodeDb episode = getTvEpisodeDetails(seriesId, seasonNumber, episodeNumber, language);
        return getImageUrl(episode == null ? null : episode.getStillPath());
    }

    @Override
    public MovieResultsPage searchMovies(String query, String language, int page) throws TmdbException {
        return get("/search/movie", searchParams(query, language, page), MovieResultsPage.class);
    }

    @Override
    public TvSeriesResultsPage searchTvs(String query, Integer firstAirDateYear, String language, int page, Integer year)
            throws TmdbException {
        Map<String, Object> params = searchParams(query, language, page);
        if (firstAirDateYear != null) params.put("first_air_date_year", firstAirDateYear);
        if (year != null) params.put("year", year);
        return get("/search/tv", params, TvSeriesResultsPage.class);
    }

    private <T> T get(String path, Map<String, ?> params, Class<T> type) throws TmdbException {
        if (!configured) throw new IllegalStateException("未配置 TMDb Read Access Token: anime-flow.tmdb.read-access-token");
        try {
            return client.get().uri(uri -> {
                uri.path(path);
                params.forEach((key, value) -> uri.queryParam(key, value));
                return uri.build();
            }).retrieve().bodyToMono(type).block(REQUEST_TIMEOUT);
        } catch (RuntimeException e) {
            throw new TmdbException(e);
        }
    }

    private static Map<String, Object> languageParams(String language) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("language", StringUtils.hasText(language) ? language.trim() : DEFAULT_LANGUAGE);
        return params;
    }

    private static Map<String, Object> searchParams(String query, String language, int page) {
        if (!StringUtils.hasText(query)) throw new IllegalArgumentException("query 不能为空");
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        Map<String, Object> params = languageParams(language);
        params.put("query", query.trim());
        params.put("include_adult", false);
        params.put("page", page);
        return params;
    }

    private static String imagePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static void validateSeason(int seriesId, int seasonNumber) {
        requirePositiveId(seriesId, "seriesId");
        if (seasonNumber < 0) throw new IllegalArgumentException("seasonNumber 不能小于 0");
    }

    private static void requirePositiveId(int id, String name) {
        if (id <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private record ImageResponse(List<Artwork> backdrops, List<Artwork> logos, List<Artwork> posters) {
    }
}
