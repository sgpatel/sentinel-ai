-- Dead-letter queue for mentions that fail fatally in processing
CREATE TABLE IF NOT EXISTS mention_dlq (
    id             VARCHAR(36)   PRIMARY KEY,
    mention_id     VARCHAR(50)   NOT NULL,
    tenant_id      VARCHAR(36)   NOT NULL DEFAULT 'default',
    failure_stage  VARCHAR(100)  NOT NULL DEFAULT 'PIPELINE',
    error_message  VARCHAR(2000),
    stack_trace    TEXT,
    payload_json   TEXT,
    status         VARCHAR(30)   NOT NULL DEFAULT 'NEW',
    retry_count    INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mention_dlq_status_created
    ON mention_dlq(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mention_dlq_mention
    ON mention_dlq(mention_id);
CREATE INDEX IF NOT EXISTS idx_mention_dlq_tenant
    ON mention_dlq(tenant_id);

