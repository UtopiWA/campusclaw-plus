# Class08 异步作业批改验收记录

状态：**功能已提交至 `45b2f7a`，用户初验通过。本轮安全工具/文档未提交；另一成员复现暂缓，待本轮工作完成后交付。两个 change 保持活动，未同步归档，标签未授权。** 第 0～11 节保留各轮实际记录，第 12 节为最新安全自查；历史告警不覆盖最新复扫结论，代理不代签成员。

- 关联 change：[2026-11-03-add-async-assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)
- 能力规约：[assignment-grading](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/assignment-grading/spec.md)、[reliable-task-execution](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/reliable-task-execution/spec.md)、[security-precheck](../../openspec/changes/2026-11-03-add-async-assignment-grading/specs/security-precheck/spec.md)
- 评审记录：[review.md](review.md)
- 交接待办：[handoff.md](handoff.md)
- 测试/评审规范：[04-testing.md](../standards/04-testing.md)
- 项目状态：[project-status.md](../project-status.md)

## 0. 范围

教师创建带评分依据的文本作业；本班学生提交文本；Web 在同一事务保存作答、任务和发件箱并返回 202；独立 `grading-worker` 通过 RabbitMQ 消费，模型只做分项判断，服务端按评分依据确定性计分；学生和本班教师按权限查询结果。教师终评已按最新验收反馈加入，详见第 9 节；完整题库、知识图谱、学习计划和班级分析不在本次范围。

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


## 9. 作业批阅与交互修正（2026-11-04）

用户明确授权的验收反馈已合并到原异步作业 change；候选为 `e26089e` 加当前工作区。以下是本轮实际执行，前述第 8 节的计数与故障记录保留为历史；代理不代签另一成员。

### 9.1 最终行为

- 全站操作反馈由 AppFeedback 在顶部显示，成功 4 秒、错误 6 秒自动关闭，支持手动关闭与重复文案；移除常驻操作横幅。失败表单保留输入，材料处理与会话重试状态仍作为业务内容保留。
- 教师弹窗填写标题、详细题目、1～100 总分、1～5 个本班 READY 文本评分文件。保存文件来源/原文快照，模型在数据库事务外提取评分项，严格检查 JSON 字段/类型、唯一 key、整数分值和合计；失败不创建作业。
- 每人每份作业仅提交一次，相同键/内容重试返回原任务，不同内容 409；历史版本兼容保留，页面不展示。
- 教师表格依次为学生、作答、状态、得分和改分操作；终评弹窗显示原始作答、AI 分数/评语/逐项依据及终评分数/评语输入。学生提交后隐藏作答框，显示本人作答与批改结果；教师终评替换学生端 AI 展示，后台不会覆盖终评。
- V9 增量迁移新增文件快照和 submission_reviews，V1～V8 未改。批改提示词默认从 v1 升至 v3，应用/Compose/模板已同步；部署显式覆盖仍优先。原 AI 分项确定性计分规则不变。

### 9.2 执行与结果

| 实际执行 | 结果和边界 |
| --- | --- |
| `python scripts/check.py --mode unit` | 规约、前端、构建、后端 197、MCP 8、脚本 20 通过，无失败/错误/跳过；后续前端小改以最终独立命令为准 |
| `npm.cmd --prefix frontend run lint`、`test -- --run`、`run build` | 最终 13 个文件、81 项通过；构建保留原有大 chunk 提醒，未通过提高阈值掩盖 |
| `TESTCONTAINERS_API_VERSION=1.44` 下 `python scripts/check.py --mode integration` | 三个独立 MySQL/Qdrant 容器测试类，24 项通过，无失败/错误/跳过；空库含 V9，团队库实际从 V8 升至 V9 |
| `backend/target/peer08/tools/Scripts/python.exe scripts/scan.py` | ESLint、npm audit、Ruff、pip-audit、Checkstyle、SpotBugs、OSV 全部通过；OSV 124/124，三类依赖审计零告警；OWASP 未运行 |
| 最后 Java 变更后再次 `mvnw ... -DskipTests compile checkstyle:check spotbugs:check` | 通过；严格拒绝模型小数/字符串分值有新增回归。原始报告在既有忽略目录，不公开秘密 |
| 团队 Compose 构建、更新 Web/worker/frontend | 七服务运行、health 200、四个演示账号登录正常；DEMO_SEED_ENABLED=false，运行 promptVersion=v3；不使用健康代替模型验收 |
| 本机 Edge + 真实 HTTP/模型 | 教师上传评分文件→弹窗创建201→学生202/PENDING→AI成功→教师终评91→学生同页自动替换及刷新恢复通过；AI原始100分未变，学生改分403、越界400、错配/同班他人/跨班404、匿名401及二次提交409通过 |
| 页面与键盘 | 1440/1000/760/470/375 五种宽度，教师真实提交行、学生已交/未交、手机终评弹窗无页面溢出；Tab首尾圈定、Escape、焦点返回和提示自动关闭通过 |
| 查询与日志 | EXPLAIN：提交锁走PRIMARY，终评走submission唯一键，教师列表走作业前缀索引并Using filesort，LIMIT200及批量关联保留；小数据不能证明大班压测。实际日志未发现本轮已知作答/提示词/口令哨兵 |

本轮先后修正了验证中发现的三个实现问题：没有 AI/终评的分数表达式导致学生列表 500、弹窗遗漏现有表单样式、教师表格含真实行时的手机布局及隐藏标签溢出。最终复查通过。上传201、可空字段省略和同名文件的脚本定位断言也已校正；未把之前失败记录算成通过。

### 9.3 定向数据清理与人工复查

仅操作团队 `campusclaw-plus`，删除 `mcp-e606e2`、`mcp-40b73a` 两个合成服务及其 4 条测试审计；正常 MCP 删除接口的审计保护未改。无技能引用这两个名称，保留 campus-tool、数学技能/助手及正常历史。

删除 13 份已确认的种子/验收作业、10 份作答/任务、9 份 AI 结果、1 份测试终评、12 条发件箱事件、1 条仅对应已删除合成任务的死信，以及 3 份本轮重复上传的评分文件。FK 按依赖顺序处理，没有关闭外键；清理期间只停止团队 Web/worker，随后恢复最终镜像。原个人项目六个容器的 ID、名称和运行状态未变。

保留一份“勾股定理练习”和“勾股定理评分依据”：student-a1 已提交，有 AI 初评和教师终评；student-a2 尚未提交，可用于人工验收。访问 <http://localhost:18080/assignments>：

1. teacher-a 查看示例的学生、作答、状态、得分，打开“改分”，核对 AI 依据并保存终评。
2. student-a1 刷新或保持可见页面，确认只有终评分数/评语，且没有作答输入框。
3. student-a2 对示例点击“提交”，等待结果，刷新后仍不能再提交；或教师绑定保留的评分文件另建作业测试。

成员交叉评审、主规格同步、归档、提交/推送与标签均未由本轮自动办理；八脚本旧业务 API 套件、全套停启/DLQ故障和 OWASP 本轮未重跑，分别保留第 8 节历史证据与未执行边界。


## 10. 参考答案支持的 AI 综合评分（2026-11-05）

本轮按用户明确决定调整评分方式：参考答案只提供内容依据，不要求列评分项，AI综合给分。第9节及更早的评分项提取/规则计分为历史方案，不改写其测试记录。课程异步、权限、事务、重试、结果唯一与教师终评契约保持。

### 10.1 实现与兼容

- 删除创建时的评分项提取模型调用；创建只校验并保存本班 READY 参考答案快照和总分。文件仍为1～5个txt/md、合计20000 code points，创建/上传失败语义不变。
- 删除 MET/PARTIAL/NOT_MET 的固定计分。worker在事务外让AI根据题目、总分、参考答案及作答返回score/rationale，允许综合权衡、认可等价解法；服务端严格检查真实JSON整数、0～总分、非空且不超过2000 code points的评语、未知/缺失字段及尾随JSON。非法结果不截断/强转、不默认给分，沿用有限重试/死信。
- promptVersion默认v4，应用/Compose/模板对齐。无新表或迁移，沿用V9快照和独立终评；新结果criteria_json为空数组，历史分项保留读取。旧criteria创建API只用于兼容总分与参考描述，旧points不控制后台固定权重。
- 教师页面改称“参考答案文件”，展示AI分数及有依据的评语；有历史分项时仍可看“原评分明细”。旧成绩和教师终评不自动重算，终评仍优先。

### 10.2 本轮实际验证

| 命令/操作 | 结果与边界 |
| --- | --- |
| `python scripts/check.py --mode unit` | 后端207、前端82（13个文件）、MCP8、脚本20及规约/构建通过，无失败/错误/跳过；创建测试去掉未注入mock的空泛断言后另行复跑6项 |
| `TESTCONTAINERS_API_VERSION=1.44` 下 `python scripts/check.py --mode integration` | 24项通过，无失败/错误/跳过；纯答案创建不调用共享ChatGateway、快照不随材料变化、后台事务外得到87分并按原值落库、原权限/单次提交/终评边界通过 |
| 默认 `python scripts/scan.py`（使用已安装扫描工具的独立Python） | ESLint、npm audit、Ruff、pip-audit、Checkstyle、SpotBugs、OSV均通过；OSV124/124，三类依赖审计零告警；可选OWASP未运行 |
| 团队Compose build/up | 仅更新campusclaw-plus的Web、worker和frontend，运行v4且种子仍关闭；原六容器ID/名称/运行状态不变 |
| Edge真实HTTP/模型 | 只含答案、无评分项/分值的文件创建201；不完整作答实际20/100、完整作答实际95/100，两者均为模型实际输出而非预设值；criteria为空，评语有具体作答依据 |
| 终评/刷新/重复 | 教师将合成作答终评为92，学生同页更新及刷新恢复；AI原始分数/评语不变；相同提交/重复消息只保留一份原结果，不同内容409；桌面/手机弹窗、五种学生页面宽度通过 |
| 留存与清理 | 原有1份AI结果和1份终评的整行指纹未变；只清理本轮1份作业、2份作答/任务/AI结果、1份终评、2条发件箱和1份文件，没有扩大清理或重评旧数据 |
| 日志/隐私 | 实际Web/worker日志检查本轮已知参考/作答哨兵与配置秘密，未命中；原始敏感输出不提交 |

自动测试控制了87等精确输出来验证“不被规则重算”；真实模型输出20/95只是本轮观察，不要求每次复现完全相同。格式和范围校验不证明AI语义总是正确，教师终评保留。参考答案复用材料的同班可读权限，当前没有新增教师私有附件。八脚本旧API套件、全套停启/DLQ故障和OWASP本轮未重跑；本轮实际复验了重复消息及改动相关接口/页面。

### 10.3 人工验收

访问 <http://localhost:18080/assignments>，先在讲义页上传**只写参考答案、不列评分项**的txt/md，待READY后由teacher-a新建作业。student-a1或student-a2提交不完整/完整作答，查看AI分数和评语；教师改分后，学生应显示终评。请用新作业验证：原先已有成绩不会自动重新评分。

用户验收与另一成员复核仍待实际确认，两个change仍活动；本轮不提交、推送、同步主规格、归档或创建标签。


## 11. 初验后的文案与演示清理（2026-11-05）

用户确认初步功能检验无误，要求删除旧演示并精简页面说明；人员交叉评审仍待完成。第9节的“保留示例”是当时状态，当前以本节为准。

- 精确删除团队库旧“勾股定理练习”（id14）及1份作答、任务、AI结果、终评、发件箱；先核对无活动任务/待发布事件，保留私有恢复备份，FK按依赖顺序删除。其他作业、账号与参考材料未改。
- 删除侧栏隔离卡片、检索范围徽标、权限宣传卡和重复范围文字；登录及上传说明改为实际操作描述，助手配置不展示“确定性技能”等实现术语。当前班级/用户名和真实处理状态保留。
- 材料架构未改：上传请求同步保存文件与元数据，随后MaterialIngestionService用有界线程池异步解析/索引；数据库状态认领及启动/定时恢复保留。当前无需为统一形式迁到RabbitMQ；作业继续使用独立队列/worker。
- 实跑前端lint、13文件82项测试和生产构建通过；只更新团队frontend。Edge检查教师/学生的讲义、登录、助手配置与作业页、上传弹窗及1440/1000/760/470/375宽度通过，旧作业不可见、health200。
- 本轮无业务后端代码或依赖变更，未重跑后端/真实模型/全量扫描；沿用前两节对应版本证据。不代签另一成员，不提交或归档。

## 12. 安全自查与文档补齐（2026-11-06）

候选：功能提交 `45b2f7a` 加本轮工具和文档。用户初验已通过；另一成员复现按最新要求暂缓，待本轮完成后统一交付。本节为协作代理实测，不作为成员签署。

| 本轮实际执行 | 结果 |
| --- | --- |
| `python scripts/security_precheck.py --env-file .env.team` | `72756d460899`：授权、停启/并发/重复消息及真实计算器会话执行完成，合成资源清理完成；SEC-07 缺技能 ID 和 Token 用量，退出 1 |
| 同入口 `--checks access` | `7e596fa885e3`：加强数据库前后比较后，SEC-01～04 全通过、清理完成，退出 0 |
| `python scripts/check_security.py --env-file .env.team` | 密钥四范围 Gitleaks 无命中；精确配置比对有 1 个公开服务名同文候选，核查说明保留，入口退出 1；默认依赖/静态扫描全部完成并通过，OSV 124/124 零告警 |
| `python -m unittest discover -s scripts/tests`、Ruff | 33 项脚本测试及全脚本规范检查通过，覆盖脱敏、非零退出、缺范围、旧报告不能冒充本轮、清理保护与 access 模式边界 |
| 两项 `CampusClawIntegrationTest` | 并发不同答案仅接受一次、outbox 失败整体回滚；独立 MySQL 容器，2 项通过，0 失败/错误/跳过；命令见[并发报告](../../reports/risk-concurrency.md) |
| 临时七服务空库启动 | 复用当前镜像、全新独立网络/卷和随机合成凭据，迁移 V9、health 200、三个演示账号登录成功；临时项目与卷已清理，团队实例保留 |

六份固定报告及三类问题草稿已写入 [reports](../../reports/security-precheck.md)，包括授权/并发/事务、密钥依赖和待交付的同伴复核。用户已决定搁置 SEC-07 的技能 ID 和 Token 用量，不列入本次同伴复核、修复任务或归档前置条件；原始红项保留。SEC-05 的原始候选数及退出码不改成零。

本轮未重跑整个后端/前端套件、全部 API、DLQ/超时回收、OWASP 或选做 SEC-08，未同步主规格、归档、提交、推送或打标签。既有对应功能结果仍见第 8～11 节。最终成员复核与交付顺序见[交接](handoff.md)。

交接文档整理：已检查当前可入库的 102 份文档（含归档和新增文件），私有配置凭据精确比对、敏感格式/本机路径检查及仓库内链接检查均未发现问题。Docker 不可用，文档密钥扫描改用经官方发布校验和核验的 Gitleaks v8.24.3 便携版，退出 0、零命中。此次仅调整文档和复核范围，没有重跑业务测试或依赖审计；原始输出留在忽略目录。
