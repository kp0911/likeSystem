package com.like.likesystem.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class LikeMetricsService {

    private final ConcurrentHashMap<String, EndpointMetric> metrics = new ConcurrentHashMap<>();

    public long start() {
        return System.nanoTime();
    }

    public void record(String endpoint, long startedAtNanos, boolean success) {
        EndpointMetric metric = metrics.computeIfAbsent(endpoint, ignored -> new EndpointMetric());
        metric.requests.increment();
        if (success) {
            metric.successes.increment();
        } else {
            metric.failures.increment();
        }
        metric.totalDurationNanos.add(System.nanoTime() - startedAtNanos);
    }

    public Map<String, EndpointMetricSnapshot> snapshot() {
        Map<String, EndpointMetricSnapshot> snapshot = new LinkedHashMap<>();
        snapshot.put("sync", snapshotOf("sync"));
        snapshot.put("buffered-async", snapshotOf("buffered-async"));
        return snapshot;
    }

    private EndpointMetricSnapshot snapshotOf(String endpoint) {
        EndpointMetric metric = metrics.computeIfAbsent(endpoint, ignored -> new EndpointMetric());
        long requests = metric.requests.sum();
        long successes = metric.successes.sum();
        long failures = metric.failures.sum();
        double averageDurationMs = requests == 0
                ? 0.0
                : metric.totalDurationNanos.sum() / 1_000_000.0 / requests;
        return new EndpointMetricSnapshot(requests, successes, failures, averageDurationMs);
    }

    private static class EndpointMetric {
        private final LongAdder requests = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
    }

    public record EndpointMetricSnapshot(
            long requests,
            long successes,
            long failures,
            double averageDurationMs
    ) {
    }
}
