package com.campusclaw.grading;

import com.campusclaw.auth.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {
    private final AssignmentService assignments;
    private final SubmissionService submissions;
    private final GradingTaskQueryService queries;
    private final CurrentUserService currentUsers;
    private final AssignmentCreationService creation;
    private final SubmissionReviewService reviews;

    public AssignmentController(AssignmentService assignments, SubmissionService submissions,
                                GradingTaskQueryService queries, CurrentUserService currentUsers,
                                AssignmentCreationService creation, SubmissionReviewService reviews) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.queries = queries;
        this.currentUsers = currentUsers;
        this.creation = creation;
        this.reviews = reviews;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView create(@Valid @RequestBody CreateAssignmentRequest request) {
        return creation.create(request, currentUsers.requireTeacher());
    }

    @GetMapping
    public List<AssignmentView> list() {
        return assignments.list(currentUsers.requireUser());
    }

    @GetMapping("/{id}")
    public AssignmentView get(@PathVariable Long id) {
        return assignments.get(id, currentUsers.requireUser());
    }

    @PostMapping("/{id}/submissions")
    public ResponseEntity<SubmitAssignmentResponse> submit(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitRequest request) {
        SubmitAssignmentResponse response = submissions.submit(
                id, request.content(), idempotencyKey, currentUsers.requireUser());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}/submissions")
    public List<TeacherSubmissionView> submissions(@PathVariable Long id) {
        return queries.teacherSubmissions(id, currentUsers.requireTeacher());
    }

    @PutMapping("/{id}/submissions/{submissionId}/review")
    public TeacherReviewView review(@PathVariable Long id, @PathVariable Long submissionId,
                                    @Valid @RequestBody ReviewSubmissionRequest request) {
        return reviews.review(id, submissionId, request, currentUsers.requireTeacher());
    }
}
