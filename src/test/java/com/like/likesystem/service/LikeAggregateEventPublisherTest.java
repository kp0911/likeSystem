package com.like.likesystem.service;

import com.like.likesystem.event.LikeCountDeltaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class LikeAggregateEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LikeAggregateEventPublisher publisher = new LikeAggregateEventPublisher(rabbitTemplate);

    @Test
    void publishReturnsTrueOnlyWhenRabbitMqConfirmsEvent() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("like.exchange"),
                eq("like.aggregate.routing.key"),
                any(LikeCountDeltaEvent.class),
                any(CorrelationData.class)
        );

        boolean published = publisher.publish(new LikeCountDeltaEvent("event-1", 1L, 100L));

        assertThat(published).isTrue();
    }

    @Test
    void publishReturnsFalseWhenRabbitMqRejectsEvent() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("like.exchange"),
                eq("like.aggregate.routing.key"),
                any(LikeCountDeltaEvent.class),
                any(CorrelationData.class)
        );

        boolean published = publisher.publish(new LikeCountDeltaEvent("event-1", 1L, 100L));

        assertThat(published).isFalse();
    }

    @Test
    void publishReturnsFalseWhenRabbitMqReturnsUnroutableEvent() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(mock(org.springframework.amqp.core.ReturnedMessage.class));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("like.exchange"),
                eq("like.aggregate.routing.key"),
                any(LikeCountDeltaEvent.class),
                any(CorrelationData.class)
        );

        boolean published = publisher.publish(new LikeCountDeltaEvent("event-1", 1L, 100L));

        assertThat(published).isFalse();
    }
}
