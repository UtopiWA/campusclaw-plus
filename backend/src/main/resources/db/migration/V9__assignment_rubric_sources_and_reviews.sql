ALTER TABLE assignments
    ADD COLUMN rubric_source_json JSON NULL,
    ADD COLUMN rubric_text MEDIUMTEXT NULL;

CREATE TABLE submission_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    score INT NOT NULL,
    comment TEXT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_submission_reviews_submission UNIQUE (submission_id),
    CONSTRAINT fk_submission_reviews_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_submission_reviews_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_submission_reviews_class FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_submission_reviews_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    CONSTRAINT ck_submission_reviews_score CHECK (score >= 0),
    INDEX idx_submission_reviews_class_assignment (class_id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
