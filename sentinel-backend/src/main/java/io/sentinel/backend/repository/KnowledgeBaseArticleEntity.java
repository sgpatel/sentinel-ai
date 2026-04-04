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
@Table(name = "knowledge_base_articles")
public class KnowledgeBaseArticleEntity {

    @Id
    public String id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(nullable = false, length = 20)
    public String visibility = "PUBLIC";

    @Column(nullable = false)
    public boolean active = true;

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

