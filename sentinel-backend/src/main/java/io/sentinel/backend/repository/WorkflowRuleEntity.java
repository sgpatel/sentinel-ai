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
@Table(name = "workflow_rules")
public class WorkflowRuleEntity {

    @Id
    public String id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(nullable = false, length = 160)
    public String name;

    @Column(length = 500)
    public String description;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false)
    public int priority = 100;

    @Column(name = "conflict_strategy", nullable = false, length = 50)
    public String conflictStrategy = "FIRST_MATCH";

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

