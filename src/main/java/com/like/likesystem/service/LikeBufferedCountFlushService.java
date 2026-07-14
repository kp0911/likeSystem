package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeBufferedCountFlushService {

    public static final String OUTBOX_KEY_PREFIX = "like:buffered:outbox:event:";

    private static final DefaultRedisScript<Long> MOVE_PENDING_TO_OUTBOX_SCRIPT = new DefaultRedisScript<>("""
            local pending = redis.call('GET', KEYS[1])
            if not pending or tonumber(pending) <= 0 then
                return 0
            end
            redis.call('SET', KEYS[1], '0')
            redis.call('SET', KEYS[2], ARGV[1] .. ':' .. pending)
            return tonumber(pending)
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LikeAggregateEventPublisher eventPublisher;
    private final LikeFlowMetricsService likeFlowMetricsService;

    public Optional<LikeCountDeltaEvent> flushPendingCount(String pendingCountKey) {
        if (!pendingCountKey.startsWith(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX)) {
            return Optional.empty();
        }

        Long videoId = parseVideoId(pendingCountKey);
        String eventId = UUID.randomUUID().toString();
        String outboxKey = OUTBOX_KEY_PREFIX + eventId;
        Long movedCount = redisTemplate.execute(
                MOVE_PENDING_TO_OUTBOX_SCRIPT,
                List.of(pendingCountKey, outboxKey),
                videoId.toString()
        );
        long delta = movedCount == null ? 0L : movedCount;

        if (delta <= 0) {
            return Optional.empty();
        }

        LikeCountDeltaEvent event = new LikeCountDeltaEvent(eventId, videoId, delta);
        likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "scheduler.flush.executed");
        likeFlowMetricsService.add(LikeFlowMetricsService.BUFFERED_ASYNC, "scheduler.flush.delta", delta);
        publishOutboxEvent(outboxKey, event);
        return Optional.of(event);
    }

    public void publishPendingOutboxEvents() throws IOException {
        ScanOptions options = ScanOptions.scanOptions()
                .match(OUTBOX_KEY_PREFIX + "*")
                .count(1000)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                publishOutboxEvent(cursor.next());
            }
        }
    }

    private Long parseVideoId(String pendingCountKey) {
        String videoId = pendingCountKey.substring(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX.length());
        return Long.valueOf(videoId);
    }

    private void publishOutboxEvent(String outboxKey) {
        String payload = redisTemplate.opsForValue().get(outboxKey);
        if (payload == null || payload.isBlank()) {
            return;
        }

        String eventId = outboxKey.substring(OUTBOX_KEY_PREFIX.length());
        String[] values = payload.split(":", 2);
        if (values.length != 2) {
            log.error("Invalid like outbox payload. outboxKey={}", outboxKey);
            return;
        }

        try {
            LikeCountDeltaEvent event = new LikeCountDeltaEvent(
                    eventId,
                    Long.valueOf(values[0]),
                    Long.valueOf(values[1])
            );
            publishOutboxEvent(outboxKey, event);
        } catch (NumberFormatException e) {
            log.error("Invalid like outbox payload. outboxKey={}", outboxKey, e);
        }
    }

    private void publishOutboxEvent(String outboxKey, LikeCountDeltaEvent event) {
        if (!eventPublisher.publish(event)) {
            return;
        }

        redisTemplate.delete(outboxKey);
        likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "rabbit.aggregate.published");
    }
}
