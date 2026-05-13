package com.like.likesystem;

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
            
            LikeEvent event = new LikeEvent(videoId, userId, "LIKE");
            rabbitTemplate.convertAndSend("like.exchange", "like.routing.key", event);
        }
    }
}
