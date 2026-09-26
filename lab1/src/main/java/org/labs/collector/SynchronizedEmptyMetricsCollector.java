package org.labs.collector;

public class SynchronizedEmptyMetricsCollector extends PlainMetricsCollector {
    @Override
    public void record(long value) {
        synchronized(this) {}
    }
}
