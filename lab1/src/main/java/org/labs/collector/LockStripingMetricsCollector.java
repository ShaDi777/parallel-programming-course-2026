package org.labs.collector;

import java.util.concurrent.atomic.AtomicLong;
import org.labs.Snapshot;

public class LockStripingMetricsCollector implements MetricsCollector {
    private static final int BLOCK_COUNT = 16;
    private static final int BUCKET_COUNT = 256;

    private final long[] buckets = new long[BUCKET_COUNT];
    private final Object[] locks = new Object[BLOCK_COUNT];

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

    public LockStripingMetricsCollector() {
        for (int i = 0; i < BLOCK_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = getBucket(value);
        synchronized (locks[bucket % BLOCK_COUNT]) {
            buckets[bucket]++;
        }

        sum.addAndGet(value);
        count.incrementAndGet();

        long current = min.get();
        while (current > value) {
            if (min.compareAndSet(current, value)) {
                break;
            }
            current = min.get();
        }

        current = max.get();
        while (current < value) {
            if (max.compareAndSet(current, value)) {
                break;
            }
            current = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[BUCKET_COUNT];
        for (int i = 0; i < BLOCK_COUNT; i++) {
            synchronized (locks[i]) {
                for (int j = 0; j < BUCKET_COUNT / BLOCK_COUNT; j++) {
                    bucketsCopy[i + j * BLOCK_COUNT] += buckets[i + j * BLOCK_COUNT];
                }
            }
        }

        long c = count.get();
        return new Snapshot(
            bucketsCopy,
            c,
            sum.get(),
            min.get(),
            max.get(),
            computePercentile(bucketsCopy, c, 0.5),
            computePercentile(bucketsCopy, c, 0.99)
        );
    }
}
