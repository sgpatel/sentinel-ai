package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecutionEntity {

    @Id
    public String id;

    @Column(name = "rule_id")
    public String ruleId;

    @Column(name = "mention_id")
    public String mentionId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "dry_run", nullable = false)
    public boolean dryRun;

    @Column(nullable = false, length = 40)
    public String status;

    @Column(name = "failure_reason", length = 500)
    public String failureReason;

    @Column(name = "correlation_id", length = 64)
    public String correlationId;

    @Column(name = "duration_ms", nullable = false)
    public long durationMs;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}

