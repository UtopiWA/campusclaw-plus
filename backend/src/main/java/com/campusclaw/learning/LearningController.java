package com.campusclaw.learning;

import com.campusclaw.auth.CurrentUserService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final CurrentUserService users;
    private final LearningCatalogService catalog;
    private final LearningStudyService study;
    private final LearningPlanService plans;
    private final LearningAnalyticsService analytics;
    private final MaterialTagService tags;

    public LearningController(CurrentUserService users, LearningCatalogService catalog, LearningStudyService study,
                               LearningPlanService plans, LearningAnalyticsService analytics, MaterialTagService tags) {
        this.users = users;
        this.catalog = catalog;
        this.study = study;
        this.plans = plans;
        this.analytics = analytics;
        this.tags = tags;
    }

    @GetMapping("/tags")
    public Map<String, Object> tags() { return catalog.tags(users.requireUser()); }

    @PostMapping({"/tags", "/points"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPoint(@Valid @RequestBody LearningRequests.PointInput input) {
        return catalog.savePoint(users.requireTeacher(), null, input);
    }

    @PutMapping({"/tags/{id}", "/points/{id}"})
    public Map<String, Object> updatePoint(@PathVariable long id, @Valid @RequestBody LearningRequests.PointInput input) {
        return catalog.savePoint(users.requireTeacher(), id, input);
    }

    @PutMapping("/materials/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceMaterialTags(@PathVariable long id, @Valid @RequestBody LearningRequests.BindingInput input) {
        tags.replace(users.requireTeacher(), id, input.pointIds());
    }

    @PostMapping("/points/{id}/materials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void material(@PathVariable long id, @Valid @RequestBody LearningRequests.MaterialInput input) {
        catalog.material(users.requireTeacher(), id, input.materialId(), false);
    }

    @DeleteMapping("/points/{id}/materials/{materialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMaterial(@PathVariable long id, @PathVariable long materialId) {
        catalog.material(users.requireTeacher(), id, materialId, true);
    }

    @GetMapping("/assignments/{id}/points")
    public List<Map<String, Object>> assignmentPoints(@PathVariable long id) { return catalog.assignmentPoints(users.requireUser(), id); }

    @GetMapping("/profile")
    public Map<String, Object> profile() { return study.dashboard(users.requireUser()); }

    @GetMapping("/plan")
    public Map<String, Object> plan() { return plans.current(users.requireUser()); }

    @PostMapping("/plan")
    public Map<String, Object> generate(@Valid @RequestBody LearningRequests.PlanInput input) { return plans.generate(users.requireUser(), input); }

    @PostMapping("/plans/{id}/tasks/{taskId}/complete")
    public Map<String, Object> complete(@PathVariable long id, @PathVariable long taskId) { return plans.finish(users.requireUser(), id, taskId); }

    @PostMapping("/plans/{id}/explanation")
    public Map<String, Object> explain(@PathVariable long id) { return plans.explain(users.requireUser(), id); }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return analytics.summary(users.requireTeacher(), from, to);
    }
}
