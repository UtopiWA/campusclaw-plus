# Class08 异步作业批改验收记录

状态：**技术实现与三条核心判据验收完成；同伴评审、规格同步、归档和版本标签待团队完成。** 未完成项不记为通过。

- 关联 change：[2026-11-03-add-async-assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)
- 能力规约：[assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/assignment-grading/spec.md)、[reliable-task-execution](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/reliable-task-execution/spec.md)
- 评审记录：[review.md](review.md)
- 测试/评审规范：[04-testing.md](../standards/04-testing.md)
- 项目状态：[project-status.md](../project-status.md)

## 0. 范围

教师创建带评分依据的文本作业；本班学生提交文本；Web 在同一事务保存作答、任务和发件箱并返回 202；独立 `grading-worker` 通过 RabbitMQ 消费，模型只做分项判断，服务端按评分依据确定性计分；学生和本班教师按权限查询结果。教师复核/改分、完整题库、知识图谱、学习计划和班级分析不在本次范围。

所有验收使用独立合成数据库/上传目录和本地可归属的 RabbitMQ 数据目录；记录只保留任务标识、状态、耗时、分数和队列计数，不包含作答、提示词、模型完整响应或凭据。

## 1. 三条核心判据

### 1.1 请求不等待执行

| 项目 | 记录 |
| --- | --- |
| 前提 | Web、独立 MySQL、Qdrant、RabbitMQ、worker 正常运行；真实 `course-chat` 模型；每份样本独立作业 |
| 操作 | `python scripts/lesson08_acceptance.py basic` 连续执行 3 次，记录提交耗时和任务起止时间 |
| 实际输出 | 提交 69.8 / 41.1 / 29.2 ms；任务 2320 / 1950 / 1633 ms；三次均 202、无分数、`status=PENDING`，最终 `SUCCEEDED`、100/100 |
| 结论 | **通过**：提交 P95 远小于后台执行耗时，202 不包含分数；请求线程未调用模型 |

依赖升级后的补充样本：提交 80.4 / 114.5 / 41.1 ms，任务 2182 / 2408 / 1511 ms，均成功。

### 1.2 停消费者不丢任务

| 项目 | 记录 |
| --- | --- |
| 前提 | 仅停止 `grading-worker`；Web、MySQL、RabbitMQ、投递器保持可用 |
| 操作 | 停止 worker → `python scripts/lesson08_acceptance.py pending` 提交作业 → 查询任务和队列 → 重启 worker → `wait --task-id <id>` |
| 实际输出 | 提交返回 202、`PENDING`；任务 `b54a5279-975f-4b83-bfd4-6ddfe7f61104` 保持 PENDING；`grading.tasks` 为 1 ready、0 consumers；重启 worker 后 `SUCCEEDED`，attempts=1，无需重新提交 |
| 结论 | **通过**：停消费者不丢任务，恢复后自动完成 |

### 1.3 重复不产生重复结果

| 项目 | 记录 |
| --- | --- |
| 前提 | 已成功的任务 `c6e95d77-1c37-4b3c-be77-9d1919d2dcae`；结果唯一约束可用 |
| 操作 | `python scripts/lesson08_acceptance.py duplicate --task-id <id>` 通过 RabbitMQ management API 重投同一 `taskId` 消息；等待消费后检查任务和结果 |
| 实际输出 | 状态 `SUCCEEDED`、attempts=1、results=1；重复消息被认领 0 行并安全 ACK，原结果未被覆盖；队列最终 0 |
| 结论 | **通过**：重复投递不产生第二份生效结果 |

同一内容重复提交也已验证：相同内容/相同 `Idempotency-Key` 返回同一 task/submission；不同内容创建新版本（v2），旧结果保留。

## 2. 队列与执行观测

- `grading.tasks` 和 `grading.dlq` 的 ready/unacked/consumers 通过 RabbitMQ management（仅本机 15672）读取；DLQ 在故障测试中为 1 条，重启 broker 后仍保留。
- 任务状态接口返回 `attempts`、`maxAttempts`、`createdAt`、`startedAt`、`finishedAt`、`lastFailureCode`/`failureCode`，可关联具体任务。
- 结构化日志事件：`grading_outbox_published`、`grading_task_completed`、`grading_retry_scheduled`、`grading_task_recovered`；含 `taskId`、状态、尝试次数、耗时和安全失败码，不含作答/提示词/模型响应。
- 一次验收快照：`SUCCEEDED 11`（累计 attempts 13）、`FAILED 1`、results=11、outbox 全部 PUBLISHED；队列 0、DLQ 1（故障样本）。这些是合成验收数据，不是生产统计。

## 3. 故障、并发与 UI

| 场景 | 实际结果 |
| --- | --- |
| 模型输出非法并重试 | 任务 `a12e5312...` 第一次 `MODEL_OUTPUT_INVALID`，按退避进入 PENDING；第二次成功，attempts=2 |
| 不可重试失败进入 DLQ | 受控无效评分依据任务 `03b4f390...` 直接 `FAILED`/`ASSIGNMENT_INVALID`（attempts=1），`GRADING_DEAD_LETTER` 已发布，DLQ=1 |
| 执行中退出/RUNNING 回收 | 模拟旧 `RUNNING` 任务 `b20cd1c1...`（attempts=1）；worker 回收为 `STALE_RUNNING` → PENDING → 重试成功，attempts=2 |
| broker 重启 | 重启 RabbitMQ 后 DLQ 消息仍在、消费者重连，新作业提交后成功；耐久队列/消息生效 |
| 数据迁移 | V7 库以 `flyway.target=7` 迁移到 V7（应用因缺少 V8 实体按预期失败）；正常启动升级到 V8，health 200 |
| 页面轮询与恢复 | 学生提交后看到 PENDING→SUCCEEDED、分数和“模型辅助分项判断”；刷新后结果仍在；教师看到本班学生提交和分数；学生无“新建作业”入口 |
| 权限 | 学生创建作业 403；跨班任务 404；匿名 401；学生查看班级提交列表 403；本班教师查询 200 |
| 重复提交 | 同内容/同幂等键返回同一 task；不同内容创建新版本；结果按 `(submission_id, version)` 唯一 |

## 4. 扫描与依赖

| 检查 | 结果 |
| --- | --- |
| ESLint | 通过 |
| npm audit（high 阈值） | 通过；仍有 12 项 low/moderate，均为开发依赖（Vitest/ESLint 链路），无 high/critical |
| Ruff | 全部通过 |
| pip-audit | `apitest/requirements.txt` 无已知漏洞 |
| Checkstyle | 0 violations（项目紧凑单行访问器风格按既有约定不纳入规则） |
| SpotBugs | 0 high 级问题 |
| OSV（Maven 运行时依赖） | 已实际查询；升级 Spring Boot 3.5.16 并覆盖 Tomcat 10.1.59、Jackson 2.21.7、Netty 4.1.137、amqp-client 5.34.0 后，仅剩两项 Spring 6.2.19 critical：SSE 片段渲染和 XsltView 路径限制。二者只在 Spring 7.0.9 修复；本项目未使用 XsltView，SSE 走 `StreamingResponseBody` 而非片段渲染，记录为不适用并列入后续 Spring Boot 4 升级项。 |
| OWASP dependency-check | **未完成**：无 NVD API Key 时 20 分钟仅更新 2%，已停止；以 OSV 作为本轮的 Java 依赖漏洞检查，不把 OWASP 写成通过 |
| 数据库查询风险 | `EXPLAIN` 已执行：任务认领走主键；发件箱走 `(status, available_at)`；最新提交走唯一版本键；结果查询走唯一 `(submission_id, version)`；教师提交和回收查询使用现有索引/班级规模下有界扫描；未发现循环内查询或无界列表 |

扫描入口为 `python scripts/scan.py`；原始输出在 `backend/target/scan-logs/`，不提交敏感日志。

## 5. 环境、候选提交与版本

| 项目 | 记录 |
| --- | --- |
| 候选提交 | `41fb5da1e41141c63c8fddac10a91b74ef3f8ad8`（实现提交；后续文档提交仅补记录） |
| 环境类别 | Windows 11；JDK 17 Corretto 17.0.10；Node 20.19；Python 3.10.6；独立 MySQL 8.0.36（3307）；Qdrant 1.15.4；便携 RabbitMQ 4.4.0 + Erlang 27.3.4.19（5672/15672）；Web 8081；worker 随机端口；Vite 8080 |
| 模型模式 | 真实网关 `course-chat` / `course-embedding`（未使用替身伪造分数） |
| Docker/Compose | **未运行**：本机无 Docker；Compose 已加入 RabbitMQ/worker 配置，但本轮以原生进程完成验收。Compose 启动、镜像构建和 Testcontainers 未由本轮独立验证。 |
| 检查范围 | `scripts/check.py --mode unit`、前端构建、三条判据、故障/并发、扫描、V7→V8、页面验收；未执行 integration Testcontainers/all |
| 版本标签 | 建议 `class08-async-assignment-grading`；**未创建**，等待团队授权 |
| 最终结论 | 技术实现与验收证据完成；同伴评审、规格同步、归档和版本标签仍未完成，不能宣称 Class08 已全部交付 |

## 6. 边界

- 不把教师复核、完整题库、知识图谱、学习计划或班级分析列为本次验收对象。
- 不把材料索引的既有异步能力当作本课队列/批改已完成。
- 不把设计文档、health 200、队列存在或某次顺利路径当作全部通过。
- 未完成自审、交叉评审、测试/扫描或证据入库的代码不得合入主干。
