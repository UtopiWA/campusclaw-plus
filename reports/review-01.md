# Class08 同伴复核记录

状态：**已在本机独立 Docker 环境执行，结果如下**。操作顺序见[交接待办](../docs/lesson-08/handoff.md)，本文件只记录实际复核结果。

已提交基线为 `e7507b6`，本次实际交付为 `af9cb93`（另含本轮验收文档修改）。技能 ID 和 Token 用量按用户决定搁置，不列入本次复核或归档前置条件，原始 SEC-07 红项保留。

| 项目 | 当前记录 |
| --- | --- |
| 复核成员 / 日期 | 王家兴，2026-11-06；本机独立环境，命令由协作代理按成员指令执行并保留原始输出 |
| 候选提交 / 环境 | `af9cb93`；Windows 11 + Docker Desktop 29.9.2 / Docker Compose v5.6.1（WSL2 Linux 容器）；项目 `campusclaw-plus`，数据库 `campusclaw_plus`，宿主端口 18080/13306/15673；mysql、qdrant、rabbitmq、mcp-demo、backend、grading-worker、frontend 七服务 healthy |
| 独立启动与页面功能 | 通过。空库首次启动以种子创建账号，`teacher-a`、`student-a1`、`student-b1` 均登录成功后关闭种子并重建 backend；上传合成参考文件后索引 READY，“勾股定理”检索 1 条命中；教师绑定参考文件新建作业，学生提交后异步显示“AI 批改 100 / 100 分”，刷新仍可查看“我的作答”和批改结果，作答编辑框已被只读区替换；教师终评 88 分并填写评语，页面提示“终评已保存”，学生刷新后优先显示“教师终评 88 / 100 分”及评语。MCP 管理页登记 `campus-tool` 后“连接并发现”成功，发现 `formula_hint`、`calculator` 工具；以 SSE 提问“计算2的20次幂”，`toolCalls` 为 `calculator@campus-tool`、状态 `ok`，回答含 `1048576`。浏览器脚本自身用“已终评”文案判断保存结果为 false，实际接口、提示与学生端结果均确认保存成功，属断言文案与页面标签不一致，不是功能失败 |
| 异步任务、停启与重复消息 | 通过，run `51677ea651a9`，`execution=complete cleanup=complete`，退出 0。停 worker 后 4 个并发提交均返回 202 且无分数；任务保持 PENDING；恢复 worker 后自动 SUCCEEDED；子任务与结果各 1 行；补发 3 条重复消息后结果不变；同一学号换答案再次提交返回 409；跨班任务查询 404。停止与恢复期间无其他提交 |
| SEC-01 匿名访问：命令、接口与返回码 | 通过，run `832b93ccdad0`，`execution=complete cleanup=complete`，退出 0。命令：`./.venv-review/Scripts/python.exe -B scripts/security_precheck.py --env-file .env.team --checks access`。`GET /api/auth/me`、`GET /api/materials`、`GET /api/assistants`、`GET /api/conversations`、`GET /api/assignments`、`POST /api/retrieval/search` 未带 Token 均为 401（6/6） |
| SEC-02 跨班访问/检索：命令、返回码与数量 | 通过（同上 run）。跨班材料按 ID 读取 404、跨班任务读取 404；本班关键词检索命中 1 条，跨班关键词检索命中 0 条。附加 SEC-03 学生未授权配置写入 403/403 且配置哈希前后一致；SEC-04 越权批改 403/403，未新增未授权作业或终评 |
| 本轮合成资源清理 | 自动脚本 `cleanup=complete`；页面登记的 `campus-ui-tool` 已在页面删除；API 计算器验收的 MCP 服务与审计按交接保留至栈撤除，技能/助手/会话已删除；页面验收新建的合成材料与作业保留在本次专用部署，未写入仓库；截图保存在本地，未入库。未提交 `.env.team`、原始日志或运行产物 |
| 规则、代码交叉评审及发现的问题 | 见下方七维与逐文件结论；本轮未发现需修改的阻塞缺陷 |
| 结论 / 最终提交 | 通过（SEC-07 技能 ID/Token 用量按既定决定搁置，不计入本次门槛）；收尾提交 `docs: verify and archive Class08 changes`，未打标签、未推送 |

## 七维复核（CHG-2026-11-03、CHG-2026-11-04 交叉范围）

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 需求覆盖率 | 通过 | 页面、API、异步与授权检查覆盖交接所列判据；SEC-01/02 有本人环境实际值 |
| 需求一致性 | 通过 | 单次提交、202+任务查询、教师终评优先、参考答案综合评分与 AGENTS 一致；创建作业不调用模型 |
| 失败路径 | 通过 | 停 worker 保持 PENDING、恢复自动完成、非重试失败进死信、RUNNING 超时由恢复调度处理；本轮实际验证停启与重复消息 |
| 查询性能风险 | 通过 | 任务查询按 task_id 主键；教师列表按班级/作业索引并限制 200 条；发件箱按状态+可用时间索引分批 |
| 重复代码 | 通过 | 复用既有 Repository、Compose 与验收脚本，未新增平行队列或清理框架 |
| 漏洞风险 | 通过（范围内） | 班级与本人校验在服务端；学生不能写配置或终评；模型输出严格 JSON、分数范围与评语长度校验；未知属性/尾随内容拒绝 |
| 高风险场景（并发/事务/一致性） | 通过 | 提交事务同时写 submission/task/outbox；条件认领更新 attempts 与 RUNNING；完成写回校验 RUNNING+attempt；结果 `(submission_id, version)` 与 `task_id` 唯一；重复消息与旧执行迟到不覆盖结果；SEC-03/04 未越权写入 |

## 逐文件结论

| 文件 | 结论 | 说明 |
| --- | --- | --- |
| `backend/src/main/java/com/campusclaw/grading/SubmissionService.java` | 可用 | 行锁、幂等键/内容哈希去重、单次提交 409、事务内写发件箱 |
| `backend/src/main/java/com/campusclaw/grading/GradingWorker.java` | 可用 | 认领与模型调用分离，手动 ACK，可重试/不可重试分支明确 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskCompletionService.java` | 可用 | 以当前 RUNNING+attempt 判断迟到执行，结果唯一约束兜底 |
| `backend/src/main/java/com/campusclaw/grading/GradingOutboxPublisher.java` | 可用 | 事务外投递、发布确认与失败重试保留记录 |
| `backend/src/main/java/com/campusclaw/grading/GradingRecoveryScheduler.java` | 可用 | 超时 RUNNING 回收；构造时校验回收预算大于模型重试预算 |
| `backend/src/main/java/com/campusclaw/grading/GradingTaskQueryService.java` | 可用 | 学生本人/本班、教师本班校验；越权统一 404 |
| `backend/src/main/java/com/campusclaw/grading/SubmissionReviewService.java` | 可用 | 教师角色、本班作业/提交行锁、分数与评语长度校验 |
| `backend/src/main/java/com/campusclaw/grading/GradingModelService.java` | 可用 | 严格 JSON、整型分数范围、未知字段/尾随内容拒绝、评语长度与空值校验 |
| `backend/src/main/java/com/campusclaw/persistence/GradingTaskRepository.java` | 可用 | `@Modifying` 条件认领仅在 PENDING/到期/未达上限时递增 attempts |
| `backend/src/main/resources/db/migration/V8__assignment_grading.sql`、`V9__assignment_rubric_sources_and_reviews.sql` | 可用 | 结果、幂等键、提交版本与终评唯一约束完整，迁移只增不改 |

## 未执行 / 保留项

- SEC-07 技能 ID 与 Token 用量按用户决定搁置，不重跑、不修复，原始红项保留；本记录不声明“安全全通过”。
- 升级 change 的 OWASP NVD 检查仍未执行，依赖项按[评审记录](../docs/lesson-08/review.md)保留原结论。
- 本轮未重跑整套自动测试；实际执行的是页面、异步、授权与代码交叉复核，未覆盖项不记为通过。
