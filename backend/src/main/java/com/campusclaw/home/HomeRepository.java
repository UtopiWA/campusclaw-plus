package com.campusclaw.home;

import com.campusclaw.learning.LearningRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 首页只取摘要，班级与本人条件在每条查询中固定。 */
@Repository
public class HomeRepository {
    private final LearningRepository rows;
    private static final String LATEST = """
            WITH latest AS (
                SELECT s.id,s.assignment_id,s.student_id,s.version,s.created_at,u.username,
                ROW_NUMBER() OVER(PARTITION BY s.assignment_id,s.student_id ORDER BY s.version DESC,s.id DESC) AS rn
                FROM submissions s JOIN users u ON u.id=s.student_id AND u.class_id=s.class_id AND u.role='STUDENT'
                WHERE s.class_id=?
            )
            """;
    private static final String COUNTS = """
            SELECT a.id,a.title,a.created_at,COUNT(s.id) AS submitted,
                COALESCE(SUM(CASE WHEN t.status IN ('PENDING','RUNNING') AND r.id IS NULL THEN 1 ELSE 0 END),0) AS grading,
                COALESCE(SUM(CASE WHEN t.status='FAILED' AND r.id IS NULL THEN 1 ELSE 0 END),0) AS attention
            FROM assignments a LEFT JOIN latest s ON s.assignment_id=a.id AND s.rn=1
            LEFT JOIN grading_tasks t ON t.submission_id=s.id
            LEFT JOIN submission_reviews r ON r.submission_id=s.id
            WHERE a.class_id=? AND a.gradable=TRUE AND a.personal_practice=FALSE
            GROUP BY a.id,a.title,a.created_at
            """;

    public HomeRepository(LearningRepository rows) { this.rows = rows; }

    public Map<String, Object> teacherCounts(long classId) {
        return rows.query(LATEST + " SELECT COUNT(*) AS assignments,COALESCE(SUM(grading),0) AS grading,"
                + "COALESCE(SUM(attention),0) AS attention FROM (" + COUNTS + ") totals", classId, classId).get(0);
    }

    public List<Map<String, Object>> recentAssignments(long classId) {
        return rows.query(LATEST + COUNTS + " ORDER BY a.created_at DESC,a.id DESC LIMIT 5", classId, classId);
    }

    public List<Map<String, Object>> attention(long classId) {
        return rows.query(LATEST + """
                SELECT a.id AS assignment_id,a.title,s.id AS submission_id,s.username,s.created_at
                FROM latest s JOIN assignments a ON a.id=s.assignment_id AND a.class_id=?
                JOIN grading_tasks t ON t.submission_id=s.id
                LEFT JOIN submission_reviews r ON r.submission_id=s.id
                WHERE s.rn=1 AND a.gradable=TRUE AND a.personal_practice=FALSE AND t.status='FAILED' AND r.id IS NULL
                ORDER BY s.created_at DESC,s.id DESC LIMIT 5
                """, classId, classId);
    }

    private static final String OWN = """
            WITH own AS (
                SELECT s.id,s.assignment_id,s.version,
                ROW_NUMBER() OVER(PARTITION BY s.assignment_id ORDER BY s.version DESC,s.id DESC) AS rn
                FROM submissions s WHERE s.class_id=? AND s.student_id=?
            )
            """;
    private static final String CLASS_WORK = """
            FROM assignments a LEFT JOIN own s ON s.assignment_id=a.id AND s.rn=1
            LEFT JOIN grading_tasks t ON t.submission_id=s.id
            LEFT JOIN submission_reviews r ON r.submission_id=s.id
            WHERE a.class_id=? AND a.gradable=TRUE AND a.personal_practice=FALSE
            """;

    public Map<String, Object> studentCounts(long classId, long studentId) {
        var result = rows.query(OWN + """
                SELECT COALESCE(SUM(CASE WHEN s.id IS NULL THEN 1 ELSE 0 END),0) AS pending,
                COALESCE(SUM(CASE WHEN t.status IN ('PENDING','RUNNING') AND r.id IS NULL THEN 1 ELSE 0 END),0) AS grading
                """ + CLASS_WORK, classId, studentId, classId).get(0);
        long practice = ((Number) rows.query("""
                SELECT COUNT(*) AS total FROM question_bank_responses b
                JOIN question_bank q ON q.id=b.question_id AND q.class_id=b.class_id AND q.deleted=FALSE AND q.enabled=TRUE
                JOIN grading_tasks t ON t.task_id=b.task_id AND t.submission_id=b.submission_id
                WHERE b.class_id=? AND b.student_id=? AND t.status IN ('PENDING','RUNNING')
                """, classId, studentId).get(0).get("total")).longValue();
        result.put("grading", ((Number) result.get("grading")).longValue() + practice);
        return result;
    }

    public List<Map<String, Object>> pending(long classId, long studentId) {
        return rows.query(OWN + "SELECT a.id,a.title,a.max_score,a.created_at " + CLASS_WORK
                + " AND s.id IS NULL ORDER BY a.created_at DESC,a.id DESC LIMIT 5", classId, studentId, classId);
    }

    public List<Map<String, Object>> recentResults(long classId, long studentId) {
        return rows.query(OWN + """
                SELECT * FROM (
                SELECT 'ASSIGNMENT' AS kind,a.id AS item_id,a.title,COALESCE(r.score,g.score) AS score,a.max_score,
                    COALESCE(r.reviewed_at,g.created_at) AS graded_at,s.id AS submission_id,
                    CASE WHEN r.id IS NOT NULL THEN 'TEACHER' ELSE 'AUTO' END AS score_source
                FROM own s JOIN assignments a ON a.id=s.assignment_id AND a.class_id=? AND a.personal_practice=FALSE
                JOIN grading_tasks t ON t.submission_id=s.id
                LEFT JOIN grading_results g ON g.task_id=t.task_id AND g.submission_id=s.id AND g.version=s.version
                LEFT JOIN submission_reviews r ON r.submission_id=s.id
                WHERE s.rn=1 AND (r.id IS NOT NULL OR (g.id IS NOT NULL AND t.status='SUCCEEDED'))
                UNION ALL
                SELECT 'PRACTICE',q.id,a.title,g.score,g.max_score,g.created_at,s.id,'AUTO'
                FROM question_bank_responses b JOIN question_bank q ON q.id=b.question_id AND q.class_id=b.class_id
                JOIN submissions s ON s.id=b.submission_id AND s.class_id=b.class_id AND s.student_id=b.student_id
                JOIN assignments a ON a.id=s.assignment_id AND a.class_id=b.class_id AND a.personal_practice=TRUE
                JOIN grading_tasks t ON t.task_id=b.task_id AND t.submission_id=s.id
                JOIN grading_results g ON g.task_id=t.task_id AND g.submission_id=s.id AND g.version=s.version
                WHERE b.class_id=? AND b.student_id=? AND q.deleted=FALSE AND q.enabled=TRUE AND t.status='SUCCEEDED'
                ) results ORDER BY graded_at DESC,submission_id DESC LIMIT 5
                """, classId, studentId, classId, classId, studentId);
    }

    public List<Map<String, Object>> tags(long classId) {
        return rows.query("""
                SELECT l.assignment_id,p.name FROM learning_assignment_points l
                JOIN learning_points p ON p.id=l.point_id AND p.class_id=l.class_id AND p.enabled=TRUE
                WHERE l.class_id=? ORDER BY l.assignment_id,p.id
                """, classId);
    }
}
