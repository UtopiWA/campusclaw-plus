package com.campusclaw.grading;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.IndexStatus;
import com.campusclaw.persistence.KnowledgeEntryRepository;
import com.campusclaw.persistence.MaterialRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 读取参考答案并保存不可变快照，创建作业不调用模型。 */
@Service
public class AssignmentCreationService {
    private final AssignmentService assignments;
    private final MaterialRepository materials;
    private final KnowledgeEntryRepository entries;
    private final ObjectMapper mapper;

    public AssignmentCreationService(AssignmentService assignments, MaterialRepository materials,
                                      KnowledgeEntryRepository entries, ObjectMapper mapper) {
        this.assignments = assignments;
        this.materials = materials;
        this.entries = entries;
        this.mapper = mapper;
    }

    public AssignmentView create(CreateAssignmentRequest request, UserAccount teacher) {
        if (teacher.getRole() != Role.TEACHER) throw new AccessDeniedException("Teacher role required");
        if (request.rubricMaterialIds() == null || request.rubricMaterialIds().isEmpty()) {
            return assignments.create(request, teacher);
        }
        if (request.criteria() != null || request.maxScore() == null || request.maxScore() < 1
                || request.maxScore() > 100 || request.rubricMaterialIds().size() > 5
                || new HashSet<>(request.rubricMaterialIds()).size() != request.rubricMaterialIds().size()) {
            throw new BadRequestException("请选择参考答案文件并填写 1～100 的总分，不要同时提交手工评分项");
        }
        List<RubricSourceView> sources = new ArrayList<>();
        StringBuilder snapshot = new StringBuilder();
        for (Long id : request.rubricMaterialIds()) {
            var material = materials.findByIdAndClassId(id, teacher.getClassId()).orElseThrow(NotFoundException::new);
            if (material.getIndexStatus() != IndexStatus.READY || material.getStoredPath() == null) {
                throw new BadRequestException("参考答案须为已解析完成的文本文件");
            }
            sources.add(new RubricSourceView(material.getId(), material.getTitle()));
            snapshot.append("\n文件：").append(material.getTitle()).append('\n');
            var contents = entries.findAllByMaterialIdAndClassIdOrderByChunkIndex(id, teacher.getClassId());
            if (contents.isEmpty() || contents.stream().allMatch(entry -> entry.getBodyText().isBlank())) {
                throw new BadRequestException("参考答案文件内容为空");
            }
            for (var entry : contents) {
                snapshot.append(entry.getBodyText()).append('\n');
                if (snapshot.codePointCount(0, snapshot.length()) > 20000) {
                    throw new BadRequestException("参考答案文件合计不能超过 20000 字，请使用精简的参考答案文件");
                }
            }
        }
        if (snapshot.toString().isBlank()) throw new BadRequestException("参考答案文件内容为空");
        String sourceJson;
        try {
            sourceJson = mapper.writeValueAsString(sources);
        } catch (RuntimeException exception) {
            throw new BadRequestException("参考答案保存失败");
        }
        return assignments.createPrepared(request,
                teacher, sourceJson, snapshot.toString());
    }

}
