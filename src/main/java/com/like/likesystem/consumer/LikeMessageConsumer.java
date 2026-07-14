package com.like.likesystem.consumer;

import com.like.likesystem.domain.ProcessedLikeEvent;
import com.like.likesystem.event.LikeCountDeltaEvent;
import com.like.likesystem.repository.ProcessedLikeEventRepository;
import com.like.likesystem.repository.VideoRepository;
import com.like.likesystem.service.LikeFlowMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LikeMessageConsumer {

    private final VideoRepository videoRepository;
    private final ProcessedLikeEventRepository processedLikeEventRepository;
    private final LikeFlowMetricsService likeFlowMetricsService;

    @RabbitListener(queues = "like.aggregate.queue")
    @Transactional
    public void receiveAggregateMessage(LikeCountDeltaEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("Like event id is required");
        }

        likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "consumer.aggregate.received");
        if (processedLikeEventRepository.existsById(event.getEventId())) {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "consumer.aggregate.duplicate");
            return;
        }

        int updated = videoRepository.incrementLikeCountBy(event.getVideoId(), event.getDelta());
        if (updated == 0) {
            throw new IllegalArgumentException("Video not found");
        }

        processedLikeEventRepository.save(new ProcessedLikeEvent(event.getEventId()));
        likeFlowMetricsService.add(LikeFlowMetricsService.BUFFERED_ASYNC, "db.aggregate.updated", event.getDelta());
    }
}
