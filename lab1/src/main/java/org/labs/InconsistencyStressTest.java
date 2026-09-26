package org.labs;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.labs.collector.MetricsCollector;

public class InconsistencyStressTest {

    public static BucketResult run(
        MetricsCollector metricsCollector,
        long[] values,
        int kThreads
    ) throws InterruptedException {
        CountDownLatch starter = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[kThreads];
        Thread[] threads = new Thread[kThreads];

        for (int k = 0; k < threads.length; k++) {
            final int kFinal = k;
            threads[k] = new Thread(() -> {
                long count = 0;
                int i = kFinal * 1000;
                try {
                    starter.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                while (!stop.get()) {
                    metricsCollector.record(values[i]);
                    count++;
                    i = (i + 1) >= values.length ? 0 : i + 1;
                }
                ops[kFinal] = count;
            });
            threads[k].start();
        }

        starter.countDown();
        Thread.sleep(1000);

        long less = 0;
        long ok = 0;
        long more = 0;
        for (int i = 0; i < 10_000; i++) {
            Snapshot snap = metricsCollector.snapshot();
            long sumBucket = Arrays.stream(snap.buckets()).sum();

            if (sumBucket < snap.count()) less++;
            else if (sumBucket > snap.count()) more++;
            else ok++;
        }

        stop.set(true);
        for (Thread t : threads) {
            t.join();
        }

        Snapshot finalSnap =  metricsCollector.snapshot();
        long actualTotal = Arrays.stream(ops).sum();

        return new BucketResult(
            less,
            ok,
            more,
            finalSnap.count() - actualTotal
        );
    }

    public record BucketResult(
        long less,
        long ok,
        long more,
        long finalDiff
    ) {}
}
