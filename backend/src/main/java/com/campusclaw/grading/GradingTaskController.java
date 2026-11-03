package com.campusclaw.grading;

import com.campusclaw.auth.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading")
public class GradingTaskController {
    private final GradingTaskQueryService queries;
    private final CurrentUserService currentUsers;

    public GradingTaskController(GradingTaskQueryService queries, CurrentUserService currentUsers) {
        this.queries = queries;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/tasks/{taskId}")
    public GradingTaskView task(@PathVariable String taskId) {
        return queries.view(taskId, currentUsers.requireUser());
    }
}
