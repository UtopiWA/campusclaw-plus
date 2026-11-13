package com.campusclaw.learning;

import com.campusclaw.persistence.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campusclaw.learning.LearningCatalogService.requireStudent;

@Service
public class LearningStudyService {
    private final LearningRepository repository;
    private final LearningCatalogService catalog;

    public LearningStudyService(LearningRepository repository, LearningCatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    public List<Map<String, Object>> evidence(UserAccount student) {
        return repository.evidence(student.getClassId(), student.getId(),
                Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UserAccount student) {
        requireStudent(student);
        var evidence = evidence(student);
        return Map.of("points", LearningRules.profile(catalog.points(student.getClassId()), evidence),
                "evidence", evidence, "evidenceCount", evidence.size(), "ruleVersion", LearningRules.VERSION);
    }
}
