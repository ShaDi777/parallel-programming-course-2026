package org.labs.collector;

public class SynchronizedMetricsCollector extends PlainMetricsCollector {
    @Override
    public synchronized void record(long value) {
        super.record(value);
    }
}
