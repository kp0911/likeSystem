package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeSyncServiceTest {

    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final LikeSyncService likeSyncService = new LikeSyncService(videoRepository, likeFlowMetricsService);

    @Test
    void processLikeAtomicallyIncrementsLikeCount() {
        when(videoRepository.incrementLikeCountBy(1L, 1L)).thenReturn(1);

        likeSyncService.processLike(1L);

        verify(videoRepository).incrementLikeCountBy(1L, 1L);
    }

    @Test
    void processLikeThrowsWhenVideoDoesNotExist() {
        when(videoRepository.incrementLikeCountBy(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> likeSyncService.processLike(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");
    }
}
