package com.like.likesystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "like.buffered.flush-enabled", havingValue = "true", matchIfMissing = true)
public class LikeBufferedCountFlushScheduler {

    private final StringRedisTemplate redisTemplate;
    private final LikeBufferedCountFlushService flushService;

    @Scheduled(fixedDelayString = "${like.buffered.flush-interval-ms:1000}")
    public void flushPendingLikeCounts() throws IOException {
        flushService.publishPendingOutboxEvents();

        ScanOptions options = ScanOptions.scanOptions()
                .match(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX + "*")
                .count(1000)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                flushService.flushPendingCount(cursor.next());
            }
        }
    }
}
