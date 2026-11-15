# Class09 安全修复、审计与注入实验记录

最后更新：2026-11-16
实现者记录的候选：`master` @ `5d9d765` 加当时工作区修改；独立复核候选为 `caa9dbf` 加本轮未提交修正
环境：Windows 11 + Docker Desktop 29.9.2，团队 `campusclaw-plus` 七服务，MySQL `campusclaw_plus`，宿主端口 18080/13306/15673；私有 `.env.team` 使用独立随机密钥。Java 17（Corretto）、Python 3.11.9 虚拟环境、Node 22.22.2。

## 1. 基线

- `docker compose --env-file .env.team ps`：七个服务 healthy。
- `http://localhost:18080/health`：200，database/qdrant up；chat/embedding/mcp 为 `not_checked`。
- 演示账号 `teacher-a`、`student-a1`、`student-b1` 登录成功；实现者专用环境曾补建 `teacher-b`（原记录为复用演示口令哈希）；本机复核改用独立随机口令新生成哈希的临时账号，不沿用此方式。
- `security_precheck.py --checks access`：run `ac98e1bc125d`，exit 0。
- `security_precheck.py --checks audit`：run `4760bfcb427e`，exit 1；SEC-07 红，缺 skillId 和 tokenUsage。
- `apitest/run_all.py`：首轮在会话流式用例出现一次红（模型未引用材料，落库被归一化为无依据），同条件重跑通过；已作为模型/数据相关波动记录，未删除失败记录。
- `check_security.py`：密钥阶段 exit 0（0 命中）；依赖阶段因 `JAVA_HOME` 仍指向 Java 8 exit 2，未记为通过。

## 2. 本轮实现

- V14 迁移：`messages` 增加 `skill_id`、Token 三列、`model_duration_ms`、`request_id`；新增 `security_audit_events`，记录拒绝/失败/流前失败。
- 模型 usage：非流式和流式解析供应商 `usage`；流式请求带 `stream_options.include_usage`；未知保持 `NULL`。
- 技能 ID：服务端路由结果进入 `AgentTurnPlan` 和消息持久化；不改变 SSE 事件顺序。
- 审计查询：`GET /api/audit/events`，教师本班，支持技能/操作人/时间窗，合并成功消息与拒绝事件。
- 拒绝追踪：学生技能/助手配置写入 403 前记录 `CONFIG_WRITE`；终评 403/404 记录 `GRADE_REVIEW`。
- 三条反例：SQL 注入、XSS、CSRF 等价，常驻 `apitest/cases/`；前端增加 DOM 级 XSS 断言。
- 安全门禁：`scripts/security_gate.py` + `Makefile` 的 `check-security`，聚合扫描、授权、审计、接口反例和脚本测试退出码。
- SEC-08：`scripts/sec08_experiment.py` 四步实验。

## 3. 验证状态

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| SEC-01～04 授权 | 绿 | `reports/security-fix-log.md`、`reports/risk-authz.md` |
| SEC-05 密钥 | 绿；0 命中 | `reports/security-baseline.md` |
| SEC-06 依赖 | 绿；124/124 OSV，0 findings，high/critical 0 | 门禁 scan 阶段、`security-baseline.md` |
| SEC-07 审计 | 红转绿；七类字段和拒绝回查通过 | run `365c1eb50877`、`reports/audit-recheck.md` |
| 三条反例 | API 套件通过；前端 158/158（干净 Node 22 容器） | `reports/injection-cases.md` |
| SEC-08 | 四步完成；学生和跨班拒绝审计均通过，模型注入现象保留 | 最终 run `b9d157fa2cbc`、`reports/sec08-experiment.md` |
| 安全门禁红绿 | 完整 run `20261114T123734Z-41820`：scan/access/audit/api/unit 全 0，总 exit 0；事务修复后 access `8b7d2bca555b`、audit `df59a91907d1`、api、unit 定向复跑全 0 | `reports/gate-red-green.md` |
| 后端单元 | 246/246 通过 | `backend/target/class09-backend-unit-final.log` |
| 后端集成 | 43/43 通过；需 `TESTCONTAINERS_API_VERSION=1.44` | `backend/target/class09-integration-final.log` |
| 全量回归 | 部分完成；宿主机 Vitest runner 异常，容器内 158/158 通过 | 本文件第 4 节 |
| 四步演示 | 通过；run `ae6a1f8cf4e1` | `backend/target/four-step-demo-ae6a1f8cf4e1.json` |
| 交叉复测 | 未执行；不得代签 | `reports/review-02.md` 起 |

## 4. 测试环境说明

Windows 宿主机上 `npm run test -- --run` 出现 Vitest “failed to find the runner/current suite”，23 个测试文件均无法加载；同一份源码在干净 `node:22-alpine` 容器中执行 `npm ci && npx vitest run` 得到 23 文件、158 测试全部通过。宿主异常已记录，不作为通过证据；后续在 CI/Linux 或修复宿主依赖后重跑。

后端集成测试在 Docker Desktop 29.9.2 上需要限制 Testcontainers API 版本：

```powershell
$env:TESTCONTAINERS_API_VERSION='1.44'
python scripts/check.py --mode integration
```

本轮实际结果：43 项测试，0 failures/errors/skipped。

## 5. 四步演示（run `ae6a1f8cf4e1`）

脚本：`scripts/four_step_demo.py --env-file .env.team`，exit 0。

| 步骤 | 实际结果 |
| --- | --- |
| 登录 | `student-a1`，role student，classId 1 |
| 学生提交 | 202，含 `taskId`，响应不含分数；提交耗时 36.4 ms |
| 任务状态 | 停 worker 后为 `PENDING`；恢复 worker 后 `SUCCEEDED`，执行记录耗时 1602 ms |
| 跨班 | B 班读取 A 班材料 404；B 班关键词检索 0 命中；A 班 1 命中（200+空/非空集合，不强改 404） |

原始结果：`backend/target/four-step-demo-ae6a1f8cf4e1.json`。

## 6. 未执行/保留

- 另一成员独立复现至少两条修复尚未执行；`reports/review-02.md`、`review-03.md` 是 AI/脚本记录，不是人工签字。
- 宿主 Windows 前端 Vitest runner 异常未修复；干净 Linux 容器结果通过，未记为宿主通过。
- 依赖扫描门禁已完成；OWASP/NVD 未执行，不作为默认门禁通过项。
- 已按 SEC 编号分组提交：`14e43cf`、`7a1d56b`、`5d4ac3a`、`015ad6a`、`474913e`；原六提交已推送；独立复核修正未提交、未同步主规格、未归档。主规格同步和归档仍需在交叉复测后另行授权。
- 教师参考配置影响评分（对照0、教师配置参考后100）；2026-11-15更正：该文件由教师上传绑定，属于已有配置权限，不认定为学生输入漏洞或安全阻断项；原实验数据保留。

## 独立复核（2026-11-14）

详见[review-04](../../reports/review-04.md)。本机Windows前端158项、后端unit251项、独立容器集成44项、脚本42项和API框架5项通过；真实SEC-07/08第四步、完整API与异步三判据均已复现并清理。审计查询用户名及366天边界已修正，补齐迁移/事务/门禁失败测试；跨班教师支持独立配置。

完整安全门禁run `20261114T142446Z-46328`退出1：scan/access/audit/api/unit分别1/0/0/0/0。依赖扫描通过，Gitleaks四范围零命中，但本机演示口令与公开字符串重合的配置密钥检查仍红。教师参考配置现象的数据保留，2026-11-15已更正为非漏洞，不涉及风险接受。当时口令处置及成员确认、同步归档未完成（最新状态见下节），change保持活动；没有提交、推送或标签。上文仍是实现者历史结果，不替代本机记录。

## 口令轮换与判定更正（2026-11-15）

2026-11-15按授权轮换五个现有演示账号的口令并同步私有 `.env.team`，种子保持关闭。五个账号新口令登录均200，旧演示口令对teacher-a返回401，账号其他字段未变。完整门禁run `20261115T152822Z-5904`的scan/access/audit/api/unit均为0，总退出0；Gitleaks四范围与配置密钥匹配均零命中，依赖扫描通过。教师在已有权限内配置给满分参考文件的现象不认定为安全漏洞，实验数据保留。实际成员确认、主规格同步和归档仍待完成，未提交、推送或创建标签。

## 同步归档（2026-11-16）

2026-11-16已按接收交付成员确认同步并归档Class09。受控红run `20261115T160013Z-33956`使用 `--fault-stage unit`，阶段0/0/0/0/1、总退出1；移除参数后run `20261115T160417Z-7996`五阶段全0、总退出0。三份主规格已同步，历史失败记录保留。实施前确认缺失作为流程偏差留档，git提交由用户后续执行；未提交、推送或创建标签。

归档及两项流程保留见[归档记录](../../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/README.md)。此前各节按实际执行日期保留，当前状态以本节为准。
