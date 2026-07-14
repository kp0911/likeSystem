package com.like.likesystem.service;

import com.like.likesystem.repository.VideoRepository;
import com.like.likesystem.repository.ProcessedLikeEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LikeTestStateResetService {

    private static final String AGGREGATE_QUEUE = "like.aggregate.queue";

    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final ProcessedLikeEventRepository processedLikeEventRepository;
    private final RabbitAdmin rabbitAdmin;
    private final LikeMetricsService likeMetricsService;
    private final LikeFlowMetricsService likeFlowMetricsService;

    @Transactional
    public ResetResult reset(Long videoId) {
        int updatedRows = videoRepository.resetLikeCount(videoId, 0L);
        long deletedProcessedEvents = processedLikeEventRepository.count();
        processedLikeEventRepository.deleteAllInBatch();
        long deletedRedisKeys = deleteBufferedKeys(videoId);
        rabbitAdmin.purgeQueue(AGGREGATE_QUEUE, true);
        likeMetricsService.reset();
        likeFlowMetricsService.reset();

        return new ResetResult(videoId, updatedRows, deletedProcessedEvents, deletedRedisKeys, AGGREGATE_QUEUE);
    }

    private long deleteBufferedKeys(Long videoId) {
        Set<String> keys = redisTemplate.keys("like:buffered:*:video:" + videoId);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }

        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0L : deleted;
    }

    public record ResetResult(
            Long videoId,
            int updatedDatabaseRows,
            long deletedProcessedEvents,
            long deletedRedisKeys,
            String purgedQueue
    ) {
    }
}
