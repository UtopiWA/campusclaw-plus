# Tasks

## 1. 基线与实施边界

- [x] 1.1 固定 Class09 基线：记录候选提交、工作区状态、Docker/Java/Python/Node 版本、`.env.team` 项目名和七服务健康状态；先跑现有功能、接口、授权和扫描入口，保留真实退出码，不预填绿。
  Verify：`git rev-parse HEAD`、`git status --short`、`docker info`、`docker compose --env-file .env.team ps` 的实际输出写入基线记录；`python scripts/check.py --mode all`、`python apitest/run_all.py`、`python scripts/security_precheck.py --env-file .env.team`、`python scripts/check_security.py --env-file .env.team` 的退出码原样记录；未执行项写明原因。

- [ ] 1.2 评审并确认本 change 的范围、审计两路方案、V14 迁移、门禁入口和 CSRF 等价口径；确认后才进入实现。
  Verify：`openspec validate --all --strict` 通过；proposal/design/specs/tasks 的范围一致；评审记录由实际成员填写，代理不代签。

## 2. V14 数据模型与审计持久化

- [x] 2.1 新增 `backend/src/main/resources/db/migration/V14__security_audit.sql`：为 `messages` 增加可空 `skill_id`、`prompt_tokens`、`completion_tokens`、`total_tokens`、`model_duration_ms`、`request_id` 及索引；新增追加式 `security_audit_events` 及班级/用户/技能/时间索引；`skill_id` 不做外键；不修改 V1～V13。
  Verify：空库迁移到 V14 成功；在 V13 数据库插入旧消息/会话/技能后升级成功，旧行新列为 `NULL`、旧数据可读；索引存在；迁移文件只新增。

- [x] 2.2 新增 `com.campusclaw.audit.SecurityAuditEvent`、Repository 和 `com.campusclaw.persistence.ConversationMessage` 的新字段，保持现有构造函数兼容；服务层写入只在有真实值时填用量/耗时，未知保持 `null`。
  Verify：实体映射通过 Hibernate `ddl-auto=validate`；聚焦单元测试覆盖 `null` 与真实值；代码检查不把未知值写 0。

- [x] 2.3 新增/扩展迁移测试：空库到 V14、V13→V14、技能删除后消息/审计快照保留、旧会话读取不变。
  Verify：`backend/mvnw.cmd -f backend/pom.xml -Dtest=SecurityAuditSchemaMigrationTest,CampusClawIntegrationTest test` 通过，0 failures/errors/skipped；报告为新生成。

## 3. 模型 usage 与稳定技能 ID

- [x] 3.1 扩展 `ChatGateway`：增加可空 `Usage`、`completeWithUsage(...)` 默认实现和 `ChatStream.usage()` 默认 `null`；保持 `complete(...)` 现有调用兼容。
  Verify：`GatewayClientTest` 覆盖非流式 usage、流式 usage、缺失/非法 usage 为 `null`；现有网关测试通过。

- [x] 3.2 修改 `OpenAiChatGateway`：非流式解析 `usage`；流式请求增加 `stream_options.include_usage=true`，在 `[DONE]` 前解析 usage-only chunk；供应商未返回时保持 `null`。
  Verify：fake HTTP/SSE 测试断言请求体包含 `stream_options`、正常 usage 被解析、缺失 usage 为 `null`；不把缺失写成 0。

- [x] 3.3 在 `AgentTurnPlan` 增加 `skillId`，由 `AgentPromptBuilder` 从已选 `Skill.getId()` 传入；`ConversationService.saveAssistantMessage` 在同一事务写入 skillId/usage/耗时/requestId；不改变 SSE `meta/delta/done` 顺序。
  Verify：`AgentConversationServiceTest`、`ConversationServiceTest`、`ConversationStreamingServiceTest` 覆盖技能 ID、成功消息字段、失败路径和 `done` 仅在提交后发送；现有 SSE 测试不变通过。

- [x] 3.4 在 `ConversationStreamingService` 只对真实模型流测量 `modelDurationMs`；固定文本轮次保持 `null`；流前/流中失败按 4.3 的审计事件规则留痕，不伪造成功消息。
  Verify：固定文本、模型成功、模型失败三类测试分别断言 `null`、真实值、失败事件；失败时无 `done`。

## 4. 审计查询与拒绝追踪

- [x] 4.1 新增 `com.campusclaw.audit` 服务：写入成功消息审计字段、记录拒绝/失败事件；拒绝事件使用独立事务，写入失败不掩盖原 401/403/404。
  Verify：聚焦测试覆盖成功、拒绝、审计写入失败三类路径；原 HTTP 状态和数据库业务数据不变。

- [x] 4.2 新增 `GET /api/audit/events`：教师本班，可选 `skillId`、`userId`、`from`、`to`、`limit`（1～100，默认 50），时间窗默认 24 小时、最大 366 天；合并 `messages` 与 `security_audit_events`，稳定排序，返回 `source` + `id` 和安全元数据；学生 403、匿名 401、跨班空集合、非法参数 400。
  Verify：`AuditControllerTest` 覆盖教师本班、学生 403、匿名 401、跨班空、skill/user/time 过滤、两路合并排序、limit/时间窗边界；响应不含正文/提示词/凭据。

- [x] 4.3 在 `CurrentUserService.requireTeacher(...)` 增加带 action/object 的重载，记录学生配置写入拒绝；在 `SubmissionReviewService` 的角色/归属校验失败处记录终评拒绝（action `GRADE_REVIEW`，object 为 assignment/submission）。
  Verify：SEC-03 同类请求返回 403 且配置/DB 不变；按学生和时间窗能查到拒绝事件；SEC-08 第四步的 403/404 有对应拒绝记录；审计写入失败不影响原响应。

- [x] 4.4 扩展 `scripts/security_precheck.py --checks audit`：SEC-07 改为查询 `/api/audit/events`，按技能、学生和时间窗断言七类字段；MCP 调用审计仍单独检查；另重放 SEC-03 并查询拒绝记录。
  Verify：`python scripts/security_precheck.py --env-file .env.team --checks audit` 退出 0；输出包含技能 ID、真实 usage、耗时、操作人、时间、对象、状态；学生查询审计为 403；未知 usage 为 `null`。

## 5. 输入安全反例与 XSS DOM

- [x] 5.1 扩展 `apitest/common/http_client.py`：支持可选请求头和 raw/multipart 上传，保持现有调用兼容；新增 `apitest/common/fixtures.py`，复用上传、运行标识和清理模式；在 `apitest/README.md` 说明安全用例参与常规回归及首个失败阻断后续的顺序。
  Verify：`apitest/tests/test_common.py` 覆盖自定义头、multipart 边界/编码和秘密脱敏；现有接口用例无回归。

- [x] 5.2 新增 `apitest/cases/api/retrieval/search-injection/POST.py`：学生 token 发送 `' OR '1'='1`、UNION 探针和伪造 `classId`；接受 200+空/本班命中或 400，不接受 500；逐条查 MySQL 断言本班/READY；非法 `limit`/`mode`/空 `query` 返回 400；相关表计数不变。
  Verify：用例单独运行通过；`python apitest/run_all.py` 中该用例通过；报告中记录注入串、命中数、跨班数和数据库前后计数。

- [x] 5.3 新增 `apitest/cases/api/materials/xss/PATCH.py`：上传恶意文件名或 PATCH 恶意标题，断言上传/PATCH/列表原样返回、MySQL 一致；扩展 `frontend/src/views/MaterialsView.test.js` 和 `frontend/src/utils/assistantMarkdown.test.js`，断言渲染 DOM 无 `script`/`img`/事件属性节点，KaTeX 不产生可执行链接。
  Verify：`python apitest/run_all.py` 通过；`npm --prefix frontend run test -- --run` 通过；测试断言实际 DOM，不以“没弹窗”代替。

- [x] 5.4 新增 `apitest/cases/api/materials/csrf/PATCH.py`：伪造带 `Origin/Referer`、无 Bearer 的 PATCH → 401 且 DB 不变；带 Bearer 对照 → 200；登录响应无 `Set-Cookie`；带 dummy Cookie 无 Bearer 仍 401；报告中记录 Bearer JWT 等价验收口径并取得教师/团队确认。
  Verify：用例通过；`reports/injection-cases.md` 记录前置/操作/结果和认证机制说明；不出现伪造的 SameSite/CSRF token 证据。

## 6. SEC-01～06 复测与资产处置

- [x] 6.1 扩展 `scripts/security_precheck.py` 的 access 路由，覆盖学习/首页等新增读入口和明确的写入口；补充 SEC-03 拒绝后审计查询、SEC-04 直接写入检查；真实缺口按 SEC 编号单独修复并补聚焦测试。
  Verify：`python scripts/security_precheck.py --env-file .env.team --checks access` 退出 0；记录 401/403/404、跨班检索数量、合法提交结果和数据库前后变化；发现缺口时先红后修，不为凑绿改权限。

- [x] 6.2 运行 `python scripts/check_security.py --env-file .env.team`：记录 Gitleaks 版本、历史/公开文件/dist/七服务日志范围、命中数和退出码；若演示口令与公开字符串重合，先在 `.env.team` 轮换并重扫；保留候选时写明限定处置和验收口径；依赖记录 high/critical 数量、工具版本和真实退出码，高危升级/替换或书面处置。
  Verify：退出 0（0 命中、high/critical 0）或按保留候选如实记录退出 1/2 和处置；`reports/security-baseline.md` 追加 Class09 节，保留 Class08 原文。

## 7. SEC-08 四步实验

- [x] 7.1 新增 `scripts/sec08_experiment.py` 和 `scripts/tests/test_sec08_experiment.py`：复用登录/上传/作业/提交/轮询/清理；恶意讲义进入参考快照并实际批改；请求提示词；请求宽松规则；请求冒充管理员改成绩；记录脱敏现象、数据库增量和工具清单。
  Verify：脚本可在团队环境运行并清理合成资源；单测覆盖运行标识、清理和第四步断言；不新增危险工具、不伪造调用日志。

- [x] 7.2 执行四步实验：前三步记录模型实际现象（不是确定性断言）；第四步断言无成绩写工具、学生终评 403、跨班 404、数据库不变、拒绝审计存在；任何越权写入即实验失败。
  Verify：`python scripts/sec08_experiment.py --env-file .env.team` 退出 0；`reports/sec08-experiment.md` 记录四步现象、代码层授权结果和未执行/不确定项。

## 8. 安全门禁与红绿证据

- [x] 8.1 新增 `scripts/security_gate.py`：顺序运行 `check_security.py`、`security_precheck.py --checks access`、`security_precheck.py --checks audit`、`apitest/run_all.py`、`scripts/tests`；任一步骤失败/未完成非零退出；2 优先于 1；摘要脱敏写入 `backend/target/security-gate/`；禁止 `|| true`、跳过范围或降阈值。
  Verify：`scripts/tests/test_security_gate.py` 覆盖 0/1/2 聚合、全步骤执行和失败传播；故意让一个阶段失败时门禁非零。

- [x] 8.2 新增根 `Makefile`：`check` 委托 `scripts/check.py --mode all`，`check-security` 委托 `scripts/security_gate.py --env-file $(ENV_FILE)`；README 增加前置条件、两种入口、阶段和退出码；本机无 make 时用 Python 入口验证，并如实记录 make 未执行。
  Verify：`python scripts/security_gate.py --env-file .env.team` 可运行；有 make 的环境执行 `make check-security` 结果一致；README 命令与实际脚本一致。

- [x] 8.3 留一次受控红绿证据：用门禁明确标记的合成故障（或清晰标记的临时合成文件）制造红，记录退出码/输出；移除后重跑记录绿；不使用真实密钥、学生数据或供应商原始敏感内容。
  Verify：`reports/gate-red-green.md` 记录红输出/退出码、改动位置、移除动作、绿输出/退出码；两次运行均有真实命令和摘要；没有 `|| true` 或阈值改动。

## 9. 回归、报告、评审与归档

- [x] 9.1 分组完成全量回归：`python scripts/check.py --mode unit`、`--mode integration`、`--mode api`（与all相同三组，独立记录退出码）、异步三判据（202 解耦/停启不丢/重复唯一结果）、`python apitest/run_all.py`（含三条反例）、前端 Vitest、后端单元/集成和迁移测试。
  Verify：全部退出 0，报告为新生成，无失败/错误/跳过；失败项先修复，不用放宽期望掩盖。

- [x] 9.2 执行第 7 节四步演示：登录取令牌 → 学生提交返回 202/taskId 且无分数 → 任务 `PENDING → RUNNING → SUCCEEDED`（过快时结合执行记录或受控慢任务）→ B 班账号读取 A 班材料 404；直接检索隔离为 200+空集合，不强改 404。
  Verify：`docs/lesson-09/README.md` 记录每步的实际响应、任务状态/执行记录和跨班结果；不伪造中间状态。

- [x] 9.3 写实际报告：`security-fix-log.md`（SEC-01～08 修复前/后/判定）、`security-baseline.md` 追加、`risk-authz.md` 追加、`audit-recheck.md`（七类字段/回查/拒绝/学生 403/单行 JSON）、`sec08-experiment.md`、`gate-red-green.md`、`injection-cases.md`、`rework-01.md`（实际返工；不得编造）、`review-02.md`/`review-03.md`（AI 自审/脚本发现）。
  Verify：每份报告有实际命令、输出摘要、退出码和未执行项；`security-precheck.md` 和 `review-01.md` 未被覆盖；评审记录写清“手段/对象/判据/结果”。

- [x] 9.4 由另一成员独立复现至少两条修复并写人工评审记录；代理只整理证据，不代签、不伪造复现结果。
  Verify：人工记录包含实际执行人、日期、环境、命令、结果；至少两条修复可独立复现；未执行项保持未完成。

- [x] 9.5 在评审和用户授权后同步主规格、运行 `openspec validate --all --strict`、归档 change，并更新 README/project-status；未完成或未授权时保持活动。
  Verify：主规格与 delta 一致；严格校验通过；归档记录有授权和证据链接；未完成项不写成已通过。

- [ ] 9.6 仅在用户明确授权后按 SEC 编号分组提交；不推送、不打标签、不重写历史；每条修复提交 message 标明 SEC 编号。
  Verify：`git log --oneline` 显示独立 SEC 提交；未授权时不产生提交；不包含真实密钥、学生数据或原始敏感日志。

## 独立复核状态（2026-11-14）

本机执行及逐文件结论见[review-04](../../../../reports/review-04.md)。已勾选项目按实现者证据与本轮实际复核核对；未把代理执行视作成员签署。原口令红项已于2026-11-15轮换，最新完整门禁退出0；仍待实际成员确认及同步归档授权。

- 1.2：代码早于规约提交，无法追认实施前人工确认。
- 5.4：自动反例已通过，接收交付成员已于2026-11-16明确确认Bearer JWT的CSRF等价口径。
- 6.2：2026-11-15已轮换五个账号及私有口令；Gitleaks四范围及配置匹配均零命中，依赖扫描通过，完整门禁退出0。
- 8.3：2026-11-16实际执行完整门禁受控红（20261115T160013Z-33956，退出1），移除故障参数后完整绿（20261115T160417Z-7996，退出0）；原始历史红项继续保留。
- 9.4：接收方代理在独立环境完成SEC-07/08等复现，接收交付成员于2026-11-16明确认可review-04，实际执行与人工确认分别记录。
- 9.5/9.6：未同步主规格、归档、提交或推送；保持活动，等待条件及授权。

2026-11-15：教师已有配置权限范围内的参考文件影响评分不认定为漏洞或阻断项；保留实验事实，不新增过滤或模型审核。五个账号口令已轮换，种子关闭，实际门禁结果见review-04补充。

2026-11-16成员明确确认review-04及CSRF等价口径，授权任务8.3通过后同步归档。1.2未在实施前完成，保留为流程偏差；9.6由用户后续提交，两项不补勾为已执行，用户已确认按该状态归档。

2026-11-16：任务8.3受控红绿已完成，三份delta已同步主规格并通过严格校验，change已按用户授权归档。28项记录为完成；1.2保留实施前确认缺失的流程偏差，9.6为用户后续git提交，两项不伪造为已执行。
