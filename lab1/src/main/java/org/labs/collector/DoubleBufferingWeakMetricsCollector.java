package org.labs.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.labs.Snapshot;

public class DoubleBufferingWeakMetricsCollector implements MetricsCollector {
    private static final int NOWHERE = -1;

    public static class ThreadDoubleBuffer {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {Long.MIN_VALUE, Long.MIN_VALUE};
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private volatile int active = 0;

    private final List<ThreadDoubleBuffer> allStates = new ArrayList<>();
    private final Object snapLock = new Object();
    private final ThreadLocal<ThreadDoubleBuffer> myState = ThreadLocal.withInitial(() -> {
        ThreadDoubleBuffer tdb = new ThreadDoubleBuffer();
        synchronized (snapLock) {
            allStates.add(tdb);
        }
        return tdb;
    });

    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = Long.MIN_VALUE;

    @Override
    public void record(long value) {
        ThreadDoubleBuffer myBufs = myState.get();
        int bufIdx;
        while (true) {
            var b = active;
            myBufs.inside.set(b);
            // if (b == active) {
                bufIdx = b;
                break;
            // }
            // myBufs.inside.setRelease(NOWHERE);
        }

        // Здесь буфер my.buf[b] гарантированно принадлежит нам:
        int bucketIdx = getBucket(value);
        myBufs.buckets[bufIdx][bucketIdx]++;
        myBufs.count[bufIdx]++;
        myBufs.sum[bufIdx] += value;
        myBufs.min[bufIdx] = Math.min(myBufs.min[bufIdx], value);
        myBufs.max[bufIdx] = Math.max(myBufs.max[bufIdx], value);

        myBufs.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadDoubleBuffer state : allStates) {
                while (state.inside.get() == old) {
                    Thread.onSpinWait();
                }

                // collect buffered results
                for (int i = 0; i < globalBuckets.length; i++) {
                    globalBuckets[i] += state.buckets[old][i];
                }
                globalCount += state.count[old];
                globalSum += state.sum[old];
                globalMin = Math.min(globalMin, state.min[old]);
                globalMax = Math.max(globalMax, state.max[old]);

                // clear old buffer
                Arrays.fill(state.buckets[old], 0);
                state.count[old] = 0;
                state.sum[old] = 0;
                state.min[old] = Long.MAX_VALUE;
                state.max[old] = Long.MIN_VALUE;
            }

            long[] out = globalBuckets.clone();
            return new Snapshot(
                out,
                globalCount,
                globalSum,
                globalMin,
                globalMax,
                computePercentile(out, globalCount, 0.5),
                computePercentile(out, globalCount, 0.99)
            );
        }
    }
}
