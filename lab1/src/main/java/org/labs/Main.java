package org.labs;

import org.labs.collector.DoubleBufferingMetricsCollector;
import org.labs.collector.DoubleBufferingWeakMetricsCollector;
import org.labs.collector.LockStripingMetricsCollector;
import org.labs.collector.MetricsCollector;
import org.labs.collector.PlainMetricsCollector;
import org.labs.collector.SynchronizedEmptyMetricsCollector;
import org.labs.collector.SynchronizedMetricsCollector;
import org.labs.collector.ThreadLocalMetricsCollector;

public class Main {
    static long[] values = ZipfGenerator.generateRandomArray();

    /*
    static void main() throws InterruptedException {
        int kThreads = 4;
        var metricsCollector = new DoubleBufferingWeakMetricsCollector();
        var result = InconsistencyStressTest.run(metricsCollector, values, kThreads);
        double brokenPart = 100 * (double) (result.less() + result.more()) / (result.ok() + result.less() + result.more());
        System.out.printf("STRESS collector=%s threads=%d -> broke=%.2f%% / sum<count=%d / sum>count=%d / ok=%d / snap.count-actual=%d %n",
                          "lockStriping", kThreads, brokenPart, result.less(), result.more(), result.ok(),  result.finalDiff());
    }
    */

    /**
     * Usage java Main {@literal <mode> <collector> <kThreads>} </br>
     * mode: benchmark / stress </br>
     * collector: plain / synchronized / synchronizedEmpty / lockStriping / threadLocal / doubleBuffer </br>
     * kThreads: int >= 1
     */
    static void main(String[] args) throws InterruptedException {
        String mode = args[0];
        String collectorName = args[1];
        int kThreads = Integer.parseInt(args[2]);

        MetricsCollector metricsCollector = getCollector(collectorName);
        switch (mode) {
            case "benchmark" -> {
                double opPerSec = Benchmark.measurePoint(metricsCollector, values, kThreads);
                System.out.printf("BENCH collector=%s threads=%d -> %.2f ops/sec (%.2f M ops/sec)%n",
                                  collectorName, kThreads, opPerSec, opPerSec / 1e6);
            }
            case "stress" -> {
                var result = InconsistencyStressTest.run(metricsCollector, values, kThreads);
                double brokenPart = 100 * (double) (result.less() + result.more()) / (result.ok() + result.less() + result.more());
                System.out.printf("STRESS collector=%s threads=%d -> broke=%.2f%% / sum<count=%d / sum>count=%d / snap.count-actual=%d %n",
                                  collectorName, kThreads, brokenPart, result.less(), result.more(), result.finalDiff());
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    static MetricsCollector getCollector(String collectorName) {
        return switch (collectorName) {
            case "plain" -> new PlainMetricsCollector();
            case "synchronized" -> new SynchronizedMetricsCollector();
            case "synchronizedEmpty" -> new SynchronizedEmptyMetricsCollector();
            case "lockStriping" -> new LockStripingMetricsCollector();
            case "threadLocal" -> new ThreadLocalMetricsCollector();
            case "doubleBuffer" -> new DoubleBufferingMetricsCollector();
            case "doubleBufferWeak" -> new DoubleBufferingWeakMetricsCollector();
            default -> throw new IllegalStateException("Unexpected value: " + collectorName);
        };
    }
}
