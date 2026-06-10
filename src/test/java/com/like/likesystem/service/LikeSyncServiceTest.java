package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
import com.like.likesystem.repository.VideoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LikeSyncServiceTest {

    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final LikeSyncService likeSyncService = new LikeSyncService(videoRepository, likeFlowMetricsService);

    @Test
    void processLikeIncrementsLikeCount() {
        Video video = new Video();
        when(videoRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(video));

        likeSyncService.processLike(1L);

        assertThat(video.getLikeCount()).isEqualTo(1L);
    }

    @Test
    void processLikeThrowsWhenVideoDoesNotExist() {
        when(videoRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeSyncService.processLike(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");
    }
}
