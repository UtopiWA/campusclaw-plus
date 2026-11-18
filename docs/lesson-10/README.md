# Class10 测试门禁、可复现交付与运行验证记录

最后更新：2026-11-18
候选工作区：`master` @ `b30ab9d` 加本轮未提交修改（镜像标签 `class10-b30ab9d-dirty`）
活动 change：[add-class10-test-gates-observability-and-delivery](../../openspec/changes/add-class10-test-gates-observability-and-delivery/proposal.md)
环境：Windows 11 + Docker Desktop 29.9.2；团队 `campusclaw-plus` 七服务；MySQL `campusclaw_plus`；宿主端口 18080/13306/15673；Java 17 目标（本机 JDK 21 构建）、Python 3.11.9 虚拟环境、Node 22.22.2；真实 Chat/Embedding 服务可用。

## 1. 基线与执行入口

| 检查 | 命令 | 本轮实际 |
| --- | --- | --- |
| 功能 unit | `python scripts/check.py --mode unit` | 退出 0；后端 256、前端 158（23 文件）、脚本 47、MCP 8 |
| 功能 integration | `TESTCONTAINERS_API_VERSION=1.44 python scripts/check.py --mode integration` | 退出 0；44 测试，0 failures/errors/skipped |
| 功能 api | `APITEST_ENV_FILE=.env.team python scripts/check.py --mode api` | 退出 0；首轮安全门禁内曾出现一次模型波动，同条件重跑通过 |
| 安全门禁 | `python scripts/security_gate.py --env-file .env.team --exceptions security-exceptions.json` | run `20261117T181025Z-73908`：五阶段全 **0**，总退出 **0**；旧无例外红记录保留，原始 5 high 仍保留 |
| 七服务 | `docker compose --env-file .env.team ps` | 6 个配置健康检查的服务 healthy；frontend/grading-worker Up 但未配置独立 healthcheck |
| 健康 | `GET /health` | 200，database/qdrant up；chat/embedding/mcp `not_checked` |

本机没有 GNU `make`，因此使用 Python 等价入口；`Makefile` 目标与 Python 入口保持同一命令。

## 2. 测试收口与四项教学行为

| 教学行为 | 本轮守护 | 执行入口 | 边界/缺口 |
| --- | --- | --- | --- |
| 解题引导 | 直接索要最终答案时，系统提示仍要求苏格拉底式引导，禁止最终答案/选项/完整过程 | `AgentPromptBuilderTest.directAnswerRequestStillGetsGuidedPromptWithoutAnswerInstructions` | 只验证提示词；模型实际是否遵守需真实模型评测 |
| 作业批改 | 迟到完成/失败不能替换终态；迟到 AI 结果与教师终评同时保留 | `GradingTaskCompletionServiceTest.terminalResultCannotBeReplacedByLateCompletion`、`GradingTaskQueryServiceTest.lateAiResultKeepsIndependentTeacherReview` | 未重新开放二次提交；使用替身模拟迟到执行 |
| 错题讲解 | 评语要求具体依据、错误/漏项和改进建议；参考文件不是固定评分表；服务端继续校验结构/长度/分数 | `GradingModelServiceTest.explanationPromptRequiresEvidenceAndImprovementWithoutFixedWeights` 等 | 不声称模型评语教学上一定正确 |
| 知识库 | 关键词/向量白名单、READY 回表、跨班过滤；四步演示和接口套件覆盖材料 404、检索空集合 | `RetrievalServiceTest`、`scripts/four_step_demo.py`、`apitest` | 跨班检索保持 200+空集合，不改成 404 |

其他场景沿用已有守护：规约 `openspec validate --all --strict`；前端测试/构建；MCP 8 项；脚本 47 项；容器集成 44 项；接口套件含 SQL 注入、XSS、CSRF 等价反例。没有以用例总数代替覆盖；没有用例的“错题讲解真实模型质量”明确列为缺口。

### 能力级追溯入口（非逐场景签字）

| 主规格能力 | 主要自动入口 | 本轮状态 |
| --- | --- | --- |
| agent-configuration | `AgentConfigurationControllerTest`、`AgentConfigurationServiceTest`、`SkillRouterTest`、`SkillTextParserTest`、`AgentConfigView.test.js`、`apitest/cases/api/skills`+`assistants` | 既有 + 本轮回归通过 |
| api-regression-harness | `apitest/run_all.py`、`scripts/api_preflight.py`、`check.py --mode api` | 通过（安全门禁内一次模型波动后重跑） |
| assignment-grading | `grading/*Test`、`CampusClawIntegrationTest`、`AssignmentsView.test.js`、`scripts/lesson08_acceptance.py` | 既有 + 本轮定向测试 |
| auth-upload | `SecurityConfigTest`、JWT/密码测试、`MaterialControllerTest`、`CampusClawIntegrationTest`、`security_precheck --checks access` | 通过 |
| conversation-streaming | conversation/retrieval 相关测试、`ConversationsView.test.js`、`apitest/.../messages` | 通过；模型质量另验 |
| knowledge-retrieval | `RetrievalServiceTest`、`AskServiceTest`、`QdrantIntegrationTest`、`SearchView.test.js`、`apitest/.../retrieval` | 通过；缓存不改变契约 |
| learning-diagnosis | `LearningPlanModelTest`、`LearningRulesTest`、`LearningView.test.js`、学习模块记录 | 既有；本轮未扩展 |
| mcp-tool-integration | `mcp/*Test`、`McpSchemaMigrationTest`、`apitest/.../mcp` | 通过 |
| question-bank | `QuestionBankView.test.js`、学习/评分集成测试、`scripts/lesson08_acceptance.py` | 既有；本轮未扩展 |
| reliable-task-execution | `Grading*Test`、`CampusClawIntegrationTest`、`risk-concurrency/transaction` | 既有 + 迟到完成测试 |
| request-observability | `JsonLogEncoderTest`、`RequestLoggingFilterTest`、`scripts/observe.py` | 通过 |
| role-home-dashboard | `HomeView.test.js`、首页记录 | 既有；本轮未扩展 |
| security-audit | `audit/*Test`、`security_precheck --checks audit`、`reports/audit-recheck.md` | 通过 |
| security-precheck | `scripts/security_precheck.py`、`scripts/tests/test_security_checks.py`、`reports/security-baseline.md` | secrets 绿；依赖红 |
| team-development-baseline | `scripts/check.py`、`Makefile`、`.github/workflows/`、脚本测试 | 功能绿；安全红 |
| ui-pagination | `PagedList.test.js`、各 view 分页测试 | 既有 |
| delivery-gates | `scripts/bench.py`、`observe.py`、`scripts/tests/test_bench.py`、`test_observe.py`、`reports/*` | 本轮新增并执行 |

该表是能力级入口，不等于每个 Scenario 已逐条签字；真实模型质量和依赖高危例外仍是缺口。

## 3. 双门禁与受控红绿

### 功能门禁

- 受控红：把 `GradingTaskCompletionServiceTest.java:48` 的期望从 `SUCCEEDED` 改为 `FAILED`，`check.py --mode unit` 在 backend-unit 失败，失败用例和 `256 tests, 1 failure` 记录在 `backend/target/class10-red/`，总退出 **1**。
- 复绿：从备份还原同一文件，同命令退出 **0**；backend 256、前端 158、脚本 47、MCP 8 全部通过。详见[门禁红绿](../../reports/gate-red-green.md)。

### 安全门禁

- 旧红记录：`python scripts/security_gate.py --env-file .env.team` 曾退出 2（scan 2、access 0、audit 0、api 1、unit 0）；scan 因 `npm audit` 5 个 high 停止。原始 summary 保留在 `backend/target/security-gate/20261117T174859Z-62904/summary.json`。
- 告警根因：开发工具链 `picomatch <= 4.0.7`（GHSA-fc5q-39xc-vjpf）及 `fdir/tinyglobby/vite/vitest` 传播；registry 无修复版本，降级到 vitest 3.1.4 实测仍解析到 picomatch 4.0.7。
- 用户确认：2026-11-18 选择“接受开发依赖例外”。新增 [security-exceptions.json](../../security-exceptions.json)，要求显式 `--exceptions`、批准人/日期、到期日、补偿措施，且每个 high/critical 包和公告都必须被覆盖；原始 audit JSON/失败码保留，输出标记 `ACCEPTED_EXCEPTION`，不写成 0 告警。
- 门禁命令：`python scripts/security_gate.py --env-file .env.team --exceptions security-exceptions.json`；run `20261117T181025Z-73908` 五阶段 scan/access/audit/api/unit 均为 **0**，总退出 **0**，summary 在 `backend/target/security-gate/20261117T181025Z-73908/summary.json`。`make check-security` 默认传入该文件。例外到期 2026-12-18，新增未覆盖公告会使 scan 失败。
- Python/Java 范围：Ruff 通过、pip-audit 无已知漏洞、Checkstyle 0 violations、SpotBugs 0 BugInstance、OSV 124/124 查询 0 漏洞；secrets 0 命中（32 提交、616 公开文件、前端 dist、七服务日志）。
- API 首次失败为模型/数据波动（流式正文与落库正文在引用标记外不一致），同条件重跑通过；失败日志保留。
- **结论：门禁在“经用户批准的开发依赖例外”下五阶段全 0；Class10 的“高危 0”字面目标仍未达到，5 个 high 原始告警保留。** 未降低 audit 级别、未排除 dev 依赖、未跳过扫描。详见[安全基线](../../reports/security-baseline.md)。

## 4. 容器、版本与启动

- 三个 Dockerfile 增加 `ARG GIT_COMMIT` 和 OCI `revision` 标签；Compose 使用 `IMAGE_TAG`/`GIT_COMMIT`，`APP_VERSION` 默认取镜像标签。
- 实际构建并启动标签 `class10-b30ab9d-dirty`；image ID、revision 和七服务状态见[启动与追溯](../../reports/boot-reproduce.md)。`/health.version` 与镜像标签一致。
- `docker compose build` 在本机 Compose v5 以 bake 退出 1；改用三个直接 `docker build` 后成功，Compose `--no-build` 启动。该本机现象已记录，不改 Dockerfile 契约。
- 构建/运行分离、配置外置、healthcheck 沿用现有结构；未在干净目录/另一机器独立启动，该人工项保持未完成。
- 工作区 dirty：镜像 revision 指向 HEAD，但包含未提交修改；没有把它写成干净提交制品。

## 5. CI 与制品追溯

- 新增 `.github/workflows/ci.yml`：托管运行器执行 unit/integration、构建三个镜像并记录 image ID/标签。
- 完整安全门禁在独立团队环境运行，保留五阶段检查和私有配置。
- 前两次远端 CI 失败在 Unit gate。修复后的远端结果待推送验证，见[流水线记录](../../reports/pipeline.md)。

## 6. 观测、告警与面板

- `scripts/observe.py` 从 `messages`、后端 `http_request_completed` 日志和 RabbitMQ management API 派生：三技能调用量分线、每技能错误率、API P95（含/不含 SSE）与队列深度。
- 三技能快照：[面板](../../reports/observe-dashboard.html)；三条合成技能各 1 次调用，均 COMPLETED；原始 JSON 在 `backend/target/observe/`。
- 告警实际触发：临时停止 Qdrant，制造 12 次 503；5 分钟窗口 202 请求、14 次 5xx（6.93%），规则“5xx 比例 > 1% 且请求数 ≥ 20”触发，实际 POST 到本地接收器并得到 HTTP 202；Qdrant 恢复后 `/health` 200。规则、时间、接收记录和面板见[观测告警](../../reports/observe-alert.md)。
- 这是派生轻量工具，不是 Prometheus/Grafana/Alertmanager 平台；没有新增应用指标接口；4xx 不进入 5xx 分子。

## 7. 压测与缓存改进

- `scripts/bench.py` 对提交、任务查询、检索分别多档位加压；每轮使用唯一合成讲义/作业，worker 保留并等待终态后清理。
- 向量缓存默认 256 条，设为 0 可关闭。原始压测每档请求数低于部分并发设置，饱和点和高并发收益待复测。
- 详细命令、副作用和未覆盖项见[压测记录](../../reports/bench.md)。

## 8. 独立复现和交叉验收

另一成员需独立启动、演示并记录交叉评审结论。三名用户反馈和手机验收已移出本轮范围。

## 9. 评审、风险与证据索引

- AI 自审：[review-05](../../reports/review-05.md)（本代理执行的命令、逐文件结论、七维检查；不是成员评审）。
- 既有累计评审：`review-01`～`review-04`；其中 `review-02`/`review-03` 为 AI/脚本来源，满足“至少三份、至少两份 AI/脚本”的数量要求，但不能替代本轮人工交叉评审。
- 高风险三查：`reports/risk-concurrency.md`、`risk-transaction.md`、`risk-authz.md`；本轮缓存/压测的并发与清理边界补记在 review-05。
- 固定证据：`reports/gate-red-green.md`、`boot-reproduce.md`、`pipeline.md`、`observe-alert.md`、`bench.md`、`review-05.md`；原始输出在忽略目录。
- 收尾交接：[handoff.md](handoff.md)（剩余工作、命令、例外和检查清单）。

## 10. 状态清单

| 项目 | 状态 |
| --- | --- |
| 四项教学行为各有检查 | 已完成（提示词/替身层，模型质量另验） |
| 规约场景→用例追溯 | 部分；第 2 节列出主要映射，真实模型评测仍待补 |
| `make check` / `make check-security` 入口 | 已完成；安全门禁在显式例外下五阶段全 0，原始 5 high 保留 |
| 功能门禁受控红→绿 | 已完成，退出 1→0 |
| 安全门禁高危 0 | **字面未达到**；5 high dev 依赖，用户 2026-11-18 确认有期限例外，门禁以 `--exceptions` 通过且不把告警记为 0 |
| 镜像提交标签与 health 版本 | 已完成（dirty 标签） |
| 干净目录/另一机器启动 | 未完成 |
| 托管 CI | 两次远端运行失败，待验证修复版 |
| 三技能面板 | 已完成 |
| 告警实际触发并送达 | 已完成（HTTP 202 本地接收器） |
| 三接口压测 | 已有小样本，实际并发与饱和点待复测 |
| 查询向量缓存 | 已实现，待同口径复测 |
| 另一成员独立启动/演示/交叉评审 | 未完成 |

结论：本轮完成了可自动化和可本地验证的工程交付与证据；安全门禁在用户确认的有期限开发依赖例外下五阶段全 0（run `20261117T181025Z-73908`），原始 5 个 high 保留且字面“高危 0”未达到。CI 修复后的远端通过记录、干净环境启动和成员交叉评审仍未完成；不得把本记录当作 Class10 全部验收通过。
