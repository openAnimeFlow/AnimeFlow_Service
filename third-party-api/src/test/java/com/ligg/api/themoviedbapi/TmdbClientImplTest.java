package com.ligg.api.themoviedbapi;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uk.co.conoregan.themoviedbapi.tools.TmdbException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TmdbClientImplTest {

    private final AtomicReference<ClientRequest> request = new AtomicReference<>();

    @Test
    void decodesMovieWithJackson3AndPreservesRequestAuthentication() throws Exception {
        var client = client("{\"id\":42,\"title\":\"movie\",\"poster_path\":\"/movie.jpg\",\"release_date\":\"2026-01-01\",\"future_field\":true}");
        var movie = client.getMovieDetails(42, null);
        assertEquals("/movie.jpg", movie.getPosterPath());
        assertEquals("2026-01-01", movie.getReleaseDate());
        assertEquals("/3/movie/42", request.get().url().getPath());
        assertEquals("language=zh-CN", request.get().url().getQuery());
        assertEquals("Bearer test-token", request.get().headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void decodesSeasonEpisodesAndImagePaths() throws Exception {
        var client = client("""
                {"id":1,"season_number":2,"episodes":[
                  {"id":12,"episode_number":1,"season_number":2,"still_path":"/episode.jpg","air_date":""}]}
                """);
        var season = client.getTvSeasonDetails(42, 2, "ja-JP");
        assertEquals(1, season.getEpisodes().getFirst().getEpisodeNumber());
        assertEquals("https://image.tmdb.org/t/p/w500/episode.jpg",
                client.getImageUrl(season.getEpisodes().getFirst().getStillPath()));
        assertEquals("/3/tv/42/season/2", request.get().url().getPath());
    }

    @Test
    void decodesAllArtworkSizesWithNativeJsonCodec() throws Exception {
        var client = client("""
                {"backdrops":[{"file_path":"/backdrop.jpg"}],"logos":[],
                 "posters":[{"file_path":"poster.jpg"}],"id":42}
                """);
        var images = client.getTvSeriesImages(42);
        assertEquals(List.of("https://image.tmdb.org/t/p/w1280/backdrop.jpg"), images.backdrops());
        assertEquals(List.of("https://image.tmdb.org/t/p/w1280/poster.jpg"), images.posters());
        assertTrue(images.logos().isEmpty());
        assertTrue(request.get().url().getQuery().contains("include_image_language=en,ja,zh,null"));
    }

    @Test
    void searchKeepsOptionalYearFiltersAndEncodesQuery() throws Exception {
        var client = client("{\"page\":2,\"total_pages\":1,\"total_results\":0,\"results\":[]}");
        assertNotNull(client.searchTvs("test & name", 2026, "en-US", 2, 2025));
        String query = request.get().url().getRawQuery();
        assertTrue(query.contains("query=test%20%26%20name"));
        assertTrue(query.contains("first_air_date_year=2026"));
        assertTrue(query.contains("year=2025"));
    }

    @Test
    void failuresRemainTmdbExceptionsAndMissingConfigurationDoesNotMakeRequests() {
        var failed = new TmdbClientImpl("test-token", WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build())));
        assertThrows(TmdbException.class, () -> failed.getMovieDetails(42, null));
        var unconfigured = new TmdbClientImpl("", WebClient.builder().exchangeFunction(request -> {
            fail("Unconfigured client must not send requests");
            return Mono.empty();
        }));
        assertThrows(IllegalStateException.class, () -> unconfigured.getMovieDetails(42, null));
    }

    private TmdbClientImpl client(String body) {
        return new TmdbClientImpl("test-token", WebClient.builder().exchangeFunction(outgoing -> {
            request.set(outgoing);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }));
    }
}
