package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LikeBufferedAsyncService {

    public static final String USER_KEY_PREFIX = "like:buffered:users:video:";
    public static final String DISPLAY_COUNT_KEY_PREFIX = "like:buffered:display:video:";
    public static final String PENDING_COUNT_KEY_PREFIX = "like:buffered:pending:video:";
    public static final String VIDEO_EXISTS_KEY_PREFIX = "like:buffered:video-exists:";

    private static final DefaultRedisScript<Long> REGISTER_LIKE_SCRIPT = new DefaultRedisScript<>("""
            local added = redis.call('SADD', KEYS[1], ARGV[1])
            if added == 1 then
                redis.call('INCR', KEYS[2])
                redis.call('INCR', KEYS[3])
            end
            return added
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final LikeFlowMetricsService likeFlowMetricsService;

    public void processLike(Long videoId, String userId) {
        validateVideoExists(videoId);

        String userKey = USER_KEY_PREFIX + videoId;
        String displayCountKey = DISPLAY_COUNT_KEY_PREFIX + videoId;
        String pendingCountKey = PENDING_COUNT_KEY_PREFIX + videoId;

        Long added = redisTemplate.execute(
                REGISTER_LIKE_SCRIPT,
                List.of(userKey, displayCountKey, pendingCountKey),
                userId
        );

        if (added != null && added > 0) {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.added");
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.display.incremented");
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.pending.incremented");
        } else {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.duplicate");
        }
    }

    private void validateVideoExists(Long videoId) {
        String videoExistsKey = VIDEO_EXISTS_KEY_PREFIX + videoId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(videoExistsKey))) {
            return;
        }

        if (!videoRepository.existsById(videoId)) {
            throw new IllegalArgumentException("Video not found");
        }

        redisTemplate.opsForValue().set(videoExistsKey, "1");
    }
}
