package com.like.likesystem.service;

import com.like.likesystem.event.LikeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeAsyncService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public void processLike(Long videoId, String userId) {
        String userKey = "like:users:video:" + videoId;
        String countKey = "like:count:video:" + videoId;

        Long added = redisTemplate.opsForSet().add(userKey, userId);

        if (added != null && added > 0) {
            redisTemplate.opsForValue().increment(countKey);

            try {
                LikeEvent event = new LikeEvent(videoId, userId, "LIKE");
                rabbitTemplate.convertAndSend("like.exchange", "like.routing.key", event);
            } catch (RuntimeException e) {
                redisTemplate.opsForSet().remove(userKey, userId);
                redisTemplate.opsForValue().decrement(countKey);
                throw e;
            }
        }
    }
}
