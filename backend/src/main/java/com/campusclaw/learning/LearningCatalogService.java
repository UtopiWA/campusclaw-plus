package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.ConflictException;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningCatalogService {
    private final LearningRepository repository;

    public LearningCatalogService(LearningRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tags(UserAccount user) {
        var points = points(user.getClassId());
        return Map.of("tags", points, "materialTags", repository.materialTags(user.getClassId()));
    }

    public List<Map<String, Object>> points(long classId) {
        var rows = repository.points(classId);
        if (rows.size() > 200) throw new BadRequestException("标签超过本版200项上限，请先整理课程范围");
        return rows;
    }

    @Transactional
    public Map<String, Object> savePoint(UserAccount teacher, Long id, LearningRequests.PointInput input) {
        requireTeacher(teacher);
        repository.lockClass(teacher.getClassId());
        validateCode(input.code());
        if (id != null && !repository.point(teacher.getClassId(), id).get("code").equals(input.code().trim())) {
            throw new BadRequestException("标签编号创建后不能修改");
        }
        Long existing = repository.pointByCode(teacher.getClassId(), input.code().trim());
        if (existing != null && !existing.equals(id)) throw new ConflictException("标签编号已存在");
        if (id == null && points(teacher.getClassId()).size() >= 200) throw new BadRequestException("本版最多200个标签");
        boolean duplicateName = points(teacher.getClassId()).stream()
                .anyMatch(row -> input.name().trim().equals(row.get("name")) && !Long.valueOf(LearningRepository.number(row, "id")).equals(id));
        if (duplicateName) throw new ConflictException("标签名称已存在");
        long result = repository.savePoint(teacher.getClassId(), teacher.getId(), id, input);
        return repository.point(teacher.getClassId(), result);
    }

    @Transactional
    public void material(UserAccount teacher, long pointId, long materialId, boolean remove) {
        requireTeacher(teacher);
        repository.lockClass(teacher.getClassId());
        repository.point(teacher.getClassId(), pointId);
        if (remove) repository.removeMaterial(teacher.getClassId(), pointId, materialId);
        else {
            repository.lockMaterial(teacher.getClassId(), materialId);
            repository.addMaterial(teacher.getClassId(), pointId, materialId);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> assignmentPoints(UserAccount user, long assignmentId) {
        return repository.assignmentPoints(user.getClassId(), assignmentId);
    }

    private void validateCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}")) {
            throw new BadRequestException("编号须为不超过40位的字母、数字、下划线或连字符");
        }
    }

    public static void requireTeacher(UserAccount user) {
        if (user.getRole() != Role.TEACHER) throw new AccessDeniedException("Teacher role required");
    }

    public static void requireStudent(UserAccount user) {
        if (user.getRole() != Role.STUDENT) throw new AccessDeniedException("Student role required");
    }
}
