-- Canonical tenant registry table for true multi-tenant support
CREATE TABLE IF NOT EXISTS tenants (
    id            VARCHAR(36)   PRIMARY KEY,
    name          VARCHAR(255)  NOT NULL,
    slug          VARCHAR(100)  NOT NULL UNIQUE,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    plan          VARCHAR(50)   NOT NULL DEFAULT 'FREE',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ensure default tenant exists for backward compatibility with existing rows
MERGE INTO tenants (id, name, slug, status, plan)
KEY (id)
VALUES ('default', 'Default Tenant', 'default', 'ACTIVE', 'FREE');

CREATE INDEX IF NOT EXISTS idx_tenants_slug   ON tenants(slug);
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);

