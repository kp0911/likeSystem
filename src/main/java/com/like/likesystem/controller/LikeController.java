package com.like.likesystem.controller;

import com.like.likesystem.service.LikeAsyncService;
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

    @PostMapping("/sync")
    public ResponseEntity<Void> likeSync(@Valid @RequestBody SyncLikeRequest request) {
        likeSyncService.processLike(request.getVideoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/async")
    public ResponseEntity<Void> likeAsync(@Valid @RequestBody AsyncLikeRequest request) {
        likeAsyncService.processLike(request.getVideoId(), request.getUserId());
        return ResponseEntity.ok().build();
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
