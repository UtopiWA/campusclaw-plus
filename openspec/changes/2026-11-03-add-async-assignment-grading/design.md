# 可靠异步作业批改设计

## Context

当前仓库已有登录、教师/学生角色、服务端班级隔离、材料上传与异步索引、检索、会话、MCP 和统一检查入口。作业只有 `Assignment` 的 ID、班级和标题，没有提交、评分或结果链路。材料异步使用有界线程池、数据库状态、原子认领和恢复调度，但没有独立消息代理，不能作为本课队列交付的替代。

本 change 继承既有鉴权、班级隔离、模型网关、Flyway 和检查入口，新增作业批改的最小闭环。范围调整、课程要求和交付边界见 [proposal](proposal.md)。

## Goals / Non-Goals

**Goals：** 教师创建带评分依据的文本作业；学生提交后立即返回 202；独立消费者异步评分；任务可查询、可恢复、可去重；重试、退避、死信和观测可复核；评分理由可追溯、分数由 AI 参考答案综合判断并由服务端校验；AI 审核和扫描流程有记录。

**Non-Goals：** 作业编辑与评分依据版本化、完整题库/题型、知识图谱、掌握度、学习计划、班级分析、端到端 exactly-once、SSE/WebSocket 任务推送和全站异步。现有登录、材料、检索、会话、MCP/SSE 契约保持不变。

## Decisions

### 1. 业务范围与评分边界

- 教师在本班创建文本作答作业：标题、详细题目、1～100 的总分和本班 READY 文件；创建时只保存参考答案文件快照，不调用模型或提取评分项；文件无需包含评分标准或权重。旧结构化 API 继续兼容。作业创建后不可编辑；需要改题或改评分依据时创建新作业，避免历史结果失去依据。
- 学生可向本班作业提交文本作答；每人每作业只提交一次；同内容/同键重试返回原任务，不同内容再次提交返回 409。历史版本保留兼容，不再产生新版本；教师终评独立保存。
- 模型根据题目、总分、参考答案和作答直接输出整数分数与有证据的评语；不固定分项和扣分权重。服务端仅做结构/类型/范围/解释校验，不重新计算或裁剪分数。页面以“AI 批改”区分初评；教师终评存在时学生只展示终评分数和评语，教师改分弹窗仍可查看 AI 原始依据。
- 评分依据和总分必须可追溯到实际提交版本；作业保存参考答案快照，结果保存模型分数/评语、模型名称和提示词版本；旧分项只用于历史读取。

### 2. 独立消息代理与执行形态

选择 **RabbitMQ**（持久化队列、发布确认、手动 ACK、死信）。Compose 新增 `rabbitmq` 服务和 `rabbitmq-data` 卷；Web 应用负责提交接口和发件箱投递；新增独立 `grading-worker` 服务，使用同一后端镜像、不同 profile，不暴露业务端口（为复用 Spring Security/应用上下文绑定随机端口），只消费队列和执行恢复调度。停止 worker 不影响 Web、MySQL、RabbitMQ 和投递器；任务保留在队列/数据库中。仅使用 `@Async` 或进程内线程池不满足本课交付形态。

不使用 Kafka：本课只需要一个持久化工作队列、确认、重投和死信，RabbitMQ 的运行与排障成本更低。不使用数据库任务表轮询替代代理：本课明确要求新增代理；数据库只承担业务状态、发件箱调度和恢复，不替代消息分发。

### 3. 数据模型与迁移

新增 Flyway `V8__assignment_grading.sql`，只增不改：

- `assignments` 增加：`prompt_text VARCHAR(4000)`、`rubric_json JSON`、`max_score INT`、`gradable BOOLEAN NOT NULL DEFAULT FALSE`、`created_by BIGINT NULL`、`created_at`/`updated_at`。旧种子作业保持 `gradable=false`，不进入新作业列表。新作业必须 `gradable=true` 且评分依据非空。
- `submissions`：`id`、`assignment_id`、`class_id`、`student_id`、`content TEXT`、`content_hash CHAR(64)`、`version INT`、`created_at`。唯一键 `(assignment_id, student_id, version)` 和 `(assignment_id, student_id, content_hash)`；索引 `(class_id, assignment_id)`。内容按 UTF-8 原样计算 SHA-256，规范化 CRLF 为 LF。
- `grading_tasks`：`task_id VARCHAR(36) PRIMARY KEY`（UUID）、`submission_id`、`assignment_id`、`class_id`、`student_id`、`status VARCHAR(16)`、`attempts INT`、`max_attempts INT`、`idempotency_key VARCHAR(128) UNIQUE`、`available_at DATETIME(6)`、`started_at`、`finished_at`、`failure_code VARCHAR(40)`、`last_failure_code VARCHAR(40)`、`created_at`、`updated_at`。索引 `(status, available_at)`、`(submission_id)`、`(class_id, created_at)`。`idempotency_key = grade:<submissionId>:<version>`。
- `grading_results`：`id`、`task_id UNIQUE`、`submission_id`、`version`、`score INT`、`max_score INT`、`criteria_json JSON`、`rationale TEXT`、`model_name VARCHAR(100)`、`prompt_version VARCHAR(40)`、`created_at`。唯一键 `(submission_id, version)`，保证同一提交版本只有一份生效结果。
- `grading_outbox`：`id`、`event_type VARCHAR(32)`、`task_id VARCHAR(36)`、`status VARCHAR(16)`、`available_at DATETIME(6)`、`attempts INT`、`last_error_code VARCHAR(40)`、`published_at`、`created_at`。索引 `(status, available_at)`。事件类型：`GRADING_DISPATCH`、`GRADING_RETRY`、`GRADING_DEAD_LETTER`；消息体只含 `taskId`，不含作答正文、密钥或个人信息。

迁移必须验证空库和上一版本（V7）升级；旧应用在新表存在时可继续运行，新增列对旧数据可空/有默认值。不修改 V1～V7。

### 4. 状态机、认领与恢复

```text
PENDING → RUNNING → SUCCEEDED
              ├─ 可重试且未达上限 → PENDING（available_at 退避）
              └─ 不可重试或已达上限 → FAILED（并投递死信）
```

- 认领使用条件更新：`UPDATE grading_tasks SET status='RUNNING', attempts=attempts+1, started_at=NOW(6) WHERE task_id=? AND status='PENDING' AND available_at<=NOW(6)`。更新 0 行表示已终态或正被其他消费者处理，消息直接 ACK，避免重复执行。
- 模型调用在数据库事务之外；调用前只做短事务认领，成功后用短事务写结果和终态，再 ACK。失败按类别写回 `PENDING`（退避）或 `FAILED`（死信）。
- `RUNNING` 超时回收：按发件箱批量预算分批读取过期任务，置回 `PENDING` 并写重试发件箱，或按上限置 `FAILED` 并写死信。阈值必须覆盖网关两次连接和读取预算，非法时启动失败。所有完成、失败及回收写回先锁定任务行，并核对 `RUNNING + attempts`；旧执行迟到不得修改新认领或终态。
- 结果提交后 ACK 前退出：消息重投后认领更新为 0 行，worker 读取已有结果并安全 ACK，不覆盖结果。消费者执行中退出：未 ACK 消息重投，配合 `RUNNING` 回收；若消息在回收前重投，认领失败并 ACK，由回收调度继续。

### 5. 事务性发件箱与投递

提交接口在**同一个本地数据库事务**中保存提交、任务和 `GRADING_DISPATCH` 发件箱记录，然后返回 202；不在请求内调用模型。事务失败时三者一起回滚。

发件箱投递器读取到期的持久化待办，在数据库事务之外发布。必须同时确认 ACK 且消息未被退回，才保存 `PUBLISHED`；ACK 但不可路由也保留待补投。失败递增投递次数并退避；发布后记录进度前退出允许重复投递，由消费端幂等吸收，不把网络调用包在长事务中。

RabbitMQ 拓扑：持久化 direct exchange `grading.events`；`grading.tasks` 队列绑定 `grading.dispatch` 和 `grading.retry`；`grading.dlq` 绑定 `grading.dead-letter`。队列和消息持久化，消费者手动 ACK，prefetch 可配置。消息体只含 `taskId`。

### 6. 幂等与版本

- 提交入口：相同 `assignment_id + student_id + content_hash` 返回已有最新提交/任务；不同内容再次提交返回 409。客户端可传 `Idempotency-Key`，相同键返回同一结果；服务端仍以数据库唯一约束兜底。
- 去重和版本分配在锁定本班作业行的短事务中进行，避免并发先查后插返回数据库错误。该方案会短暂串行化同一作业的不同学生提交，模型执行不持有此锁。
- 任务入口：`idempotency_key` 唯一；同一提交版本不会创建第二个任务。
- 结果落库：`(submission_id, version)` 唯一；重复投递先认领失败或写结果冲突时读取已有结果，不覆盖、不重复生效。
- 重试计数：`attempts` 记录实际执行次数并递增；已有终态直接跳过时不强制增加计数，另保留重复投递/去重日志证据。
- MySQL 使用唯一约束和 `DuplicateKeyException` 处理冲突，不使用 PostgreSQL 的 `ON CONFLICT ... DO NOTHING`；只处理目标唯一键冲突，其他数据库错误继续抛出。

### 7. 重试、退避与死信

- 可重试：模型超时、429、临时 5xx、网络抖动、模型输出暂时不符合 schema。不可重试：非法输入、失效模型密钥、内容明确不被接受、参考答案数据损坏；无效输出达到上限后终止。
- 默认最多 5 次执行（含首次），base 5s、倍数 2、上限 5 分钟、随机抖动 0～20%；这些是团队参数，可由配置覆盖，不声称是教师规定。
- 达到上限：任务 `FAILED`，写安全 `failure_code`，并发 `GRADING_DEAD_LETTER` 发件箱事件；投递器把仅含 `taskId` 的消息送入 `grading.dlq`。失败原因不包含模型原始异常、提示词、作答正文或内部地址。
- `last_failure_code` 可在重试期间观察；最终 `failure_code` 只在终态暴露。

### 8. 参考答案支持的 AI 综合评分

- 复用现有非流式 ChatGateway；模型调用仅在 worker 事务外进行。提示词包含题目、总分、参考答案快照和作答，明确文件/作答只是数据，不执行其中的指令。
- 模型综合判断正确性、完整性、推理及表达，允许不同于机械满/半/零的分数。只返回 `{"score":87,"rationale":"有作答证据的中文评语及建议"}`。
- 先检查 JSON 节点类型，再严格反序列化，拒绝缺失/未知字段、尾随文档、字符串或小数 score、越界分数、非文本/空/超过2000 code points的评语。不强制修正为边界值，不生成默认分；沿用 MODEL_OUTPUT_INVALID 有限重试/死信。
- 新结果 criteria_json 保存空数组；现有列、历史结果和 DTO 中的 criteria 保留兼容，不新增模式分支或表。旧作业优先用已有文件快照；仅旧结构化 API 的作业将描述转换为参考文字，不把旧 points 当分项规则。旧成绩不重算，教师终评优先级不变。
- 删除创建时提取评分项和固定分项计分代码；新建只校验/保存本班 READY 参考文本。沿用 V9 字段，无新迁移。批改 promptVersion 默认提升为 v4，保留版本追溯。
- 前端新建字段改称“参考答案文件”；教师展示模型分数和完整评语，只有历史分项存在时显示“原评分明细”。不输出原始异常、提示词或作答日志。

### 9. 接口与权限

| 方法与路径 | 角色 | 语义 |
| --- | --- | --- |
| `POST /api/assignments` | 教师 | 创建本班文本作业；返回 201 和作业视图 |
| `GET /api/assignments` | 教师/学生 | 列出本班可评分作业；学生附带本人最新提交/任务摘要 |
| `GET /api/assignments/{id}` | 教师/学生 | 读取本班作业详情；学生附带本人最新提交/任务摘要 |
| `POST /api/assignments/{id}/submissions` | 学生 | 同事务保存提交/任务/发件箱，返回 202、`taskId`、`status=PENDING`、`statusUrl`；不在请求内调用模型 |
| `GET /api/assignments/{id}/submissions` | 教师 | 查看本班每名学生最新提交、状态、AI 结果与终评 |
| `GET /api/grading/tasks/{taskId}` | 学生本人/本班教师 | 只读任务状态；成功时含结果，失败时含安全失败码；查询无副作用 |

错误：未登录 401；学生创建作业或教师代提交 403；跨班或非本人任务 404（不泄露存在性）；空/超长/非法评分依据 400；相同内容重复提交返回已有任务（200/202），不同内容再次提交返回 409。任务 ID 不是授权凭证，所有查询都从服务端身份派生班级和本人边界。

学生任务读取同时检查本人和当前班级，调班后不能继续访问旧班任务。教师提交列表按学生/历史版本排序，最多读取 200 条后取每名学生最新一份；页面不显示内部版本字段。

### 10. 前端轮询

- 学生 `/assignments`：作业列表、提交文本、显示任务状态。提交成功后立即显示 `PENDING`；轮询间隔约 2 秒，连续失败退避到 10 秒；`SUCCEEDED/FAILED` 终止轮询；切换作业、离开页面或组件卸载时清理定时器/请求。
- 刷新页面后从作业详情中的本人最新任务恢复轮询；任务 ID 已终态时直接显示结果，不重新提交。
- 教师 `/assignments`：通过弹窗创建作业；列表查看学生、作答、状态、得分，改分弹窗展示 AI 分数/有依据的评语及历史明细并保存教师终评。
- 轮询只是读取；前端限制重复点击仅改善体验，后端仍做入口去重。
- 轮询/恢复读取携带 AbortSignal 和选择版本；切换、卸载或新提交后，旧响应不得写回或重新启动轮询。首次加载自动恢复选中作业的本人最新任务；正常查询重置失败次数。

### 11. 观测

- 结构化日志固定事件：`grading_task_claimed`、`grading_task_completed`、`grading_retry_scheduled`、`grading_dead_lettered`、`grading_outbox_published`；字段包含 `taskId`、`status`、`attempts`、`durationMs`、安全 `failureCode`，不含作答/提示词/模型响应。
- 任务状态接口提供 `attempts`、`maxAttempts`、时间戳、`lastFailureCode`/`failureCode`，用于关联具体任务。
- 队列长度与死信：通过 RabbitMQ management/`rabbitmqctl list_queues` 记录 `grading.tasks`、`grading.dlq` 的 ready/unacked 数量；数据库按状态/尝试次数聚合；消费耗时来自完成日志。验收记录保存脱敏命令与输出，不提交原始敏感日志。

### 12. 配置、部署与兼容

- Compose 新增 `rabbitmq`（持久化卷、健康检查、内部 5672；management 端口仅绑定 127.0.0.1）和 `grading-worker`（同一后端镜像、worker profile、依赖 MySQL/Qdrant/RabbitMQ、无前端端口）。
- `.env.example` 增加 RabbitMQ 用户/口令/管理端口和 grading 重试参数的可选键名；真实凭据只来自私有 `.env`。README 增加 broker 启动、worker 停止/启动和观测命令。
- 迁移只增不改；旧应用在新表存在时可运行；回滚应用镜像时保留 RabbitMQ/数据库卷，停止 worker 即可停止新执行路径。不提供破坏性 down migration。
- 不把登录、权限等需要立即结果的路径改为排队；现有助手对话/SSE 保持原样。
- 仓库主干保持 `master`，不因课件示例名称 `main` 重命名分支。
- Compose 将全部评分参数传给 Web/worker；worker 关闭材料恢复和 Qdrant 初始化，避免首次启动争建 collection。时间参数必须为正，抖动后的退避仍受最大值限制。镜像仅预取项目依赖，不在构建预热阶段拉取可选扫描插件依赖。

### 13. 测试与验收

- 后端单元：提交/任务/发件箱同事务与回滚；条件认领；幂等键和结果唯一约束；退避/jitter 边界；状态机；模型 JSON 类型/分数范围严格校验与综合分数原样保存；权限矩阵；状态查询无副作用。
- 集成：MySQL + RabbitMQ Testcontainers（或等价隔离代理）+ 模型替身；覆盖发件箱发布/确认、消费者认领、重复投递、结果冲突、崩溃恢复、重试/死信。
- 接口：新增作业创建、提交、状态查询、教师查看、重复提交、跨班/越权用例；`scripts/check.py` 的 integration/api 清单须显式包含新类/用例。
- 三条核心判据：请求不等待（202 无分数、提交耗时与后台耗时对比）、停消费者不丢任务（停 worker、提交、pending、恢复完成）、重复不产生重复结果（重复请求/重复消息、唯一结果、次数证据）。
- 故障/并发：结果提交后 ACK 前退出、执行中退出、并发认领、broker 不可用、重试耗尽、死信、轮询停止/刷新恢复、未登录/跨班/他人任务。
- 所有验收使用独立或明确归属的合成数据；模拟慢执行和真实模型分别标注，不伪造历史同步基线。
- 三条核心判据保留为后续自动化回归来源；本课不要求完整监控平台或全量压测体系。

### 14. AI 审核与扫描

- `AGENTS.md` 增加 AI 生成代码的审核/合入约束并指向 `docs/standards/04-testing.md`。
- `04-testing.md` 增加七类核查清单、七维评审表、逐文件“可用/待修改/风险无效”结论、高风险（并发/事务/一致性）复核、合入顺序和批量扫描要求。
- 新增 `docs/lesson-08/README.md`（三条判据、观测、环境/版本/标签）和 `review.md`（自审、同伴评审、问题整改与复核）。文档自包含，不引用仓库外课件。
- 扫描入口固定为可执行命令：Java Checkstyle/SpotBugs/OSV（OWASP dependency-check 为可选补充），前端 ESLint + `npm audit`，Python Ruff + `pip-audit`，数据库 `EXPLAIN` + 人工索引/N+1 复核；新增 `scripts/scan.py` 和 `scripts/osv_scan.py` 并在 lesson-08 记录工具、范围、结果和人工处置。未接入或未执行的扫描记为未完成，不写成已通过。
- 合入顺序：AI 初稿注明范围 → 开发者自审/整改 → 测试与扫描 → 另一成员交叉评审和复核 → 证据入库 → 合入。代理不能代签成员结论。
- OSV 空依赖清单、查询失败或无法判定严重性时必须非零退出，不将不完整扫描解释为无漏洞；明确记录剩余告警和成员风险确认。

### 15. 工作拆分

- **第一部分（成员 B / 王家兴）**：范围与 change 文档、审核规则/评审表/扫描入口、lesson-08 记录骨架；实现后独立复现三条判据、故障/并发验收、批量扫描、七维评审/逐文件结论/高风险复核、问题整改复核和最终证据。
- **第二部分（成员 A / 王培鑫）**：RabbitMQ/Compose、V8 迁移、作业/提交/任务/结果/发件箱、202 接口、投递器、独立 worker、模型评分适配器、状态/教师接口、前端轮询、单元/集成/接口测试、扫描工具和自审整改。
- 两人都实际读代码；A 自审，B 交叉评审；下一轮交换角色。共享契约先评审，不同时拆改同一功能。

## Capability—Scenario—Verification 对应

| 能力 | 场景范围 | 验证 |
| --- | --- | --- |
| `assignment-grading` | 创建、提交、重复、查询、权限、评分可解释、版本 | 单元/接口/前端测试 + 真实或替身模型验收 |
| `reliable-task-execution` | 202 解耦、停消费者、重复消息、崩溃、重试/退避、死信、观测 | 集成测试 + 三条核心判据 + 故障/并发脚本/记录 |

## Risks / Trade-offs

- 引入 RabbitMQ 增加部署和运维复杂度 → Compose 固化配置、健康检查、持久化卷和最小拓扑；worker 可独立停启。
- 发件箱定时轮询增加数据库压力 → 只查 `(status, available_at)` 索引、批量小、发布在事务外；不使用长事务。
- 重复投递可能重复调用模型 → 条件认领、结果唯一约束、重复消息 ACK；不承诺 exactly-once，只保证结果不重复生效。
- 模型判断可能不稳定 → 严格 schema/类型/范围校验、有证据的评语和教师终评、明确“模型辅助”标注；教师终评独立保存，保留 AI 原始评分依据。
- 前端轮询可能造成请求放大 → 间隔退避、终态停止、卸载清理、刷新恢复；后端查询只读。
- 教师终评与 AI 原始结果分开保存，避免后台迟到完成覆盖教师决定；终评写入核对本班作业、提交归属、分数边界和操作者。
- 当前环境可能没有 Docker/RabbitMQ → 本课验收必须补齐 broker；不能以材料索引或进程内线程池替代。

## Migration Plan

1. 评审本 change 与范围调整。
2. 实现并评审 V8 迁移、RabbitMQ/worker、接口和前端。
3. 在独立合成环境执行三条判据、故障/并发和扫描，记录证据。
4. 另一成员交叉评审通过后，同步主规格、归档、更新项目状态，并按授权创建迭代标签。
5. 回滚应用镜像时停止 worker、保留 RabbitMQ/MySQL 卷；新增表对旧应用兼容，不做破坏性回滚。


## 2026-11-04 验收反馈修正（已授权实施）

- 全站操作成功/失败提示使用共享顶部 Toast，成功 4 秒、错误 6 秒自动消失，可关闭；保留输入和重试操作，不以横幅承载操作反馈。持久化业务状态（批改中/失败）属于结果内容，简洁展示。
- 新建作业用 BaseModal，字段为标题、详细题目、总分、评分依据文件。文件只允许本班已有 READY 的 txt/md，最多 5 个、合计 20000 字符；原文及来源标题/编号保存快照。材料之后变化不影响该作业。创建只校验和保存参考答案，不调用模型。2026-11-05 起取消评分项提取，详见第8节；读取材料和保存作业仍是短数据库操作。
- 新增 V9：assignments 保存 rubric_source_json 和 rubric_text；新增 submission_reviews，一提交一条，包含 assignment/class/submission/teacher、终评分数、评语和时间。不得修改 V1～V8。旧记录可空，原始 AI 结果保持不变。
- 新增 PUT /api/assignments/{id}/submissions/{submissionId}/review；仅本班教师，学生 403、跨班/错配 404，分数 0～作业总分、评语 1～2000 字符。锁定提交行串行新增/更新终评，不涉及网络。允许在 AI 未完成时人工终评；后台完成不能改终评。
- 任务/教师列表响应补充作答、AI 结果及 teacherReview，保留旧字段兼容；教师列表每学生仅展示最新历史提交，最多 200 行并批量关联。学生显示终评优先；无终评时显示 AI；任务结束后可低频刷新终评、重新进入必读最新结果，不重新提交。
- 教师表格为学生、作答、状态、得分，末列改分操作；改分弹窗上部为作答/AI 初评与依据，下部为终评分数/评语。左侧仅名称与状态/分数。学生提交后隐藏输入，展示自己的作答及批改结果。页面不显示任务编号、执行次数、版本或英语装饰。
- 一次提交通过现有作业行锁后检查该学生已有提交来保证；相同内容/键幂等，不同内容 409。保留旧唯一键与内部 version 字段，避免破坏既有多版本记录。
- 指定 MCP 和示例数据只在确认归属的 campusclaw-plus 清理；有审计的指定合成 MCP 连同其合成审计按本次明确授权删除，不开放绕过历史保护的普通删除接口。清理示例只匹配已知验收前缀，保留用户新增作业、账号、文件和其他 MCP。


### 2026-11-05 初验后清理

用户初步功能检验通过。仅定向删除旧演示作业“勾股定理练习”及关联记录；参考材料保留。去掉侧栏隔离宣传、检索范围徽标及登录的底层鉴权文案，保留身份和处理状态。材料仍由现有有界线程池异步执行、数据库认领和定时恢复，不为技术形式统一引入消息队列迁移。
