package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 聚合读取既有成绩，所有条件值均经参数绑定，绝不回写原评分。 */
@Repository
public class LearningRepository {
    private final JdbcTemplate jdbc;

    public LearningRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockClass(long classId) {
        one("SELECT id FROM classes WHERE id=? FOR UPDATE", classId);
    }

    public void lockStudent(long classId, long studentId) {
        one("SELECT id FROM users WHERE id=? AND class_id=? AND enabled=TRUE FOR UPDATE", studentId, classId);
    }

    public List<Map<String, Object>> points(long classId) {
        return query("SELECT * FROM learning_points WHERE class_id=? ORDER BY id LIMIT 201", classId);
    }

    public Map<String, Object> point(long classId, long id) {
        return one("SELECT * FROM learning_points WHERE id=? AND class_id=?", id, classId);
    }

    public Long pointByCode(long classId, String code) {
        var items = query("SELECT id FROM learning_points WHERE class_id=? AND code=?", classId, code);
        return items.isEmpty() ? null : number(items.get(0), "id");
    }

    public long savePoint(long classId, long actorId, Long id, LearningRequests.PointInput input) {
        if (id == null) return insert("""
                INSERT INTO learning_points(class_id,code,name,description,created_by,enabled,updated_at)
                VALUES(?,?,?,?,?,?,?)
                """, classId, input.code().trim(), input.name().trim(), input.description().trim(),
                actorId, input.enabled(), Instant.now());
        jdbc.update("UPDATE learning_points SET name=?,description=?,enabled=?,updated_at=? WHERE id=? AND class_id=?",
                input.name().trim(), input.description().trim(), input.enabled(), now(), id, classId);
        return id;
    }

    public void lockMaterial(long classId, long id) {
        one("SELECT id FROM materials WHERE id=? AND class_id=? FOR UPDATE", id, classId);
    }

    public List<Map<String, Object>> materialTags(long classId) {
        return query("""
                SELECT l.point_id,m.id,m.title,p.enabled FROM learning_point_materials l
                JOIN materials m ON m.id=l.material_id AND m.class_id=l.class_id
                JOIN learning_points p ON p.id=l.point_id AND p.class_id=l.class_id
                WHERE l.class_id=? ORDER BY m.id,l.point_id
                """, classId);
    }

    public void replaceMaterialTags(long classId, long materialId, List<Long> ids) {
        jdbc.update("DELETE FROM learning_point_materials WHERE class_id=? AND material_id=?", classId, materialId);
        for (Long id : ids) addMaterial(classId, id, materialId);
    }

    public void tagSource(long assignmentId, long classId, long materialId, long pointId) {
        jdbc.update("""
                INSERT INTO learning_assignment_tag_sources(assignment_id,class_id,material_id,point_id) VALUES(?,?,?,?)
                """, assignmentId, classId, materialId, pointId);
    }

    public List<Map<String, Object>> students(long classId) {
        return query("SELECT id,username FROM users WHERE class_id=? AND role='STUDENT' ORDER BY username,id", classId);
    }

    public Map<String, Object> readyMaterial(long classId, long id) {
        return one("SELECT id,title FROM materials WHERE id=? AND class_id=? AND index_status='READY'", id, classId);
    }

    public void addMaterial(long classId, long pointId, long materialId) {
        jdbc.update("INSERT IGNORE INTO learning_point_materials(class_id,point_id,material_id) VALUES(?,?,?)",
                classId, pointId, materialId);
    }

    public void removeMaterial(long classId, long pointId, long materialId) {
        jdbc.update("DELETE FROM learning_point_materials WHERE class_id=? AND point_id=? AND material_id=?",
                classId, pointId, materialId);
    }

    public List<Map<String, Object>> materials(long classId) {
        return query("""
                SELECT l.point_id,m.id,m.title FROM learning_point_materials l
                JOIN materials m ON m.id=l.material_id AND m.class_id=l.class_id
                WHERE l.class_id=? AND m.index_status='READY' ORDER BY l.point_id,m.id
                """, classId);
    }

    public List<Map<String, Object>> planMaterials(long classId) {
        return query("""
                SELECT l.point_id,m.id,m.title,m.updated_at,COALESCE(LEFT(k.chunk_text,200),'') AS excerpt
                FROM learning_point_materials l JOIN materials m ON m.id=l.material_id AND m.class_id=l.class_id
                LEFT JOIN knowledge_chunks k ON k.material_id=m.id AND k.class_id=m.class_id
                    AND k.chunk_index=0 AND k.index_status='READY'
                WHERE l.class_id=? AND m.index_status='READY' ORDER BY l.point_id,m.id
                """, classId);
    }

    public boolean claimGeneration(long id, String claim) {
        return jdbc.update("""
                UPDATE learning_plans SET generation_state='RUNNING',generation_claim=?,generation_started_at=?
                WHERE id=? AND (generation_started_at IS NULL OR generation_started_at<?)
                  AND (generation_state<>'RUNNING' OR generation_started_at<?)
                """, claim, now(), id, utc(Instant.now().minusSeconds(15)),
                utc(Instant.now().minusSeconds(120))) == 1;
    }

    public boolean hasGenerationClaim(long id, String claim) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM learning_plans WHERE id=? AND generation_state='RUNNING' AND generation_claim=?",
                Long.class, id, claim) == 1;
    }

    public void endGeneration(long id, String claim, String state) {
        jdbc.update("UPDATE learning_plans SET generation_state=?,generation_claim=NULL WHERE id=? AND generation_claim=?",
                state, id, claim);
    }

    public void planBudget(long id, int days, int minutes, String message) {
        jdbc.update("UPDATE learning_plans SET days=?,daily_minutes=?,explanation=?,explanation_state='DONE' WHERE id=?",
                days, minutes, message, id);
    }

    public void lockAssignment(long classId, long id) {
        one("SELECT id FROM assignments WHERE id=? AND class_id=? AND gradable=TRUE FOR UPDATE", id, classId);
    }

    public long submissionCount(long classId, long assignmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM submissions WHERE assignment_id=? AND class_id=?",
                Long.class, assignmentId, classId);
    }

    public void snapshotAssignment(long assignmentId, long classId, long questionId, String kind, String answer, String explanation) {
        jdbc.update("""
                INSERT INTO learning_question_assignments(assignment_id,class_id,question_id,kind,expected_answer,explanation,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, assignmentId, classId, questionId, kind, answer, explanation, now());
    }

    public Map<String, Object> objectiveAssignment(long assignmentId, long classId) {
        var rows = query("SELECT * FROM learning_question_assignments WHERE assignment_id=? AND class_id=?", assignmentId, classId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> assignmentPoints(long classId, long assignmentId) {
        one("SELECT id FROM assignments WHERE id=? AND class_id=?", assignmentId, classId);
        return query("SELECT point_id FROM learning_assignment_points WHERE assignment_id=? AND class_id=? ORDER BY point_id",
                assignmentId, classId);
    }

    public void bindAssignment(long classId, long id, List<Long> pointIds) {
        jdbc.update("DELETE FROM learning_assignment_points WHERE assignment_id=? AND class_id=?", id, classId);
        for (Long pointId : pointIds) jdbc.update(
                "INSERT INTO learning_assignment_points(assignment_id,class_id,point_id) VALUES(?,?,?)", id, classId, pointId);
    }

    public List<Map<String, Object>> evidence(long classId, Long studentId, Instant from, Instant to) {
        // 两路在 SQL 内先约束班级/身份/时间；最终上限只用于明确拒绝过大查询，不截断统计。
        String identity = studentId == null ? "" : " AND e.student_id=?";
        String sql = """
                SELECT e.*,u.username FROM (
                SELECT 'QUESTION' AS source_type,s.id AS source_id,l.point_id,s.student_id,
                g.score,g.max_score,g.created_at AS occurred_at,a.title AS label,
                b.question_id,s.content AS answer,g.rationale AS feedback,'TEXT' AS answer_kind
                FROM question_bank_responses b JOIN submissions s ON s.id=b.submission_id AND s.class_id=b.class_id
                JOIN assignments a ON a.id=s.assignment_id AND a.class_id=s.class_id AND a.personal_practice=TRUE
                JOIN learning_assignment_points l ON l.assignment_id=a.id AND l.class_id=s.class_id
                JOIN grading_results g ON g.task_id=b.task_id AND g.submission_id=s.id AND g.version=s.version
                WHERE b.class_id=?
                UNION ALL
                SELECT 'ASSIGNMENT',s.id,l.point_id,s.student_id,COALESCE(r.score,g.score),a.max_score,
                COALESCE(r.reviewed_at,g.created_at),a.title,qa.question_id,s.content,COALESCE(r.comment,g.rationale),COALESCE(qa.kind,'TEXT')
                FROM submissions s JOIN assignments a ON a.id=s.assignment_id AND a.class_id=s.class_id
                JOIN learning_assignment_points l ON l.assignment_id=a.id AND l.class_id=s.class_id
                LEFT JOIN grading_results g ON g.submission_id=s.id AND g.version=s.version
                LEFT JOIN submission_reviews r ON r.submission_id=s.id
                LEFT JOIN learning_question_assignments qa ON qa.assignment_id=a.id AND qa.class_id=s.class_id
                WHERE s.class_id=? AND a.personal_practice=FALSE AND (r.id IS NOT NULL OR g.id IS NOT NULL)
                ) e JOIN users u ON u.id=e.student_id AND u.class_id=? AND u.role='STUDENT'
                WHERE e.occurred_at>=? AND e.occurred_at<?
                """ + identity + " ORDER BY e.occurred_at,e.source_type,e.source_id,e.point_id LIMIT 10001";
        List<Object> args = new ArrayList<>(List.of(classId, classId, classId, utc(from), utc(to)));
        if (studentId != null) args.add(studentId);
        var items = query(sql, args.toArray());
        if (items.size() > 10000) throw new BadRequestException("记录较多，请缩小统计时间范围");
        return items;
    }

    public long studentCount(long classId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE class_id=? AND role='STUDENT'",
                Long.class, classId);
    }

    public Map<String, Object> plan(long classId, long studentId) {
        var rows = query("SELECT * FROM learning_plans WHERE class_id=? AND student_id=?", classId, studentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createPlan(long classId, long studentId, String goal, String key) {
        return insert("""
                INSERT INTO learning_plans(class_id,student_id,goal,revision,evidence_key,created_at,updated_at)
                VALUES(?,?,?,1,?,?,?)
                """, classId, studentId, goal, key, Instant.now(), Instant.now());
    }

    public void revisePlan(long id, String goal, int revision, String key) {
        jdbc.update("""
                UPDATE learning_plans SET goal=?,revision=?,evidence_key=?,explanation=NULL,
                explanation_state='NONE',explanation_claim=NULL,explanation_started_at=NULL,updated_at=? WHERE id=?
                """, goal, revision, key, now(), id);
        jdbc.update("UPDATE learning_plan_tasks SET status='REPLACED' WHERE plan_id=? AND status='PENDING'", id);
    }

    public List<Map<String, Object>> planTasks(long planId) {
        return query("SELECT * FROM learning_plan_tasks WHERE plan_id=? AND status<>'REPLACED' ORDER BY position_no,id", planId);
    }

    public void addTask(long planId, long pointId, String title, int position, int revision, String resources) {
        insert("""
                INSERT INTO learning_plan_tasks(plan_id,point_id,title,position_no,revision,resource_json,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, planId, pointId, title, position, revision, resources, Instant.now());
    }

    public void finishTask(long planId, long taskId) {
        one("SELECT id FROM learning_plan_tasks WHERE id=? AND plan_id=? AND status<>'REPLACED'", taskId, planId);
        int changed = jdbc.update("UPDATE learning_plan_tasks SET status='DONE',completed_at=? WHERE id=? AND plan_id=? AND status='PENDING'",
                now(), taskId, planId);
        if (changed == 1) jdbc.update("""
                UPDATE learning_plans SET explanation=IF(generation_state='DONE',explanation,NULL),
                explanation_state=IF(generation_state='DONE','DONE','NONE'),explanation_claim=NULL,
                explanation_started_at=NULL,updated_at=? WHERE id=?
                """, now(), planId);
    }

    public void addHistory(long planId, int revision, String summary) {
        insert("INSERT INTO learning_plan_history(plan_id,revision,summary,created_at) VALUES(?,?,?,?)",
                planId, revision, summary, Instant.now());
    }

    public List<Map<String, Object>> history(long planId) {
        return query("SELECT revision,summary,created_at FROM learning_plan_history WHERE plan_id=? ORDER BY revision DESC", planId);
    }

    public void handoff(long planId, int revision, String role, String method, String input, String output) {
        insert("""
                INSERT INTO learning_handoffs(plan_id,revision,role_name,method,input_summary,output_summary,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, planId, revision, role, method, input, output, Instant.now());
    }

    public List<Map<String, Object>> handoffs(long planId) {
        return query("""
                SELECT revision,role_name,method,input_summary,output_summary,created_at FROM learning_handoffs
                WHERE plan_id=? ORDER BY id DESC
                """, planId);
    }

    public boolean claimExplanation(long id, int revision, String claim) {
        return jdbc.update("""
                UPDATE learning_plans SET explanation_state='RUNNING',explanation_started_at=?,explanation_claim=?
                WHERE id=? AND revision=? AND (explanation_state IN ('NONE','FAILED')
                OR (explanation_state='RUNNING' AND explanation_started_at<?))
                """, now(), claim, id, revision, utc(Instant.now().minusSeconds(120))) == 1;
    }

    public boolean saveExplanation(long id, int revision, String claim, String text, String state) {
        return jdbc.update("""
                UPDATE learning_plans SET explanation=?,explanation_state=?
                WHERE id=? AND revision=? AND explanation_state='RUNNING' AND explanation_claim=?
                """, text, state, id, revision, claim) == 1;
    }

    long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i] instanceof Instant instant ? utc(instant) : args[i]);
            }
            return statement;
        }, key);
        Number result = key.getKey();
        if (result == null) throw new IllegalStateException("Insert did not return a key");
        return result.longValue();
    }

    Map<String, Object> one(String sql, Object... args) {
        var rows = query(sql, args);
        if (rows.isEmpty()) throw new NotFoundException();
        return rows.get(0);
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String label = meta.getColumnLabel(i);
                StringBuilder camel = new StringBuilder();
                boolean upper = false;
                for (char c : label.toCharArray()) {
                    if (c == '_') upper = true;
                    else {
                        camel.append(upper ? Character.toUpperCase(c) : c);
                        upper = false;
                    }
                }
                Object value = rs.getObject(i);
                // Connector/J 对 DATETIME 可能返回 LocalDateTime；本项目数据库和写入时间均使用 UTC。
                item.put(camel.toString(), value instanceof Timestamp t ? t.toInstant().toString()
                        : value instanceof java.time.LocalDateTime t ? t.toInstant(java.time.ZoneOffset.UTC).toString() : value);
            }
            return item;
        }, args);
    }

    private java.time.LocalDateTime now() { return utc(Instant.now()); }

    private java.time.LocalDateTime utc(Instant instant) {
        return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    public static long number(Map<String, Object> item, String key) {
        return ((Number) item.get(key)).longValue();
    }
}
