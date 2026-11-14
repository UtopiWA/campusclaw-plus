ALTER TABLE messages
    ADD COLUMN skill_id BIGINT NULL AFTER skill_name,
    ADD COLUMN prompt_tokens INT NULL,
    ADD COLUMN completion_tokens INT NULL,
    ADD COLUMN total_tokens INT NULL,
    ADD COLUMN model_duration_ms BIGINT NULL,
    ADD COLUMN request_id VARCHAR(64) NULL,
    ADD CONSTRAINT ck_messages_usage_nonnegative CHECK (
        (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
        AND (model_duration_ms IS NULL OR model_duration_ms >= 0)
    ),
    ADD INDEX idx_messages_skill_created (skill_id, created_at),
    ADD INDEX idx_messages_created_id (created_at, id);

CREATE TABLE security_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    object_id BIGINT NULL,
    skill_id BIGINT NULL,
    skill_name VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    http_status INT NULL,
    failure_code VARCHAR(64) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    duration_ms BIGINT NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_security_audit_status CHECK (status IN ('SUCCESS', 'FAILED', 'DENIED')),
    CONSTRAINT ck_security_audit_usage_nonnegative CHECK (
        (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
        AND (duration_ms IS NULL OR duration_ms >= 0)
    ),
    INDEX idx_audit_class_created (class_id, created_at),
    INDEX idx_audit_class_user_created (class_id, user_id, created_at),
    INDEX idx_audit_class_skill_created (class_id, skill_id, created_at),
    INDEX idx_audit_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
