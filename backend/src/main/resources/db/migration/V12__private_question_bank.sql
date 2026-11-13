ALTER TABLE assignments ADD COLUMN personal_practice BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_assignment_purpose ON assignments(class_id, personal_practice, gradable);

CREATE TABLE question_bank (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_question_class_state(class_id, deleted, enabled, updated_at)
);

CREATE TABLE question_bank_responses (
    question_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (question_id, student_id),
    UNIQUE KEY uk_question_response_submission(submission_id),
    UNIQUE KEY uk_question_response_task(task_id),
    FOREIGN KEY (question_id) REFERENCES question_bank(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (submission_id) REFERENCES submissions(id),
    FOREIGN KEY (task_id) REFERENCES grading_tasks(task_id),
    INDEX idx_question_response_student(class_id, student_id)
);
