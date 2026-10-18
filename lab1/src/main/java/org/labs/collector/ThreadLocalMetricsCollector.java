package org.labs.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import jdk.internal.vm.annotation.Contended;
import org.labs.Snapshot;

public class ThreadLocalMetricsCollector implements MetricsCollector {
    @Contended
    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock  = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (listLock) {
            allStates.add(state);
        }
        return state;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = getBucket(value);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        // min и max обновляются обычным сравнением без CAS:
        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] out = new long[256];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        List<ThreadState> copyOfStates;
        synchronized (listLock) {
            copyOfStates = new ArrayList<>(allStates);
        }

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < 256; i++) out[i] += s.buckets.get(i); // C++: .load(relaxed)
            count += s.count.get();

            sum   += s.sum.get();
            min    = Math.min(min, s.min.get());
            max    = Math.max(max, s.max.get());
        }

        long p50 = computePercentile(out, count, 0.50);
        long p99 = computePercentile(out, count, 0.99);
        return new Snapshot(out, count, sum, min, max, p50, p99);
    }
}
