package org.labs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.labs.collector.MetricsCollector;

public class Benchmark {

    public static double run(
        MetricsCollector metricsCollector,
        long[] values,
        int kThreads,
        int seconds
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

        long t0 = System.nanoTime();
        starter.countDown();
        Thread.sleep(Duration.ofSeconds(seconds));
        stop.set(true);
        long t1 = System.nanoTime();
        double measuredSeconds = (double) (t1 - t0) / 1e9;

        for (Thread t : threads) {
            t.join();
        }

        long total = Arrays.stream(ops).sum();
        return total / measuredSeconds;
    }

    public static double measurePoint(
        MetricsCollector metricsCollector,
        long[] values,
        int kThreads
    ) throws InterruptedException {
        run(metricsCollector, values, kThreads, 5);
        var results = new ArrayList<Double>();
        for (int i = 0; i < 5; i++) {
            results.add(run(metricsCollector, values, kThreads, 5));
        }
        System.out.println("-- " + metricsCollector.snapshot().count());
        results.sort(Double::compare);
        return results.get(results.size() / 2);
    }
}
