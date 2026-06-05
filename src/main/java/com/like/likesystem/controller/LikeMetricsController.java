package com.like.likesystem.controller;

import com.like.likesystem.service.LikeMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/like/metrics")
@RequiredArgsConstructor
public class LikeMetricsController {

    private final LikeMetricsService likeMetricsService;

    @GetMapping
    public Map<String, LikeMetricsService.EndpointMetricSnapshot> metrics() {
        return likeMetricsService.snapshot();
    }
}
