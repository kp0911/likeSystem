package com.like.likesystem.controller;

import com.like.likesystem.service.LikeMetricsService;
import com.like.likesystem.service.LikeFlowMetricsService;
import com.like.likesystem.service.LikeSystemStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/like")
@RequiredArgsConstructor
public class LikeMetricsController {

    private final LikeMetricsService likeMetricsService;
    private final LikeFlowMetricsService likeFlowMetricsService;
    private final LikeSystemStateService likeSystemStateService;

    @GetMapping("/metrics")
    public Map<String, LikeMetricsService.EndpointMetricSnapshot> metrics() {
        return likeMetricsService.snapshot();
    }

    @GetMapping("/flow-metrics")
    public Map<String, Map<String, Long>> flowMetrics() {
        return likeFlowMetricsService.snapshot();
    }

    @GetMapping("/system-state")
    public LikeSystemStateService.LikeSystemStateSnapshot systemState(
            @RequestParam(defaultValue = "1") Long videoId
    ) {
        return likeSystemStateService.snapshot(videoId);
    }
}
