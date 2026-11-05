# Tasks

2026-11-04 用户已明确授权升级及独立 Docker 部署；输入基线 `d5167f3`，当时工作区干净，包含前轮修正。12/14 项技术实施及验证完成；4.1/4.2 等待实际成员复核与收尾，不能由代理勾选。实际结果见 `docs/lesson-08/README.md` 第 8 节。分工建议：成员 A 负责完整升级及自测，成员 B 负责独立复现和交叉评审；若双方调整角色，整段交换，不按接口/页面细碎分工。

## 1. 基线与升级前提（开发）

- [x] 1.1 评审 proposal/design 并取得明确实施指令；记录输入候选提交和 Class08 工作区修正范围。Verify：用户本轮明确要求按方案升级并以本机 Docker 为准；`d5167f3` 工作区干净，已包含前轮修正，未覆盖原修正。
- [x] 1.2 固定依赖版本矩阵：Boot 4.0.9 / Framework 7.0.10，选择已发布且匹配 Boot 4.0 的 springdoc 3.0.x 补丁，核对 Rabbit/commons/log4j 等修复版本与新增告警。Verify：记录确切版本、官方依据，执行 Maven effective-pom/dependency:tree 取得升级前清单，忽略目录保存原始产物；不以浮动 latest 作为最终配置。

## 2. 框架与依赖兼容修正（开发）

- [x] 2.1 升级 parent、starter 和相关依赖，处理 Boot 3 版本覆盖，修复已登记七项 Maven 告警。Verify：`backend/mvnw.cmd -f backend/pom.xml -DskipTests package` 成功，有效依赖树中 Spring/Servlet/AMQP 组合一致，不含旧 Tomcat 10 覆盖，Rabbit 客户端不低于已修复版本；不降低扫描门槛。
- [x] 2.2 完成 Jackson 3 与配置迁移，适配 HTTP、模型严格解析、Qdrant/MCP、持久化快照和 JSON 日志。Verify：对应聚焦测试通过；缺失/未知字段、null verdict、尾随 JSON 仍按原契约拒绝，旧 JSON/时间/枚举可读，HTTP 响应字段与日志白名单不变。
- [x] 2.3 迁移 Boot 测试包、Mockito 注解、springdoc 和必要安全/JPA/AMQP API。Verify：原三个容器测试类仍纳入 integration；JWT/SSE、短事务、attempt 隔离、publisher confirms/returns 聚焦断言保留并通过，不删除失败测试或引入未登记跳过。
- [x] 2.4 复查 npm/Python 告警并做本次必要的兼容补丁；更新锁文件及确有变化的工具要求。Verify：npm ci、前端测试/构建与相关 Python 测试通过，记录每项残留编号及原因；不运行 audit fix --force，不把未接受风险登记当作已解决。

## 3. 自测、完整扫描与文档（开发）

- [x] 3.0 配置独立团队 Compose 项目、私有配置与冲突端口；核实 Docker 数据盘位于 D 盘。Verify：现有容器/数据未修改，团队七服务正常启动，持久卷与镜像属于团队项目；保留最终实例供人工验收，记录磁盘占用及共享层口径。

- [x] 3.1 执行 `python scripts/check.py --mode unit` 和 `--mode integration`。Verify：实际计数/失败/错误/跳过写入 lesson-08 新增的依赖升级记录；三个独立容器类全部执行，解释与升级前用例数量差异；Docker 不可达不得跳过算成功。
- [x] 3.2 执行 `python scripts/scan.py` 并核对报告。Verify：各工具实际完成，OSV 查询清单完整、已登记七项 Maven 告警消除、新 high/critical 为零；低/中危逐项修复或保持未完成等待成员决定；扫描失败/未知/不完整不能记通过，OWASP 未运行就明确未运行。
- [x] 3.3 在独立 Compose 验证空库 Web/worker 同时冷启动，以及由升级前应用写入合成数据的 V8 数据库升级兼容。Verify：两服务可用，Flyway 校验和不变，原评分依据/会话/工具快照可读、新作业可提交；不清库绕过问题，不接真实数据库；记录旧候选与回退可读性结果。
- [x] 3.4 在该环境执行 `python scripts/check.py --mode api`、作业核心判据、DLQ/模拟 RUNNING 回收及权限矩阵，并走通材料/MCP/对话/作业页面。Verify：202 无分数、停 worker 不丢任务、恢复自动完成、重复结果一份；首帧前后失败/终态轮询/刷新恢复正确，日志消息无敏感正文；升级前记录不算升级后结果。
- [x] 3.5 同步 README、配置和运维说明及 lesson-08 记录。Verify：实际环境变量保持兼容，框架/镜像变更和未执行项写明，保留升级前结果与成员拒绝风险的历史；无凭据、原始敏感日志或仓库外路径引用。

## 4. 独立复核与交付门槛（另一成员）

2026-11-06：升级及后续业务修正已包含于 `45b2f7a`；最新安全检查见 `reports/security-precheck.md`。按用户要求，另一成员复现暂缓，待本轮其他任务完成后交付，再由对方完成实际评审与最终提交；以下两项保持未勾选。

- [ ] 4.1 从实际升级候选独立启动并复现受影响功能，完成七维、逐文件及并发/事务/一致性复核。Verify：另一成员实际记录结论和残留风险决定，尤其核对严格 JSON、JWT/SSE 和异步批改；不能只看 health 或开发者报告，代理不代签；无关自动测试不默认重复。
- [ ] 4.2 通过后分别核对依赖升级 change 与 Class08 的剩余门槛。Verify：`openspec validate --all --strict`、`git diff --check` 通过；本升级中未处理的依赖/兼容问题继续阻断，Class08 按其实际交付范围收尾；规格同步、归档、提交/推送按当时授权办理，版本标签仍须单独授权。
