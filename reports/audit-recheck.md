# Class09 技能审计回查

日期：2026-11-14  
入口：`python scripts/security_precheck.py --env-file .env.team --checks audit`  
环境：团队七服务 Docker 栈；合成服务器、技能、助手和会话均在结束后清理。

## 实际运行

- 修复前：run `4760bfcb427e`，exit 1。SEC-07 红：`skillId=false`，`tokenUsage=false`，`tokenUsage=null`。
- 修复后：run `365c1eb50877`，exit 0。SEC-07 绿，`fields` 全部为 true，`cleanup=complete`。
- 完整门禁 audit 阶段 run `c82ca4956a9e`，exit 0，execution/cleanup complete。
- `SecurityAuditService` 改为 `REQUIRES_NEW` 后，最终定向 audit run `df59a91907d1`，exit 0，execution/cleanup complete；跨班 404 拒绝审计在服务事务回滚后仍保留（SEC-08 run `b9d157fa2cbc`）。

## 七类字段

| 信息 | 实际值 | 来源 |
| --- | --- | --- |
| 操作人 | `student-a1`，userId 2 | 服务端身份 / `conversations.user_id` |
| 操作时间 | `2026-11-14T11:54:50.516461Z` | 服务端 `messages.created_at` |
| 操作类型 | `SKILL_EXECUTE` | 审计查询映射 |
| 操作对象 | `MESSAGE`，objectId 14，conversation 关联 | `messages.id` |
| 结果状态 | `SUCCESS` | 消息状态 |
| 技能标识 | skillId 11，技能名称由运行标识生成 | 服务端技能路由 |
| 用量与耗时 | prompt 253、completion 18、total 271；模型耗时 207 ms | 供应商 `usage` + 实际计时 |

数据库回读与查询一致：`messages.skill_id=11`、`prompt_tokens=253`、`completion_tokens=18`、`total_tokens=271`、`model_duration_ms=207`、`request_id` 非空。未知用量保持 `NULL`，没有写 0。

## 拒绝回查

在审计检查中重放 SEC-03：学生更新本班技能，接口返回 403，数据库不变；教师按学生和时间窗查询得到：

```json
{
  "action": "CONFIG_WRITE",
  "objectType": "SKILL",
  "objectId": 10,
  "status": "DENIED",
  "httpStatus": 403,
  "failureCode": "FORBIDDEN"
}
```

## 终评拒绝回查

SEC-08 首次运行 `a3a3fa25eeeb` 发现学生终评虽返回 403，但控制器先抛 403，服务层没有机会写 `GRADE_REVIEW` 审计；修复为控制器使用带 action/object 的 `requireTeacher` 重载。重跑 `fe5e50280004` 后 `denialAudit=true`：学生终评 403、跨班教师 404，数据库计数不变，教师可按学生和时间窗查到 `GRADE_REVIEW/DENIED`。

进一步把 `SecurityAuditService` 改为 `REQUIRES_NEW` 独立事务，确保跨班教师在 `SubmissionReviewService` 事务内抛 404 时，审计不会被外层回滚。最终运行 `b9d157fa2cbc`：学生和跨班拒绝审计均为 true；脚本一度只按 submissionId 匹配跨班事件，实际服务先记录 assignment 对象的 404，已修正判据。

## 查询授权

- 教师 `GET /api/audit/events` 返回本班记录；学生请求返回 403。
- B 班教师按同一技能查询返回空集合，未出现 A 班用户名、技能或对象。
- 查询支持 `skillId`、`userId`、`from`、`to`、`limit`；`limit` 1～100，时间窗最大 366 天，非法参数返回 400。
- 响应只含安全元数据，不含消息正文、引用、提示词、工具原始参数/结果或凭据。

## 日志格式

`JsonLogEncoderTest` 和 `RequestLoggingFilterTest` 覆盖单行 JSON、换行/引号转义、敏感字段排除和请求 ID；本轮后端日志为单行 JSON，未出现用户正文或凭据。夹具包含换行/引号的合成输入时，日志仍按单行解析。

## 未执行/保留

- 另一成员尚未独立复现审计接口和拒绝记录；不得代签。
- MCP 工具调用审计仍由 `/api/mcp/tool-calls` 单独提供；本次不扩展工具表结构。
- 模型供应商未返回 usage 时字段保持 `NULL`，具体供应商限制在实际响应中记录。
