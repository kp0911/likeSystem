package com.like.likesystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_like_event")
@Getter
@NoArgsConstructor
public class ProcessedLikeEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedLikeEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
