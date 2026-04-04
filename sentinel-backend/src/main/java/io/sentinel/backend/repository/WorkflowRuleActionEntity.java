package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_rule_actions")
public class WorkflowRuleActionEntity {

    @Id
    public String id;

    @Column(name = "rule_id", nullable = false)
    public String ruleId;

    @Column(name = "action_type", nullable = false, length = 80)
    public String actionType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    public String payloadJson;

    @Column(nullable = false)
    public int position;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}

