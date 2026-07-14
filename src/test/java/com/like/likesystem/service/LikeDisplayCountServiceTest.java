package com.like.likesystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LikeDisplayCountServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LikeDisplayCountService service = new LikeDisplayCountService(redisTemplate);

    @Test
    void initializeIfAbsentUsesDatabaseLikeCountAsDisplayBaseline() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.initializeIfAbsent(1L, 42L);

        verify(valueOperations).setIfAbsent("like:buffered:display:video:1", "42");
    }

    @Test
    void readOrDefaultReturnsDatabaseCountWhenDisplayCacheIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("like:buffered:display:video:1")).thenReturn(null);

        long count = service.readOrDefault(1L, 42L);

        assertThat(count).isEqualTo(42L);
    }
}
