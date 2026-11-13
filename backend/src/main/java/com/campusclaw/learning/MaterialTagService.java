package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.persistence.UserAccount;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campusclaw.learning.LearningCatalogService.requireTeacher;
import static com.campusclaw.learning.LearningRepository.number;

/** 讲义标签可变；作业创建时复制标签及来源，历史成绩不随讲义改签而漂移。 */
@Service
public class MaterialTagService {
    private final LearningRepository repository;

    public MaterialTagService(LearningRepository repository) { this.repository = repository; }

    @Transactional
    public void lockForCreation(UserAccount teacher) {
        requireTeacher(teacher);
        repository.lockClass(teacher.getClassId());
    }

    @Transactional
    public void replace(UserAccount teacher, long materialId, List<Long> ids) {
        requireTeacher(teacher);
        if (ids == null || ids.size() > 20 || ids.stream().anyMatch(java.util.Objects::isNull)) throw new BadRequestException("每份讲义最多选择20个标签");
        repository.lockClass(teacher.getClassId());
        repository.lockMaterial(teacher.getClassId(), materialId);
        var distinct = ids.stream().distinct().toList();
        for (Long id : distinct) {
            if (!Boolean.TRUE.equals(repository.point(teacher.getClassId(), id).get("enabled"))) {
                throw new BadRequestException("请选择已启用的标签");
            }
        }
        repository.replaceMaterialTags(teacher.getClassId(), materialId, distinct);
    }

    @Transactional
    public void inherit(UserAccount teacher, long assignmentId, List<Long> materialIds) {
        requireTeacher(teacher);
        repository.lockClass(teacher.getClassId());
        repository.lockAssignment(teacher.getClassId(), assignmentId);
        if (materialIds == null || materialIds.isEmpty()) return;
        for (Long id : materialIds.stream().distinct().sorted().toList()) repository.lockMaterial(teacher.getClassId(), id);
        var sources = repository.materialTags(teacher.getClassId()).stream()
                .filter(row -> materialIds.contains(number(row, "id")) && Boolean.TRUE.equals(row.get("enabled"))).toList();
        repository.bindAssignment(teacher.getClassId(), assignmentId,
                sources.stream().map(row -> number(row, "pointId")).distinct().sorted().toList());
        for (Map<String, Object> source : sources) repository.tagSource(assignmentId, teacher.getClassId(),
                number(source, "id"), number(source, "pointId"));
    }
}
