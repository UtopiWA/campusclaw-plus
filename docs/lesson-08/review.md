# Class08 评审记录

状态：**本次独立技术复核及修正已执行；成员 A 暂不接受依赖风险，当前 change 保持活动，不进行规格同步、归档或提交，标签未授权。** 第 0～6 节保留实现方原始自审记录供对照，当前独立结论以第 7 节为准；代理不代签成员结论。

- 关联 change：[2026-11-03-add-async-assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)
- 验收记录：[README.md](README.md)
- 交接待办：[handoff.md](handoff.md)
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

## 7. 交接后的独立技术复核（2026-11-04）

### 7.1 范围与执行主体

以 `5cdd020` 为接手基线，对最近三次提交及本次工作区修正进行复核。代码检查、工具执行和本节整理均由接收方协作代理完成，**尚不代表成员 A 已签署评审**。实现方记录没有用作本次运行结果。

独立新建 Compose 项目、MySQL/Qdrant/RabbitMQ 卷和合成账号口令；仅私下复用模型配置。测试宿主为 Windows、Java 20.0.2 / Node 22.21.1 / Python 3.11.2，容器运行 Java 17；前端先执行 npm ci，再用锁文件中的 Vitest 4.1.12 检查。npm 提示部分开发依赖需要更高 Node 22 补丁版，未把本机版本写成完全满足其 engines。

### 7.2 七维结论

| 维度 | 本次检查及实际依据 | 技术结论 |
| --- | --- | --- |
| 需求覆盖 | 教师创建、学生 202、独立 worker、恢复/幂等、结果查询和真实页面；逐条对照两份 delta spec | 本次业务范围覆盖，教师复核等非目标不冒称完成 |
| 需求一致性 | 修正“只交一次/不支持新版本”与已实现版本链的矛盾；模型总分口径改为不采信且未知字段拒绝 | 实现、设计和规约已对齐，主规格待确认后同步 |
| 失败路径 | DLQ、模拟 RUNNING 回收、事务回滚、旧执行迟到、不可路由、永久模型拒绝及非法输出 | 聚焦单测、MySQL 集成和真实运行均有对应依据 |
| 查询性能 | 实跑 EXPLAIN：任务主键、发件箱状态/时间索引、提交版本索引；教师列表 200 条、恢复分批 | 小规模合成集可接受；有 filesort/非覆盖过滤，不宣称压测或扫描成本恒定 |
| 重复代码 | 复用 ChatGateway、当前身份、统一 HTTP 和 AppShell；新增异常仅作模型失败分类 | 未新增业务依赖或通用任务框架 |
| 漏洞风险 | 完整 scan 实跑；OSV 103/103，仍有 2 critical 与 5 moderate；npm 1 low/11 moderate | 原始扫描退出 1；成员 A 暂不接受风险，交付门槛未通过 |
| 高风险 | 并发提交、结果/终态原子性、发件箱、旧 attempt 隔离、本人/班级权限与隐私 | 本节下表及独立验收记录给出依据 |

### 7.3 发现的问题与修正

| 编号 | 原问题/触发条件 | 修正与证据 |
| --- | --- | --- |
| R01 | 去重/版本先查后插，并发请求撞唯一键；教师/恢复查询无界 | 作业行短事务锁串行分配；教师 LIMIT 200、恢复小批量；并发相同/不同内容与回滚集成断言 |
| R02 | 超时回收后旧执行可写回新 RUNNING；任意完整性错误被当作成功 | 锁任务行并匹配 attempts；移除笼统冲突成功分支；迟到成功/失败/重试均不得改新认领 |
| R03 | publisher ACK 但不可路由仍标 PUBLISHED | 启用 publisher returns，并同时核对 returned；不可路由回归断言 |
| R04 | 学生只校验本人 ID，调班后仍能读旧班任务 | 同时检查当前班级；专门拒绝场景与测试 |
| R05 | 旧轮询在切换/卸载后回写或重启，首次刷新不自动恢复；成功查询也累计失败次数 | AbortSignal、选择版本和卸载标记；恢复最新任务；仅连续失败计数；前端回归及真实页面复测 |
| R06 | 缺失解释、null verdict、尾随 JSON 或 null 评分项处理不严；无效模型密钥被反复重试 | 严格输出校验与 400；模型 4xx 永久拒绝分类保留其他网关重试语义；相应单测 |
| R07 | OSV 查询失败返回空列表，未知严重性或空依赖清单可能误判无漏洞 | 不完整即非零退出，记录逐项查询失败及 JSON 报告；6 项脚本回归 |
| R08 | 验收脚本固定原生进程地址/端口，FAILED 或重复后变化仍可能退出 0 | 显式 env/base/management 参数、数据库端口读取配置；实际判据失败返回非零，2 项脚本回归 |
| R09 | Compose 未传递 GRADING 参数；worker 与 Web 同时初始化 Qdrant 导致首次启动失败 | 共享配置映射，worker 关闭材料/Qdrant 初始化；实际重建及全新卷同时启动通过 |
| R10 | 时间参数可非法；回收预算未覆盖两次调用；抖动可突破上限 | 正时长/倍数校验、完整超时预算和最大退避限制；回收日志只在转移成功时输出 |
| R11 | 镜像预热下载可选扫描插件大量依赖 | dependency:resolve 仅预取项目依赖，JDK17 镜像已实际构建 |
| R12 | 规约及运行说明仍保留只支持首次提交、缺作业入口等过期表述 | 修正规约、README、产品/运行说明；未修改已交付迁移 |

首次复现中还遇到两类**测试过程问题**：RabbitMQ management 统计未刷新时过早断言，改为等待统计收敛；DLQ 夹具破坏评分依据后未恢复导致列表 500，恢复自有合成数据并重跑页面通过。后端容器重建后 Nginx 旧解析曾产生 502，通过重启独立前端恢复，操作说明已补入 README。这些失败没有记为通过。

### 7.4 高风险复核

| 查什么 | 规约依据和手段 | 结果 |
| --- | --- | --- |
| 并发提交/重复投递 | assignment-grading 并发/幂等；两线程实际 MySQL 测试，真实队列重投后比较结果 ID/分数/时间戳 | 相同内容同 task，不同内容独立版本；结果 1 份且不变 |
| 事务/数据一致性 | reliable-task-execution；发件箱保存抛错后查库；完成路径行锁/attempt；模型调用在事务外 | 不留半套提交/任务；旧 attempt 无权写回，非唯一键错误不冒称成功 |
| 认领与恢复 | 条件更新含 attempts 上限；模拟旧 RUNNING，回收后真实模型完成 | 恢复为 SUCCEEDED、attempts=2；非重试失败 FAILED/死信 |
| 访问控制 | 真实 401/403/404/200；学生本人且本班、教师本班；非法评分项 400 | 矩阵符合，拒绝未产生越权记录 |
| 日志/消息 | 固定字段代码核查及最终快照 218 行实际 JSON 日志；普通队列采样时为 0，死信实读 1 条 | 日志未见测试口令/模型凭据/作答；死信 payload 仅 taskId；普通消息格式另由序列化代码核对 |

### 7.5 依赖遗留与成员决定（暂不接受风险）

2026-11-04，成员 A 明确回复：**“暂不接受风险，保留活动 change”**。以下为本次复核提出的适用性依据和处置建议，**未获接受，不构成风险豁免**。已另立[独立依赖升级 change](../../openspec/changes/2026-11-04-upgrade-spring-security-baseline/proposal.md)，仅完成规划；整改、复查并获得实际评审后再判断能否收尾 Class08，本轮不直接实施框架升级。

- **两项 Spring 告警保留，不降低扫描阈值。** [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) 要求 SSE 视图片段及攻击者可影响他人流内容；本项目由 ConversationController 返回 StreamingResponseBody，自行编码 JSON SSE，未使用视图片段。 [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) 要求 XsltView 等视图渲染组合，本项目 REST 控制器及配置未使用这些路径。故建议按当前调用路径不适用处置，不能据此称依赖本身不受影响。
- Spring 官方将上述两项标为 low/medium，而 OSV/GitHub 为 critical；保留两种来源差异，OSV 仍退出 1。公开修复为 Spring 7.0.9，6.2.20 为商业支持版本，不直接把 Spring 7 塞入 Boot 3。引入相关视图、SSE 机制或升级依赖时，原适用性判断立即失效；长期对外部署前重新审查并安排独立 Boot 4 升级。
- **另外 5 项 Maven moderate**：amqp-client 三项涉及 JSON-RPC/配置加载，当前只使用受控 RabbitTemplate；commons-lang3 的 ClassUtils 递归、log4j-api 的 MapMessage 序列化未在本项目代码直接使用，当前日志为 Logback 自定义 JSON。建议登记待补丁升级和依赖变更复核，不把传递依赖的间接调用风险视为已证明不存在；修复版本分别为 amqp-client 5.36.1、commons-lang3 3.18.0、log4j-api 2.25.5/2.26.1。
- **npm 12 项**（1 low、11 moderate）在开发工具链；high/critical 为 0。保留报告并安排兼容升级，不执行 audit fix --force。
- **OWASP 本轮未重跑、未通过。** 其为可选补充；本轮采用已完整执行的 OSV 运行时依赖审计、npm audit 与 pip-audit，并明确工具范围。没有宣称 NVD 更新完成或扫描全部通过。
- 处置责任：升级实施者修复并复查依赖，另一成员独立复核；成员 A 本次未接受风险，不将技术测试通过等同于交付通过，不能勾选归档门槛。

### 7.6 逐文件技术结论

范围为原三次提交和本轮修正的具体文件。下列“可用”指代码/记录技术复核结果，不能代替成员签署；pom 的待修改项为依赖整改，风险接受方案未获批准。新增测试与最终检查结果见[独立验收](README.md#7-交接后的独立复现2026-11-04)。

| 文件 | 结论 | 核查依据 |
| --- | --- | --- |
| `.env.example` | 可用 | 真实独立 Compose 启动；参数传入两角色，worker 不初始化材料/Qdrant，发布返回启用 |
| `README.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `apitest/README.md` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/Dockerfile` | 可用 | JDK17 镜像实际构建成功；仅预取项目依赖，保留原打包与运行镜像 |
| `backend/config/checkstyle/checkstyle.xml` | 可用 | 实际执行规范检查；没有放宽权限、采信或漏洞阈值 |
| `backend/pom.xml` | 待修改 | 运行时依赖仍有 2 critical / 5 moderate；成员 A 未接受风险，需独立升级整改后复查 |
| `backend/src/main/java/com/campusclaw/CampusClawApplication.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/common/DependencyRejectedException.java` | 可用 | 模型非流式 4xx 永久拒绝分类；Embedding/Qdrant 与 SSE 原重试契约保留 |
| `backend/src/main/java/com/campusclaw/config/GradingProperties.java` | 可用 | 时间/倍数校验；抖动后不突破最大退避；边界有回归断言 |
| `backend/src/main/java/com/campusclaw/conversation/AgentPromptBuilder.java` | 可用 | 原提交仅删除未使用 import；既有业务回归通过 |
| `backend/src/main/java/com/campusclaw/gateway/GatewayRestClientFactory.java` | 可用 | 模型非流式 4xx 永久拒绝分类；Embedding/Qdrant 与 SSE 原重试契约保留 |
| `backend/src/main/java/com/campusclaw/gateway/OpenAiChatGateway.java` | 可用 | 模型非流式 4xx 永久拒绝分类；Embedding/Qdrant 与 SSE 原重试契约保留 |
| `backend/src/main/java/com/campusclaw/grading/AssignmentController.java` | 可用 | 核查方法/状态码和服务端授权；真实 201/202/401/403/404 矩阵通过 |
| `backend/src/main/java/com/campusclaw/grading/AssignmentService.java` | 可用 | 创建校验、班级范围、评分依据快照；列表使用批量关联查询 |
| `backend/src/main/java/com/campusclaw/grading/AssignmentView.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/CreateAssignmentRequest.java` | 可用 | 标题/题目/评分项约束；null 评分项按 400 拒绝 |
| `backend/src/main/java/com/campusclaw/grading/GradingBackoff.java` | 可用 | 时间/倍数校验；抖动后不突破最大退避；边界有回归断言 |
| `backend/src/main/java/com/campusclaw/grading/GradingLog.java` | 可用 | 固定日志字段与白名单；运行日志隐私核对，不输出原始业务内容 |
| `backend/src/main/java/com/campusclaw/grading/GradingMessage.java` | 可用 | 持久化拓扑；消息仅 taskId，死信实读样本核对 |
| `backend/src/main/java/com/campusclaw/grading/GradingModelService.java` | 可用 | 分项规则计分；缺失解释、空 verdict、未知字段及尾随 JSON 拒绝 |
| `backend/src/main/java/com/campusclaw/grading/GradingNonRetryableException.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/GradingOutboxPublisher.java` | 可用 | 发布确认与 returned 同时核对；不可路由不能误标 PUBLISHED |
| `backend/src/main/java/com/campusclaw/grading/GradingOutcome.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/GradingRecoveryScheduler.java` | 可用 | 回收预算覆盖两次网络尝试；小批量读取，只有成功转移才记恢复日志 |
| `backend/src/main/java/com/campusclaw/grading/GradingResultView.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/GradingRetryableException.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskCompletionService.java` | 可用 | 行锁和当前 attempts 隔离迟到执行；认领受最大次数限制，回收分批 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskController.java` | 可用 | 核查方法/状态码和服务端授权；真实 201/202/401/403/404 矩阵通过 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskQueryService.java` | 可用 | 本人及当前班级检查；教师列表数据库 LIMIT 200，并批量取关联数据 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskView.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/GradingWorker.java` | 可用 | 模型在事务外；提交结果/失败/重试后 ACK；不将任意数据库异常判成功 |
| `backend/src/main/java/com/campusclaw/grading/ModelGradingResponse.java` | 可用 | 分项规则计分；缺失解释、空 verdict、未知字段及尾随 JSON 拒绝 |
| `backend/src/main/java/com/campusclaw/grading/RabbitConfig.java` | 可用 | 持久化拓扑；消息仅 taskId，死信实读样本核对 |
| `backend/src/main/java/com/campusclaw/grading/RubricCodec.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/RubricCriterion.java` | 可用 | 标题/题目/评分项约束；null 评分项按 400 拒绝 |
| `backend/src/main/java/com/campusclaw/grading/SubmissionService.java` | 可用 | 短事务锁作业行后去重/分配版本；并发相同和不同内容均有 MySQL 集成断言 |
| `backend/src/main/java/com/campusclaw/grading/SubmitAssignmentResponse.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/SubmitRequest.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/grading/TeacherSubmissionView.java` | 可用 | 核对数据视图/异常/装配的本次差异；字段与调用方契约一致，无独立外部副作用 |
| `backend/src/main/java/com/campusclaw/mcp/McpToolView.java` | 可用 | 原提交仅删除未使用 import；既有业务回归通过 |
| `backend/src/main/java/com/campusclaw/observability/JsonLogEncoder.java` | 可用 | 固定日志字段与白名单；运行日志隐私核对，不输出原始业务内容 |
| `backend/src/main/java/com/campusclaw/observability/MdcTaskDecorator.java` | 可用 | 固定日志字段与白名单；运行日志隐私核对，不输出原始业务内容 |
| `backend/src/main/java/com/campusclaw/observability/RequestLoggingFilter.java` | 可用 | 固定日志字段与白名单；运行日志隐私核对，不输出原始业务内容 |
| `backend/src/main/java/com/campusclaw/persistence/Assignment.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/AssignmentRepository.java` | 可用 | 短事务锁作业行后去重/分配版本；并发相同和不同内容均有 MySQL 集成断言 |
| `backend/src/main/java/com/campusclaw/persistence/GradingEventType.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingOutbox.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingOutboxRepository.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingOutboxStatus.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingResult.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingResultRepository.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingTask.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/GradingTaskRepository.java` | 可用 | 行锁和当前 attempts 隔离迟到执行；认领受最大次数限制，回收分批 |
| `backend/src/main/java/com/campusclaw/persistence/GradingTaskStatus.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/Submission.java` | 可用 | 实体/枚举/仓储与 V8 字段和唯一键对应；未增加外部网络调用 |
| `backend/src/main/java/com/campusclaw/persistence/SubmissionRepository.java` | 可用 | 本人及当前班级检查；教师列表数据库 LIMIT 200，并批量取关联数据 |
| `backend/src/main/java/com/campusclaw/retrieval/RetrievalService.java` | 可用 | 原提交仅删除未使用 import；既有业务回归通过 |
| `backend/src/main/resources/application-worker.yml` | 可用 | 真实独立 Compose 启动；参数传入两角色，worker 不初始化材料/Qdrant，发布返回启用 |
| `backend/src/main/resources/application.yml` | 可用 | 真实独立 Compose 启动；参数传入两角色，worker 不初始化材料/Qdrant，发布返回启用 |
| `backend/src/main/resources/db/migration/V8__assignment_grading.sql` | 可用 | 未修改已交付迁移；新空库完整启动、唯一约束和事务回滚通过 |
| `backend/src/test/java/com/campusclaw/CampusClawIntegrationTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/gateway/GatewayRestClientFactoryTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingBackoffTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingModelServiceTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingOutboxPublisherTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingRecoverySchedulerTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingTaskCompletionServiceTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `backend/src/test/java/com/campusclaw/grading/GradingTaskQueryServiceTest.java` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `docker-compose.yml` | 可用 | 真实独立 Compose 启动；参数传入两角色，worker 不初始化材料/Qdrant，发布返回启用 |
| `docs/lesson-08/README.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/lesson-08/handoff.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/lesson-08/review.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/project-scope.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/project-status.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/standards/01-product.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `docs/standards/04-testing.md` | 可用 | 区分实现方与本次独立结果；保留未完成确认/归档状态，链接限定仓库内 |
| `frontend/eslint.config.js` | 可用 | 实际执行规范检查；没有放宽权限、采信或漏洞阈值 |
| `frontend/package-lock.json` | 可用 | npm ci 按锁文件安装；Vitest4.1.12 下 76 项通过，审计保留 12 项低/中危 |
| `frontend/package.json` | 可用 | npm ci 按锁文件安装；Vitest4.1.12 下 76 项通过，审计保留 12 项低/中危 |
| `frontend/src/components/AppShell.vue` | 可用 | 新增作业入口与原角色路由保持一致；真实页面访问及现有测试通过 |
| `frontend/src/router/index.js` | 可用 | 新增作业入口与原角色路由保持一致；真实页面访问及现有测试通过 |
| `frontend/src/services/assignments.js` | 可用 | 取消旧请求并校验选择版本；刷新恢复、终态停止及真实浏览器链路通过 |
| `frontend/src/views/AssignmentsView.test.js` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `frontend/src/views/AssignmentsView.vue` | 可用 | 取消旧请求并校验选择版本；刷新恢复、终态停止及真实浏览器链路通过 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/design.md` | 可用 | 现有契约矛盾已校正；只新增细化场景，未提前同步主规格或假勾成员确认 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md` | 可用 | 现有契约矛盾已校正；只新增细化场景，未提前同步主规格或假勾成员确认 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/specs/assignment-grading/spec.md` | 可用 | 现有契约矛盾已校正；只新增细化场景，未提前同步主规格或假勾成员确认 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/specs/reliable-task-execution/spec.md` | 可用 | 现有契约矛盾已校正；只新增细化场景，未提前同步主规格或假勾成员确认 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/tasks.md` | 可用 | 现有契约矛盾已校正；只新增细化场景，未提前同步主规格或假勾成员确认 |
| `ruff.toml` | 可用 | 实际执行规范检查；没有放宽权限、采信或漏洞阈值 |
| `scripts/lesson08_acceptance.py` | 可用 | 显式目标/配置参数；FAILED、202 含分数及重复后变更均不再误判通过 |
| `scripts/osv_scan.py` | 可用 | 103/103 查询；失败/空清单/未知严重性非零退出，原始告警不抑制 |
| `scripts/scan.py` | 可用 | 完整实跑；工具缺失的首次失败保留；OSV 告警导致整体退出 1 |
| `scripts/tests/test_lesson08_acceptance.py` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `scripts/tests/test_osv_scan.py` | 可用 | 核查断言区分正确/错误实现；最终检查计数见验收记录，不把旧报告当结果 |
| `docs/standards/05-operations.md` | 可用 | 同步任务日志白名单、评分配置与 worker 初始化边界，保留迁移/部署范围说明 |
| `openspec/config.yaml` | 可用 | 移除过期 Class07 状态；区分已实现异步批改和未通过交付门槛，规则及 apply 指引保留 |
| `openspec/changes/2026-11-03-add-async-assignment-grading/README.md` | 可用 | 修正“未实现”描述；保留活动状态，不提前同步或归档 |
| `openspec/changes/2026-11-04-upgrade-spring-security-baseline/.openspec.yaml` | 可用（规划） | 兼容迁移不改外部契约，明确 skip_specs，不虚构新业务需求 |
| `openspec/changes/2026-11-04-upgrade-spring-security-baseline/proposal.md` | 可用（规划） | 独立承载未接受风险的整改，保留 Class08 活动状态和现有修正 |
| `openspec/changes/2026-11-04-upgrade-spring-security-baseline/design.md` | 可用（规划） | 官方平台/迁移依据；明确 JSON、AMQP、旧数据兼容与逐项复扫边界，未执行升级 |
| `openspec/changes/2026-11-04-upgrade-spring-security-baseline/tasks.md` | 可用（规划） | 全部未勾选，按开发/测试整体分工，另一成员复核且不代签 |

### 7.7 当前合入门槛

- [x] 独立执行 unit、integration、API、真实模型核心判据、故障与页面检查，结果另行记录。
- [x] 并发/事务/权限/迟到执行/输入输出/配置/扫描可靠性的问题已修正并补回归。
- [x] 七维、逐文件、高风险及依赖适用性证据已整理，原始扫描非零结果保留。
- [ ] 成员 A 实际确认本次评审、规则文本和依赖处置；本次明确暂不接受风险，门槛未通过，代理未代签。
- [ ] 依赖整改、复查并通过评审后，才同步主规格、严格校验、归档、更新状态并提交；标签仍按单独授权执行。

**当前结论：功能技术复核及修正完成，依赖风险处置未获接受，Class08 保持活动、交付未完成。**
