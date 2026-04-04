package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_rule_conditions")
public class WorkflowRuleConditionEntity {

    @Id
    public String id;

    @Column(name = "rule_id", nullable = false)
    public String ruleId;

    @Column(name = "field_name", nullable = false, length = 80)
    public String fieldName;

    @Column(name = "operator", nullable = false, length = 40)
    public String operator;

    @Column(name = "value_text", length = 255)
    public String valueText;

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

