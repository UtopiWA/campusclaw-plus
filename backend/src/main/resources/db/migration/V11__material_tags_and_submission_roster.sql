-- V10 已部署：保留原表与评分，不再读取知识关系。
CREATE TABLE learning_assignment_tag_sources (
    assignment_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    PRIMARY KEY (assignment_id, material_id, point_id),
    CONSTRAINT fk_tag_source_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_tag_source_point FOREIGN KEY (point_id) REFERENCES learning_points(id)
);
-- material_id 是创建时的来源快照，讲义删除后仍保留来源标识。
CREATE INDEX idx_submission_roster ON submissions(class_id, assignment_id, student_id, version);
