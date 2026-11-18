# Class10 收尾交接

交接对象：另一位团队成员
交接日期：2026-11-18
原始实现提交：`659bdf6`，交接提交：`35bd6c2`。CI 修复结果见[流水线记录](../../reports/pipeline.md)。
Class10 记录：[README](README.md)
活动 change：[add-class10-test-gates-observability-and-delivery](../../openspec/changes/add-class10-test-gates-observability-and-delivery/proposal.md)

## 0. 先看结论

- 已提交并推送 `659bdf6`：功能门禁、镜像追溯配置、CI 配置、压测/缓存、观测/告警、Class10 证据、AI 自审和活动 OpenSpec change。
- 原实现本地功能门禁通过：后端 **256**、前端 **158**、脚本 **53**、MCP **8**、容器集成 **44**、真实接口通过。修复版检查另见流水线记录。
- 安全门禁在用户确认的有期限开发依赖例外下绿：run `20261117T181025Z-73908`，scan/access/audit/api/unit 全 **0**。**这不是“高危 0”**：`npm audit` 仍有 5 个 high，例外 `security-exceptions.json` 到期 **2026-12-18**。
- 未完成：CI 修复后的远端通过记录、干净目录/另一机器启动、另一成员独立复现/演示、人工交叉评审、场景级追溯、真实模型行为评测、性能 worker 侧口径、就绪/存活与 RPO/RTO 实际恢复、五动作预演。

## 1. 当前提交与环境

| 项目 | 实际值 |
| --- | --- |
| 原实现基线 | `659bdf6` |
| 本地环境 | Windows 11 + Docker Desktop 29.9.2；团队 `campusclaw-plus`；MySQL `campusclaw_plus`；宿主端口 18080/13306/15673 |
| Java | 17 目标；本机用 JDK 21 构建，Testcontainers 需 `TESTCONTAINERS_API_VERSION=1.44` |
| Python | `.venv-review` 3.11.9，已安装 apitest 依赖 |
| Node | 22.22.2 |
| 运行镜像标签 | `class10-b30ab9d-dirty`；OCI revision 为 `b30ab9d`，**构建时工作区有未提交修改** |
| 健康 | `/health` 200，database/qdrant up；chat/embedding/mcp 为 `not_checked` |

接手后用包含 CI 修复的候选提交重建干净镜像，核对 revision 并记录 image ID。

## 2. 接手前置

```powershell
git fetch origin
git checkout master
git pull --ff-only
git rev-parse --short HEAD
git status --short
```

使用包含 CI 修复的候选提交，确认构建目录无未提交修改。

环境要求：

- Docker Desktop 正常运行，`docker info` 成功。
- 自己准备私有 `.env.team`，不要向作者索取或复用密钥，不提交到 Git。
- 保持项目名 `campusclaw-plus`、库名 `campusclaw_plus`、端口 18080/13306/15673；冲突时改自己的配置并记录。
- Java 17、Python 3.11+、Node 22；安装 `apitest/requirements.txt` 中的 Python 依赖。完整接口验收需要 Chat/Embedding 服务。
- 完整安全门禁另需 Gitleaks 镜像、OSV 网络和私有配置；请在独立团队环境执行并核对候选镜像的 revision。

```powershell
docker compose --env-file .env.team ps
curl.exe -f http://localhost:18080/health
```

预期：MySQL/Qdrant/RabbitMQ/mcp-demo/backend 显示 healthy；frontend 与 grading-worker 只显示 Up，因为未配置独立 healthcheck。`/health` 200 只代表数据库/Qdrant 就绪，不代表模型/MCP 可用。

### 重建干净镜像（建议）

```powershell
$commit = git rev-parse HEAD
$tag = "class10-" + (git rev-parse --short HEAD)
docker build --build-arg GIT_COMMIT=$commit -t "campusclaw-plus-backend:$tag" ./backend
docker build --build-arg GIT_COMMIT=$commit -t "campusclaw-plus-frontend:$tag" ./frontend
docker build --build-arg GIT_COMMIT=$commit -t "campusclaw-plus-mcp-demo:$tag" ./mcp-server
$env:IMAGE_TAG = $tag
docker compose --env-file .env.team up -d --no-build --wait --wait-timeout 300
```

- 本机 `docker compose build` 曾因 Compose v5 bake 退出 1；直接 `docker build` 成功，沿用该方式并在记录中写明。
- 重建 backend 后若前端 502，执行 `docker compose --env-file .env.team restart frontend`。
- 核对 `docker image inspect` 的 `org.opencontainers.image.revision` 与 `/health.version`。

## 3. 已完成并已提交

| 工作 | 证据 |
| --- | --- |
| 四项教学行为聚焦测试 | `AgentPromptBuilderTest`、`GradingTaskCompletionServiceTest`、`GradingTaskQueryServiceTest`、`GradingModelServiceTest`、`RetrievalServiceTest` |
| 功能门禁受控红→绿 | [gate-red-green.md](../../reports/gate-red-green.md)；退出 1→0，日志在 `backend/target/class10-red/` |
| 安全门禁五阶段 0（用户确认例外） | run `20261117T181025Z-73908`；`backend/target/security-gate/20261117T181025Z-73908/summary.json` |
| 开发依赖例外 | `security-exceptions.json`、`scripts/security_exceptions.py`、`scripts/tests/test_security_exceptions.py`；到期 2026-12-18 |
| 镜像 revision/health 版本 | [boot-reproduce.md](../../reports/boot-reproduce.md) |
| CI 配置 | `.github/workflows/ci.yml`；[pipeline.md](../../reports/pipeline.md) |
| 三接口压测 + 查询向量缓存 | [bench.md](../../reports/bench.md)；原始 JSON 在 `backend/target/bench/` |
| 三技能面板 + 告警送达 | [observe-alert.md](../../reports/observe-alert.md)；HTML 快照在 `reports/` |
| Class10 记录与 AI 自审 | [README](README.md)、[review-05.md](../../reports/review-05.md) |
| 活动 OpenSpec change | 已通过 `openspec validate --all --strict`；未同步主规格、未归档 |

### 安全例外要点

- `npm audit` 原始 5 个 high：`fdir/picomatch/tinyglobby/vite/vitest`，根因 `GHSA-fc5q-39xc-vjpf`（`picomatch <= 4.0.7`）。
- 当前 registry 无修复版本；实测 vitest 3.1.4 仍解析到 picomatch 4.0.7。
- 用户 2026-11-18 明确选择接受**开发依赖例外**；原始 audit JSON/失败码保留，日志标记 `ACCEPTED_EXCEPTION`，不能写成 0 告警。
- 例外到期 **2026-12-18**；到期前复查，上游有修复版本时升级/替换。新增未覆盖公告会让 scan 失败。

## 4. 剩余工作

### A. 必须补做的验收（对应 tasks.md 未勾选）

| # | 任务 | 做法与判据 |
| --- | --- | --- |
| 1 | 3.4 干净目录/另一机器启动 | 使用修复版提交、独立工作副本、私有配置和独立卷/端口。记录命令、提交、健康结果及镜像标签。 |
| 2 | 3.5 实际 CI 运行与镜像追溯 | 推送修复提交后核对单元、集成和三镜像构建，记录 run ID、提交和镜像标识。完整安全门禁在独立团队环境执行，检查主干必需检查设置。 |
| 3 | 7.3 另一成员独立复现与交叉评审 | 自己启动、演示、复测至少两条关键路径；按“可用/待修改/风险无效”逐文件结论、七维检查、并发/事务/权限三查；不得复制作者结论或代签。 |

### B. 需要补强/确认的部分完成项

| # | 事项 | 现状与建议 |
| --- | --- | --- |
| 1 | 规约场景逐条追溯 | 当前是能力级入口，不是每个 Scenario 签字；按主规格逐条补“场景→测试/接口用例”矩阵，缺用例先补。 |
| 2 | 真实模型行为评测 | 解题引导/错题讲解目前只验证提示词和替身；用合成样本实际跑“直接索要答案仍引导”“讲解不整段复制答案”，记录模型表现，不把提示词测试当模型通过。 |
| 3 | 性能口径 | 旧记录的请求数不足，先用报告中的有效档位复测缓存前后；补 worker/模型执行吞吐单独口径、队列状态和 `EXPLAIN`，区分接收吞吐与模型吞吐。 |
| 4 | 主干保护与 CI 实证 | 前两次 CI 因单元门禁环境错误退出；配置实际分支保护/必需检查，记录一次受控红或安全例外验证。 |
| 5 | 就绪/存活与 RPO/RTO | 当前只有 `/health` 和 Compose healthcheck；明确是否新增 readiness/liveness（需 change 规约），并说明单实例部署；RPO/RTO 目标与实际恢复演练分开，未演练不得写达标。 |
| 6 | 展示五动作预演 | 门禁受控拦坏→一键启动→技能面板→四步演示→压测前后对比；正式报告按第 11 课。 |
| 7 | 安全例外复查 | 2026-12-18 前复查；上游修复则升级/替换并移除例外。字面“高危 0”仍未达到。 |

### C. 收尾流程（需用户授权）

1. 按实际结果更新 `tasks.md`；不要把未执行项勾上。
2. 另一成员完成独立复现和人工评审后，再决定同步主规格、归档 change。
3. 更新 README、项目状态、Class10 记录中的活动/归档状态。
4. `openspec validate --all --strict`、`git diff --check`、`git status --short`。
5. 提交/推送/合入/标签按用户和团队授权办理；不强制推送、不改写已推送历史。

## 5. 常用命令

```powershell
# 功能门禁
python scripts/check.py --mode unit
$env:TESTCONTAINERS_API_VERSION='1.44'
python scripts/check.py --mode integration
$env:APITEST_ENV_FILE='.env.team'
python scripts/check.py --mode api

# 安全门禁（用户确认例外，显式传入）
python scripts/security_gate.py --env-file .env.team --exceptions security-exceptions.json

# 无例外对照（应红）
python scripts/security_gate.py --env-file .env.team

# 压测（默认保留 worker 并等待终态；队列初始非空会拒绝）
python scripts/bench.py --env-file .env.team --levels 1,4,8 --requests 16

# 观测（三技能面板；告警需显式 webhook）
python scripts/observe.py --env-file .env.team --window 5m `
  --alert-webhook http://127.0.0.1:18081/alerts --alert-min-requests 20

# 四步演示
python scripts/four_step_demo.py --env-file .env.team

# 脚本测试
python -m unittest discover -s scripts/tests
```

本机没有 GNU make；`make check`、`make check-security`、`make bench`、`make observe` 的等价 Python 命令如上。`make check-security` 默认传入 `security-exceptions.json`。

## 6. 已知问题与注意

- **安全例外**：不要为了“变绿”改 `--audit-level`、加 `--omit=dev`、删除例外校验或跳过扫描；例外只按显式文件生效。
- **API 模型波动**：安全门禁内曾出现流式正文与落库正文在引用标记外不一致；同条件重跑通过。保留失败日志，不要改写期望。
- **Compose bake**：本机 `docker compose build` 可能 bake 退出 1；用直接 `docker build` + `--no-build`，记录现象。
- **前端 502**：重建 backend 后重启 frontend。
- **dirty 镜像**：已推送提交是 `659bdf6`，但现有镜像标签来自 dirty 工作区 @ `b30ab9d`；接手后重建干净镜像并更新证据。
- **不要提交**：`.env.team`、真实密钥、学生数据、原始供应商响应、未脱敏日志、`class10.md` 课程文件。
- **不要代签**：成员评审和独立复现结论必须由实际执行人填写；AI 只可协助整理。
- **不要提前归档**：change 仍未同步主规格/归档；需复测、评审和用户授权。

## 7. 交接检查清单

- [ ] 已拉取 `659bdf6` 或其后提交，工作区无未提交业务修改。
- [ ] 私有 `.env.team`、Docker、Java/Python/Node、模型服务可用。
- [ ] 七服务启动，`/health` 200；确认 frontend/worker 没有独立 healthcheck。
- [ ] 重建干净镜像，核对 revision 和 `/health.version`。
- [ ] 运行功能 unit/integration/api 和脚本测试，记录真实退出码/计数。
- [ ] 运行安全门禁（显式例外），确认五阶段 0；同时确认无例外时仍会红。
- [ ] 完成干净目录/另一机器启动。
- [ ] 检查CI 修复后的远端运行、镜像标识、本地完整安全门禁和主干保护。
- [ ] 完成另一成员独立启动/演示/交叉评审并签字。
- [ ] 补完场景追溯、真实模型评测、性能 worker 口径、就绪/RPO、五动作预演。
- [ ] 2026-12-18 前复查安全例外。
- [ ] 经授权后再提交/推送/同步/归档。

## 8. 证据索引

- [Class10 记录](README.md)
- [门禁红绿](../../reports/gate-red-green.md)
- [启动与镜像](../../reports/boot-reproduce.md)
- [CI 流水线](../../reports/pipeline.md)
- [观测与告警](../../reports/observe-alert.md)
- [压测与缓存](../../reports/bench.md)
- [AI 自审](../../reports/review-05.md)
- [安全基线](../../reports/security-baseline.md)
- [活动 OpenSpec tasks](../../openspec/changes/add-class10-test-gates-observability-and-delivery/tasks.md)
- 原始日志：`backend/target/class10-red/`、`backend/target/security-gate/`、`backend/target/scan-logs/`、`backend/target/bench/`、`backend/target/observe/`
