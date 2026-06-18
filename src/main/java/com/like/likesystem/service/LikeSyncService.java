package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
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
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        video.addLike();
        likeFlowMetricsService.increment(LikeFlowMetricsService.SYNC, "db.direct.updated");
    }
}
