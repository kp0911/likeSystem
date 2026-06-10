package com.like.likesystem.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeFlowMetricsServiceTest {

    private final LikeFlowMetricsService service = new LikeFlowMetricsService();

    @Test
    void incrementsAndAddsStepCounters() {
        service.increment("sync", "request.received");
        service.add("sync", "db.direct.updated", 10L);

        assertThat(service.snapshot().get("sync").get("request.received")).isEqualTo(1L);
        assertThat(service.snapshot().get("sync").get("db.direct.updated")).isEqualTo(10L);
    }
}
