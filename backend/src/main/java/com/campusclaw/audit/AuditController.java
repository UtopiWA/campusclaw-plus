package com.campusclaw.audit;

import com.campusclaw.auth.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "安全审计")
public class AuditController {
    private final AuditQueryService audit;
    private final CurrentUserService currentUsers;

    public AuditController(AuditQueryService audit, CurrentUserService currentUsers) {
        this.audit = audit;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/events")
    @Operation(summary = "查看本班技能执行与拒绝审计")
    public List<AuditEventView> events(@RequestParam(required = false) Long skillId,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(defaultValue = "50") int limit) {
        return audit.query(currentUsers.requireTeacher(), userId, skillId, from, to, limit);
    }
}
