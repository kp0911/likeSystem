package com.like.likesystem.service;

import com.like.likesystem.event.LikeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeAsyncServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeAsyncService likeAsyncService = new LikeAsyncService(redisTemplate, rabbitTemplate, likeFlowMetricsService);

    @Test
    void processLikePublishesEventWhenUserLikeIsNew() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.add("like:users:video:1", "user-1")).thenReturn(1L);

        likeAsyncService.processLike(1L, "user-1");

        verify(valueOperations).increment("like:count:video:1");

        ArgumentCaptor<LikeEvent> eventCaptor = ArgumentCaptor.forClass(LikeEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("like.exchange"), eq("like.routing.key"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getVideoId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(eventCaptor.getValue().getAction()).isEqualTo("LIKE");
    }

    @Test
    void processLikeDoesNothingWhenUserAlreadyLiked() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("like:users:video:1", "user-1")).thenReturn(0L);

        likeAsyncService.processLike(1L, "user-1");

        verify(redisTemplate, never()).opsForValue();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void processLikeCompensatesRedisWhenRabbitPublishFails() {
        RuntimeException publishFailure = new RuntimeException("publish failed");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.add("like:users:video:1", "user-1")).thenReturn(1L);
        org.mockito.Mockito.doThrow(publishFailure)
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatThrownBy(() -> likeAsyncService.processLike(1L, "user-1"))
                .isSameAs(publishFailure);

        verify(setOperations).remove("like:users:video:1", "user-1");
        verify(valueOperations).decrement("like:count:video:1");
    }
}
