ALTER TABLE learning_plans
    ADD COLUMN days INT NOT NULL DEFAULT 7,
    ADD COLUMN daily_minutes INT NOT NULL DEFAULT 30,
    ADD COLUMN generation_state VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN generation_claim VARCHAR(36) NULL,
    ADD COLUMN generation_started_at DATETIME(6) NULL,
    ADD CONSTRAINT ck_learning_plan_budget CHECK (days BETWEEN 1 AND 14 AND daily_minutes BETWEEN 10 AND 120),
    ADD CONSTRAINT ck_learning_plan_generation CHECK (generation_state IN ('NONE','RUNNING','DONE','FAILED'));
