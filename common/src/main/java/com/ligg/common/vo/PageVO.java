package com.ligg.common.vo;

import java.util.List;

/**
 * 通用分页结果（与具体持久层解耦，供接口返回）。
 *
 * @param records 当前页数据
 * @param total   总条数
 * @param current 当前页码（从 1 开始）
 * @param size    每页条数
 * @param pages   总页数
 * @param <T>     记录类型
 * @author Ligg
 */
public record PageVO<T>(
        List<T> records,
        long total,
        long current,
        long size,
        long pages
) {
}
