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
@Table(name = "grading_results", uniqueConstraints = {
        @UniqueConstraint(name = "uk_grading_results_task", columnNames = {"task_id"}),
        @UniqueConstraint(name = "uk_grading_results_submission_version",
                columnNames = {"submission_id", "version"})
})
public class GradingResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private int score;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "criteria_json", nullable = false, columnDefinition = "JSON")
    private String criteriaJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GradingResult() {
    }

    public GradingResult(String taskId, Long submissionId, int version, int score, int maxScore,
                         String criteriaJson, String rationale, String modelName, String promptVersion) {
        this.taskId = taskId;
        this.submissionId = submissionId;
        this.version = version;
        this.score = score;
        this.maxScore = maxScore;
        this.criteriaJson = criteriaJson;
        this.rationale = rationale;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public Long getSubmissionId() { return submissionId; }
    public int getVersion() { return version; }
    public int getScore() { return score; }
    public int getMaxScore() { return maxScore; }
    public String getCriteriaJson() { return criteriaJson; }
    public String getRationale() { return rationale; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
