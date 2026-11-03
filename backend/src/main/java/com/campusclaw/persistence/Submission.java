package com.campusclaw.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "submissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_submissions_version",
                columnNames = {"assignment_id", "student_id", "version"}),
        @UniqueConstraint(name = "uk_submissions_content",
                columnNames = {"assignment_id", "student_id", "content_hash"}),
        @UniqueConstraint(name = "uk_submissions_client_key",
                columnNames = {"assignment_id", "student_id", "client_key"})
})
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "client_key", length = 128)
    private String clientKey;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Submission() {
    }

    public Submission(Long assignmentId, Long classId, Long studentId, String content,
                      String contentHash, String clientKey, int version) {
        this.assignmentId = assignmentId;
        this.classId = classId;
        this.studentId = studentId;
        this.content = content;
        this.contentHash = contentHash;
        this.clientKey = clientKey;
        this.version = version;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getClassId() { return classId; }
    public Long getStudentId() { return studentId; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public String getClientKey() { return clientKey; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
