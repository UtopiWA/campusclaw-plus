ALTER TABLE assignments
    ADD COLUMN prompt_text VARCHAR(4000) NULL,
    ADD COLUMN rubric_json JSON NULL,
    ADD COLUMN max_score INT NULL,
    ADD COLUMN gradable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL;

CREATE INDEX idx_assignments_class_gradable ON assignments (class_id, gradable);

CREATE TABLE submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    client_key VARCHAR(128) NULL,
    version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_submissions_version UNIQUE (assignment_id, student_id, version),
    CONSTRAINT uk_submissions_content UNIQUE (assignment_id, student_id, content_hash),
    CONSTRAINT uk_submissions_client_key UNIQUE (assignment_id, student_id, client_key),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_submissions_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX idx_submissions_class_assignment (class_id, assignment_id),
    INDEX idx_submissions_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grading_tasks (
    task_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    available_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    failure_code VARCHAR(40) NULL,
    last_failure_code VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT uk_grading_tasks_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_grading_tasks_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_grading_tasks_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_grading_tasks_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_grading_tasks_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX idx_grading_tasks_status_available (status, available_at),
    INDEX idx_grading_tasks_submission (submission_id),
    INDEX idx_grading_tasks_class_created (class_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grading_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(36) NOT NULL,
    submission_id BIGINT NOT NULL,
    version INT NOT NULL,
    score INT NOT NULL,
    max_score INT NOT NULL,
    criteria_json JSON NOT NULL,
    rationale TEXT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_grading_results_task UNIQUE (task_id),
    CONSTRAINT uk_grading_results_submission_version UNIQUE (submission_id, version),
    CONSTRAINT fk_grading_results_task FOREIGN KEY (task_id) REFERENCES grading_tasks (task_id),
    CONSTRAINT fk_grading_results_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grading_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    available_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(40) NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grading_outbox_task FOREIGN KEY (task_id) REFERENCES grading_tasks (task_id),
    INDEX idx_grading_outbox_status_available (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
