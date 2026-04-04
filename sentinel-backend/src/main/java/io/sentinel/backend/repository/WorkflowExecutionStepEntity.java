package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution_steps")
public class WorkflowExecutionStepEntity {

    @Id
    public String id;

    @Column(name = "execution_id", nullable = false)
    public String executionId;

    @Column(name = "step_type", nullable = false, length = 40)
    public String stepType;

    @Column(name = "step_name", nullable = false, length = 120)
    public String stepName;

    @Column(nullable = false)
    public boolean success;

    @Column(name = "details_json", columnDefinition = "TEXT")
    public String detailsJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}

