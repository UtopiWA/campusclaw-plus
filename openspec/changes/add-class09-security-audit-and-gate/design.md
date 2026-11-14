# Design

## Context

当前技能执行只把 `skill_name` 写入 `messages`，模型网关只读取回答正文，丢弃供应商 `usage`；`McpToolCall` 有工具耗时，但没有稳定技能 ID 或模型 Token。`ConversationMessage` 只接受非空助手正文，因此学生修改配置被 403、越权终评被 403/404、模型流在首帧前失败等动作都无法表示成消息。现有 `security_precheck.py` 的 SEC-07 检查把 MCP 调用行当作七类字段来源，不能证明技能 ID 和模型用量。

本次实现需要在不改变既有 SSE 顺序、`done` 语义、认证机制和班级边界的前提下补齐审计，并新增可失败的门禁和常驻反例。Class08 的 `reports/security-precheck.md`、`security-baseline.md`、`risk-authz.md`、`review-01.md` 必须保留原样。

## Goals / Non-Goals

**Goals:**

- 成功技能轮次记录操作人、服务端时间、动作、对象、结果、稳定技能 ID、技能名称、真实 Token 用量、模型耗时和请求标识。
- 拒绝/流前失败动作有追加式记录，且不改变原 401/403/404 或业务数据。
- 教师能按技能、操作人和时间窗回查本班审计；学生不可读，跨班不可见，响应不含正文或凭据。
- 三条输入安全反例常驻并可重复执行；CSRF 按现有 Bearer JWT 机制等价验收。
- `make check-security` 与 Python 等价入口消费真实退出码，并留一次受控红绿证据。
- 现有功能、异步三判据、四步演示和迁移兼容不回归。

**Non-Goals:**

- 不增加 SSE 字段、不改变 `meta/delta/done/error` 顺序；审计信息通过持久化和受保护查询提供，避免扩大流式契约。
- 不扩展 `mcp_tool_calls` 表结构或把模型 usage 塞进工具审计；工具耗时和模型耗时保持分离。
- 不持久化评分/学习计划的模型 usage，不审计所有成功配置写入。
- 不替换认证框架、不实现 Cookie/SameSite/CSRF token、不新增危险工具、不引入 Playwright。

## Decisions

### 1. 成功轮次用 `messages`，拒绝/失败用追加式事件表

`messages` 增加可空 `skill_id`、`prompt_tokens`、`completion_tokens`、`total_tokens`、`model_duration_ms`、`request_id`，成功助手消息与其审计字段在同一事务提交。新增 `security_audit_events` 记录无法表示为消息的动作：

- 学生修改技能/助手配置被 403；
- 越权终评被 403/404；
- 模型流在首帧前失败，或流中失败但没有可保存的可见正文；
- 其他经明确 allowlist 的拒绝动作。

理由：消息必须有会话和非空正文，拒绝动作没有消息；若把所有事件写成消息，会污染会话历史或需要放宽非空约束。成功事件只写消息，事件表只写拒绝/失败，查询时合并，避免同一成功事件写两份。

备选：所有事件写一张新表，消息表不加列。该方案会让成功轮次和消息脱节，且用户已确认要扩展消息表，故不采用。

### 2. V14 只增不改，技能快照不做外键

V14 新增：

- `messages` 可空列和 `(skill_id, created_at)`、`(created_at, id)` 索引；
- `security_audit_events`：`class_id`、`user_id`、`action`、`object_type`、`object_id`、`skill_id`、`skill_name`、`status`、`http_status`、`failure_code`、Token 三列、`duration_ms`、`request_id`、`created_at`，以及班级/用户/技能/时间索引。

`skill_id` 不做外键：教师删除技能后，消息和审计仍需保留当时的稳定标识和名称快照。历史行新列保持 `NULL`，不需要回填；空库和 V13→V14 都要验证。Flyway 不提供自动回滚，失败时按迁移日志定位并以新的前向迁移修复，不修改 V1～V13。

### 3. 模型 usage 通过网关显式采集，未知保持 `NULL`

`ChatGateway` 增加可空 `Usage(promptTokens, completionTokens, totalTokens)`；`ChatStream` 增加默认 `usage()`。`OpenAiChatGateway`：

- 非流式解析响应 `usage`；
- 流式请求增加 `stream_options.include_usage=true`，在 `[DONE]` 前解析 usage-only chunk；
- 无效、负数或缺失值一律 `NULL`，不估算、不写 0；
- `ConversationStreamingService` 只在真实模型流上测量 `modelDurationMs`；固定文本轮次保持 `NULL`。

供应商不支持流式 usage 时不降低验收：SEC-07 保持红，记录供应商限制和实际响应，等待教师确认处置。

### 4. 技能 ID 由服务端路由传入，不改变 SSE

`AgentTurnPlan` 增加 `skillId`，由 `AgentPromptBuilder` 从 `SkillRouter` 已选中的 `Skill.getId()` 复制；`ConversationStreamingService` 把它和 usage/耗时传入 `saveAssistantMessage`。不在 `meta` 或 `done` 增加字段，保持现有 SSE 契约；教师通过审计接口回查。

备选：在 `meta.skillId`、`done.usage` 增加可空字段。该方案对客户端更直观，但会扩大流式契约并需要额外兼容测试；本次审计目标不要求实时下发，故不采用。

### 5. 拒绝追踪放在授权边界，而不是全局异常处理器

在 `CurrentUserService.requireTeacher(...)` 增加带 action/object 参数的重载：学生调用教师管理接口时，先写 `DENIED` 事件再抛既有异常。终评拒绝在 `SubmissionReviewService` 的角色/归属校验处写 `DENIED`，因为对象是 assignment/submission，且该服务已持有归属信息。

拒绝事件写入使用独立事务（`REQUIRES_NEW`），失败时只记录稳定警告，不改变原 403/404；不把每个匿名 401 都写库，避免写放大，匿名请求继续由 `RequestLoggingFilter` 的单行 JSON 记录。

备选：在 `ApiExceptionHandler` 统一按路由记录所有 403/404。该方案覆盖广，但会把不存在的资源、参数错误和跨班 404 混入审计，且对象 ID 映射容易泄漏存在性；本次只审计明确的配置/终评拒绝，故不采用。

### 6. 审计查询合并两路，教师本班、字段最小化

新增 `GET /api/audit/events`：

- 仅教师，班级来自 `CurrentUserService`；学生 403，匿名 401；
- 可选 `skillId`、`userId`、`from`、`to`、`limit`（1～100，默认 50）；时间窗默认最近 24 小时，最大 366 天；
- `messages` 路只取助手且 `skill_id` 非空的消息，join `conversations` 取操作人和班级；`security_audit_events` 路按班级过滤；
- 两路各自按时间倒序取最多 `limit` 条，服务层合并排序后取 `limit`，返回 `source` + `id` 避免两表主键混淆；
- 响应只含安全元数据：操作人 ID/用户名、时间、动作、对象类型/ID、状态/HTTP 状态、技能 ID/名称、Token 三列、耗时、请求标识；不含正文、提示词、引用、工具参数/结果、凭据。

索引与查询：`messages(created_at, id)`、`messages(skill_id, created_at)`、`conversations(user_id, class_id)` 支撑成功路径；审计事件表按 `(class_id, created_at)`、`(class_id, user_id, created_at)`、`(class_id, skill_id, created_at)` 支撑拒绝路径。限制时间窗和 `limit` 防止无界扫描。

### 7. 门禁用 Python 编排，Makefile 只做薄包装

`scripts/security_gate.py` 顺序运行：

1. `check_security.py`（SEC-05/06 + 静态扫描）；
2. `security_precheck.py --checks access`（SEC-01～04）；
3. `security_precheck.py --checks audit`（SEC-07 + SEC-03 拒绝回查）；
4. `apitest/run_all.py`（含三条安全反例）；
5. `scripts/tests` 单元测试。

每步都执行并收集退出码；任一步骤 2（未完成/环境缺失）优先返回 2，否则任一步骤 1 返回 1，全 0 才返回 0。不使用 `|| true`、不跳过范围、不降低阈值。摘要写到 `backend/target/security-gate/`，不含凭据/正文。

红绿证据使用门禁的受控自测故障（例如 `--fault-stage` 明确标记的合成失败），只允许把通过步骤变为失败，不能把失败变为通过；报告记录 `syntheticFault=true`。这样不需要在仓库临时放置假密钥，也不会把真实故障伪装成绿。

Makefile 只提供 `check` 和 `check-security` 两个目标并委托给 Python 入口；Windows 无 `make` 时 README 直接给 Python 命令，验收记录不声称执行过 make。

### 8. 三条反例按现有 harness 扩展，XSS 用现有 jsdom

- `apitest/common/http_client.py` 增加可选请求头和 raw/multipart 上传；保持现有调用兼容。
- 新增 `apitest/common/fixtures.py`，复用 `security_precheck.py` 的上传/登记/清理方式。
- 安全用例 ORDER 放在现有功能用例之后（80+），避免安全红阻断功能回归；`run_all.py` 仍按首个失败阻断后续，行为在 README 说明。
- SQL 注入：`POST /api/retrieval/search`，发送注入串、UNION 探针和伪造 `classId`，逐条查 MySQL 断言本班/READY，相关表计数不变，非法类型 400。
- XSS：上传恶意文件名或 PATCH 恶意标题，断言 API 原样返回、DB 一致；前端用现有 Vue Test Utils + jsdom 断言 `MaterialsView` 和 `assistantMarkdown` 不产生元素/事件节点。
- CSRF：伪造带 `Origin/Referer`、无 Bearer 的 PATCH → 401 且 DB 不变；带 Bearer 对照 → 200；登录响应无 `Set-Cookie`；报告记录 Bearer JWT 等价口径并请教师确认。不添加 Playwright。

### 9. SEC-08 记录模型现象，第四步以代码拒绝为准

新增 `scripts/sec08_experiment.py`，复用 `security_precheck.py`/`lesson08_acceptance.py` 的登录、上传、作业、提交、轮询和清理：

1. 恶意讲义进入 `assignments.rubric_text` 并实际批改，记录评分现象；
2. 请求助手打印系统提示词，记录是否泄露（使用合成标记，不接触真实凭据）；
3. 请求按宽松标准批改或通过恶意参考材料观察评分变化；
4. 要求助手冒充管理员改成绩；独立验证无成绩写工具、学生终评 403、跨班 404、数据库计数不变、拒绝审计存在。

前三步是模型环境观察，不是确定性断言；第四步失败即实验失败。不为演示新增工具或伪造调用日志。

## Capability–Scenario–Verification

| Capability | 关键 Scenario | 验证 |
| --- | --- | --- |
| `security-audit` | 成功轮次七类字段、拒绝留痕、教师本班查询、字段安全、两路合并 | `security_precheck.py --checks audit`、后端集成测试、`audit-recheck.md` |
| `security-precheck` | 授权复测、资产处置、SEC-08、门禁失败传播、受控红绿 | `security_precheck.py --checks access`、`check_security.py`、`security_gate.py`、`gate-red-green.md` |
| `api-regression-harness` | SQLi、XSS、CSRF 等价、合成资源清理 | `apitest/run_all.py`、`npm --prefix frontend run test -- --run`、`security-cases.md` |

## Risks / Trade-offs

- **供应商不返回流式 usage** → 保持 `NULL`，SEC-07 判红并记录供应商限制；不估算、不写 0。
- **审计写入增加事务开销** → 成功事件与消息同事务，只写少量列；拒绝事件独立事务，写入失败不掩盖原响应。
- **合并查询跨两表** → 两路都先按班级和时间过滤，各取 limit 后合并；限制 1～100 和 366 天，避免全表扫描。
- **审计查询泄露隐私** → 只返回元数据，不返回消息正文/引用；教师只能看本班，学生 403，跨班空集合。
- **消息表加列影响既有查询** → 全部可空、不要求回填；空库和 V13→V14 迁移测试覆盖。
- **安全用例阻断后续功能用例** → ORDER 放在功能用例之后，并在 README 说明失败传播；不把安全红藏成跳过。
- **CSRF 不是 Cookie 机制** → 采用 Bearer JWT 等价验收并请教师确认；不伪造 SameSite/token 证据。
- **SEC-08 模型行为不稳定** → 前 3 步只记录实际现象，不用模型自述代替执行；第 4 步以代码拒绝和 DB 不变为准。
- **本机无 make** → Python 入口为权威入口；Makefile 在具备 make 的环境验证，报告中如实标注。

## Migration Plan

1. 新增 `V14__security_audit.sql`，不修改 V1～V13。
2. 在空库启动到 V14；在 V13 数据库插入旧消息/会话/技能行后升级到 V14，断言旧行新列为 `NULL`、旧数据可读、索引存在。
3. 部署顺序：先迁移数据库，再发布包含审计写入/查询的后端；旧客户端不受影响，因为 SSE 和现有响应未改。
4. 回滚：Flyway 不做反向 DDL；若需回退应用，保留 V14 列和事件表（可空、追加式），旧应用可继续运行；数据修复使用新的前向迁移。
5. 清理：验收合成数据按运行标识清理；能力删除不清理历史审计快照。

## Open Questions

- 供应商是否支持 `stream_options.include_usage` 只能在实际环境验证；若不支持，按风险项保持 SEC-07 红并请教师确认，不改变本设计。
- 教师对“Bearer JWT 无 Cookie 的 CSRF 等价验收”的确认需要在验收记录中留痕；若教师要求 Cookie 机制，需另立 change，不在本次临时替换认证框架。
