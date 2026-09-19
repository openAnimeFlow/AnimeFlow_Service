package com.ligg.flowclient.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Connection-owned MySQL lock: no expiring lease can let an old upload overtake a new one. */
@Component
@RequiredArgsConstructor
public class CollectionWriteLock {
    private final DataSource dataSource;

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
                    // Never return a pooled connection that might still own this lock.
                    connection.abort(Runnable::run);
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("收藏更新锁不可用", e);
        }
    }
}
