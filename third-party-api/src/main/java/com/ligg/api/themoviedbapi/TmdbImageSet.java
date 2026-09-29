package com.ligg.api.themoviedbapi;

import java.util.Collections;
import java.util.List;

/** Image URLs returned by TMDb's images endpoint. */
public record TmdbImageSet(List<String> backdrops, List<String> logos, List<String> posters) {

    public TmdbImageSet {
        backdrops = backdrops == null ? Collections.emptyList() : List.copyOf(backdrops);
        logos = logos == null ? Collections.emptyList() : List.copyOf(logos);
        posters = posters == null ? Collections.emptyList() : List.copyOf(posters);
    }
}
