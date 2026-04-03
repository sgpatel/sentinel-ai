package io.sentinel.backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prediction_history")
public class PredictionHistoryEntity {

    @Id
    public String id;

    @Column(name = "mention_id", nullable = false)
    public String mentionId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "virality_score_6h", nullable = false)
    public double viralityScore6h;

    @Column(name = "virality_score_24h", nullable = false)
    public double viralityScore24h;

    @Column(name = "escalation_level", nullable = false)
    public String escalationLevel;

    @Column(name = "recommended_action", nullable = false)
    public String recommendedAction;

    @Column(name = "predicted_at", nullable = false)
    public Instant predictedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (predictedAt == null) predictedAt = Instant.now();
    }
}

