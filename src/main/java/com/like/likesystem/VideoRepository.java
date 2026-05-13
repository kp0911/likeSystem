package com.like.likesystem;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Video v WHERE v.id = :id")
    Optional<Video> findByIdWithPessimisticLock(@Param("id") Long id);
}
