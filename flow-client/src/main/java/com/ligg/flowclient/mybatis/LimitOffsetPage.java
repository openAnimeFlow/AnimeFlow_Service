package com.ligg.flowclient.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 将 Bangumi 风格的 {@code limit}/{@code offset} 适配为 MyBatis-Plus 分页插件入参。
 * <p>
 * 分页拦截器通过 {@link #offset()} 计算 SQL 偏移量，重写后可支持任意 offset，而不局限于页码对齐。
 */
public class LimitOffsetPage<T> extends Page<T> {

    private final long rowOffset;

    public LimitOffsetPage(long limit, long offset) {
        super(1, limit);
        this.rowOffset = Math.max(offset, 0);
    }

    @Override
    public long offset() {
        return rowOffset;
    }
}
