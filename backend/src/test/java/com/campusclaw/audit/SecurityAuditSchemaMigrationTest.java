package com.campusclaw.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SecurityAuditSchemaMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("audit_migration").withUsername("audit_test")
            .withPassword("synthetic-migration-password").withCommand("--ngram-token-size=2");

    @Test
    void upgradesPopulatedV13AndKeepsAuditSnapshotsAfterSkillDeletion() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("13").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO classes(id,name) VALUES(1,'Synthetic class')");
            sql.executeUpdate("""
                    INSERT INTO users(id,username,password_hash,role,class_id,enabled)
                    VALUES(1,'synthetic-teacher','synthetic-hash','TEACHER',1,TRUE)
                    """);
            sql.executeUpdate("""
                    INSERT INTO assistants(id,class_id,name,prompt_text,enabled,created_at,updated_at)
                    VALUES(1,1,'synthetic-assistant','synthetic prompt',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            sql.executeUpdate("""
                    INSERT INTO skills(id,class_id,name,trigger_text,material_scope,tool_refs,enabled,
                                       instructions,source_text,created_at,updated_at)
                    VALUES(1,1,'synthetic-skill','trigger','','knowledge_search',TRUE,
                           'synthetic instructions','synthetic source',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            sql.executeUpdate("""
                    INSERT INTO conversations(id,class_id,user_id,assistant_id,title,created_at,updated_at)
                    VALUES(1,1,1,1,'synthetic conversation',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            sql.executeUpdate("""
                    INSERT INTO messages(id,conversation_id,role,content,skill_name,citations_json,status,created_at)
                    VALUES(1,1,'ASSISTANT','historical answer','synthetic-skill',JSON_ARRAY(),'COMPLETED',UTC_TIMESTAMP(6))
                    """);
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT content,skill_id,prompt_tokens,completion_tokens,total_tokens,model_duration_ms,request_id FROM messages WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("historical answer");
                for (int index = 2; index <= 7; index++) assertThat(rows.getObject(index)).isNull();
            }
            sql.executeUpdate("UPDATE messages SET skill_id=1,prompt_tokens=2,completion_tokens=3,total_tokens=5,model_duration_ms=9 WHERE id=1");
            sql.executeUpdate("""
                    INSERT INTO security_audit_events(class_id,user_id,action,object_type,object_id,skill_id,
                                                     skill_name,status,http_status,created_at)
                    VALUES(1,1,'CONFIG_WRITE','SKILL',1,1,'synthetic-skill','DENIED',403,UTC_TIMESTAMP(6))
                    """);
            sql.executeUpdate("DELETE FROM skills WHERE id=1");
            try (var rows = sql.executeQuery("SELECT skill_id,skill_name,total_tokens FROM messages WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1L);
                assertThat(rows.getString(2)).isEqualTo("synthetic-skill");
                assertThat(rows.getInt(3)).isEqualTo(5);
            }
            try (var rows = sql.executeQuery("SELECT skill_id,skill_name,status FROM security_audit_events")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1L);
                assertThat(rows.getString(2)).isEqualTo("synthetic-skill");
                assertThat(rows.getString(3)).isEqualTo("DENIED");
            }
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE messages SET total_tokens=-1 WHERE id=1"))
                    .isInstanceOf(SQLException.class);
            try (var rows = sql.executeQuery("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('idx_messages_skill_created','idx_messages_created_id','idx_audit_class_created','idx_audit_class_user_created','idx_audit_class_skill_created','idx_audit_action_created')")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(6);
            }
        }
    }
}
