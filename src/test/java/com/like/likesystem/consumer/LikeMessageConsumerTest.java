package com.like.likesystem.consumer;

import com.like.likesystem.event.LikeCountDeltaEvent;
import com.like.likesystem.domain.Video;
import com.like.likesystem.event.LikeEvent;
import com.like.likesystem.repository.VideoRepository;
import com.like.likesystem.service.LikeFlowMetricsService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeMessageConsumerTest {

    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final Channel channel = mock(Channel.class);
    private final LikeMessageConsumer consumer = new LikeMessageConsumer(videoRepository, likeFlowMetricsService);

    @Test
    void receiveMessageIncrementsLikeAndAcks() throws IOException {
        Video video = new Video();
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));

        consumer.receiveMessage(new LikeEvent(1L, "user-1", "LIKE"), channel, 10L);

        assertThat(video.getLikeCount()).isEqualTo(1L);
        verify(channel).basicAck(10L, false);
        verify(channel, never()).basicNack(10L, false, false);
    }

    @Test
    void receiveMessageNacksAndRethrowsWhenVideoDoesNotExist() throws IOException {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.receiveMessage(new LikeEvent(1L, "user-1", "LIKE"), channel, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");

        verify(channel).basicNack(10L, false, false);
        verify(channel, never()).basicAck(10L, false);
    }

    @Test
    void receiveAggregateMessageIncrementsLikeByDeltaAndAcks() throws IOException {
        when(videoRepository.incrementLikeCountBy(1L, 100L)).thenReturn(1);

        consumer.receiveAggregateMessage(new LikeCountDeltaEvent(1L, 100L), channel, 10L);

        verify(videoRepository).incrementLikeCountBy(1L, 100L);
        verify(channel).basicAck(10L, false);
        verify(channel, never()).basicNack(10L, false, false);
    }

    @Test
    void receiveAggregateMessageNacksAndRethrowsWhenVideoDoesNotExist() throws IOException {
        when(videoRepository.incrementLikeCountBy(1L, 100L)).thenReturn(0);

        assertThatThrownBy(() -> consumer.receiveAggregateMessage(new LikeCountDeltaEvent(1L, 100L), channel, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");

        verify(channel).basicNack(10L, false, false);
        verify(channel, never()).basicAck(10L, false);
    }
}
