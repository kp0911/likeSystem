package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeBufferedCountFlushService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final LikeFlowMetricsService likeFlowMetricsService;

    public Optional<LikeCountDeltaEvent> flushPendingCount(String pendingCountKey) {
        if (!pendingCountKey.startsWith(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX)) {
            return Optional.empty();
        }

        String count = redisTemplate.opsForValue().getAndSet(pendingCountKey, "0");
        long delta = parseCount(count);

        if (delta <= 0) {
            return Optional.empty();
        }

        Long videoId = parseVideoId(pendingCountKey);
        LikeCountDeltaEvent event = new LikeCountDeltaEvent(videoId, delta);
        rabbitTemplate.convertAndSend("like.exchange", "like.aggregate.routing.key", event);
        likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "scheduler.flush.executed");
        likeFlowMetricsService.add(LikeFlowMetricsService.BUFFERED_ASYNC, "scheduler.flush.delta", delta);
        likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "rabbit.aggregate.published");
        return Optional.of(event);
    }

    private long parseCount(String count) {
        if (count == null || count.isBlank()) {
            return 0L;
        }
        return Long.parseLong(count);
    }

    private Long parseVideoId(String pendingCountKey) {
        String videoId = pendingCountKey.substring(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX.length());
        return Long.valueOf(videoId);
    }
}
