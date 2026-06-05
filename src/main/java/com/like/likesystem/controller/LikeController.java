package com.like.likesystem.controller;

import com.like.likesystem.service.LikeAsyncService;
import com.like.likesystem.service.LikeBufferedAsyncService;
import com.like.likesystem.service.LikeMetricsService;
import com.like.likesystem.service.LikeSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeSyncService likeSyncService;
    private final LikeAsyncService likeAsyncService;
    private final LikeBufferedAsyncService likeBufferedAsyncService;
    private final LikeMetricsService likeMetricsService;

    @PostMapping("/sync")
    public ResponseEntity<Void> likeSync(@Valid @RequestBody SyncLikeRequest request) {
        long startedAt = likeMetricsService.start();
        try {
            likeSyncService.processLike(request.getVideoId());
            likeMetricsService.record("sync", startedAt, true);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            likeMetricsService.record("sync", startedAt, false);
            throw e;
        }
    }

    @PostMapping("/async")
    public ResponseEntity<Void> likeAsync(@Valid @RequestBody AsyncLikeRequest request) {
        long startedAt = likeMetricsService.start();
        try {
            likeAsyncService.processLike(request.getVideoId(), request.getUserId());
            likeMetricsService.record("async-event", startedAt, true);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            likeMetricsService.record("async-event", startedAt, false);
            throw e;
        }
    }

    @PostMapping("/buffered-async")
    public ResponseEntity<Void> likeBufferedAsync(@Valid @RequestBody AsyncLikeRequest request) {
        long startedAt = likeMetricsService.start();
        try {
            likeBufferedAsyncService.processLike(request.getVideoId(), request.getUserId());
            likeMetricsService.record("buffered-async", startedAt, true);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            likeMetricsService.record("buffered-async", startedAt, false);
            throw e;
        }
    }

    @Data
    public static class SyncLikeRequest {
        @NotNull
        @Positive
        private Long videoId;
    }

    @Data
    public static class AsyncLikeRequest {
        @NotNull
        @Positive
        private Long videoId;

        @NotBlank
        private String userId;
    }
}
