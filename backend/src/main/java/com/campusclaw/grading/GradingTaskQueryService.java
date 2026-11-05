package com.campusclaw.grading;

import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingResult;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.Submission;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import com.campusclaw.persistence.SubmissionReviewRepository;
import com.campusclaw.persistence.SubmissionReview;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingTaskQueryService {
    private final GradingTaskRepository tasks;
    private final GradingResultRepository results;
    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;
    private final UserAccountRepository users;
    private final ObjectMapper objectMapper;
    private final SubmissionReviewRepository reviews;

    public GradingTaskQueryService(GradingTaskRepository tasks, GradingResultRepository results,
                                   SubmissionRepository submissions, AssignmentRepository assignments,
                                   UserAccountRepository users, ObjectMapper objectMapper,
                                   SubmissionReviewRepository reviews) {
        this.tasks = tasks;
        this.results = results;
        this.submissions = submissions;
        this.assignments = assignments;
        this.users = users;
        this.objectMapper = objectMapper;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public GradingTaskView view(String taskId, UserAccount user) {
        GradingTask task = tasks.findById(taskId).orElseThrow(NotFoundException::new);
        boolean allowed = switch (user.getRole()) {
            case STUDENT -> user.getId().equals(task.getStudentId())
                    && user.getClassId().equals(task.getClassId());
            case TEACHER -> user.getClassId().equals(task.getClassId());
        };
        if (!allowed) throw new NotFoundException();
        GradingResultView result = results.findByTaskId(taskId).map(this::resultView).orElse(null);
        String content = submissions.findById(task.getSubmissionId()).map(Submission::getContent).orElse("");
        TeacherReviewView review = reviews.findBySubmissionId(task.getSubmissionId())
                .map(TeacherReviewView::from).orElse(null);
        return new GradingTaskView(
                task.getTaskId(), task.getSubmissionId(), task.getAssignmentId(),
                task.getStatus().name(), task.getAttempts(), task.getMaxAttempts(),
                task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt(),
                task.getLastFailureCode(), task.getFailureCode(), result, content, review);
    }

    @Transactional(readOnly = true)
    public List<TeacherSubmissionView> teacherSubmissions(Long assignmentId, UserAccount teacher) {
        Assignment assignment = assignments.findByIdAndClassId(assignmentId, teacher.getClassId())
                .filter(Assignment::isGradable)
                .orElseThrow(NotFoundException::new);
        List<Submission> historical = submissions.findByAssignmentIdAndClassIdOrderByStudentIdAscVersionDesc(
                assignment.getId(), teacher.getClassId(), PageRequest.of(0, 200));
        Map<Long, Submission> latest = new java.util.LinkedHashMap<>();
        historical.forEach(item -> latest.putIfAbsent(item.getStudentId(), item));
        List<Submission> items = List.copyOf(latest.values());
        if (items.isEmpty()) return List.of();
        List<Long> submissionIds = items.stream().map(Submission::getId).toList();
        Map<Long, GradingTask> taskBySubmission = new HashMap<>();
        for (GradingTask task : tasks.findBySubmissionIdIn(submissionIds)) {
            taskBySubmission.putIfAbsent(task.getSubmissionId(), task);
        }
        Map<Long, GradingResult> resultBySubmission = new HashMap<>();
        for (GradingResult result : results.findBySubmissionIdIn(submissionIds)) {
            resultBySubmission.putIfAbsent(result.getSubmissionId(), result);
        }
        Map<Long, SubmissionReview> reviewBySubmission = reviews.findBySubmissionIdIn(submissionIds).stream()
                .collect(Collectors.toMap(SubmissionReview::getSubmissionId, r -> r));
        Set<Long> studentIds = items.stream().map(Submission::getStudentId).collect(Collectors.toSet());
        Map<Long, String> usernames = users.findAllById(studentIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));
        return items.stream().map(submission -> {
            GradingTask task = taskBySubmission.get(submission.getId());
            GradingResult result = resultBySubmission.get(submission.getId());
            SubmissionReview review = reviewBySubmission.get(submission.getId());
            return new TeacherSubmissionView(
                    submission.getId(), submission.getStudentId(),
                    usernames.getOrDefault(submission.getStudentId(), "未知学生"),
                    submission.getVersion(), submission.getContent(), submission.getCreatedAt(),
                    task == null ? null : task.getTaskId(),
                    task == null ? null : task.getStatus().name(),
                    review != null ? Integer.valueOf(review.getScore()) : result == null ? null : result.getScore(),
                    assignment.getMaxScore(),
                    task == null ? null : task.getFailureCode(),
                    result == null ? null : resultView(result),
                    review == null ? null : TeacherReviewView.from(review));
        }).toList();
    }

    private GradingResultView resultView(GradingResult result) {
        List<GradingResultView.CriterionResult> criteria;
        try {
            criteria = objectMapper.readValue(result.getCriteriaJson(),
                    new TypeReference<List<GradingResultView.CriterionResult>>() { });
        } catch (Exception exception) {
            criteria = List.of();
        }
        return new GradingResultView(result.getScore(), result.getMaxScore(), criteria, result.getRationale());
    }
}
