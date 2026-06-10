package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
import com.like.likesystem.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeSystemStateServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeSystemStateService service = new LikeSystemStateService(redisTemplate, videoRepository, rabbitAdmin);

    @Test
    void combinesRedisRabbitAndDatabaseState() {
        Video video = new Video();
        video.addLike();
        Properties eventQueueProperties = new Properties();
        eventQueueProperties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 3);
        Properties aggregateQueueProperties = new Properties();
        aggregateQueueProperties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 2);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("like:count:video:1")).thenReturn("5");
        when(valueOperations.get("like:buffered:display:video:1")).thenReturn("7");
        when(valueOperations.get("like:buffered:pending:video:1")).thenReturn("4");
        when(videoRepository.findById(1L)).thenReturn(Optional.of(video));
        when(rabbitAdmin.getQueueProperties("like.queue")).thenReturn(eventQueueProperties);
        when(rabbitAdmin.getQueueProperties("like.aggregate.queue")).thenReturn(aggregateQueueProperties);

        LikeSystemStateService.LikeSystemStateSnapshot snapshot = service.snapshot(1L);

        assertThat(snapshot.asyncEventRedisCount()).isEqualTo(5L);
        assertThat(snapshot.bufferedDisplayCount()).isEqualTo(7L);
        assertThat(snapshot.bufferedPendingCount()).isEqualTo(4L);
        assertThat(snapshot.databaseLikeCount()).isEqualTo(1L);
        assertThat(snapshot.eventQueueMessages()).isEqualTo(3L);
        assertThat(snapshot.aggregateQueueMessages()).isEqualTo(2L);
    }
}
