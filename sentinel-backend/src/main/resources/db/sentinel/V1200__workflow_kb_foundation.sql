CREATE TABLE workflow_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    conflict_strategy VARCHAR(50) NOT NULL DEFAULT 'FIRST_MATCH',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_workflow_rules_tenant_enabled_priority
    ON workflow_rules(tenant_id, enabled, priority);

CREATE TABLE workflow_rule_conditions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36) NOT NULL,
    field_name VARCHAR(80) NOT NULL,
    operator VARCHAR(40) NOT NULL,
    value_text VARCHAR(255),
    position INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_workflow_condition_rule FOREIGN KEY (rule_id) REFERENCES workflow_rules(id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_conditions_rule_pos
    ON workflow_rule_conditions(rule_id, position);

CREATE TABLE workflow_rule_actions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    payload_json CLOB,
    position INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_workflow_action_rule FOREIGN KEY (rule_id) REFERENCES workflow_rules(id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_actions_rule_pos
    ON workflow_rule_actions(rule_id, position);

CREATE TABLE workflow_executions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36),
    mention_id VARCHAR(255),
    tenant_id VARCHAR(100) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    correlation_id VARCHAR(64),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_workflow_execution_rule FOREIGN KEY (rule_id) REFERENCES workflow_rules(id)
);

CREATE INDEX idx_workflow_exec_tenant_created
    ON workflow_executions(tenant_id, created_at);

CREATE TABLE workflow_execution_steps (
    id VARCHAR(36) PRIMARY KEY,
    execution_id VARCHAR(36) NOT NULL,
    step_type VARCHAR(40) NOT NULL,
    step_name VARCHAR(120) NOT NULL,
    success BOOLEAN NOT NULL,
    details_json CLOB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_workflow_step_execution FOREIGN KEY (execution_id) REFERENCES workflow_executions(id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_step_execution
    ON workflow_execution_steps(execution_id);

CREATE TABLE knowledge_base_articles (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content CLOB NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_kb_articles_tenant_visibility
    ON knowledge_base_articles(tenant_id, visibility, active);

CREATE TABLE knowledge_base_article_tags (
    id VARCHAR(36) PRIMARY KEY,
    article_id VARCHAR(36) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_kb_tag_article FOREIGN KEY (article_id) REFERENCES knowledge_base_articles(id) ON DELETE CASCADE
);

CREATE INDEX idx_kb_tags_article
    ON knowledge_base_article_tags(article_id);

CREATE TABLE knowledge_base_feedback (
    id VARCHAR(36) PRIMARY KEY,
    article_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    helpful BOOLEAN NOT NULL,
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_kb_feedback_article FOREIGN KEY (article_id) REFERENCES knowledge_base_articles(id) ON DELETE CASCADE
);

CREATE INDEX idx_kb_feedback_article
    ON knowledge_base_feedback(article_id);

