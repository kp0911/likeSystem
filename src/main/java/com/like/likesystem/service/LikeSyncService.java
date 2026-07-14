package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeSyncService {

    private final VideoRepository videoRepository;
    private final LikeFlowMetricsService likeFlowMetricsService;

    @Transactional
    public void processLike(Long videoId) {
        int updated = videoRepository.incrementLikeCountBy(videoId, 1L);
        if (updated == 0) {
            throw new IllegalArgumentException("Video not found");
        }
        likeFlowMetricsService.increment(LikeFlowMetricsService.SYNC, "db.direct.updated");
    }
}
