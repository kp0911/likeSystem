package com.like.likesystem;

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
    public ResponseEntity<Void> likeSync(@RequestBody LikeRequest request) {
        likeSyncService.processLike(request.getVideoId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/async")
    public ResponseEntity<Void> likeAsync(@RequestBody LikeRequest request) {
        likeAsyncService.processLike(request.getVideoId(), request.getUserId());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class LikeRequest {
        private Long videoId;
        private String userId;
    }
}
