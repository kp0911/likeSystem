package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeBufferedCountFlushServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final LikeAggregateEventPublisher eventPublisher = mock(LikeAggregateEventPublisher.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final LikeBufferedCountFlushService flushService = new LikeBufferedCountFlushService(
            redisTemplate,
            eventPublisher,
            likeFlowMetricsService
    );

    @Test
    void flushPendingCountMovesDeltaToOutboxThenPublishesIt() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(100L);
        when(eventPublisher.publish(any())).thenReturn(true);

        Optional<LikeCountDeltaEvent> event = flushService.flushPendingCount("like:buffered:pending:video:1");

        assertThat(event).isPresent();
        assertThat(event.get().getVideoId()).isEqualTo(1L);
        assertThat(event.get().getDelta()).isEqualTo(100L);
        assertThat(event.get().getEventId()).isNotBlank();

        ArgumentCaptor<LikeCountDeltaEvent> eventCaptor = ArgumentCaptor.forClass(LikeCountDeltaEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getVideoId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getDelta()).isEqualTo(100L);
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith(LikeBufferedCountFlushService.OUTBOX_KEY_PREFIX));
    }

    @Test
    void flushPendingCountDoesNotPublishWhenCountIsZero() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(0L);

        Optional<LikeCountDeltaEvent> event = flushService.flushPendingCount("like:buffered:pending:video:1");

        assertThat(event).isEmpty();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void flushPendingCountKeepsOutboxWhenBrokerPublishFails() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(100L);
        when(eventPublisher.publish(any())).thenReturn(false);

        flushService.flushPendingCount("like:buffered:pending:video:1");

        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.startsWith(LikeBufferedCountFlushService.OUTBOX_KEY_PREFIX));
    }
}
