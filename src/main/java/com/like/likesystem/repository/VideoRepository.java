package com.like.likesystem.repository;

import com.like.likesystem.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Modifying
    @Query("UPDATE Video v SET v.likeCount = v.likeCount + :delta WHERE v.id = :id")
    int incrementLikeCountBy(@Param("id") Long id, @Param("delta") Long delta);

    @Modifying
    @Query("UPDATE Video v SET v.likeCount = :likeCount WHERE v.id = :id")
    int resetLikeCount(@Param("id") Long id, @Param("likeCount") Long likeCount);
}
