package com.like.likesystem.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class LikeFlowMetricsService {

    public static final String SYNC = "sync";
    public static final String BUFFERED_ASYNC = "buffered-async";

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, LongAdder>> metrics = new ConcurrentHashMap<>();

    public void increment(String mode, String step) {
        metrics.computeIfAbsent(mode, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(step, ignored -> new LongAdder())
                .increment();
    }

    public void add(String mode, String step, long count) {
        metrics.computeIfAbsent(mode, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(step, ignored -> new LongAdder())
                .add(count);
    }

    public Map<String, Map<String, Long>> snapshot() {
        Map<String, Map<String, Long>> snapshot = new LinkedHashMap<>();
        snapshot.put(SYNC, snapshotOf(SYNC));
        snapshot.put(BUFFERED_ASYNC, snapshotOf(BUFFERED_ASYNC));
        return snapshot;
    }

    private Map<String, Long> snapshotOf(String mode) {
        Map<String, Long> values = new LinkedHashMap<>();
        ConcurrentHashMap<String, LongAdder> steps = metrics.computeIfAbsent(mode, ignored -> new ConcurrentHashMap<>());
        steps.forEach((step, value) -> values.put(step, value.sum()));
        return values;
    }
}
