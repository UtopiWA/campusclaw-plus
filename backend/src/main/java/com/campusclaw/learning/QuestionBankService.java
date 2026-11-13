package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.ConflictException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.grading.AssignmentCreationService;
import com.campusclaw.grading.GradingTaskQueryService;
import com.campusclaw.grading.GradingTaskView;
import com.campusclaw.grading.SubmissionService;
import com.campusclaw.grading.SubmitAssignmentResponse;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTaskStatus;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.campusclaw.learning.LearningCatalogService.requireStudent;
import static com.campusclaw.learning.LearningCatalogService.requireTeacher;
import static com.campusclaw.learning.LearningRepository.number;

@Service
public class QuestionBankService {
    private final QuestionBankRepository bank;
    private final LearningRepository learning;
    private final AssignmentCreationService creation;
    private final SubmissionService submissions;
    private final GradingTaskRepository tasks;
    private final GradingTaskQueryService queries;
    private final ObjectMapper mapper;

    public QuestionBankService(QuestionBankRepository bank, LearningRepository learning, AssignmentCreationService creation,
                               SubmissionService submissions, GradingTaskRepository tasks, GradingTaskQueryService queries, ObjectMapper mapper) {
        this.bank = bank;
        this.learning = learning;
        this.creation = creation;
        this.submissions = submissions;
        this.tasks = tasks;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UserAccount user) {
        boolean teacher = user.getRole() == Role.TEACHER;
        var questions = bank.list(user.getClassId(), teacher);
        if (questions.size() > 2000) throw new BadRequestException("题目较多，请先整理题库（本版上限2000题）");
        var tags = bank.tags(user.getClassId());
        var own = teacher ? List.<Map<String, Object>>of() : bank.summaries(user.getClassId(), user.getId());
        return questions.stream().map(q -> {
            var view = view(q, tags, teacher);
            if (!teacher) view.put("myLatest", own.stream().filter(r -> number(r, "questionId") == number(q, "id")).findFirst().orElse(null));
            return view;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UserAccount user, long id) {
        var q = visible(user, id, false);
        var view = view(q, bank.tags(user.getClassId()), user.getRole() == Role.TEACHER);
        if (user.getRole() == Role.STUDENT) {
            var latest = bank.latest(user.getClassId(), user.getId(), id);
            view.put("myLatest", latest == null ? null : queries.view(latest.get("taskId").toString(), user));
            if (latest != null) {
                view.put("submittedPrompt", latest.get("promptText"));
                view.put("submittedTitle", latest.get("title"));
                view.put("questionChanged", number(q, "assignmentId") != number(latest, "submittedAssignmentId"));
            }
        }
        return view;
    }

    @Transactional
    public Map<String, Object> save(UserAccount teacher, Long id, QuestionBankRequests.Question input) {
        requireTeacher(teacher);
        learning.lockClass(teacher.getClassId());
        if (id != null) visible(teacher, id, true);
        else if (bank.list(teacher.getClassId(), true).size() >= 2000) throw new BadRequestException("本版最多2000道题目");
        var prepared = creation.createPractice(input.assignment(), teacher);
        long result = bank.save(teacher.getClassId(), teacher.getId(), id, prepared.id(), input.enabled());
        return get(teacher, result);
    }

    @Transactional
    public void availability(UserAccount teacher, long id, boolean enabled) {
        requireTeacher(teacher);
        learning.lockClass(teacher.getClassId());
        visible(teacher, id, true);
        bank.availability(teacher.getClassId(), id, enabled);
    }

    @Transactional
    public void delete(UserAccount teacher, long id) {
        requireTeacher(teacher);
        learning.lockClass(teacher.getClassId());
        visible(teacher, id, true);
        bank.delete(teacher.getClassId(), id);
    }

    @Transactional
    public SubmitAssignmentResponse submit(UserAccount student, long id, String content, String key) {
        return submit(student, id, content, key, null);
    }

    @Transactional
    public SubmitAssignmentResponse submit(UserAccount student, long id, String content, String key, Long contentVersion) {
        requireStudent(student);
        learning.lockClass(student.getClassId());
        learning.lockStudent(student.getClassId(), student.getId());
        var q = visible(student, id, true);
        var previous = bank.latest(student.getClassId(), student.getId(), id);
        if (previous != null) {
            var task = tasks.lockById(previous.get("taskId").toString()).orElseThrow(NotFoundException::new);
            if (key != null && !key.isBlank() && key.trim().equals(previous.get("clientKey"))) {
                return new SubmitAssignmentResponse(task.getTaskId(), task.getStatus().name(),
                        "/api/grading/tasks/" + task.getTaskId(), task.getSubmissionId(), (int) number(previous, "version"));
            }
            if (contentVersion != null && contentVersion != number(q, "assignmentId")) {
                throw new ConflictException("题目已更新，请刷新题目后重新提交");
            }
            if (task.getStatus() == GradingTaskStatus.PENDING || task.getStatus() == GradingTaskStatus.RUNNING) {
                throw new ConflictException("正在批改，请等待结果后再重新作答");
            }
            bank.clearPrevious(number(previous, "submissionId"), student.getClassId(), student.getId());
        }
        if (contentVersion != null && contentVersion != number(q, "assignmentId")) {
            throw new ConflictException("题目已更新，请刷新题目后重新提交");
        }
        var accepted = submissions.submitPractice(number(q, "assignmentId"), content, key, student);
        bank.replace(id, student.getClassId(), student.getId(), accepted.submissionId(), accepted.taskId());
        return accepted;
    }

    @Transactional(readOnly = true)
    public GradingTaskView latest(UserAccount student, long id) {
        requireStudent(student);
        visible(student, id, false);
        var latest = bank.latest(student.getClassId(), student.getId(), id);
        if (latest == null) throw new NotFoundException();
        return queries.view(latest.get("taskId").toString(), student);
    }

    private Map<String, Object> visible(UserAccount user, long id, boolean lock) {
        var q = bank.question(user.getClassId(), id, lock);
        if (user.getRole() != Role.TEACHER && !Boolean.TRUE.equals(q.get("enabled"))) throw new NotFoundException();
        return q;
    }

    private Map<String, Object> view(Map<String, Object> q, List<Map<String, Object>> tags, boolean teacher) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : List.of("id", "title", "maxScore", "enabled", "createdAt", "updatedAt")) result.put(key, q.get(key));
        result.put("prompt", q.get("promptText"));
        result.put("contentVersion", q.get("assignmentId"));
        result.put("tags", tags.stream().filter(t -> number(t, "questionId") == number(q, "id"))
                .map(t -> Map.of("id", t.get("id"), "name", t.get("name"))).toList());
        if (teacher) result.put("rubricSources", mapper.readTree(q.get("rubricSourceJson").toString()));
        return result;
    }
}
