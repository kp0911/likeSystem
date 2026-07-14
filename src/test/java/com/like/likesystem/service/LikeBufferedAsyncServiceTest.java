package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeBufferedAsyncServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final LikeFlowMetricsService likeFlowMetricsService = mock(LikeFlowMetricsService.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeBufferedAsyncService likeBufferedAsyncService = new LikeBufferedAsyncService(
            redisTemplate,
            videoRepository,
            likeFlowMetricsService
    );

    @Test
    void processLikeRunsAtomicRedisScriptWhenUserLikeIsNew() {
        when(redisTemplate.hasKey("like:buffered:video-exists:1")).thenReturn(false);
        when(videoRepository.existsById(1L)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(), anyList(), eq("user-1"))).thenReturn(1L);

        likeBufferedAsyncService.processLike(1L, "user-1");

        verify(valueOperations).set("like:buffered:video-exists:1", "1");
        verify(redisTemplate).execute(any(), anyList(), eq("user-1"));
    }

    @Test
    void processLikeRecordsDuplicateWhenAtomicScriptReturnsZero() {
        when(redisTemplate.hasKey("like:buffered:video-exists:1")).thenReturn(true);
        when(redisTemplate.execute(any(), anyList(), eq("user-1"))).thenReturn(0L);

        likeBufferedAsyncService.processLike(1L, "user-1");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void processLikeRejectsVideoThatDoesNotExistBeforeWritingRedisLikeState() {
        when(redisTemplate.hasKey("like:buffered:video-exists:999")).thenReturn(false);
        when(videoRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> likeBufferedAsyncService.processLike(999L, "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Video not found");

        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }
}
