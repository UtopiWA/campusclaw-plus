package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.grading.GradingOutcome;
import com.campusclaw.persistence.Assignment;
import org.springframework.stereotype.Service;

/** 仅供已发布的旧客观作业按原快照批改，不提供模板或发布接口。 */
@Service
public class LegacyObjectiveGrader {
    public static final String MODEL = "rule-math-v1";
    private final LearningRepository repository;

    public LegacyObjectiveGrader(LearningRepository repository) { this.repository = repository; }

    public GradingOutcome tryGrade(Assignment assignment, String answer) {
        var snapshot = repository.objectiveAssignment(assignment.getId(), assignment.getClassId());
        if (snapshot == null) return null;
        boolean correct;
        try {
            correct = LearningRules.correct(snapshot.get("kind").toString(), answer, snapshot.get("expectedAnswer").toString());
        } catch (BadRequestException exception) {
            correct = false;
        }
        String rationale = (correct ? "答案正确。" : "答案不符或作答格式不正确。") + "参考答案："
                + snapshot.get("expectedAnswer") + "；" + snapshot.get("explanation");
        if (rationale.codePointCount(0, rationale.length()) > 2000) {
            rationale = rationale.substring(0, rationale.offsetByCodePoints(0, 2000));
        }
        return new GradingOutcome(correct ? assignment.getMaxScore() : 0, assignment.getMaxScore(), "[]",
                rationale, MODEL, "objective-v1");
    }
}
