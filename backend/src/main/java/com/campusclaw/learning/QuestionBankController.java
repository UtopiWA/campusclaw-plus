package com.campusclaw.learning;

import com.campusclaw.auth.CurrentUserService;
import com.campusclaw.grading.GradingTaskView;
import com.campusclaw.grading.SubmitAssignmentResponse;
import com.campusclaw.grading.SubmitRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/questions")
public class QuestionBankController {
    private final QuestionBankService questions;
    private final CurrentUserService users;

    public QuestionBankController(QuestionBankService questions, CurrentUserService users) {
        this.questions = questions;
        this.users = users;
    }
    @GetMapping
    public List<Map<String, Object>> list() { return questions.list(users.requireUser()); }
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) { return questions.get(users.requireUser(), id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody QuestionBankRequests.Question input) { return questions.save(users.requireTeacher(), null, input); }
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody QuestionBankRequests.Question input) {
        return questions.save(users.requireTeacher(), id, input);
    }
    @PutMapping("/{id}/availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void availability(@PathVariable long id, @RequestBody QuestionBankRequests.Availability input) {
        questions.availability(users.requireTeacher(), id, input.enabled());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { questions.delete(users.requireTeacher(), id); }
    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitAssignmentResponse submit(@PathVariable long id, @Valid @RequestBody SubmitRequest input,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestHeader(value = "If-Match", required = false) Long contentVersion) {
        return questions.submit(users.requireUser(), id, input.content(), key, contentVersion);
    }
    @GetMapping("/{id}/response")
    public GradingTaskView latest(@PathVariable long id) { return questions.latest(users.requireUser(), id); }
}
