# Class09 AI 自审记录（review-02）

日期：2026-11-14  
性质：**AI 自审，不是成员交叉评审**。开发者/成员仍需自行确认，代理不得代签。

## 手段 / 对象 / 判据 / 结果

| 手段 | 对象 | 判据 | 结果 |
| --- | --- | --- | --- |
| 代码阅读 + 聚焦测试 | V14、审计实体/仓库、审计服务、查询接口 | 七类字段齐全；学生 403；跨班空；未知用量 null | 通过（run `365c1eb50877`；19/19 聚焦测试） |
| 构造请求 + 数据库回读 | `messages` 与 `security_audit_events` | 成功消息与拒绝事件分别落库；数据库字段与 API 查询一致 | 通过 |
| 构造拒绝请求 | SEC-03 技能配置、SEC-08 终评 | 403/404 不变；拒绝事件可回查；业务数据不变 | 通过（技能拒绝；终评待 SEC-08 记录） |
| 接口回归 | SQLi/XSS/CSRF 三条常驻用例 | 注入不扩大范围；标题按文本；无 Bearer 伪造请求 401 | 通过（`apitest/run_all.py` exit 0） |
| 前端 DOM 测试 | 材料标题、Markdown 事件属性 | 无 `script`/`img`/事件属性节点；文本节点保留 | 通过（干净 Node 22 容器 158/158） |
| 迁移检查 | 空库到 V14、V13 数据升级 | 只增不改；旧行新列 null；索引存在 | 已在团队库 V14 启动成功；空库/升级自动测试待全量回归 |
| 门禁自测 | `security_gate.py` | 0/1/2 聚合；受控故障只能把通过改为失败 | 单元测试通过；完整门禁结果见 `gate-red-green.md` |

## 七维自审

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 需求覆盖 | 通过（范围内） | SEC-07 七类字段、拒绝回查、三条反例、门禁和 SEC-08 均有对应实现/脚本 |
| 需求一致性 | 通过 | Bearer JWT 无状态边界未改；SSE 顺序和 `done` 语义未改；班级来自服务端 |
| 失败路径 | 通过 | 流前/流中失败、审计写入失败、非法查询参数、无 usage 均有显式处理 |
| 查询性能风险 | 可接受 | 审计查询按班级+时间+可选用户/技能索引过滤，limit 1～100，时间窗 ≤366 天 |
| 重复代码 | 可接受 | 复用现有 `CurrentUserService`、`LearningAnalyticsService` 时间窗模式、`McpServerService` 分页/用户名模式 |
| 漏洞风险 | 通过（范围内） | 审计查询教师本班；学生 403；无正文/提示词/凭据；拒绝事件不改变原响应 |
| 并发/事务/一致性 | 通过（范围内） | 成功消息与审计字段同事务；拒绝事件单条独立保存；`messages` 与 events 两路合并不重复成功事件 |

## 逐文件结论

| 文件 | 结论 | 说明 |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/V14__security_audit.sql` | 可用 | 只增列/新表/索引；可空列无需回填；skill_id 无外键保留快照 |
| `backend/.../audit/SecurityAuditEvent.java` | 可用 | 字段与迁移一致；无正文/凭据 |
| `backend/.../audit/SecurityAuditEventRepository.java` | 可用 | 查询先按 class_id、时间，再应用可选过滤；消息投影不读正文 |
| `backend/.../audit/SecurityAuditService.java` | 可用 | `REQUIRES_NEW` 独立事务保存；调用方回滚不丢审计；单条保存失败只记稳定警告，不掩盖原业务响应 |
| `backend/.../audit/AuditQueryService.java` | 可用 | 教师角色、limit/时间窗校验、两路合并稳定排序 |
| `backend/.../audit/AuditController.java` | 可用 | 仅教师；班级来自当前用户 |
| `backend/.../auth/CurrentUserService.java` | 可用 | 新增带 action/object 的教师检查重载；读接口仍不写审计 |
| `backend/.../grading/SubmissionReviewService.java` | 可用 | 角色/归属拒绝写 GRADE_REVIEW，业务结果不变 |
| `backend/.../gateway/ChatGateway.java`、`OpenAiChatGateway.java` | 可用 | usage 可选、null 保持、流式 include_usage；不改变回答正文 |
| `backend/.../conversation/ConversationService.java`、`ConversationStreamingService.java`、`AgentConversationService.java` | 可用 | 成功同事务、失败留痕、done 只在提交后；SSE 契约未改 |
| `backend/.../persistence/ConversationMessage.java` | 可用 | 新字段可空；旧构造兼容 |
| `backend/.../persistence/Material.java` | 可用 | 增加 `@DynamicUpdate`，后台索引只更新实际变化的字段，避免覆盖并发标题更新 |
| `scripts/security_precheck.py` | 可用 | SEC-07 改查新接口；仍保留 MCP 审计；新增拒绝回查和匿名路由 |
| `scripts/security_gate.py` | 可用 | 退出码聚合、阶段日志、受控故障只降级不改绿 |
| `Makefile` | 可用 | 薄包装；无 make 时 README 给 Python 入口 |
| `apitest/common/http_client.py`、`fixtures.py` | 可用 | 可选头/原始上传；合成材料登记清理 |
| `apitest/cases/api/retrieval/search-injection/POST.py` | 可用 | 注入/伪造 classId/非法类型/计数不变 |
| `apitest/cases/api/materials/xss/PATCH.py` | 可用 | API 原样文本；DOM 由前端测试覆盖 |
| `apitest/cases/api/materials/csrf/PATCH.py` | 可用 | 401/200 对照；无 Set-Cookie；仅 Cookie 401 |
| `scripts/sec08_experiment.py` | 待实际执行 | 第四步硬断言；前三步模型现象只记录 |

## 发现与处置

- **前端宿主 Vitest runner 异常**：Windows 宿主 23 文件均无法加载；干净 Node 22 Linux 容器 158/158 通过。已记录，未把宿主异常写成通过；不属于本次安全代码缺陷。
- **依赖扫描基线 Java 8**：首次基线 exit 2，已保留；门禁在 Corretto 17 重跑，最终结果见门禁报告。
- **模型 usage 供应商限制**：实际网关返回 usage；若未来供应商不返回，SEC-07 应判红，不得估算。
- **交叉复测**：未执行；本文件不能替代 review-03/人工评审。

## 结论

本轮 AI 自审没有发现需要阻塞合入的缺陷；但成员交叉评审、SEC-08 实际记录、门禁最终红绿和全量回归完成前，不得把本文件当作验收通过。
