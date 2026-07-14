package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeAggregateEventPublisher {

    private static final String EXCHANGE = "like.exchange";
    private static final String ROUTING_KEY = "like.aggregate.routing.key";

    private final RabbitTemplate rabbitTemplate;

    public boolean publish(LikeCountDeltaEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getEventId());

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm != null && confirm.isAck() && correlationData.getReturned() == null) {
                return true;
            }

            log.warn("RabbitMQ publish was not confirmed or routed. eventId={}, reason={}, returned={}",
                    event.getEventId(),
                    confirm == null ? "no confirm" : confirm.getReason(),
                    correlationData.getReturned() != null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("RabbitMQ publish was interrupted. eventId={}", event.getEventId(), e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("RabbitMQ publish failed. eventId={}", event.getEventId(), e);
        }

        return false;
    }
}
