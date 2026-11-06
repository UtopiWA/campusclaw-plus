# Design

## Context

动机及范围见 [proposal](proposal.md)。升级前基线使用 Boot 3.5.16，覆盖 Tomcat 10.1.59、Jackson 2.21.7、Netty 4.1.137.Final、amqp-client 5.34.0；springdoc 为 2.10.0，测试使用 `@MockBean` / `@SpyBean`。JSON 不仅用于 HTTP，还用于模型严格解析、Qdrant/MCP、评分依据快照和日志，因此不能只改 parent 后以编译成功验收。当前实际版本和验证见下方实施核对及 lesson-08 第 8 节。

Class08 的数据一致性修正已包含在输入提交 `d5167f3`，change 尚未归档。升级需保留作业行锁、attempt 隔离、publisher return 检查、worker 初始化边界和页面请求取消等现有行为，不以旧 HEAD 覆盖修正。验收继续写入 [lesson-08](../../../docs/lesson-08/README.md)，新增“依赖升级”小节区分升级前后的实际结果。

## Goals / Non-Goals

**Goals:** 在 Java 17 上完成受支持的 Boot 4.0 依赖组合，修复已登记告警并保留外部契约；以有效依赖树、完整扫描和业务复现作为结果依据。

**Non-Goals:** 不添加业务场景或表字段，不调整身份/权限、RAG 阈值、评分公式、SSE 事件和外部配置默认；配置名称若因框架迁移确需映射，保持现有环境变量可用并显式说明。其余非目标见 proposal。

## Decisions

### 1. 使用 Boot 4.0.9 平台组合，逐项审计覆盖版本

规划目标为 Boot 4.0.9 / Framework 7.0.10，而非把 Spring 7 强塞进 Boot 3。Java 17 保留，内嵌容器按平台迁至 Tomcat 11 / Servlet 6.1。系统要求与版本依据：[官方系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[BOM 坐标](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)。Spring 两项告警的公开修复为 7.0.9，详情见 [review 7.5](../../../docs/lesson-08/review.md#75-依赖遗留与成员决定暂不接受风险)。

对照 effective POM / dependency tree 核对实际版本，移除 Boot 3 的 Tomcat/Jackson/Netty 覆盖并复扫。4.0.9 BOM 仍列 amqp-client 5.27.1，Rabbit 客户端固定使用已发布的 **5.37.0**，实际验证确认/返回/ACK 兼容；告警修复界限标为 5.36.1，但该版本未在 Central 发布，不能直接作为依赖版本。commons-lang3 3.19.0 和 log4j-api 2.25.5 已由平台管理且处于修复范围，不额外覆盖。不能只改版本或屏蔽扫描便声称已修复。

替代方案：商业 6.2 补丁不在当前预算与交付范围；当前路径不适用的风险接受已被成员拒绝。Boot 4.1 不作为本次默认升级目标，避免扩大迁移跨度；如 4.0 目标出现新漏洞，应先更新方案再实施。

### 2. 一次迁至 Jackson 3，显式保留序列化和校验行为

实施版本核对：Maven Central 未发布 amqp-client 5.36.1，已选用可获取且高于修复界限的 **5.37.0**，以完整 OSV 复扫确认。实际 Boot 4.0.9 BOM 为 Jackson 3.1.7、Tomcat 11.0.27、Netty 4.2.19.Final、commons-lang3 3.19.0、log4j-api 2.25.5；后两者已在登记修复范围内，不再额外降级/覆盖。springdoc 固定 **3.0.3**。

按 [官方迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) 迁移 mapper、reader、异常及配置；注解包不能机械替换。覆盖 HTTP 入参/响应、时间/枚举、可选字段、持久化 JSON、评分输出未知字段/尾随文档拒绝、MCP schema 和日志白名单。读取升级前生成的评分依据、会话快照和工具快照，确认旧 JSON 仍可读。

不同时留下两套自动配置 mapper。官方 Jackson 2 兼容模块已弃用，故不作为默认方案；若第三方兼容性迫使采用过渡模块，先把具体原因、边界和清理任务写入本 change 并重新评审，不能静默加入。

### 3. 同步框架适配与测试，保留真实断言

迁移 Boot 4 的 starter、切片测试包和 Mockito 注解；springdoc 选择 **3.0.x** 中已发布且复扫无阻断问题的确切补丁版本，记录选择依据。[官方兼容矩阵](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) 将 Boot 4.0 对应至 springdoc 3.0.x。

保留手写 `StreamingResponseBody` JSON SSE，不改成视图片段；校验首帧前后失败、`done` 与持久化顺序。保留模型永久失败分类与其他网关的原重试次数。检查 JWT 的当前数据库身份、学生本人/本班授权、评分/发件箱短事务和 Rabbit publisher confirms/returns。迁移测试 API 时不得删断言、改为跳过或清库规避 ORM/Flyway 错误。

### 4. 沿用扫描门槛，残留项必须显式处理

执行 `python scripts/scan.py` 并保留各子工具结果和有效依赖清单；OSV 必须全部查询完成，已登记七项 Maven 告警不再命中，新 high/critical 为零。npm/Python 扫描分别报告；对 low/moderate 采用兼容补丁，不运行破坏性自动修复。若任何既有或新增告警不能修复，列出具体编号、原因与后续动作并留待成员决定；本方案不预先接受任何残留风险。

OWASP 为现有规范中的可选补充；本次未执行记录保持真实。若实施时选择运行，NVD 未完成也不得冒称通过。扫描失败或不完整阻断验收，不能通过 suppression、改严重性或旧报告制造成功。

## Risks / Trade-offs

- JSON 默认值、时间格式、null 或未知字段处理改变 → 用旧数据快照及现有失败断言比较；模型解析仍严格拒绝非法输出。
- AMQP/ORM 主版本更改改变锁或投递行为 → 独立 MySQL/RabbitMQ 实测并发、回滚、重复投递、超时恢复和不可路由，不只验证 health。
- 测试包迁移造成少跑用例 → 对照升级前的三个 integration 类及测试清单，逐项解释数量变化，未登记跳过不通过。
- BOM 仍含已知漏洞或扫描出现新项 → 以实际依赖及当次完整报告为准，必要安全覆盖需兼容验证，不能把平台管理当作安全证明。
- 迁移扩大为业务重构 → 超出兼容修正的行为先更新 change，不在本次顺带实现。

## Migration Plan

本轮用户已授权实施，基线为 `d5167f3`，先前复核修正已提交。部署默认使用独立项目 `campusclaw-plus` 和端口 18080/13306/15673；私有 `.env.team` 不提交，原 `.env` 及个人项目容器/数据保留。镜像与具名卷由项目名隔离，Docker Desktop 的 D 盘磁盘设置仅核验，不迁移或重启整个引擎。先用升级前应用在团队空库生成合成 V8 数据，再升级同一专用数据库；另外使用独立临时卷验证冷启动。团队最终实例保留运行，供人工验收。

1. 实施前记录 Class08 实际候选提交和本轮修正差异，确认依赖升级方案已评审并获明确实施指令。采用开发/测试整体分工，避免按单个功能层拆分。
2. 在隔离环境执行升级、单测/构建、完整扫描及三组 integration；记录确切版本和报告边界。
3. 同时启动独立 Web/worker；测试空库，以及由升级前应用生成的合成 V8 数据库/JSON。保留原数据，确认 Flyway 不改已交付校验和、旧记录可读，新增提交正常；若不兼容，阻断并修正，不连接真实业务数据库。
4. 实跑现有 API、JWT/班级权限、材料/MCP/流式对话和页面；作业重复执行三条核心判据、DLQ 和模拟 RUNNING 恢复。升级前结果仅作比较，不能当作升级后通过证据。
5. 另一成员复核结果及残留风险；通过后再分别判断依赖 change 与 Class08 的完成条件。成员未明确接受风险时继续保持活动；提交、归档、标签按各自授权执行。

回退使用已记录的升级前候选代码与镜像，不修改数据库业务结构、不清理业务卷；先在自有合成数据上确认旧版本可读。当前不执行真实部署或回退演练；若发现必须迁移数据结构，先停止并补充迁移方案与评审。
