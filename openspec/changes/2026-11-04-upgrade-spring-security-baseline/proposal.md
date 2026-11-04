# Proposal

## Why

Class08 独立复核后仍有两项 Spring critical 和五项 Maven moderate，成员 A 已明确“暂不接受风险，保留活动 change”。本 change 将框架与依赖整改独立管理，避免以当前未使用触发路径代替修复，或把大版本迁移混入异步批改复核。

状态：**仅完成升级规划，未实施、未验收**。Class08 保持活动，其现有工作区修正保留；本 change 完成不自动代表 Class08 评审、归档或发布通过。

## What Changes

- 将后端升级至 Spring Boot **4.0.9**，采用其 Spring Framework **7.0.10** 基线；保留 Java 17，核对 Spring Security、JPA/Hibernate、Flyway、AMQP 和 Servlet 容器的兼容性。版本依据见 design；实施时若已新增漏洞，先更新本方案，不能照抄版本后宣称安全。
- **BREAKING（内部依赖及测试 API）**：迁移 Jackson 3、Boot 4 的 starter/测试包、`@MockitoBean` / `@MockitoSpyBean`，选用与 Boot 4.0 匹配的 springdoc 3.0.x 补丁版本。外部 HTTP、JSON、SSE、权限和数据契约保持兼容。
- 逐项处置 amqp-client、commons-lang3、log4j-api 告警；不能假定 Boot BOM 自动修复全部传递依赖。移除不再适用的 Boot 3 版本覆盖，必要安全覆盖必须有确切版本和复扫依据。
- 复扫 Maven、npm 与 Python；npm 低/中危工具链问题登记兼容补丁路径，不使用 `audit fix --force`。任何残留告警须逐项说明，未经成员明确接受保持未完成。
- 在独立环境验证旧库升级兼容、Web/worker 冷启动、JSON 严格校验、JWT/SSE/MCP 和异步批改核心判据；沿用现有检查入口与 lesson-08 记录，不新建验证目录。

## Capabilities

### New Capabilities

无。本次仅做安全补丁、框架迁移及兼容修正，不新增业务行为。

### Modified Capabilities

无。已检查主规格 `team-development-baseline` 及当前 Class08 契约；本次必须保持既有外部行为，`.openspec.yaml` 使用 `skip_specs: true`，不为依赖升级虚构 delta spec。若实施发现必须改变外部契约，应先调整本方案并补充相应规格，再实施。

## Impact

- 涉及 `backend/pom.xml`、Java JSON/框架适配及测试、后端配置与镜像；必要时同步 Compose、扫描脚本和文档。前端只允许告警相关的依赖/锁文件兼容修正。
- 不新增数据库业务字段或修改已交付迁移；检查现有 V8 在升级框架下的行为，不能以清库解决兼容问题。
- 输入基线是 `5cdd020` 加 Class08 独立复核的工作区修正；实施前记录实际候选提交/差异，不能覆盖或丢失这些修正。Class08 的主规格同步、归档与提交仍暂停。
- 成员按开发/测试整体分工，另一成员复核；代理整理结果不代签。风险和既有依据见 [review 7.5](../../../docs/lesson-08/review.md#75-依赖遗留与成员决定暂不接受风险)。

## Non-goals

不增加教师复核、题库、诊断等业务；不重构任务框架、改用 WebFlux、改变 RAG/MCP 路由或 JWT 机制；不升级 Java 主版本；不引入商业 Spring 补丁；不放宽扫描阈值或添加漏洞忽略；不自动提交、归档、推送、打标签或迁移真实数据库。
