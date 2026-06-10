package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
import com.like.likesystem.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@RequiredArgsConstructor
public class LikeSystemStateService {

    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final RabbitAdmin rabbitAdmin;

    public LikeSystemStateSnapshot snapshot(Long videoId) {
        return new LikeSystemStateSnapshot(
                videoId,
                readLong("like:count:video:" + videoId),
                readLong(LikeBufferedAsyncService.DISPLAY_COUNT_KEY_PREFIX + videoId),
                readLong(LikeBufferedAsyncService.PENDING_COUNT_KEY_PREFIX + videoId),
                videoRepository.findById(videoId).map(Video::getLikeCount).orElse(0L),
                queueMessages("like.queue"),
                queueMessages("like.aggregate.queue")
        );
    }

    private long readLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private long queueMessages(String queueName) {
        Properties properties = rabbitAdmin.getQueueProperties(queueName);
        if (properties == null) {
            return 0L;
        }

        Object messageCount = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        if (messageCount instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public record LikeSystemStateSnapshot(
            Long videoId,
            long asyncEventRedisCount,
            long bufferedDisplayCount,
            long bufferedPendingCount,
            long databaseLikeCount,
            long eventQueueMessages,
            long aggregateQueueMessages
    ) {
    }
}
