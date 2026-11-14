package com.campusclaw.auth;

import com.campusclaw.audit.AuditAction;
import com.campusclaw.audit.AuditObjectType;
import com.campusclaw.audit.SecurityAuditService;
import com.campusclaw.persistence.ClassRoomRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** 将 JWT 中的最小用户标识解析为数据库当前用户、角色和班级上下文。 */
@Service
public class CurrentUserService {
    private final UserAccountRepository users;
    private final ClassRoomRepository classRooms;
    private final SecurityAuditService audit;

    public CurrentUserService(UserAccountRepository users, ClassRoomRepository classRooms,
                              SecurityAuditService audit) {
        this.users = users;
        this.classRooms = classRooms;
        this.audit = audit;
    }

    public UserAccount requireUser() {
        // JWT 不固化业务权限；每次请求回查数据库，使禁用、角色和班级变更立即生效。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        try {
            return requireUser(Long.valueOf(jwt.getSubject()));
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
    }

    public UserAccount requireUser(Long userId) {
        return users.findById(userId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
    }

    public UserAccount requireTeacher() {
        return requireTeacher(null, null, null);
    }

    /** 写入型教师接口使用该重载，在拒绝时留下可回查的动作和对象。 */
    public UserAccount requireTeacher(AuditAction action, AuditObjectType objectType, Long objectId) {
        UserAccount user = requireUser();
        if (user.getRole() != Role.TEACHER) {
            if (action != null && objectType != null) {
                audit.recordDenied(user, action, objectType, objectId,
                        objectType == AuditObjectType.SKILL ? objectId : null,
                        null, 403, "FORBIDDEN", MDC.get("requestId"));
            }
            throw new AccessDeniedException("Teacher role required");
        }
        return user;
    }

    public CurrentUserView view(UserAccount user) {
        String className = classRooms.findById(user.getClassId()).map(room -> room.getName()).orElse("未知班级");
        return new CurrentUserView(
                user.getId(), user.getUsername(), user.getRole().name().toLowerCase(), user.getClassId(), className);
    }

    public record CurrentUserView(Long id, String username, String role, Long classId, String className) {
    }
}
