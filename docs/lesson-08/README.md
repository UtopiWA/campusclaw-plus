# Class08 异步作业批改验收记录

状态：**依赖升级与独立团队 Docker 部署已完成技术验证，等待用户人工验收和另一成员复核；两个 change 保持活动，未同步归档、未提交，标签未授权。** 第 0～6 节为实现方记录，第 7 节为升级前独立复核，第 8 节为本轮升级及部署结果，历史告警不覆盖最新复扫结论。

- 关联 change：[2026-11-03-add-async-assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)
- 能力规约：[assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/assignment-grading/spec.md)、[reliable-task-execution](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/reliable-task-execution/spec.md)
- 评审记录：[review.md](review.md)
- 交接待办：[handoff.md](handoff.md)
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

## 7. 交接后的独立复现（2026-11-04）

接手基线为 `5cdd020`，实际测试对象为该基线加本轮复核修正的源码/镜像。以下结果由接收方代理在独立环境实际执行，不复用实现方的通过结论，也不代替成员 A 的最终确认。问题及逐文件结论见 [review.md 第 7 节](review.md#7-交接后的独立技术复核2026-11-04)。

### 7.1 环境与自动检查

独立 Compose 项目 `cc08-peer`，新建 MySQL 8.4、Qdrant 1.15.4、RabbitMQ 4.4.0 卷及合成口令；不连接现有应用数据库。Web/worker 使用 Dockerfile 的 Java 17 镜像；宿主 Java 20.0.2、Node 22.21.1、Python 3.11.2，Docker Engine 29.8.2 / Compose 5.6.0。模型为私下配置的真实服务。前端先 npm ci，使用锁文件中的 Vitest 4.1.12；部分开发依赖有 Node 补丁版本 engines 警告，未忽略记录。

| 实际执行 | 本次结果 |
| --- | --- |
| `python scripts/check.py --mode unit` | 最终退出 0：前端 12 文件/76 项及构建、后端 184 项、MCP 8 项、脚本 18 项；无失败/错误/跳过 |
| `python scripts/check.py --mode integration` | 最终退出 0，22 项；本机 Docker 客户端适配通过测试专用 `TESTCONTAINERS_API_VERSION=1.44` 指定，不影响应用配置 |
| `python scripts/check.py --mode api` | 退出 0；独立 READY 材料、B 班教师和受控账号；8 个业务脚本及 4 项清理通过 |
| 独立镜像构建/启动 | 通过；首次暴露 worker 与 Web 同时建 Qdrant collection 的竞态，修正后另用 `cc08-cold` 的全新卷同时启动成功并清理 |
| `python scripts/scan.py`（临时虚拟环境） | 完整执行退出 1：规范/静态分析、pip-audit、npm high 阈值检查通过；OSV 103/103 查询完整，2 critical + 5 moderate 保留 |
| 最后小批量恢复及脚本修正 | Java Checkstyle/SpotBugs 与 Python Ruff 均退出 0；最终真实恢复复验 SUCCEEDED、attempts=2 |

首次 scan 因宿主缺 Ruff 失败，后在忽略目录内创建临时虚拟环境，安装 Ruff 0.17.3 / pip-audit 2.10.1 后重跑。未执行 OWASP/NVD 补充检查，未运行单独的 `--mode all`（unit/integration/api 已分别执行）；不宣称扫描全部通过。V7→V8 旧库/旧应用兼容性沿用实现方已有记录，本次独立确认了空库迁移和新版本现有业务回归，没有把沿用项标为本次独立通过。

### 7.2 三条核心判据与故障

| 判据/场景 | 本次实际结果 |
| --- | --- |
| 请求不等待 | 三次提交 90.1 / 60.8 / 28.8 ms，后台 2813 / 1756 / 1821 ms；均 202、PENDING、不含分数，最终 SUCCEEDED。以这 3 个样本的最近秩口径，提交 P95=90.1 ms，显著小于后台耗时；不是压力测试 |
| 停消费者不丢任务 | 停 `grading-worker` 后新提交保持 PENDING，队列 ready≥1、consumers=0；恢复后 SUCCEEDED，无须重新提交 |
| 重复不产生重复结果 | 已完成任务重投后结果行数 1，结果 ID/分数/创建时间及任务视图不变；相同内容重提返回同一 task |
| 不可重试失败/DLQ | 自有合成作业评分依据临时设为不合法结构，任务 FAILED / ASSIGNMENT_INVALID，attempts=1，DLQ 实读 1 条；随后恢复原评分依据 |
| 模拟 RUNNING 回收 | 新建自有任务设为 5 分钟前 RUNNING、attempts=1，恢复后真实模型完成 SUCCEEDED、attempts=2；最终分批恢复修正另做聚焦复验 |
| 并发与事务 | 实际 MySQL 两线程验证相同内容同 task、不同内容版本递增；发件箱保存异常后提交/任务均回滚；旧 attempt 的成功/失败/重试回写被拒 |
| 权限 | 学生创建 403、跨班作业/任务 404、匿名 401、学生提交列表 403、教师 200；null 评分项 400；调班后的任务访问由专门单测拒绝 |

不同内容/不同任务的模型分项判断可能不同；本次只验证同一结果不被重投覆盖，不宣称模型判断完全确定或等同教师最终成绩。

复现核心流程使用已维护入口，参数可指向新的独立环境：

```powershell
python scripts/lesson08_acceptance.py basic
docker compose stop grading-worker
python scripts/lesson08_acceptance.py pending
$taskId = Read-Host '输入上一步输出的 taskId'
docker compose start grading-worker
python scripts/lesson08_acceptance.py wait --task-id $taskId
python scripts/lesson08_acceptance.py duplicate --task-id $taskId
```

本次环境使用 `--env-file backend/target/peer08/private.env --base-url http://127.0.0.1:18080 --management-url http://127.0.0.1:15678`；临时配置已清理且不提交，其他成员按 README 准备自己的配置。故障复现仅对新建合成任务：停 worker 后将该任务设为旧 RUNNING 再恢复；DLQ 用合成作业暂时不合法的 rubric 触发，并在观察失败后还原 rubric。权限操作使用上述具体方法/身份/结果，不能把不存在的接口返回 404 当作鉴权通过。

### 7.3 页面、查询与隐私

本机 Edge 无头实例在独立浏览器上下文中真实执行教师建作业、学生提交、202、刷新恢复、轮询终止和教师查看；刷新没有重新提交。1440/1000/760/470/375 五个宽度均无横向溢出，实际截图只保留在忽略目录。成员原有浏览器资料、口令均未使用。

EXPLAIN：任务读取走主键；发件箱走 `idx_grading_outbox_status_available`，教师查询走提交版本索引；两者仍可能 filesort。恢复通过状态索引过滤并限制批次。这里是小规模合成集验证，LIMIT 限制返回量，不代表数据库检查行数恒为该上限，也未完成生产压测。

最终候选的日志隐私快照共 218 行合法 JSON，字段均在允许集合，未出现测试口令、模型凭据或已知作答文本。普通队列采样时为空，死信实读 1 条，仅 `taskId`；普通消息的字段边界同时静态核对 `GradingMessage` 和发布端序列化，没有冒称对空队列获得了正文样本。

### 7.4 失败记录与收尾状态

- 保留曾失败的检查：缺 Ruff；网关永久拒绝分类最初误改 Embedding 重试次数（已缩回模型非流式范围并通过旧回归）；首次 Web/worker 初始化竞态；队列统计未收敛；故障夹具未恢复导致页面 500；后端重建后 Nginx 旧解析导致 502。修正或清理后重跑对应检查，不把失败输出删掉冒充从未发生。
- OSV 两项 Spring 告警及 5 项 moderate、npm 的 1 low/11 moderate、OWASP 未运行均保留。成员 A 已明确暂不接受风险，依据与决定见 review 7.5；独立升级 change 仅完成规划，未降低阈值、未实施依赖升级。
- 当前仅技术复核完成；成员 A 未接受风险，主规格同步、归档、提交/推送暂停，标签未授权。`cc08-peer`/`cc08-cold` 的本轮自建容器和卷、临时凭据副本已清理，原始 `.env` 和其他数据未修改。

## 8. 依赖升级与独立团队 Docker 部署（2026-11-04）

用户本轮明确授权按[独立升级方案](../../openspec/changes/2026-11-04-upgrade-spring-security-baseline/proposal.md)实施，并以本机 Docker 结果为团队部署基线。输入为 `d5167f30b4378b63bf8e762763f0233bd0c8ec4d` 的干净工作区，已含前轮审查修正；实际验证对象为该提交加本轮工作区改动。执行及整理人为协作代理，不代签另一成员结论。

### 8.1 运行环境与隔离

团队项目 `campusclaw-plus` 保留七个服务运行：frontend、backend、grading-worker、mysql、qdrant、rabbitmq、mcp-demo。后端与 worker 共用一个镜像，使用独立网络与四个具名卷。原有六个容器的 ID、镜像、挂载和启停状态均与运行前快照一致，没有改动其数据库或凭据。

私有 `.env.team` 单独生成数据库、JWT、MCP、RabbitMQ 和演示账号凭据，仅私下复用模型服务配置；原 `.env` 不变。网页/MySQL/RabbitMQ 管理端口为 18080/13306/15673，均绑定 127.0.0.1。演示用户为 `teacher-a/student-a1/student-b1`，口令从私有配置读取；另为接口验收新建合成 `teacher-b`，独立生成 BCrypt，不读取或复制原用户哈希。

Docker Desktop 数据盘已核验在 D 盘，不迁移、不重启整个引擎。容器使用 Java 17、Node 22.23.2；宿主沿用 Java 20.0.2、Node 22.21.1、Python 3.11.2，宿主 Node 对部分开发依赖有 engines 警告。部署和前端镜像构建以实际 Docker 结果为准。

### 8.2 实际依赖与扫描

| 范围 | 实际版本/结果 |
| --- | --- |
| 后端平台 | Boot 4.0.9、Framework 7.0.10、Tomcat 11.0.27、Jackson 3.1.7；应用代码不使用 Jackson 2 自动配置兼容模块 |
| 配套依赖 | amqp-client 5.37.0、springdoc 3.0.3、commons-lang3 3.19.0、log4j-api 2.25.5；以 effective POM 和依赖树核对 |
| 第三方 JSON | springdoc 的 Swagger 依赖仍传递引入 Jackson 2.21.7，应用 mapper 已迁至 Jackson 3；完整 OSV 扫描覆盖该传递依赖 |
| 前端补丁 | KaTeX 0.18.2；ESLint 10/Vue 插件及测试工具兼容更新；glob/minimatch 固定到修复范围，实际 lint/测试/构建通过 |
| Maven OSV | **124/124 查询完成、0 告警、退出 0**，原七项告警消除；无 suppression 或阈值降低 |
| npm / Python | npm 全量 audit 为 0 告警；pip-audit 无已知告警 |
| 完整 `scripts/scan.py` | ESLint、npm audit、Ruff、pip-audit、Checkstyle、SpotBugs、OSV 全部实际执行，**退出 0** |

保留两项实施失败及处理：方案中的 amqp-client 5.36.1 未在 Central 发布，改用已发布的 5.37.0 并完整复扫；npm 10 干净解析依赖时出现 `edgesOut` 内部异常，使用临时 Node 容器中的 npm 12.2.1 生成锁文件后，本机与 Docker 的 `npm ci` 均成功。未使用 force/legacy-peer-deps，不将首次失败记为通过。OWASP/NVD 补充扫描本轮未运行，不能称已完成。

### 8.3 实际功能与兼容验证

| 实际命令/操作 | 结果 |
| --- | --- |
| `python scripts/check.py --mode unit` | 后端 184、前端 76、MCP 8、脚本 18 项及前端构建通过；随后增加配置隔离测试，脚本独立复查为 **20 项通过** |
| `TESTCONTAINERS_API_VERSION=1.44` 下执行 `--mode integration` | 三个独立容器类，**22 项，无失败/错误/跳过**；没有改连团队或个人应用库 |
| 选择 `.env.team` 执行 `--mode api` | 8 个业务脚本及 4 项清理通过；合成 READY 材料、教师/学生、MCP、工具快照、配置与 SSE 实际执行 |
| 真实模型批改 | 三次提交 82.3/36.8/36.5 ms，后台 2345/2364/2160 ms；202、PENDING、无分数，最终 SUCCEEDED。样本数 3，非压测 |
| 重复、暂停和故障 | 重复消息结果一份且不变；相同内容复用原 task；停 worker 后 PENDING，恢复自动完成；非重试失败进 DLQ；模拟旧 RUNNING 恢复为 SUCCEEDED、attempts=2 |
| 权限与校验 | 学生创建 403、跨班 404、匿名 401、学生教师列表 403、教师 200、null 评分项 400 |
| 旧数据升级 | 用 Boot 3 专用镜像先在团队新库生成真实批改结果，再启动 Boot 4，完整任务视图与评分结果不变；迁移校验与旧记录可读 |
| 回退可读性 | 临时 Boot 3 实例读取 Boot 4 保存的会话、工具快照与成绩，响应相同；只读检查后已移除该实例及其临时凭据副本，主实例一直使用 Boot 4 |
| 空库冷启动 | 独立临时全新卷中 Web/worker 同时启动成功，随后只清理该冷启动项目；团队验收卷保留 |
| 浏览器 | 本机 Edge 实际教师建作业、学生 202、刷新恢复且不重发、终态轮询停止、教师结果；1440/1000/760/470/375 无横向溢出 |
| 计算器链路 | 新建 `campus-tool`、数学计算技能和数学学习助手；“计算2的20次幂”实际调用 calculator，得到 1048576，模型 SSE 完成并持久化工具快照，保留供人工操作 |
| 最终隐私快照 | 566 行实际 JSON 日志仅含允许字段，未出现本轮口令、模型凭据或已知作答；普通队列为空，死信实读 1 条且 payload 仅 taskId，不声称从空队列取得正文样本 |

正式维护入口也已实际执行：`python scripts/lesson08_acceptance.py basic --env-file .env.team` 自动读取三个端口，最终 SUCCEEDED；`APITEST_ENV_FILE=.env.team` 下完整 API 检查通过，不依赖忽略目录中的辅助脚本。

### 8.4 人工验收与资源

在仓库根目录使用 `docker compose --env-file .env.team ps` 查看团队实例。访问 <http://localhost:18080>，以教师创建带评分依据的文本作业，再用学生提交、刷新及查看分项结果；教师重新选择作业可查看提交列表。新建“数学学习助手”会话输入“计算2的20次幂”，应出现计算器调用及结果。演示种子的旧“示例作业”没有评分依据，应使用新创建的文本作业验收。

停/恢复消费者均加同一配置文件：`docker compose --env-file .env.team stop grading-worker` / `start grading-worker`。队列管理 <http://127.0.0.1:15673>，账号来自私有配置。一个 FAILED 和一条死信是本轮合成故障验收的预期记录，普通队列已清空，不表示当前服务不可用。

磁盘快照：七个容器写层约 2.1 MiB，四个持久卷实际分配约 214 MiB（含稀疏文件的逻辑长度约 645 MiB）；六个不同镜像逻辑大小合计约 1.59 GiB，共享层不能视为本轮新增物理占用。D 盘整个 Docker 数据盘约 13.53 GiB，包含旧项目、镜像和构建缓存，不能归为团队实例独占。数据、日志和后续构建会增长；未对其他项目或全局缓存执行清理。

原始测试报告、截图和基线镜像材料留在既有忽略位置。私有 `.env.team` 保留供用户验收，不提交；临时冷启动/兼容实例已清理，未修改原 `.env`。尚待另一成员按 Docker 基线独立复现和实际确认；未执行提交、推送、主规格同步、归档或标签。
