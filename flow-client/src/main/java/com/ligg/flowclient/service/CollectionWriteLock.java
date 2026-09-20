package com.ligg.flowclient.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * 基于 MySQL 命名锁实现收藏写入串行化。
 *
 * <p>{@code GET_LOCK} 和 {@code RELEASE_LOCK} 都绑定到获取锁时的物理数据库连接，
 * 因此这里必须直接持有连接，不能拆成普通 MyBatis Mapper 调用，否则连接池可能让
 * 获取锁和释放锁使用不同连接。锁会一直持有到 {@code action} 执行结束，避免旧的
 * 上传、同步或本地编辑覆盖后续写入。</p>
 *
 * <p>锁键按数据库、用户和条目隔离；获取不到锁时立即失败，不在请求线程中等待。</p>
 */
@Component
@RequiredArgsConstructor
public class CollectionWriteLock {
    private final DataSource dataSource;

    /**
     * 在指定用户条目的写锁保护下执行操作，并保证锁最终释放。
     *
     * @param userId 用户 ID
     * @param subjectId 条目 ID；任务级操作使用约定的负数值
     * @param action 需要在锁内执行的操作
     * @param <T> 操作返回值类型
     * @return 操作返回值
     */
    public <T> T execute(Long userId, int subjectId, Supplier<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            String key = "flow:collection:" + Integer.toHexString(connection.getCatalog().hashCode())
                    + ':' + userId + ':' + subjectId;
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, key);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalStateException("该收藏正在更新，请稍后重试");
                    }
                }
            }
            try {
                return action.get();
            } finally {
                try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    statement.setString(1, key);
                    statement.execute();
                } catch (SQLException e) {
                    // 绝不能归还可能仍持有该锁的连接池连接。
                    connection.abort(Runnable::run);
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("收藏更新锁不可用", e);
        }
    }
}
