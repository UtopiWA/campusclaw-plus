package com.campusclaw.grading;

import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingResult;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTaskStatus;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.Submission;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.SubmissionReview;
import com.campusclaw.persistence.SubmissionReviewRepository;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GradingTaskQueryServiceTest {
    @Test
    void lateAiResultKeepsIndependentTeacherReview() {
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingResultRepository results = mock(GradingResultRepository.class);
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SubmissionReviewRepository reviews = mock(SubmissionReviewRepository.class);
        GradingTask task = new GradingTask("task-review", 5L, 3L, 1L, 2L, 5, "key");
        ReflectionTestUtils.setField(task, "status", GradingTaskStatus.SUCCEEDED);
        Assignment assignment = new Assignment(1L, "作业", "题目", "[]", 100, 1L);
        ReflectionTestUtils.setField(assignment, "id", 3L);
        Submission submission = new Submission(3L, 1L, 2L, "作答", "hash", null, 1);
        ReflectionTestUtils.setField(submission, "id", 5L);
        SubmissionReview review = new SubmissionReview(submission);
        review.update(1L, 90, "教师终评保留");
        GradingResult lateResult = new GradingResult("task-review", 5L, 1, 60, 100, "[]",
                "迟到的自动批改", "model", "v5");
        when(tasks.findById("task-review")).thenReturn(Optional.of(task));
        when(assignments.findById(3L)).thenReturn(Optional.of(assignment));
        when(results.findByTaskId("task-review")).thenReturn(Optional.of(lateResult));
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));
        when(reviews.findBySubmissionId(5L)).thenReturn(Optional.of(review));
        UserAccount teacher = new UserAccount("teacher", "synthetic", Role.TEACHER, 1L);

        var view = new GradingTaskQueryService(tasks, results, submissions, assignments,
                mock(UserAccountRepository.class), new ObjectMapper(), reviews).view("task-review", teacher);

        assertNotNull(view.result());
        assertEquals(60, view.result().score());
        assertNotNull(view.teacherReview());
        assertEquals(90, view.teacherReview().score());
        assertEquals("教师终评保留", view.teacherReview().comment());
    }

    @Test
    void studentMovedToAnotherClassCannotReadOldClassTask() {
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingResultRepository results = mock(GradingResultRepository.class);
        when(tasks.findById("task")).thenReturn(Optional.of(
                new GradingTask("task", 1L, 1L, 1L, 2L, 5, "key")));
        UserAccount moved = new UserAccount("student", "synthetic", Role.STUDENT, 9L);
        ReflectionTestUtils.setField(moved, "id", 2L);
        GradingTaskQueryService service = new GradingTaskQueryService(tasks, results,
                mock(SubmissionRepository.class), mock(AssignmentRepository.class),
                mock(UserAccountRepository.class), new ObjectMapper(), mock(com.campusclaw.persistence.SubmissionReviewRepository.class));
        assertThrows(NotFoundException.class, () -> service.view("task", moved));
        verifyNoInteractions(results);
    }
}
