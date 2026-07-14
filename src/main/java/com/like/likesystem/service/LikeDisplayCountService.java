package com.like.likesystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeDisplayCountService {

    private static final DefaultRedisScript<Long> INCREMENT_IF_PRESENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('INCR', KEYS[1])
            end
            return nil
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void initializeIfAbsent(Long videoId, long databaseLikeCount) {
        redisTemplate.opsForValue().setIfAbsent(key(videoId), Long.toString(databaseLikeCount));
    }

    public void incrementIfInitialized(Long videoId) {
        try {
            redisTemplate.execute(INCREMENT_IF_PRESENT_SCRIPT, List.of(key(videoId)));
        } catch (RuntimeException e) {
            log.warn("Failed to update Redis display count after sync database update. videoId={}", videoId, e);
        }
    }

    public long readOrDefault(Long videoId, long databaseLikeCount) {
        String value = redisTemplate.opsForValue().get(key(videoId));
        if (value == null || value.isBlank()) {
            return databaseLikeCount;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid Redis display count. videoId={}", videoId, e);
            return databaseLikeCount;
        }
    }

    public boolean isInitialized(Long videoId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(videoId)));
    }

    private String key(Long videoId) {
        return LikeBufferedAsyncService.DISPLAY_COUNT_KEY_PREFIX + videoId;
    }
}
