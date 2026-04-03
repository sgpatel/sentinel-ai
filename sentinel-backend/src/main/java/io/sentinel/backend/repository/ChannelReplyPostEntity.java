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
@Table(name = "channel_reply_posts")
public class ChannelReplyPostEntity {

    @Id
    public String id;

    @Column(name = "mention_id", nullable = false)
    public String mentionId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(nullable = false)
    public String channel;

    @Column(nullable = false)
    public String status;

    @Column(name = "external_post_id")
    public String externalPostId;

    @Column(name = "error_message", length = 1000)
    public String errorMessage;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

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

