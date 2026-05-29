package com.ligg.common.thirdparty.bangumi.response;

import lombok.Data;

import java.util.List;

/**
 * Bangumi 章节评论列表（上游返回 JSON 数组，客户端组装为 {@code data}）。
 */
@Data
public class EpisodeCommentsDto {

    private List<EpisodeCommentDto> data;
}
