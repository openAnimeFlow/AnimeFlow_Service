package com.ligg.flowscheduler.archive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 同步写入有行数及估算数据量上限的批次，consumer 返回后立即释放实体引用。 */
public final class ArchiveBatchWriter<T> {
    private final int maxRows;
    private final long maxBytes;
    private final Consumer<List<T>> consumer;
    private final List<T> batch;
    private long bytes;

    public ArchiveBatchWriter(int maxRows, long maxBytes, Consumer<List<T>> consumer) {
        this.maxRows = Math.max(1, maxRows);
        this.maxBytes = Math.max(1, maxBytes);
        this.consumer = consumer;
        this.batch = new ArrayList<>(Math.min(this.maxRows, 1024));
    }

    public void add(T row, long estimatedBytes) {
        if (!batch.isEmpty() && estimatedBytes > maxBytes - bytes) flush();
        batch.add(row);
        bytes += estimatedBytes;
        if (batch.size() >= maxRows || bytes >= maxBytes) flush();
    }

    public void flush() {
        if (batch.isEmpty()) return;
        consumer.accept(batch);
        batch.clear();
        bytes = 0;
    }
}
