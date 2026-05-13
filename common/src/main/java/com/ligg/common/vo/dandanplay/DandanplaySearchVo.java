/**
 * @author Ligg
 * @date 2026/5/10 15:08
 */
package com.ligg.common.vo.dandanplay;

import java.time.LocalDateTime;
import java.util.List;

public record DandanplaySearchVo(
        List<Animes> animes,
        int errorCode,
        boolean success,
        String errorMessage) {
    public record Animes(int animeId,
                         String bangumiId,
                         String animeTitle,
                         String type,
                         String typeDescription,
                         String imageUrl,
                         LocalDateTime startDate,
                         int episodeCount,
                         double rating,
                         boolean isFavorited) {
    }
}
