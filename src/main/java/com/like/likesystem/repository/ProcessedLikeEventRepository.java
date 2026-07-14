package com.like.likesystem.repository;

import com.like.likesystem.domain.ProcessedLikeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedLikeEventRepository extends JpaRepository<ProcessedLikeEvent, String> {
}
