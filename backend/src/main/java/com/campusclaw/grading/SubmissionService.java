package com.campusclaw.grading;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingEventType;
import com.campusclaw.persistence.GradingOutbox;
import com.campusclaw.persistence.GradingOutboxRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.Submission;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {
    private static final int MAX_CONTENT_CODE_POINTS = 10000;
    private static final int MAX_CLIENT_KEY_LENGTH = 128;

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final GradingTaskRepository tasks;
    private final GradingOutboxRepository outbox;
    private final GradingProperties properties;

    public SubmissionService(AssignmentRepository assignments, SubmissionRepository submissions,
                             GradingTaskRepository tasks, GradingOutboxRepository outbox,
                             GradingProperties properties) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.tasks = tasks;
        this.outbox = outbox;
        this.properties = properties;
    }

    @Transactional
    public SubmitAssignmentResponse submit(Long assignmentId, String content, String clientKey, UserAccount user) {
        if (user.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Student role required");
        }
        String normalized = normalize(content);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || codePoints > MAX_CONTENT_CODE_POINTS) {
            throw new BadRequestException("作答内容应为 1～10000 个字符");
        }
        String key = normalizeClientKey(clientKey);
        Assignment assignment = assignments
                .findByIdAndClassIdAndGradableTrue(assignmentId, user.getClassId())
                .orElseThrow(NotFoundException::new);
        if (key != null) {
            var byKey = submissions.findFirstByAssignmentIdAndStudentIdAndClientKey(
                    assignment.getId(), user.getId(), key);
            if (byKey.isPresent()) return response(byKey.get());
        }
        String hash = sha256(normalized);
        var byContent = submissions.findFirstByAssignmentIdAndStudentIdAndContentHash(
                assignment.getId(), user.getId(), hash);
        if (byContent.isPresent()) return response(byContent.get());

        int version = submissions.findMaxVersion(assignment.getId(), user.getId()) + 1;
        Submission submission = submissions.saveAndFlush(new Submission(
                assignment.getId(), user.getClassId(), user.getId(), normalized, hash, key, version));
        String taskId = UUID.randomUUID().toString();
        GradingTask task = new GradingTask(taskId, submission.getId(), assignment.getId(),
                user.getClassId(), user.getId(), properties.maxAttempts(),
                "grade:" + submission.getId() + ":" + version);
        tasks.saveAndFlush(task);
        outbox.save(new GradingOutbox(GradingEventType.GRADING_DISPATCH, taskId, Instant.now()));
        return response(submission, task);
    }

    private SubmitAssignmentResponse response(Submission submission) {
        GradingTask task = tasks.findFirstBySubmissionIdOrderByCreatedAtDesc(submission.getId())
                .orElseThrow(() -> new IllegalStateException("Submission has no grading task"));
        return response(submission, task);
    }

    private SubmitAssignmentResponse response(Submission submission, GradingTask task) {
        return new SubmitAssignmentResponse(
                task.getTaskId(),
                task.getStatus().name(),
                "/api/grading/tasks/" + task.getTaskId(),
                submission.getId(),
                submission.getVersion());
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String normalizeClientKey(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) return null;
        String value = clientKey.trim();
        if (value.length() > MAX_CLIENT_KEY_LENGTH || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new BadRequestException("Idempotency-Key 格式不合法");
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
