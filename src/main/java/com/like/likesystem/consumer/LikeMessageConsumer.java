package com.like.likesystem.consumer;

import com.like.likesystem.event.LikeCountDeltaEvent;
import com.like.likesystem.repository.VideoRepository;
import com.like.likesystem.service.LikeFlowMetricsService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LikeMessageConsumer {

    private final VideoRepository videoRepository;
    private final LikeFlowMetricsService likeFlowMetricsService;

    @RabbitListener(queues = "like.aggregate.queue", ackMode = "MANUAL")
    @Transactional
    public void receiveAggregateMessage(LikeCountDeltaEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "consumer.aggregate.received");
            int updated = videoRepository.incrementLikeCountBy(event.getVideoId(), event.getDelta());
            if (updated == 0) {
                throw new IllegalArgumentException("Video not found");
            }
            likeFlowMetricsService.add(LikeFlowMetricsService.BUFFERED_ASYNC, "db.aggregate.updated", event.getDelta());

            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, false);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e);
        }
    }
}
