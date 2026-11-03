# Class08 评审记录

状态：**开发者自审完成；成员 A 的交叉评审、规格同步、归档和版本标签未完成。** 代理可以协助整理，但不能代签成员结论。

- 关联 change：[2026-11-03-add-async-assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)
- 验收记录：[README.md](README.md)
- 测试/评审规范：[04-testing.md](../standards/04-testing.md)

## 0. 评审元数据

| 项目 | 内容 |
| --- | --- |
| 候选提交/差异范围 | 本次异步批改实现提交（含后端、前端、迁移、脚本、Compose 和文档） |
| AI 生成范围 | 新增 grading 包、V8 迁移、前端 `/assignments`、扫描/验收脚本和证据文档由协作代理生成；成员 B 授权并确认按建议推进，成员 A 尚未交叉评审 |
| 开发者自审 | 协作代理执行技术自审，成员 B 授权；不是成员 A 的交叉评审 |
| 同伴评审 | **待成员 A（王培鑫）实际阅读代码并确认** |
| 实际执行的检查 | 后端 173 项非容器测试、前端 12 文件/73 项、三条判据、DLQ/恢复/broker 重启、V7→V8、页面验收、Checkstyle/SpotBugs/ESLint/Ruff/pip-audit/npm audit/OSV；Testcontainers/all 未执行 |
| 合并结论 | 开发者自审通过；同伴评审和最终归档待完成 |

## 1. 七维评审

| 维度 | 记录 | 结论 |
| --- | --- | --- |
| 需求覆盖率 | 作业创建、202 提交、发件箱、独立 worker、状态/教师查询、前端轮询、幂等/版本、重试/退避/死信、观测、权限均已实现；教师复核/改分等按范围排除 | 覆盖本次场景 |
| 需求一致性 | API、V8 表、状态机、幂等键、结果唯一键与规约一致；Spring Boot 3.5.16 安全升级和 worker 随机端口已同步 design | 一致，需同伴复核升级影响 |
| 失败路径覆盖 | 非法输入、未登录、学生/教师越权、跨班、重复内容、事务失败、模型非法输出、不可重试失败、DLQ、RUNNING 回收、broker 重启均有测试或实测 | 通过 |
| 查询性能风险 | 认领主键、发件箱 `(status, available_at)`、最新提交唯一版本键、结果唯一键、教师提交索引；`EXPLAIN` 未发现无界扫描；回收查询使用 status 索引并按班级规模有界 | 可接受，无循环查询 |
| 重复代码比例 | 复用现有 `ChatGateway`、`CurrentUserService`、异常映射、前端 `apiRequest`/`AppShell`；新增评分与队列代码独立，无大段复制 | 可接受 |
| 漏洞风险 | 升级 Spring Boot 3.5.16，覆盖 Tomcat 10.1.59/Jackson 2.21.7/Netty 4.1.137/amqp-client 5.34.0；OSV 剩两项 Spring 6.2.19 critical，当前代码不使用 XsltView/片段渲染，记录为不适用并列入 Spring Boot 4 升级；npm 余 12 项 low/moderate dev 依赖；OWASP 因 NVD 限速未完成，以 OSV 为准 | 有条件通过，需团队确认剩余风险 |
| 高风险场景 | 并发认领、事务/发件箱、结果唯一、重复投递、崩溃恢复、broker 重启、权限矩阵均有可复核结果 | 通过 |

## 2. 逐文件结论

| 文件 | 结论 | 说明 |
| --- | --- | --- |
| `persistence/Assignment.java`、`Submission.java`、`GradingTask.java`、`GradingResult.java`、`GradingOutbox.java` | 可用 | 字段、唯一键和状态与 V8 一致 |
| `resources/db/migration/V8__assignment_grading.sql` | 可用 | 空库和 V7→V8 升级通过；只增不改 |
| `grading/AssignmentService.java`、`SubmissionService.java` | 可用 | 202/同事务/幂等/版本已实测 |
| `grading/GradingModelService.java` | 可用 | 严格 JSON、分项判断、确定性计分、非法输出重试已实测 |
| `grading/GradingTaskCompletionService.java`、`GradingWorker.java`、`GradingRecoveryScheduler.java` | 可用 | 认领、结果唯一、ACK、DLQ、RUNNING 回收已实测 |
| `grading/GradingOutboxPublisher.java`、`RabbitConfig.java` | 可用 | 发布确认、失败重试、持久化队列已实测 |
| `grading/GradingTaskQueryService.java`、`AssignmentController.java`、`GradingTaskController.java` | 可用 | 只读查询、教师/学生权限已实测 |
| `config/GradingProperties.java`、`application.yml`、`application-worker.yml` | 可用 | worker 绑定随机端口以复用安全上下文，不暴露业务端口；demo seed 在 worker 关闭 |
| `frontend/src/views/AssignmentsView.vue`、`services/assignments.js`、`router/index.js`、`AppShell.vue` | 可用 | 提交、轮询、刷新恢复、教师提交列表已做浏览器验收 |
| `frontend/eslint.config.js`、`scripts/scan.py`、`scripts/osv_scan.py`、`scripts/lesson08_acceptance.py` | 可用 | 扫描/验收命令可执行；OSV 发现已记录 |
| `docker-compose.yml`、`.env.example`、`README.md` | 可用 | RabbitMQ/worker 配置与说明已同步；Compose 未实际启动 |
| `observability/JsonLogEncoder.java`、`MdcTaskDecorator.java`、`RequestLoggingFilter.java` 及三处旧导入/单行语句 | 可用 | 仅做日志字段/风格修正，行为由既有测试覆盖 |
| `pom.xml` | 可用 | Spring Boot 3.5.16 安全升级和依赖覆盖；173 项测试通过；需同伴复核升级影响 |

## 3. 高风险复核

| 场景 | 查了什么 | 规约/设计依据 | 结论 |
| --- | --- | --- | --- |
| 并发认领/重复投递 | 条件 `UPDATE ... WHERE status=PENDING`；重复消息认领 0 行并 ACK；结果唯一约束 | `reliable-task-execution` 至少一次/幂等场景 | 实测重复消息 results=1、attempts 不增 |
| 事务边界/发件箱 | 提交、任务、发件箱同事务；发布在事务外；失败/重试/确认 | 事务性发件箱和故障窗口 | 迁移、提交、发布重试和 broker 重启通过 |
| 消费者退出/RUNNING 回收 | 模拟 5 分钟前 RUNNING；恢复调度置 PENDING 并重试 | 崩溃恢复场景 | `STALE_RUNNING` → SUCCEEDED attempts=2 |
| 重试/退避/死信 | 模型非法输出重试；不可重试失败 DLQ；退避/抖动单测 | 有限重试/指数退避/死信 | 非法输出 attempts=2 成功；DLQ=1 |
| 权限/班级/本人 | 学生创建 403、跨班 404、匿名 401、学生教师列表 403、教师 200 | `assignment-grading` 权限场景 | 全部符合 |
| 依赖漏洞 | OSV 实际查询；Tomcat/Jackson/Netty/amqp-client 覆盖；Spring 6.2 两项 critical 审查代码路径 | 安全规范/扫描要求 | 适用高危已修复；两项不适用路径已登记，待 Spring Boot 4 升级 |

## 4. 问题记录

| 文件/位置 | 对应场景或规则 | 问题及触发条件 | 修正动作 | 复核结果 |
| --- | --- | --- | --- | --- |
| `V8__assignment_grading.sql` | Hibernate schema validate | `content_hash CHAR(64)` 与实体 `varchar(64)` 不符，启动失败 | 改为 `VARCHAR(64)`，重建合成库 | 迁移和启动通过 |
| RabbitMQ 用户权限 | 发件箱发布 | `set_permissions` 引号被 cmd 保留，写 exchange 被拒 | 重新设置 `.* .* .*` | 发布成功，21 次失败后补投 |
| `application-worker.yml` | worker 启动 | `web-application-type: none` 导致 SecurityConfig 缺少 AuthenticationConfiguration | worker 改用随机端口并关闭 demo seed | worker 启动、消费通过 |
| `GradingTaskRepository.claim` | 原子认领 | `@Modifying` 查询缺少事务，抛 TransactionRequiredException | 加 `@Transactional` | 认领/结果通过 |
| `pom.xml` | 依赖漏洞 | Spring Boot 3.3.5/Tomcat/Jackson/Netty/RabbitMQ 高危 | 升级 3.5.16 并覆盖补丁版本 | 173 项测试和三条判据复测通过 |
| OSV 两项 Spring 6.2 critical | 依赖扫描 | 仅 7.0.9 修复；当前代码不用 XsltView/片段渲染 | 登记为不适用，后续 Spring Boot 4 升级 | 未清零，不能写成扫描全通过 |
| OWASP dependency-check | 依赖扫描 | 无 NVD API Key，20 分钟仅 2% | 停止，改用 OSV | 记为未完成 |

## 5. 自审与同伴评审

| 角色 | 记录 |
| --- | --- |
| 开发者自审 | 协作代理执行，成员 B 授权；已按七类清单检查，修复上述问题并复测受影响项 |
| 同伴评审 | **待成员 A（王培鑫）实际阅读代码、复核七维/逐文件/高风险和扫描处置，并记录结论** |
| 代理协助 | 代理只整理材料和执行检查；成员对最终结论负责，不能代签 |

## 6. 合入门槛

- [x] AI 初稿已注明生成范围，开发者已自审并整改。
- [x] 后端 173 项、前端 73 项、三条核心判据和故障/并发有实际结果。
- [x] 扫描入口已执行；适用高危已修复，剩余项和不适用项已记录（OWASP 未完成）。
- [x] 七维、逐文件结论、高风险复核和问题整改已填写。
- [ ] 成员 A 交叉评审通过，证据由双方确认。
- [ ] 规格同步、归档、项目状态更新和版本标签按完成条件与授权办理。

**当前结论：** 开发者自审和本机技术验收通过；Class08 尚未完成同伴评审、规格同步、归档和标签，不能宣称全部交付。
