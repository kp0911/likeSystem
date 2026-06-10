package com.like.likesystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeBufferedAsyncService {

    public static final String USER_KEY_PREFIX = "like:buffered:users:video:";
    public static final String DISPLAY_COUNT_KEY_PREFIX = "like:buffered:display:video:";
    public static final String PENDING_COUNT_KEY_PREFIX = "like:buffered:pending:video:";

    private final StringRedisTemplate redisTemplate;
    private final LikeFlowMetricsService likeFlowMetricsService;

    public void processLike(Long videoId, String userId) {
        String userKey = USER_KEY_PREFIX + videoId;
        String displayCountKey = DISPLAY_COUNT_KEY_PREFIX + videoId;
        String pendingCountKey = PENDING_COUNT_KEY_PREFIX + videoId;

        Long added = redisTemplate.opsForSet().add(userKey, userId);

        if (added != null && added > 0) {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.added");
            redisTemplate.opsForValue().increment(displayCountKey);
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.display.incremented");
            redisTemplate.opsForValue().increment(pendingCountKey);
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.pending.incremented");
        } else {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.duplicate");
        }
    }
}
