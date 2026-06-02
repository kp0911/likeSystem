package com.like.likesystem.consumer;

import com.like.likesystem.domain.Video;
import com.like.likesystem.event.LikeEvent;
import com.like.likesystem.repository.VideoRepository;
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

    @RabbitListener(queues = "like.queue", ackMode = "MANUAL")
    @Transactional
    public void receiveMessage(LikeEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            Video video = videoRepository.findById(event.getVideoId())
                    .orElseThrow(() -> new IllegalArgumentException("Video not found"));
            video.addLike();

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
