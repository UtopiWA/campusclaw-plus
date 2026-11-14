# Class09 安全修复、技能审计与安全门禁

## Why

Class08 安全自查保留了 SEC-07 红项：技能执行只记录技能名称，没有稳定技能 ID，也没有模型真实 Token 用量；SEC-08 提示注入实验尚未执行。第 9 课要求把真实缺口补齐，并用常驻反例、可回查审计和失败即非零退出的安全门禁证明修复有效且原有功能未回归。当前没有活动 change，Class09 尚未开工。

## What Changes

- 新增 V14 迁移：为 `messages` 增加稳定 `skill_id`、模型 Token 用量、模型耗时和请求标识；新增只记录拒绝/流前失败等“无法表示为助手消息”的 `security_audit_events`。成功技能轮次继续以 `messages` 为事实来源，避免重复写审计。
- 补充模型 usage 采集：非流式和流式均解析供应商返回的 `usage`；供应商未返回时保持 `NULL`，不估算、不写 0。
- 新增教师本班审计查询 `GET /api/audit/events`，支持按技能、操作人和时间窗回查；学生不可读，跨班不可见；只返回安全元数据，不返回提示词、作答或凭据。
- 记录拒绝尝试：学生修改技能/助手配置、越权终评等动作在返回 403/404 前写入审计事件；合法本人提交和既有授权行为不变。
- 在 `apitest/cases/` 增加常驻 SQL 注入、XSS、CSRF 等价反例，并补充前端 DOM 级 XSS 断言；CSRF 按项目的 Bearer JWT 无状态机制等价验收，不伪造 Cookie/SameSite 证据。
- 重放 SEC-01～06，保留 Class08 原始红项；密钥扫描以 0 命中为目标，保留候选时记录限定处置和真实退出码；依赖高危清零或书面处置。
- 执行 SEC-08 四步实验，记录模型实际行为与工具/接口执行结果；越权写入必须被代码拒绝并留痕，不新增危险工具或伪造调用日志。
- 新增统一安全门禁 `scripts/security_gate.py` 和 `Makefile` 的 `check-security`/`check` 目标；门禁消费各步骤真实退出码，禁止 `|| true`、降阈值或跳过范围；用受控合成故障留一次先红后绿证据。README 同时记录 Windows 无 `make` 时的 Python 等价入口。
- 新增/补充 `reports/` 下的修复台账、审计回查、SEC-08 实验、门禁红绿、反例记录和评审记录；保留 `security-precheck.md`、`review-01.md` 等历史证据。

## Capabilities

### New Capabilities

- `security-audit`: 稳定技能标识、真实模型用量与耗时、拒绝/失败留痕、教师本班受保护回查及字段安全边界。

### Modified Capabilities

- `security-precheck`: 删除 SEC-07 技能 ID/Token 用量“搁置”条款，增加 Class09 复测、资产处置、SEC-08 和失败即非零退出的安全门禁要求。
- `api-regression-harness`: 增加 SQL 注入、XSS、CSRF 等价安全反例常驻、请求头/上传辅助能力和回归要求。

## Impact

- 数据：新增 Flyway V14；`messages` 只增加可空列和索引，`security_audit_events` 为追加式新表；不修改 V1～V13，不要求历史数据回填。
- 后端：`gateway` 增加 usage 解析；`conversation` 持久化技能 ID/usage/耗时；新增 `audit` 包、教师审计接口和拒绝事件写入；不改变现有 SSE 事件顺序、`done` 语义和业务错误状态。
- 前端：不改变页面行为和认证方式；只增加材料标题、Markdown 渲染的 DOM 级安全测试。
- 测试与工具：扩展 `apitest` 公共请求/上传能力，新增三条安全用例；新增 `scripts/security_gate.py`、`Makefile`、SEC-08 实验脚本和安全检查扩展。
- 文档与证据：新增 `reports/security-fix-log.md`、`audit-recheck.md`、`sec08-experiment.md`、`gate-red-green.md`、反例记录和评审记录；追加更新 `security-baseline.md`、`risk-authz.md`；不覆盖 Class08 历史文件。
- 权限边界：审计查询仅教师、仅本班；学生 403；跨班空集合；所有班级仍来自服务端当前用户，不接受客户端 `classId`。

## Non-goals

- 不把认证改为 Cookie 会话，不实现 SameSite/CSRF token，不为凑样例替换现有 Bearer JWT 架构。
- 不新增成绩写入或其他危险 MCP 工具，不伪造工具调用或模型执行结果。
- 不为演示新增 Playwright/浏览器自动化；XSS 使用现有 Vue Test Utils + jsdom。
- 不为达成绿而降低采信阈值、扩大权限/出站白名单、删除审计或跳过校验。
- 不扩展到评分/学习计划的模型 usage 持久化，不审计所有成功配置写入；本次只覆盖技能执行和明确的拒绝/失败动作。
- 不修改已交付迁移，不重写 Git 历史，不在未授权时提交、推送、归档或创建标签。
- 不代签成员评审或交叉复测；人工结论必须由实际成员填写。
