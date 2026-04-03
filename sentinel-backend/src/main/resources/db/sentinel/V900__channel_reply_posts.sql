-- Per-channel reply posting audit log
CREATE TABLE IF NOT EXISTS channel_reply_posts (
    id                VARCHAR(36)   PRIMARY KEY,
    mention_id        VARCHAR(50)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL DEFAULT 'default',
    channel           VARCHAR(50)   NOT NULL,
    status            VARCHAR(30)   NOT NULL,
    external_post_id  VARCHAR(255),
    error_message     VARCHAR(1000),
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channel_reply_posts_mention ON channel_reply_posts(mention_id);
CREATE INDEX IF NOT EXISTS idx_channel_reply_posts_tenant ON channel_reply_posts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_channel_reply_posts_status ON channel_reply_posts(status);

