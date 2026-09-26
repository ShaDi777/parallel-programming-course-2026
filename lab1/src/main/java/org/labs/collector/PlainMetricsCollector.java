package org.labs.collector;

import org.labs.Snapshot;

public class PlainMetricsCollector implements MetricsCollector {
    long[] buckets = new long[256];
    long count = 0;
    long sum = 0;
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;

    @Override
    public void record(long value) {
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
        buckets[getBucket(value)]++;
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = buckets.clone();
        return new Snapshot(
            bucketsCopy,
            count,
            sum,
            min,
            max,
            computePercentile(bucketsCopy, count, 0.5),
            computePercentile(bucketsCopy, count, 0.99)
        );
    }
}
