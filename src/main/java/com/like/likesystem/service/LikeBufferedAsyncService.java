package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
import com.like.likesystem.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LikeBufferedAsyncService {

    public static final String USER_KEY_PREFIX = "like:buffered:user:video:";
    public static final String DISPLAY_COUNT_KEY_PREFIX = "like:buffered:display:video:";
    public static final String PENDING_COUNT_KEY_PREFIX = "like:buffered:pending:video:";
    public static final String VIDEO_EXISTS_KEY_PREFIX = "like:buffered:video-exists:";

    private static final DefaultRedisScript<Long> REGISTER_LIKE_SCRIPT = new DefaultRedisScript<>("""
            local added = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1])
            if added then
                redis.call('INCR', KEYS[2])
                redis.call('INCR', KEYS[3])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final LikeDisplayCountService likeDisplayCountService;
    private final LikeFlowMetricsService likeFlowMetricsService;
    private final long userDedupTtlSeconds;

    public LikeBufferedAsyncService(
            StringRedisTemplate redisTemplate,
            VideoRepository videoRepository,
            LikeDisplayCountService likeDisplayCountService,
            LikeFlowMetricsService likeFlowMetricsService,
            @Value("${like.buffered.user-dedup-ttl-seconds:2592000}") long userDedupTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.videoRepository = videoRepository;
        this.likeDisplayCountService = likeDisplayCountService;
        this.likeFlowMetricsService = likeFlowMetricsService;
        if (userDedupTtlSeconds <= 0) {
            throw new IllegalArgumentException("like.buffered.user-dedup-ttl-seconds must be positive");
        }
        this.userDedupTtlSeconds = userDedupTtlSeconds;
    }

    public void processLike(Long videoId, String userId) {
        validateVideoExistsAndInitializeDisplayCount(videoId);

        String userKey = USER_KEY_PREFIX + videoId + ":user:" + userId;
        String displayCountKey = DISPLAY_COUNT_KEY_PREFIX + videoId;
        String pendingCountKey = PENDING_COUNT_KEY_PREFIX + videoId;

        Long added = redisTemplate.execute(
                REGISTER_LIKE_SCRIPT,
                List.of(userKey, displayCountKey, pendingCountKey),
                Long.toString(userDedupTtlSeconds)
        );

        if (added != null && added > 0) {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.added");
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.display.incremented");
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.pending.incremented");
        } else {
            likeFlowMetricsService.increment(LikeFlowMetricsService.BUFFERED_ASYNC, "redis.dedup.duplicate");
        }
    }

    private void validateVideoExistsAndInitializeDisplayCount(Long videoId) {
        String videoExistsKey = VIDEO_EXISTS_KEY_PREFIX + videoId;
        Video video = null;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(videoExistsKey))) {
            video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found"));
            redisTemplate.opsForValue().set(videoExistsKey, "1");
        }

        if (!likeDisplayCountService.isInitialized(videoId)) {
            if (video == null) {
                video = videoRepository.findById(videoId)
                        .orElseThrow(() -> new IllegalArgumentException("Video not found"));
            }
            likeDisplayCountService.initializeIfAbsent(videoId, video.getLikeCount());
        }
    }
}
