package com.like.likesystem.consumer;

import com.like.likesystem.event.LikeCountDeltaEvent;
import com.like.likesystem.repository.ProcessedLikeEventRepository;
import com.like.likesystem.repository.VideoRepository;
import com.like.likesystem.service.LikeFlowMetricsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeMessageConsumerTest {

    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final ProcessedLikeEventRepository processedLikeEventRepository = mock(ProcessedLikeEventRepository.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final LikeMessageConsumer consumer = new LikeMessageConsumer(
            videoRepository,
            processedLikeEventRepository,
            likeFlowMetricsService
    );

    @Test
    void receiveAggregateMessageIncrementsLikeByDeltaAndRecordsEventId() {
        when(videoRepository.incrementLikeCountBy(1L, 100L)).thenReturn(1);

        consumer.receiveAggregateMessage(new LikeCountDeltaEvent("event-1", 1L, 100L));

        verify(videoRepository).incrementLikeCountBy(1L, 100L);
        verify(processedLikeEventRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receiveAggregateMessageRethrowsWhenVideoDoesNotExist() {
        when(videoRepository.incrementLikeCountBy(1L, 100L)).thenReturn(0);

        assertThatThrownBy(() -> consumer.receiveAggregateMessage(new LikeCountDeltaEvent("event-1", 1L, 100L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");

        verify(processedLikeEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receiveAggregateMessageSkipsAlreadyProcessedEvent() {
        when(processedLikeEventRepository.existsById("event-1")).thenReturn(true);

        consumer.receiveAggregateMessage(new LikeCountDeltaEvent("event-1", 1L, 100L));

        verify(videoRepository, never()).incrementLikeCountBy(1L, 100L);
    }
}
