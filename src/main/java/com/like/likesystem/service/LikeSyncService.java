package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class LikeSyncService {

    private final VideoRepository videoRepository;
    private final LikeDisplayCountService likeDisplayCountService;
    private final LikeFlowMetricsService likeFlowMetricsService;

    @Transactional
    public void processLike(Long videoId) {
        int updated = videoRepository.incrementLikeCountBy(videoId, 1L);
        if (updated == 0) {
            throw new IllegalArgumentException("Video not found");
        }
        incrementDisplayCountAfterCommit(videoId);
        likeFlowMetricsService.increment(LikeFlowMetricsService.SYNC, "db.direct.updated");
    }

    private void incrementDisplayCountAfterCommit(Long videoId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            likeDisplayCountService.incrementIfInitialized(videoId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                likeDisplayCountService.incrementIfInitialized(videoId);
            }
        });
    }
}
