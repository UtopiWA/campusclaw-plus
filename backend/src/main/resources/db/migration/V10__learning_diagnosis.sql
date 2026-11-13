CREATE TABLE learning_points (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_by BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_learning_point_code (class_id, code),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_relations (
    class_id BIGINT NOT NULL,
    from_id BIGINT NOT NULL,
    to_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    PRIMARY KEY (class_id, from_id, to_id, kind),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (from_id) REFERENCES learning_points(id),
    FOREIGN KEY (to_id) REFERENCES learning_points(id),
    CHECK (from_id <> to_id),
    CHECK (kind IN ('PREREQUISITE', 'RELATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_point_materials (
    class_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    PRIMARY KEY (class_id, point_id, material_id),
    FOREIGN KEY (point_id) REFERENCES learning_points(id),
    FOREIGN KEY (material_id) REFERENCES materials(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    point_id BIGINT NOT NULL,
    prompt VARCHAR(2000) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    options_json JSON NOT NULL,
    answer VARCHAR(80) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    difficulty INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_learning_question_code (class_id, code),
    INDEX idx_learning_questions_recommend (class_id, enabled, point_id, difficulty, id),
    FOREIGN KEY (point_id) REFERENCES learning_points(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (kind IN ('CHOICE', 'NUMBER')),
    CHECK (difficulty BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_practices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    prompt VARCHAR(2000) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    options_json JSON NOT NULL,
    expected_answer VARCHAR(80) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    difficulty INT NOT NULL,
    answer VARCHAR(80) NULL,
    score INT NULL,
    created_at DATETIME(6) NOT NULL,
    answered_at DATETIME(6) NULL,
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (question_id) REFERENCES learning_questions(id),
    FOREIGN KEY (point_id) REFERENCES learning_points(id),
    INDEX idx_learning_practice_student (class_id, student_id, id),
    INDEX idx_learning_practice_evidence (class_id, answered_at, point_id, student_id),
    CHECK (score IS NULL OR score IN (0,100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_assignment_points (
    assignment_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    PRIMARY KEY (assignment_id, point_id),
    INDEX idx_learning_assignment_class (class_id, assignment_id),
    FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    FOREIGN KEY (point_id) REFERENCES learning_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    goal VARCHAR(200) NOT NULL,
    revision INT NOT NULL,
    evidence_key VARCHAR(64) NOT NULL,
    explanation TEXT NULL,
    explanation_state VARCHAR(16) NOT NULL DEFAULT 'NONE',
    explanation_claim VARCHAR(36) NULL,
    explanation_started_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_learning_plan_student (class_id, student_id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (class_id) REFERENCES classes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_plan_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    position_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    revision INT NOT NULL,
    resource_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    FOREIGN KEY (plan_id) REFERENCES learning_plans(id),
    FOREIGN KEY (point_id) REFERENCES learning_points(id),
    INDEX idx_learning_plan_tasks (plan_id, status, position_no, id),
    CHECK (status IN ('PENDING','DONE','REPLACED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_plan_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    revision INT NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_learning_plan_revision (plan_id, revision),
    FOREIGN KEY (plan_id) REFERENCES learning_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_handoffs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    revision INT NOT NULL,
    role_name VARCHAR(16) NOT NULL,
    method VARCHAR(16) NOT NULL,
    input_summary VARCHAR(1000) NOT NULL,
    output_summary VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES learning_plans(id),
    INDEX idx_learning_handoffs_plan (plan_id, revision, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_question_assignments (
    assignment_id BIGINT NOT NULL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    expected_answer VARCHAR(80) NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    FOREIGN KEY (question_id) REFERENCES learning_questions(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_learning_question_assignment_class (class_id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
