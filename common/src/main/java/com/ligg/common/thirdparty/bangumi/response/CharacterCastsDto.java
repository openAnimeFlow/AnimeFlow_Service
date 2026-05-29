package com.ligg.common.thirdparty.bangumi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ligg.common.thirdparty.bangumi.model.BangumiSubject;
import lombok.Data;

import java.util.List;

/**
 * Bangumi 角色出演作品 {@code GET /p1/characters/{id}/casts} 响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CharacterCastsDto {

    private List<Item> data;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private BangumiSubject subject;
        private List<SubjectCharactersDto.Cast> casts;
        private Integer type;
    }
}
