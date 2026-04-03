-- Saved searches scoped by tenant and user
CREATE TABLE IF NOT EXISTS saved_searches (
    id          VARCHAR(36)  PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    query_json  TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_searches_tenant_user
    ON saved_searches(tenant_id, user_id, updated_at DESC);

