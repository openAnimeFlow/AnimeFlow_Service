package com.ligg.api.themoviedbapi;

import uk.co.conoregan.themoviedbapi.model.core.MovieResultsPage;
import uk.co.conoregan.themoviedbapi.model.core.TvSeriesResultsPage;
import uk.co.conoregan.themoviedbapi.model.movies.MovieDb;
import uk.co.conoregan.themoviedbapi.model.tv.series.TvSeriesDb;
import uk.co.conoregan.themoviedbapi.model.tv.episode.TvEpisodeDb;
import uk.co.conoregan.themoviedbapi.model.tv.season.TvSeasonDb;
import uk.co.conoregan.themoviedbapi.tools.TmdbException;

public interface TmdbClient {

    MovieDb getMovieDetails(int movieId, String language) throws TmdbException;

    TvSeriesDb getTvSeriesDetails(int seriesId, String language) throws TmdbException;

    TvEpisodeDb getTvEpisodeDetails(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException;

    TvSeasonDb getTvSeasonDetails(int seriesId, int seasonNumber, String language) throws TmdbException;

    String getImageUrl(String imagePath);

    String getTvEpisodeStillUrl(int seriesId, int seasonNumber, int episodeNumber, String language)
            throws TmdbException;

    MovieResultsPage searchMovies(String query, String language, int page) throws TmdbException;

    TvSeriesResultsPage searchTvs(String query, Integer firstAirDateYear, String language, int page, Integer year) throws TmdbException;
}
