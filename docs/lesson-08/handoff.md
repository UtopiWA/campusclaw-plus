# Class08 交接：已完成事项与成员 A 待办

更新：2026-11-04。

- 实现提交：`41fb5da1e41141c63c8fddac10a91b74ef3f8ad8`
- 验收记录提交：`5dedbe865f30550f224662706e69b0371b3a1b9d`
- 当前状态：技术实现与本机验收完成；change 仍为活动状态，未同步规格、未归档、未创建版本标签。
- 关联：[change 提案](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)、[验收记录](README.md)、[评审记录](review.md)。

## 1. 本次已完成（协作代理执行，成员 B 授权）

### 1.1 实现

- 后端：V8 迁移，`submissions`/`grading_tasks`/`grading_results`/`grading_outbox`；教师创建作业、学生 202 提交、只读任务状态、教师本班提交列表。
- 可靠性：提交+任务+发件箱同事务；RabbitMQ 持久化队列、发布确认、手动 ACK；独立 `grading-worker`；条件认领；结果唯一约束；有限重试、指数退避+抖动、DLQ；`RUNNING` 超时回收。
- 评分：真实 `course-chat` 模型只返回分项判断/证据/解释；服务端按评分依据确定性计分；严格 JSON 校验；结果标注“模型辅助分项判断 + 规则计分”。
- 前端：`/assignments` 教师创建/查看、学生提交/轮询/结果、刷新恢复；权限由后端强制。
- 运行：Compose 已加入 RabbitMQ 和 `grading-worker`；`.env.example` 和 README 已同步。
- 安全：Spring Boot 升级到 3.5.16；覆盖 Tomcat 10.1.59、Jackson 2.21.7、Netty 4.1.137、amqp-client 5.34.0。

### 1.2 已验证

| 项 | 结果 |
| --- | --- |
| `scripts/check.py --mode unit` | 退出 0；OpenSpec、前端 12 文件/73 项、后端 173 项、MCP 8 项、脚本 10 项 |
| 请求不等待 | 3 次提交 69.8/41.1/29.2 ms；后台 2320/1950/1633 ms；202 无分数、最终 SUCCEEDED |
| 停消费者 | 停 worker 后任务 PENDING；队列 1 ready/0 consumers；恢复后 SUCCEEDED，无需重提 |
| 重复消息 | 同一 task 重投后 attempts=1、results=1，原结果未覆盖 |
| 故障 | 模型非法输出重试成功；不可重试失败进入 DLQ；`RUNNING` 回收成功；broker 重启后 DLQ/队列仍在 |
| 迁移 | V7 库升级到 V8，health 200 |
| 页面 | 学生提交/轮询/刷新恢复；教师看到本班提交和分数；学生无创建入口 |
| 扫描 | Checkstyle/SpotBugs/ESLint/Ruff/pip-audit/npm audit 通过；OSV 见下方遗留 |

完整证据和命令见 [README.md](README.md)，逐项问题见 [review.md](review.md)。

### 1.3 已知遗留（未写成通过）

- **OSV** 仍报告两项 Spring 6.2.19 critical：`GHSA-j9f9-w8pj-32f8`（SSE 片段渲染）和 `GHSA-pc63-qcmh-9cmg`（XsltView 路径限制）。二者只在 Spring 7.0.9 修复；当前代码不使用 XsltView，SSE 走 `StreamingResponseBody` 而非片段渲染，已记录为不适用，后续需 Spring Boot 4 升级。
- **OWASP dependency-check 未完成**：无 NVD API Key 时更新过慢，已停止；本轮以 OSV 作为 Java 依赖检查。
- **Compose/Testcontainers 未运行**：本机无 Docker；以独立 MySQL/RabbitMQ 原生进程完成验收。Compose 配置已提交但未实际启动。
- `npm audit` 仍有 12 项 low/moderate，均在开发依赖链路，无 high/critical。

## 2. 成员 A（王培鑫）需要做什么

### 2.1 独立复现

1. 阅读 [AGENTS.md](../../AGENTS.md)、[change 提案](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)、[design](../../openspec/changes/2026-11-03-add-async-assignment-grading/design.md)、[tasks](../../openspec/changes/2026-11-03-add-async-assignment-grading/tasks.md) 和本次提交 `41fb5da`。
2. 在自己的环境启动完整链路。有 Docker 时按 README 使用 Compose；没有 Docker 时准备等价的独立 MySQL、Qdrant、RabbitMQ 和 worker，**不得改连现有应用数据库或复用无关账号**。
3. 至少独立走通：教师创建作业 → 学生提交 → 202/PENDING → 后台 SUCCEEDED；停 worker → PENDING → 恢复 → 完成；重投同一消息 → 只有一份结果；必要时补一次 DLQ/`RUNNING` 回收。不能只看本记录或 health 200 就判定通过。
4. 跑一次受影响检查（unit；若环境允许，补 integration/API），记录实际命令、环境和结果。

### 2.2 代码与规约审核

1. 按 [04-testing.md](../standards/04-testing.md) 完成七维评审、逐文件“可用/待修改/风险无效”和并发/事务/一致性高风险复核。
2. 重点核对：提交/任务/发件箱同事务、条件认领、结果唯一约束、ACK 时序、重试/退避/死信、`RUNNING` 回收、任务 ID 不能绕过班级/本人授权、日志不含作答/提示词/凭据。
3. 检查 change 的 delta specs、design 与实现是否一致；如发现偏差，先记录并修正，再补受影响检查。

### 2.3 依赖与扫描处置

1. 复核 OSV 两项 Spring 6.2 critical 的“不适用”判断；如认为需要立即修复，提出独立 Spring Boot 4 升级 change，不把升级硬塞进本次验收。
2. 复核 OWASP 未完成和 npm dev 依赖 12 项 low/moderate 的处置；不能把未执行写成通过。
3. 检查本次 `pom.xml` 安全升级是否影响现有功能；如发现问题，记录并复测。

### 2.4 记录与签字

1. 在 [review.md](review.md) 填写“同伴评审”和合入门槛，记录实际阅读的代码、问题、整改和复核结论。
2. 代理不能代签；只有实际审核的成员可以确认结论。
3. 发现问题时修正并补跑受影响检查；无修改不重跑整套 all。

### 2.5 通过后归档

1. 同步 delta specs 到主规格，执行 `openspec validate --all --strict` 和 `git diff --check`。
2. 归档 `2026-11-03-add-async-assignment-grading`，更新 [project-status.md](../project-status.md) 中的活动 change 链接和状态。
3. 版本标签建议 `class08-async-assignment-grading`，指向验收后的提交；**创建标签前需团队授权**。
4. 提交并推送；不要提交 `.env`、`class08.md` 或原始敏感日志。

### 2.6 不要做

- 不把 `class08.md` 课件或 `.env` 提交到仓库。
- 不降低采信/重试/权限阈值来让扫描或测试变绿。
- 不把 OWASP 未完成、Compose/Testcontainers 未运行或 OSV 两项遗留写成“已通过”。
- 不在交叉评审完成前归档或打标签。

## 3. 当前运行信息

- 应用：<http://127.0.0.1:8080/>；Web 8081；worker 随机端口；RabbitMQ 5672/15672；MySQL 3307；Qdrant 6333。
- 账号：`teacher-a`、`student-a1`、`student-b1`；密码在私有 `.env` 的 `DEMO_SEED_PASSWORD`，未提交。
- 模型：真实网关 `course-chat` / `course-embedding`；未使用替身伪造分数。
- 原始扫描/检查输出在忽略目录 `backend/target/`，不提交。

## 4. 完成判据

成员 A 完成独立复现、七维/逐文件/高风险评审、扫描处置记录，并在 [review.md](review.md) 签字后，才可同步规格、归档、更新状态并按授权创建标签。在此之前，Class08 只能标记为“技术实现和本机验收完成，待同伴评审/归档”。
