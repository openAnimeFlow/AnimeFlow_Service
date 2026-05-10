package com.ligg.common.vo.dandanplay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BangumiDetailVo(
        DetailVo bangumi,
        int errorCode,
        boolean success,
        String errorMessage) {


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetailVo(
            String type,
            String typeDescription,
            List<TitleVo> titles,
            List<SeasonVo> seasons,
            List<EpisodeVo> episodes,
            String summary,
            String intro,
            List<String> metadata,
            String bangumiUrl,
            double userRating,
            Boolean favoriteStatus,
            String comment,
            Map<String, Double> ratingDetails,
            List<ListAnimeVo> relateds,
            List<ListAnimeVo> similars,
            List<TagVo> tags,
            List<OnlineDatabaseVo> onlineDatabases,
            List<TrailerVo> trailers,
            int animeId,
            String bangumiId,
            String animeTitle,
            String imageUrl,
            String searchKeyword,
            boolean isOnAir,
            int airDay,
            boolean isFavorited,
            boolean isRestricted,
            double rating) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TitleVo(String language, String title) {
        }

        /**
         * 季度列表；当前开放接口可能返回空数组，结构扩展时在此补充字段即可。
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record SeasonVo() {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EpisodeVo(
                Integer seasonId,
                int episodeId,
                String episodeTitle,
                String episodeNumber,
                LocalDateTime lastWatched,
                LocalDateTime airDate) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ListAnimeVo(
                int animeId,
                String bangumiId,
                String animeTitle,
                String imageUrl,
                String searchKeyword,
                boolean isOnAir,
                int airDay,
                boolean isFavorited,
                boolean isRestricted,
                double rating) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TagVo(int id, String name, int count) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OnlineDatabaseVo(String name, String url) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TrailerVo(
                int id,
                String url,
                String title,
                String imageUrl,
                LocalDateTime date) {
        }
    }
}
