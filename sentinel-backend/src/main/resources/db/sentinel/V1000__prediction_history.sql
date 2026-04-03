-- Prediction history for viral-risk monitoring and audits
CREATE TABLE IF NOT EXISTS prediction_history (
    id                VARCHAR(36)   PRIMARY KEY,
    mention_id        VARCHAR(50)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL DEFAULT 'default',
    virality_score_6h DOUBLE PRECISION NOT NULL,
    virality_score_24h DOUBLE PRECISION NOT NULL,
    escalation_level  VARCHAR(20)   NOT NULL,
    recommended_action VARCHAR(100) NOT NULL,
    predicted_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prediction_history_tenant_time
    ON prediction_history(tenant_id, predicted_at DESC);
CREATE INDEX IF NOT EXISTS idx_prediction_history_mention
    ON prediction_history(mention_id);

