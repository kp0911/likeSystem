package com.like.likesystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeBufferedAsyncServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeBufferedAsyncService likeBufferedAsyncService = new LikeBufferedAsyncService(redisTemplate);

    @Test
    void processLikeIncrementsDisplayAndPendingCountsWhenUserLikeIsNew() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.add("like:buffered:users:video:1", "user-1")).thenReturn(1L);

        likeBufferedAsyncService.processLike(1L, "user-1");

        verify(valueOperations).increment("like:buffered:display:video:1");
        verify(valueOperations).increment("like:buffered:pending:video:1");
    }

    @Test
    void processLikeDoesNothingWhenUserAlreadyLiked() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add("like:buffered:users:video:1", "user-1")).thenReturn(0L);

        likeBufferedAsyncService.processLike(1L, "user-1");

        verify(redisTemplate, never()).opsForValue();
    }
}
