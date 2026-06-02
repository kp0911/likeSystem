package com.like.likesystem.service;

import com.like.likesystem.domain.Video;
import com.like.likesystem.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeSyncService {

    private final VideoRepository videoRepository;

    @Transactional
    public void processLike(Long videoId) {
        Video video = videoRepository.findByIdWithPessimisticLock(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        video.addLike();
    }
}
