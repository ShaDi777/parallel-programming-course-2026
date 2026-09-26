package org.labs.collector;

import org.labs.Snapshot;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();

    default int getBucket(long value) {
        return (int) Math.min(value / 4, 255);
    }

    default long computePercentile(long[] buckets, long totalCount, double perc) {
        double threshold = totalCount * perc;
        long total = 0;
        for (var i = 0; i < buckets.length; i++) {
            total += buckets[i];
            if (total >= threshold) {
                return i * 4L;
            }
        }
        return buckets.length * 4L;
    }
}
