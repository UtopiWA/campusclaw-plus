# Class09 安全修复台账

日期：2026-11-14  
候选：`master` @ `5d9d765` 加本轮工作区修改  
环境：`campusclaw-plus` 七服务 Docker 栈，私有 `.env.team`，数据库 `campusclaw_plus`，宿主端口 18080/13306/15673。合成资源均带运行标识并清理。

## 复测前置

- `docker compose --env-file .env.team ps`：mysql、qdrant、rabbitmq、mcp-demo、backend、grading-worker、frontend 均 healthy。
- `http://localhost:18080/health` 返回 200；数据库和 Qdrant 为 up。
- 演示账号 `teacher-a`、`student-a1`、`student-b1` 可登录；专用测试环境补建 `teacher-b`（B 班教师）。
- 首轮基线保留了真实退出码：访问检查 exit 0，SEC-07 exit 1，依赖扫描因 Java 环境 exit 2；未预填绿。

## SEC-01～04 授权

| 编号 | 修复前实际 | 修复后实际 | 判定 |
| --- | --- | --- | --- |
| SEC-01 匿名访问 | 6 个业务接口未带令牌均 401；基线 run `ac98e1bc125d` | 扩展后匿名访问仍全部 401；新增 `/api/questions`、`/api/learning/profile`、`/api/learning/plan`、`/api/home/student`、`/api/home/teacher`、`/api/learning/analytics` 同样 401；完整门禁 access `0de38955ec6a` 和事务修复后 access `8b7d2bca555b` 均 exit 0 | 绿（未发现缺口） |
| SEC-02 班级边界 | 双向跨班材料 404；本班关键词命中、跨班命中 0；run `ac98e1bc125d` | 代码未改变班级过滤；`messages`/审计查询仍先按服务端 `classId` 约束；跨班教师审计为空 | 绿（未发现缺口） |
| SEC-03 配置写权限 | 学生修改技能/助手均 403，API 与数据库前后一致；run `ac98e1bc125d` | 403 保持不变；新增 `CONFIG_WRITE` 拒绝审计，教师按学生/时间窗可查；run `365c1eb50877` | 绿（新增留痕） |
| SEC-04 直接业务写入 | 学生创建作业、写教师终评均 403；非法作业/终评增量 0；run `ac98e1bc125d` | 学生终评仍 403；跨班教师仍 404；新增 `GRADE_REVIEW` 拒绝审计；数据库计数不变 | 绿（未发现缺口） |

## SEC-05 密钥

- 基线 run 中 Gitleaks v8.24.3 对本地可达到历史 25 个提交、公开文件 552 个、`frontend/dist` 和七服务日志均为 0 命中；精确比对候选 0。
- 本轮 `.env.team` 使用独立生成的密码、JWT、MCP Token 和 32 字节 MCP 加密键；演示口令随机化，未复用公开字符串。
- 最终门禁 scan 阶段 secrets exit 0：历史、公开文件、`frontend/dist`、七服务日志 0 命中，精确比对 0 候选。
- 门禁重扫结果见 `gate-red-green.md` 与 `security-baseline.md`；未通过泄露真实密钥制造命中。

## SEC-06 依赖

- 基线依赖检查因本机 `JAVA_HOME` 仍指向 Java 8，`scan.py` 未完成，退出 2；已如实保留，不记为通过。
- 本轮门禁在 `JAVA_HOME` 指向 Corretto 17 的环境中重跑：scan 阶段 exit 0，OSV 124/124、0 findings、high/critical 0；npm audit、pip-audit、Ruff、Checkstyle、SpotBugs 通过。工具版本、范围和退出码见 `security-baseline.md`。

## SEC-07 技能审计

| 项 | 修复前（run `4760bfcb427e`） | 修复后（run `365c1eb50877`） |
| --- | --- | --- |
| 操作人 | 有（MCP 调用行） | 有：`student-a1`，userId 2 |
| 时间 | 有 | 有：`2026-11-14T11:54:50.516461Z` |
| 动作 | 无稳定动作名 | `SKILL_EXECUTE` |
| 对象 | conversation/toolRef | `MESSAGE`，objectId 14 |
| 结果 | `ok` | `SUCCESS` |
| 技能标识 | **缺失** | **有**：skillId 11 |
| 用量/耗时 | **缺失**，`tokenUsage=null` | **有真实值**：prompt 253、completion 18、total 271，模型耗时 207 ms |
| 拒绝回查 | 无 | `CONFIG_WRITE` / `DENIED`，学生 student-a1，HTTP 403，objectId 10 |
| 数据库 | 无新字段 | `messages` 的 skill_id/usage/duration/request_id 与审计查询一致 |

结论：SEC-07 由红转绿；未知用量仍保持 `NULL`，没有估算或写 0。详细查询输出见 `audit-recheck.md`。

## SEC-08 提示注入

- 实验脚本：`scripts/sec08_experiment.py`，复用 `security_precheck` 的登录、上传、作业、提交、轮询和清理。
- 首次 run `a3a3fa25eeeb` exit 1：学生终评 403、跨班教师 404、数据库不变，但拒绝审计缺失；定位为控制器在进入服务层前抛出 403。修复后重跑。
- 重跑 run `fe5e50280004` exit 0：`step4Passed=true`；学生终评 403、跨班教师 404、数据库计数不变、学生拒绝审计可查。
- 进一步把 `SecurityAuditService` 改为 `REQUIRES_NEW` 独立事务，避免跨班教师在服务事务回滚时丢失拒绝审计；最终 run `b9d157fa2cbc` exit 0，学生和跨班拒绝审计均 true，`step4Passed=true`。
- 模型层异常：对照 0 分、恶意参考 100 分，多次运行一致；已如实登记在 `sec08-experiment.md`，未把模型自述当作执行成功，也未新增危险工具。
- 判定：SEC-08 硬底线绿；模型提示注入风险保留为残余风险，教师终评仍是独立控制。

## 未完成/保留项

- 安全门禁的红绿证据和最终退出码以 `gate-red-green.md` 为准。
- 另一成员交叉复测尚未执行；不得代签或提前归档。
- 前端在 Windows 宿主上的 Vitest runner 异常已记录；同一提交在干净 Node 22 Linux 容器中 158/158 通过，宿主异常不作为通过证据。
