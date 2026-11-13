package com.campusclaw.learning;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionBankRepository {
    private final JdbcTemplate jdbc;
    private final LearningRepository rows;
    private static final String QUESTION = """
            SELECT q.id,q.class_id,q.assignment_id,q.enabled,q.deleted,q.created_at,q.updated_at,
            a.title,a.prompt_text,a.max_score,a.rubric_source_json
            FROM question_bank q JOIN assignments a ON a.id=q.assignment_id AND a.class_id=q.class_id
            """;

    public QuestionBankRepository(JdbcTemplate jdbc, LearningRepository rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public List<Map<String, Object>> list(long classId, boolean teacher) {
        return rows.query(QUESTION + " WHERE q.class_id=? AND q.deleted=FALSE"
                + (teacher ? "" : " AND q.enabled=TRUE") + " ORDER BY q.updated_at DESC,q.id DESC LIMIT 2001", classId);
    }

    public Map<String, Object> question(long classId, long id, boolean lock) {
        return rows.one(QUESTION + " WHERE q.id=? AND q.class_id=? AND q.deleted=FALSE" + (lock ? " FOR UPDATE" : ""), id, classId);
    }

    public long save(long classId, long teacherId, Long id, long assignmentId, boolean enabled) {
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (id == null) return rows.insert("""
                INSERT INTO question_bank(class_id,assignment_id,enabled,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?)
                """, classId, assignmentId, enabled, teacherId, now, now);
        jdbc.update("UPDATE question_bank SET assignment_id=?,enabled=?,updated_at=? WHERE id=? AND class_id=?",
                assignmentId, enabled, now, id, classId);
        return id;
    }

    public void availability(long classId, long id, boolean enabled) {
        jdbc.update("UPDATE question_bank SET enabled=?,updated_at=? WHERE id=? AND class_id=?", enabled, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), id, classId);
    }

    public void delete(long classId, long id) {
        jdbc.update("UPDATE question_bank SET deleted=TRUE,enabled=FALSE,updated_at=? WHERE id=? AND class_id=?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), id, classId);
    }

    public List<Map<String, Object>> tags(long classId) {
        return rows.query("""
                SELECT q.id AS question_id,p.id,p.name,p.enabled FROM question_bank q
                JOIN learning_assignment_points l ON l.assignment_id=q.assignment_id AND l.class_id=q.class_id
                JOIN learning_points p ON p.id=l.point_id AND p.class_id=q.class_id
                WHERE q.class_id=? AND q.deleted=FALSE ORDER BY p.id
                """, classId);
    }

    public List<Map<String, Object>> learningResources(long classId) {
        return rows.query("""
                SELECT q.id,l.point_id,q.assignment_id AS content_version,a.title,LEFT(a.prompt_text,240) AS excerpt,q.updated_at FROM question_bank q
                JOIN assignments a ON a.id=q.assignment_id AND a.class_id=q.class_id
                JOIN learning_assignment_points l ON l.assignment_id=q.assignment_id AND l.class_id=q.class_id
                WHERE q.class_id=? AND q.deleted=FALSE AND q.enabled=TRUE ORDER BY q.id,l.point_id
                """, classId);
    }

    public List<Map<String, Object>> summaries(long classId, long studentId) {
        return rows.query("""
                SELECT b.question_id,b.submission_id,b.task_id,t.status,g.score,g.max_score,s.created_at,s.assignment_id AS content_version
                FROM question_bank_responses b JOIN grading_tasks t ON t.task_id=b.task_id
                JOIN submissions s ON s.id=b.submission_id
                LEFT JOIN grading_results g ON g.task_id=t.task_id AND g.submission_id=s.id AND g.version=s.version
                WHERE b.class_id=? AND b.student_id=?
                """, classId, studentId);
    }

    public Map<String, Object> latest(long classId, long studentId, long questionId) {
        var list = rows.query("""
                SELECT b.*,s.client_key,s.version,s.assignment_id AS submitted_assignment_id,a.prompt_text,a.title FROM question_bank_responses b
                JOIN submissions s ON s.id=b.submission_id JOIN assignments a ON a.id=s.assignment_id
                WHERE b.class_id=? AND b.student_id=? AND b.question_id=?
                """, classId, studentId, questionId);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isLatest(String taskId, long classId, long studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM question_bank_responses WHERE task_id=? AND class_id=? AND student_id=?",
                Long.class, taskId, classId, studentId) == 1;
    }

    public void replace(long questionId, long classId, long studentId, long submissionId, String taskId) {
        jdbc.update("""
                INSERT INTO question_bank_responses(question_id,class_id,student_id,submission_id,task_id) VALUES(?,?,?,?,?)
                ON DUPLICATE KEY UPDATE submission_id=VALUES(submission_id),task_id=VALUES(task_id)
                """, questionId, classId, studentId, submissionId, taskId);
    }

    public void clearPrevious(long submissionId, long classId, long studentId) {
        jdbc.update("DELETE g FROM grading_results g JOIN submissions s ON s.id=g.submission_id "
                + "WHERE s.id=? AND s.class_id=? AND s.student_id=?", submissionId, classId, studentId);
        // 旧任务仍可能有已确认/死信消息，只保留标识和状态，不保留被覆盖的作答及评分。
        jdbc.update("UPDATE submissions SET content='',content_hash=SHA2(CONCAT('replaced:',id),256) "
                + "WHERE id=? AND class_id=? AND student_id=?", submissionId, classId, studentId);
    }
}
