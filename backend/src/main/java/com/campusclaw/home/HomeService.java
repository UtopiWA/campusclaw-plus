package com.campusclaw.home;

import com.campusclaw.learning.LearningAnalyticsService;
import com.campusclaw.learning.LearningPlanService;
import com.campusclaw.learning.LearningStudyService;
import com.campusclaw.persistence.UserAccount;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campusclaw.learning.LearningCatalogService.requireStudent;
import static com.campusclaw.learning.LearningCatalogService.requireTeacher;
import static com.campusclaw.learning.LearningRepository.number;

@Service
public class HomeService {
    private final HomeRepository repository;
    private final LearningAnalyticsService analytics;
    private final LearningStudyService study;
    private final LearningPlanService plans;

    public HomeService(HomeRepository repository, LearningAnalyticsService analytics, LearningStudyService study, LearningPlanService plans) {
        this.repository = repository;
        this.analytics = analytics;
        this.study = study;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> teacher(UserAccount teacher) {
        requireTeacher(teacher);
        long classId = teacher.getClassId();
        var profile = analytics.summary(teacher, null, null);
        var points = list(profile.get("points")).stream().filter(p -> !Boolean.FALSE.equals(p.get("enabled"))).toList();
        var bars = points.stream().filter(p -> p.get("mastery") != null)
                .sorted(Comparator.<Map<String, Object>>comparingDouble(p -> ((Number) p.get("mastery")).doubleValue())
                        .thenComparingLong(p -> number(p, "pointId")))
                .limit(8).map(p -> fields(p, "pointId", "name", "mastery", "participants")).toList();
        var counts = repository.teacherCounts(classId);
        counts.put("students", profile.get("students"));
        return Map.of("stats", counts, "mastery", bars, "unknownTags", points.stream().filter(p -> p.get("mastery") == null).count(),
                "assignments", repository.recentAssignments(classId), "attention", repository.attention(classId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> student(UserAccount student) {
        requireStudent(student);
        long classId = student.getClassId();
        var points = list(study.dashboard(student).get("points")).stream().filter(p -> !Boolean.FALSE.equals(p.get("enabled"))).toList();
        var states = List.of("MASTERED", "DEVELOPING", "WEAK", "UNKNOWN").stream().map(state ->
                Map.<String, Object>of("state", state, "count", points.stream().filter(p -> state.equals(p.get("state"))).count())).toList();
        var preview = plans.preview(student);
        var tasks = list(preview.getOrDefault("tasks", List.of()));
        var pendingTasks = tasks.stream().filter(t -> !"DONE".equals(t.get("status"))).toList();
        var plan = preview.isEmpty() ? Map.of() : Map.of("total", tasks.size(), "done", tasks.size() - pendingTasks.size(),
                "pending", pendingTasks.size(), "tasks", pendingTasks.stream().limit(5)
                        .map(t -> fields(t, "id", "title", "type", "day", "minutes", "instruction", "resources")).toList());
        var stats = repository.studentCounts(classId, student.getId());
        stats.put("weak", points.stream().filter(p -> "WEAK".equals(p.get("state"))).count());
        stats.put("planPending", pendingTasks.size());
        var pending = repository.pending(classId, student.getId());
        var tags = repository.tags(classId);
        pending.forEach(a -> a.put("tags", tags.stream().filter(t -> number(t, "assignmentId") == number(a, "id")).map(t -> t.get("name")).toList()));
        return Map.of("stats", stats, "mastery", states, "plan", plan, "pending", pending,
                "results", repository.recentResults(classId, student.getId()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) { return (List<Map<String, Object>>) value; }

    private Map<String, Object> fields(Map<String, Object> source, String... keys) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : keys) result.put(key, source.get(key));
        return result;
    }
}
