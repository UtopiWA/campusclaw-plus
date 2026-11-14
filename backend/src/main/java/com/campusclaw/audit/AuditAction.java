package com.campusclaw.audit;

/** 稳定动作名；新增动作时必须同步审计查询和测试。 */
public enum AuditAction {
    SKILL_EXECUTE,
    CONFIG_WRITE,
    GRADE_REVIEW
}
