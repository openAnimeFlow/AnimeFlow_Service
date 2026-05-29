package com.ligg.common.thirdparty.bangumi.response;

import lombok.Data;

import java.util.List;

/**
 * Bangumi 角色吐槽列表（上游返回 JSON 数组，客户端组装为 {@code data}）。
 */
@Data
public class CharacterCommentsDto {

    private List<CharacterCommentDto> data;
}
