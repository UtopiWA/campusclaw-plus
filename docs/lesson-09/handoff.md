# Class09 收尾交接

交接对象：另一名团队成员  
交接日期：2026-11-14  
当前远端：`origin/master` @ `caa9dbf`  
归档 change：[add-class09-security-audit-and-gate](../../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/proposal.md)

## 交接完成（2026-11-16）

2026-11-16已按接收交付成员确认同步并归档Class09。受控红run `20261115T160013Z-33956`使用 `--fault-stage unit`，阶段0/0/0/0/1、总退出1；移除参数后run `20261115T160417Z-7996`五阶段全0、总退出0。三份主规格已同步，历史失败记录保留。实施前确认缺失作为流程偏差留档，git提交由用户后续执行；未提交、推送或创建标签。

接收成员已明确确认独立复核及CSRF等价口径。[归档记录](../../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/README.md)与[review-04](../../reports/review-04.md)保存实际结果及流程偏差。剩余操作仅为用户自行git提交；下文为原交接方案，保留历史要求，不代表当前仍未复核或归档。

## 0. 原交接时的现状（历史）

Class09 的实现和自动化证据已经完成并推送，但以下事项仍未完成：

- 另一成员尚未独立复现至少两条修复，也没有人工交叉评审签字。
- OpenSpec change 仍是活动状态，未同步主规格、未归档。
- `tasks.md` 尚未按实际完成情况勾选；不要为了“看起来完成”全部勾上。
- 作者本机的 `.env.team`、Docker 数据卷、模型服务和测试日志都没有随仓库提供。

本轮提交：

| 提交 | 内容 |
| --- | --- |
| `14e43cf` | SEC-03/04/07：V14 审计字段、模型 usage、教师本班审计查询、拒绝留痕 |
| `7a1d56b` | SQLi/XSS/CSRF 常驻反例及前端 DOM 断言 |
| `5d4ac3a` | SEC-08 四步实验与四步演示脚本 |
| `015ad6a` | 安全门禁、`Makefile`、访问/审计预检扩展 |
| `474913e` | OpenSpec change、Class09 记录、修复台账与门禁证据 |
| `caa9dbf` | 回填提交哈希到证据文档 |

你的收尾目标只有四件事：

1. 在自己的环境独立复现至少两条修复。
2. 写真实的人工交叉评审记录，不代签、不复制作者结论。
3. 复测通过且用户授权后，同步主规格并归档 change。
4. 完成收尾文档提交；推送按团队授权办理。

## 1. 拉取与前置

```powershell
git fetch origin
git checkout master
git pull --ff-only
git rev-parse --short HEAD
git status --short
```

预期 HEAD 为 `caa9dbf` 或其后包含本交接的提交；工作区不应有未提交的业务修改。

环境要求：

- Docker Desktop 正常运行，`docker info` 成功。
- 自己准备私有 `.env.team`，不要向作者索取或复用其密钥，也不要把该文件提交到 Git。
- 保留项目名 `campusclaw-plus`、数据库 `campusclaw_plus`、宿主端口 18080/13306/15673；如冲突，改自己的配置并在记录中写明。
- Java 17；Python 3.11+；Node 22 或使用 `node:22-alpine` 容器跑前端测试。
- 至少一套可用的 Chat/Embedding 模型服务；SEC-07 和 SEC-08 需要真实模型响应。

```powershell
docker compose --env-file .env.team ps
```

预期七个服务 healthy：`mysql`、`qdrant`、`rabbitmq`、`mcp-demo`、`backend`、`grading-worker`、`frontend`。`http://localhost:18080/health` 返回 200 只代表数据库/Qdrant 就绪，不代表模型可用。

准备独立 Python 环境：

```powershell
python -m venv .venv-review
.\.venv-review\Scripts\python.exe -m pip install --no-cache-dir -r apitest/requirements.txt
```

## 2. 必须独立复现的修复（至少两条）

### 2.1 SEC-07 技能审计

```powershell
.\.venv-review\Scripts\python.exe -B scripts/security_precheck.py --env-file .env.team --checks audit
```

必须实际确认，而不是只看报告：

- 退出 0，控制台有 `execution=complete cleanup=complete`。
- 查询 `GET /api/audit/events` 返回操作人、时间、动作、对象、结果状态、技能标识、用量/耗时七类信息。
- 技能 ID 来自服务端路由结果；Token 用量是供应商真实返回值；供应商没返回时保持 `null`，不是 0。
- 学生查询审计返回 403；B 班教师查 A 班记录为空集合。
- 重放 SEC-03 后，学生配置写入仍 403，配置和数据库不变，教师能查到 `CONFIG_WRITE`/`DENIED` 记录。

参考证据：[审计回查](../../reports/audit-recheck.md)、[修复台账](../../reports/security-fix-log.md)。

### 2.2 SEC-08 越权与拒绝审计

```powershell
.\.venv-review\Scripts\python.exe -B scripts/sec08_experiment.py --env-file .env.team
```

必须确认第四步硬底线：

- 学生调用终评接口返回 403。
- 跨班教师调用返回 404。
- 数据库成绩/提交计数不变。
- 学生和跨班教师的拒绝审计都能查到。
- 没有新增成绩写工具；工具选择仍是确定性代码。

前三步是模型环境现象，不是确定性通过项。教师将整份参考文件替换为给满分指令后，观察到对照0分、配置参考后100分。2026-11-15更正：教师已有管理参考文件的权限，学生作答并非恶意内容，这不构成学生输入漏洞或安全阻断项；保留原数据，不要求接受此项风险。用户报告多次学生恶意输入测试未发现问题，代理本轮未重跑这些输入。参考证据：[SEC-08 实验](../../reports/sec08-experiment.md)。

### 2.3 建议补做的一条

至少再选一条，建议 SEC-03 拒绝留痕或 SQL 注入反例：

```powershell
.\.venv-review\Scripts\python.exe -B scripts/security_precheck.py --env-file .env.team --checks access
.\.venv-review\Scripts\python.exe -B apitest/run_all.py
```

接口套件包含 `apitest/cases/api/retrieval/search-injection/`、`materials/xss/`、`materials/csrf/` 三条常驻反例。必须看实际 HTTP 状态、数据库前后计数和清理状态；任一失败先停下记录红项，不要改期望值凑绿。

## 3. 回归与证据核对

按影响范围选择性重跑，但至少核对当前 HEAD 的证据是否对得上：

| 项目 | 期望 | 入口 |
| --- | --- | --- |
| 后端单元 | 246/246 | `python scripts/check.py --mode unit` |
| 后端集成 | 43/43，需 `TESTCONTAINERS_API_VERSION=1.44` | `python scripts/check.py --mode integration` |
| 前端 | 干净 Node 22 容器 158/158 | `node:22-alpine` 内 `npm ci && npx vitest run` |
| 接口回归 | 全部通过，含三条反例 | `python apitest/run_all.py` |
| 脚本单测 | 38 项通过 | `python -m unittest discover -s scripts/tests` |
| 规格 | `openspec validate --all --strict` 通过 | OpenSpec CLI |
| 空白检查 | 无输出 | `git diff --check` |

注意两项已登记的限制：

- Windows 宿主上前端 Vitest 有 runner 异常，23 个测试文件无法加载；干净 Linux 容器通过。不要把宿主异常写成通过，也不要用“页面看起来正常”替代。
- 作者最后一次完整门禁重跑时 `pip-audit` 子进程挂起，已停止并保留现象；完整门禁全绿证据是 run `20261114T123734Z-41820`，最终事务修复后 access/audit/api/unit 是定向复跑。你若重跑门禁，记录真实退出码；挂起就写挂起，不要补写通过。

## 4. 人工交叉评审记录

新建 `reports/review-04.md`（不要覆盖 `review-01`～`review-03`），至少包含：

- 实际执行人、日期、评审提交 `caa9dbf` 或你实际拉取的 HEAD。
- 环境：自己的 `.env.team` 项目名、Docker/Java/Python/Node 版本、模型服务是否可用。
- 复现的至少两条修复：命令、实际输出摘要、退出码、清理结果。
- 每个被审文件的结论：`可用` / `待修改` / `风险无效`。
- 七维检查：接口入口、业务逻辑、数据库操作、公共函数、错误处理、安全与隐私、性能。
- 并发、事务、一致性专项结论，尤其是：V14 只增不改、审计 `REQUIRES_NEW` 与原 403/404 的关系、成功消息与 `done` 的提交顺序、未知 usage 保持 `null`。
- 未执行项和残余风险。
- 最终结论：通过 / 有条件通过 / 不通过。只有实际做过才签字。

建议重点审这些文件：

- [V14 迁移](../../backend/src/main/resources/db/migration/V14__security_audit.sql)
- [审计包](../../backend/src/main/java/com/campusclaw/audit/)
- [会话流式服务](../../backend/src/main/java/com/campusclaw/conversation/ConversationStreamingService.java)
- [当前用户服务](../../backend/src/main/java/com/campusclaw/auth/CurrentUserService.java)
- [终评服务](../../backend/src/main/java/com/campusclaw/grading/SubmissionReviewService.java)
- [安全门禁](../../scripts/security_gate.py)
- [审计预检](../../scripts/security_precheck.py)
- [SEC-08 脚本](../../scripts/sec08_experiment.py)
- [OpenSpec change](../../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/)

## 5. 评审通过后的收尾

以下步骤必须同时满足“复测通过”和“用户明确授权”，否则保持活动状态。

1. **按实际结果勾选 `tasks.md`**  
   只勾选你亲自验证过的项；人工复测（9.4）、同步归档（9.5）在完成前保持未勾选。

2. **同步主规格**  
   把 change 的三份 delta 同步到主规格：
   - 新增 `openspec/specs/security-audit/`
   - 更新 `openspec/specs/security-precheck/`
   - 更新 `openspec/specs/api-regression-harness/`

   使用团队已有的 OpenSpec 流程，不要在归档前直接手改主规格。同步后运行：

   ```powershell
   openspec validate --all --strict
   ```

3. **归档 change**  
   按 OpenSpec 归档流程把 `add-class09-security-audit-and-gate` 移入 `openspec/changes/archive/2026-11-14-add-class09-security-audit-and-gate/`（日期以实际归档日为准），补归档说明和证据链接。

4. **更新文档**  
   更新 [README](../../README.md)、[项目状态](../project-status.md)、[Class09 记录](README.md) 中的活动/归档状态和链接。不要把未执行项写成已通过。

5. **最终检查**  
   ```powershell
   openspec validate --all --strict
   git diff --check
   git status --short
   ```

6. **提交与推送**  
   建议 message：`docs: verify and archive Class09 security audit`。只在用户/团队授权后提交和推送；不强制推送、不改写已推送历史、不打标签。

### 如果发现问题

- 先在 `review-04.md` 写“待修改/不通过”和最小复现，不要直接归档。
- 修复用新提交完成，提交 message 标明对应 SEC 编号；不要 amend 已推送提交。
- 修复后重跑受影响检查，更新报告中的实际结果，再请人复核。
- 不允许通过降低阈值、扩大白名单、跳过校验、删审计或加 `|| true` 让检查变绿。

## 6. 明确不能做

- 不提交 `.env.team`、真实密钥、学生数据、供应商原始响应或未脱敏日志。
- 不代签作者或其他成员的评审结论，不伪造复现结果。
- 不把模型在 SEC-08 中的口头拒绝当成注入已修复；代码层 403/404/数据库不变/拒绝审计才是硬判据。
- 不把 Windows 宿主 Vitest 异常写成前端通过。
- 不把挂起的门禁运行写成通过。
- 不在评审完成前同步或归档，不强制推送，不重写历史。

## 7. 收尾清单

- [x] 核对本机HEAD与 `origin/master` 同为 `caa9dbf`，记录自己的环境；无需拉取。
- [x] 七服务运行，配置健康探针的五个服务均healthy，演示账号可登录。
- [x] 独立复现 SEC-07 审计，七类字段和拒绝回查通过。
- [x] 独立复现 SEC-08 第四步，403/404、数据库不变、拒绝审计通过。
- [x] 至少再核对一条修复（建议 SEC-03 拒绝留痕或 SQL 注入反例）。
- [x] 核对后端单元、集成、前端、接口、脚本和规格证据；未执行项如实记录。
- [x] 写 `reports/review-04.md`，含代理实际执行、逐文件结论和七维检查；成员确认仍待完成。
- [x] 按实际结果更新 `tasks.md`；口令处置、人审、受控故障与同步归档已完成；实施前流程偏差和用户后续提交仍如实留存。
- [x] 用户授权后同步三份 delta 到主规格，`openspec validate --all --strict` 通过。
- [x] 归档 change，更新 README/项目状态/Class09 记录。
- [ ] 最终 `git diff --check`、提交、按授权推送。

## 8. 证据索引

- [Class09 记录](README.md)
- [修复台账](../../reports/security-fix-log.md)
- [审计回查](../../reports/audit-recheck.md)
- [SEC-08 实验](../../reports/sec08-experiment.md)
- [门禁红绿](../../reports/gate-red-green.md)
- [三条反例](../../reports/injection-cases.md)
- [返工记录](../../reports/rework-01.md)
- [AI 自审](../../reports/review-02.md)
- [脚本发现](../../reports/review-03.md)
- [OpenSpec 任务](../../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/tasks.md)
