package com.campusclaw.grading;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingResult;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.Submission;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.UserAccount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final GradingTaskRepository tasks;
    private final GradingResultRepository results;
    private final RubricCodec rubrics;

    public AssignmentService(AssignmentRepository assignments, SubmissionRepository submissions,
                             GradingTaskRepository tasks, GradingResultRepository results,
                             RubricCodec rubrics) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.tasks = tasks;
        this.results = results;
        this.rubrics = rubrics;
    }

    @Transactional
    public AssignmentView create(CreateAssignmentRequest request, UserAccount teacher) {
        List<RubricCriterion> criteria = request.criteria();
        if (criteria == null || criteria.isEmpty() || criteria.size() > 10) {
            throw new BadRequestException("评分依据数量应为 1～10 条");
        }
        Set<String> keys = new HashSet<>();
        long total = 0;
        for (RubricCriterion criterion : criteria) {
            if (!keys.add(criterion.key())) {
                throw new BadRequestException("评分依据 key 不能重复");
            }
            total += criterion.points();
        }
        if (total > 100) {
            throw new BadRequestException("评分依据总分不能超过 100");
        }
        String title = request.title().trim();
        String prompt = request.prompt().trim();
        if (title.isEmpty() || prompt.isEmpty()) {
            throw new BadRequestException("标题和题目不能为空");
        }
        Assignment assignment = new Assignment(teacher.getClassId(), title, prompt,
                rubrics.write(criteria), (int) total, teacher.getId());
        return view(assignments.saveAndFlush(assignment), null);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> list(UserAccount user) {
        List<Assignment> items = assignments.findByClassIdAndGradableTrueOrderByCreatedAtDesc(user.getClassId());
        if (items.isEmpty()) return List.of();
        Map<Long, AssignmentView.MyLatestSubmission> latest = user.getRole() == Role.STUDENT
                ? latestFor(user.getId(), items) : Map.of();
        List<AssignmentView> views = new ArrayList<>(items.size());
        for (Assignment assignment : items) {
            views.add(view(assignment, latest.get(assignment.getId())));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public AssignmentView get(Long id, UserAccount user) {
        Assignment assignment = assignments.findByIdAndClassIdAndGradableTrue(id, user.getClassId())
                .orElseThrow(NotFoundException::new);
        AssignmentView.MyLatestSubmission latest = user.getRole() == Role.STUDENT
                ? latestFor(assignment.getId(), user.getId()) : null;
        return view(assignment, latest);
    }

    @Transactional(readOnly = true)
    public Assignment requireForTeacher(Long id, UserAccount teacher) {
        return assignments.findByIdAndClassId(id, teacher.getClassId())
                .filter(Assignment::isGradable)
                .orElseThrow(NotFoundException::new);
    }

    private AssignmentView view(Assignment assignment, AssignmentView.MyLatestSubmission latest) {
        return new AssignmentView(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getPromptText(),
                rubrics.parse(assignment.getRubricJson()),
                assignment.getMaxScore() == null ? 0 : assignment.getMaxScore(),
                assignment.getCreatedAt(),
                latest);
    }

    private AssignmentView.MyLatestSubmission latestFor(Long assignmentId, Long studentId) {
        Submission submission = submissions
                .findFirstByAssignmentIdAndStudentIdOrderByVersionDesc(assignmentId, studentId)
                .orElse(null);
        if (submission == null) return null;
        GradingTask task = tasks.findFirstBySubmissionIdOrderByCreatedAtDesc(submission.getId()).orElse(null);
        GradingResult result = results
                .findBySubmissionIdAndVersion(submission.getId(), submission.getVersion())
                .orElse(null);
        return new AssignmentView.MyLatestSubmission(
                submission.getId(), submission.getVersion(),
                task == null ? null : task.getTaskId(),
                task == null ? null : task.getStatus().name(),
                result == null ? null : result.getScore(),
                result == null ? null : result.getMaxScore());
    }

    private Map<Long, AssignmentView.MyLatestSubmission> latestFor(Long studentId, List<Assignment> items) {
        List<Long> assignmentIds = items.stream().map(Assignment::getId).toList();
        List<Submission> all = submissions.findByStudentIdAndAssignmentIdInOrderByIdDesc(studentId, assignmentIds);
        Map<Long, Submission> latest = new HashMap<>();
        for (Submission submission : all) {
            latest.putIfAbsent(submission.getAssignmentId(), submission);
        }
        if (latest.isEmpty()) return Map.of();
        List<Long> submissionIds = latest.values().stream().map(Submission::getId).toList();
        Map<Long, GradingTask> taskBySubmission = new HashMap<>();
        for (GradingTask task : tasks.findBySubmissionIdIn(submissionIds)) {
            taskBySubmission.putIfAbsent(task.getSubmissionId(), task);
        }
        Map<Long, GradingResult> resultBySubmission = new HashMap<>();
        for (GradingResult result : results.findBySubmissionIdIn(submissionIds)) {
            resultBySubmission.putIfAbsent(result.getSubmissionId(), result);
        }
        Map<Long, AssignmentView.MyLatestSubmission> values = new HashMap<>();
        for (Map.Entry<Long, Submission> entry : latest.entrySet()) {
            Submission submission = entry.getValue();
            GradingTask task = taskBySubmission.get(submission.getId());
            GradingResult result = resultBySubmission.get(submission.getId());
            values.put(entry.getKey(), new AssignmentView.MyLatestSubmission(
                    submission.getId(), submission.getVersion(),
                    task == null ? null : task.getTaskId(),
                    task == null ? null : task.getStatus().name(),
                    result == null ? null : result.getScore(),
                    result == null ? null : result.getMaxScore()));
        }
        return values;
    }
}
