# Class10 测试门禁、可复现交付与运行验证记录

最后更新：2026-11-19
当前验收提交：`c8e8abf`。第 1 节保留原实现的本地结果，修复版的 CI 和镜像复现见第 4、5 节。
活动 change：[add-class10-test-gates-observability-and-delivery](../../openspec/changes/add-class10-test-gates-observability-and-delivery/proposal.md)
原验收环境：Windows 11 + Docker Desktop 29.9.2，候选为 `b30ab9d` 加未提交修改，使用团队七服务和真实 Chat/Embedding。

## 1. 基线与执行入口

| 检查 | 命令 | 本轮实际 |
| --- | --- | --- |
| 功能 unit | `python scripts/check.py --mode unit` | 退出 0；后端 256、前端 158（23 文件）、脚本 47、MCP 8 |
| 功能 integration | `TESTCONTAINERS_API_VERSION=1.44 python scripts/check.py --mode integration` | 退出 0；44 测试，0 failures/errors/skipped |
| 功能 api | `APITEST_ENV_FILE=.env.team python scripts/check.py --mode api` | 退出 0；首轮安全门禁内曾出现一次模型波动，同条件重跑通过 |
| 安全门禁 | `python scripts/security_gate.py --env-file .env.team --exceptions security-exceptions.json` | run `20261117T181025Z-73908`：五阶段全 **0**，总退出 **0**；旧无例外红记录保留，原始 5 high 仍保留 |
| 七服务 | `docker compose --env-file .env.team ps` | 5 个配置健康检查的服务 healthy，frontend 和 grading-worker 运行中 |
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
| team-development-baseline | `scripts/check.py`、`Makefile`、`.github/workflows/`、脚本测试 | 功能绿；完整安全门禁按已批准开发依赖例外运行 |
| ui-pagination | `PagedList.test.js`、各 view 分页测试 | 既有 |
| delivery-gates | `scripts/bench.py`、`observe.py`、`scripts/tests/test_bench.py`、`test_observe.py`、`reports/*` | 本轮新增并执行 |

### Class10 新增规约的场景与验证入口

| `delivery-gates` 场景 | 验证入口与结果 |
| --- | --- |
| 功能门禁通过 | `scripts/check.py --mode unit/integration/api`，原本地结果见第 1 节，修复版 unit/integration 由 CI #3 复测 |
| 安全门禁通过、缺少安全门禁环境 | `scripts/tests/test_security_gate.py`，并查 [安全基线](../../reports/security-baseline.md) 的完整及未完成运行 |
| 受控红绿 | 本地改坏测试期望、恢复并复测，详见 [门禁红绿](../../reports/gate-red-green.md) |
| 例外覆盖、未覆盖或过期 | `scripts/tests/test_security_exceptions.py` 覆盖正常、无例外匹配、过期和未批准 |
| 推送触发、手动导出镜像包 | [CI #3](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44282282173) 通过；镜像包本地已导出并导入，远端可选上传步骤尚未运行 |
| 版本可反查 | [启动与追溯](../../reports/boot-reproduce.md) 记录镜像 revision 与健康版本 |
| 四项教学行为 | 对照本节第一张表；提示词和替身测试覆盖已有行为，真实模型输出质量另验 |
| 分档压测、同口径复测、清理边界 | `scripts/tests/test_bench.py` 覆盖在途峰值、请求不足和清理故障；运行数据看 [压测记录](../../reports/bench.md) |
| 三技能错误率、API P95、队列、告警送达 | `scripts/tests/test_observe.py` 与 [观测告警记录](../../reports/observe-alert.md)，包括实际 HTTP 202 送达 |

上表覆盖 Class10 的新增场景，已有主规格仍按能力列出入口。

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
- 修复版 `c8e8abf` 已在干净副本和独立数据卷启动七服务。用本地合成模型验证登录、异步批改、跨班隔离和版本反查，见[干净目录复现](../../reports/boot-reproduce.md#5-修复提交的干净目录复现)。

## 5. CI 与制品追溯

- 新增 `.github/workflows/ci.yml`：托管运行器执行 unit/integration、构建三个镜像并记录 image ID/标签。
- 完整安全门禁在独立团队环境运行，保留五阶段检查和私有配置。
- `c8e8abf` 的 CI #3 已通过单元、集成和三个镜像构建。主干尚未配置必需检查，见[流水线记录](../../reports/pipeline.md)。

## 6. 观测、告警与面板

- `scripts/observe.py` 从 `messages`、后端 `http_request_completed` 日志和 RabbitMQ management API 派生：三技能调用量分线、每技能错误率、API P95（含/不含 SSE）与队列深度。
- 三技能快照：[面板](../../reports/observe-dashboard.html)；三条合成技能各 1 次调用，均 COMPLETED；原始 JSON 在 `backend/target/observe/`。
- 告警实际触发：临时停止 Qdrant，制造 12 次 503；5 分钟窗口 202 请求、14 次 5xx（6.93%），规则“5xx 比例 > 1% 且请求数 ≥ 20”触发，实际 POST 到本地接收器并得到 HTTP 202；Qdrant 恢复后 `/health` 200。规则、时间、接收记录和面板见[观测告警](../../reports/observe-alert.md)。
- 这是派生轻量工具，不是 Prometheus/Grafana/Alertmanager 平台；没有新增应用指标接口；4xx 不进入 5xx 分子。

## 7. 压测与缓存改进

- `scripts/bench.py` 对提交、任务查询、检索分别多档位加压；每轮使用唯一合成讲义/作业，worker 保留并等待终态后清理。
- 受控复测的实际并发为 1/2/4。缓存从 0 调到 256 后，Embedding 调用从 25 次降到 1 次，两轮各 24 个批改任务成功。真实环境饱和点仍待验证。
- 详细命令、副作用和未覆盖项见[压测记录](../../reports/bench.md)。

## 8. 独立复现和交叉验收

2026-11-19，用户确认另一成员已审核完毕，任务 7.3 据此结项。实际复现和评审明细由执行成员留存。三名用户反馈和手机验收已移出本轮范围。

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
| 规约场景→用例追溯 | 第 2 节列出 Class10 新增场景与验证入口，真实模型评测仍待补 |
| `make check` / `make check-security` 入口 | 已完成；安全门禁在显式例外下五阶段全 0，原始 5 high 保留 |
| 功能门禁受控红→绿 | 已完成，退出 1→0 |
| 安全门禁高危 0 | **字面未达到**；5 high dev 依赖，用户 2026-11-18 确认有期限例外，门禁以 `--exceptions` 通过且不把告警记为 0 |
| 镜像提交标签与 health 版本 | 干净提交 `c8e8abf` 验证通过 |
| 干净目录/另一机器启动 | 干净目录与独立卷复现通过，合成模型 |
| 托管 CI | 第 3 次远端运行通过；主干必需检查待配置 |
| 三技能面板 | 已完成 |
| 告警实际触发并送达 | 已完成（HTTP 202 本地接收器） |
| 三接口压测 | 受控并发 1/2/4 通过，真实环境饱和点待测 |
| 查询向量缓存 | 同口径复测通过，见压测记录 |
| 另一成员独立启动、演示和交叉评审 | 已按用户确认关闭任务 7.3 |

远端 CI 和干净目录复现已通过，成员评审已确认完成。主干必需检查和实际供应商环境性能复测仍待收尾。安全门禁仍执行第 3 节记录的有期限依赖例外。
