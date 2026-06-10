package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeBufferedCountFlushServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeBufferedCountFlushService flushService = new LikeBufferedCountFlushService(redisTemplate, rabbitTemplate, likeFlowMetricsService);

    @Test
    void flushPendingCountPublishesAggregateEventAndResetsCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndSet("like:buffered:pending:video:1", "0")).thenReturn("100");

        Optional<LikeCountDeltaEvent> event = flushService.flushPendingCount("like:buffered:pending:video:1");

        assertThat(event).isPresent();
        assertThat(event.get().getVideoId()).isEqualTo(1L);
        assertThat(event.get().getDelta()).isEqualTo(100L);

        ArgumentCaptor<LikeCountDeltaEvent> eventCaptor = ArgumentCaptor.forClass(LikeCountDeltaEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("like.exchange"), eq("like.aggregate.routing.key"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getVideoId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getDelta()).isEqualTo(100L);
    }

    @Test
    void flushPendingCountDoesNotPublishWhenCountIsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndSet("like:buffered:pending:video:1", "0")).thenReturn("0");

        Optional<LikeCountDeltaEvent> event = flushService.flushPendingCount("like:buffered:pending:video:1");

        assertThat(event).isEmpty();
        verify(rabbitTemplate, never()).convertAndSend(
                eq("like.exchange"),
                eq("like.aggregate.routing.key"),
                org.mockito.ArgumentMatchers.any(LikeCountDeltaEvent.class)
        );
    }
}
