package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mention_dlq")
public class MentionDlqEntity {

    @Id
    public String id;

    @Column(name = "mention_id", nullable = false)
    public String mentionId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "failure_stage", nullable = false)
    public String failureStage = "PIPELINE";

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    public String stackTrace;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    public String payloadJson;

    @Column(nullable = false)
    public String status = "NEW";

    @Column(name = "retry_count", nullable = false)
    public int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "last_retry_at")
    public Instant lastRetryAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

