-- Tenant-scoped competitor handles for competitive intelligence
CREATE TABLE IF NOT EXISTS competitor_handles (
    id          VARCHAR(36)   PRIMARY KEY,
    tenant_id   VARCHAR(36)   NOT NULL,
    handle      VARCHAR(100)  NOT NULL,
    label       VARCHAR(255),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_competitor_handles_tenant
    ON competitor_handles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_competitor_handles_tenant_handle
    ON competitor_handles(tenant_id, handle);

