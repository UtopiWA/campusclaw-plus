package com.campusclaw.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assignments")
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(nullable = false)
    private String title;

    @Column(name = "prompt_text", length = 4000)
    private String promptText;

    @Column(name = "rubric_json", columnDefinition = "JSON")
    private String rubricJson;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(nullable = false)
    private boolean gradable = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Assignment() {
    }

    /** 兼容既有演示作业；没有题目和评分依据时不可批改。 */
    public Assignment(Long classId, String title) {
        this.classId = classId;
        this.title = title;
        this.gradable = false;
    }

    public Assignment(Long classId, String title, String promptText, String rubricJson, int maxScore, Long createdBy) {
        this.classId = classId;
        this.title = title;
        this.promptText = promptText;
        this.rubricJson = rubricJson;
        this.maxScore = maxScore;
        this.createdBy = createdBy;
        this.gradable = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public String getTitle() { return title; }
    public String getPromptText() { return promptText; }
    public String getRubricJson() { return rubricJson; }
    public Integer getMaxScore() { return maxScore; }
    public boolean isGradable() { return gradable; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
