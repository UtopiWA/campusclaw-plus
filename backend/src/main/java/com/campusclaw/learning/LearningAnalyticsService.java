package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.persistence.UserAccount;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campusclaw.learning.LearningCatalogService.requireTeacher;
import static com.campusclaw.learning.LearningRepository.number;

@Service
public class LearningAnalyticsService {
    private final LearningRepository repository;
    private final LearningCatalogService catalog;

    public LearningAnalyticsService(LearningRepository repository, LearningCatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UserAccount teacher, String fromText, String toText) {
        requireTeacher(teacher);
        Instant from = Instant.parse("2000-01-01T00:00:00Z");
        Instant to = Instant.parse("2100-01-01T00:00:00Z");
        if (fromText != null || toText != null) {
            try {
                LocalDate end = toText == null ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(toText);
                LocalDate start = fromText == null ? end.minusDays(29) : LocalDate.parse(fromText);
                if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 365) {
                    throw new BadRequestException("请选择不超过366天的有效统计范围");
                }
                from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
                to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeException exception) {
                throw new BadRequestException("请输入有效日期，例如 2026-11-11");
            }
        }
        var evidence = repository.evidence(teacher.getClassId(), null, from, to);
        var students = repository.students(teacher.getClassId());
        var tags = catalog.points(teacher.getClassId());
        var byStudent = evidence.stream().collect(Collectors.groupingBy(row -> number(row, "studentId")));
        // 先计算每名学生的最近20次表现，再对有证据的学生等权平均，避免刷题次数影响班级均值。
        var profiles = students.stream().collect(Collectors.toMap(row -> number(row, "id"),
                row -> LearningRules.profile(tags, byStudent.getOrDefault(number(row, "id"), List.of()))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var tag : tags) {
            long tagId = number(tag, "id");
            List<Map<String, Object>> details = new ArrayList<>();
            for (var student : students) {
                var profile = profiles.get(number(student, "id")).stream().filter(row -> number(row, "id") == tagId).findFirst().orElseThrow();
                var detail = new LinkedHashMap<String, Object>();
                for (String key : List.of("mastery", "state", "sampleCount", "totalCount", "lastStudiedAt")) {
                    detail.put(key, profile.get(key));
                }
                detail.put("studentId", student.get("id"));
                detail.put("username", student.get("username"));
                details.add(detail);
            }
            var measured = details.stream().filter(row -> row.get("mastery") != null).toList();
            var row = new LinkedHashMap<String, Object>();
            row.put("pointId", tagId);
            row.put("name", tag.get("name"));
            row.put("enabled", tag.get("enabled"));
            row.put("mastery", measured.isEmpty() ? null : Math.round(measured.stream()
                    .mapToDouble(item -> ((Number) item.get("mastery")).doubleValue()).average().orElseThrow() * 10) / 10.0);
            row.put("participants", measured.size());
            row.put("notParticipating", students.size() - measured.size());
            row.put("attempts", details.stream().mapToLong(item -> number(item, "totalCount")).sum());
            row.put("mastered", details.stream().filter(item -> "MASTERED".equals(item.get("state"))).count());
            row.put("weak", details.stream().filter(item -> "WEAK".equals(item.get("state"))).count());
            row.put("students", details);
            rows.add(row);
        }
        return Map.of("students", students.size(), "points", rows, "ruleVersion", LearningRules.VERSION,
                "denominator", "每名学生取该标签最近20次有效得分比例的均值；班级掌握度为有证据学生的等权平均。未作答不计零分，教师终评优先。",
                "from", fromText == null ? "" : fromText, "to", toText == null ? "" : toText);
    }
}
