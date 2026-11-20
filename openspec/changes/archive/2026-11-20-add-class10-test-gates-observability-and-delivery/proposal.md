# Class10 测试门禁、可复现交付与运行验证

## Why

Class09 已把安全门禁和审计补齐。Class10 补充四项教学行为守护、功能门禁红绿、可追溯镜像、观测和压测证据，并验证托管 CI 与另一成员独立复现。

## What Changes

- 补齐四项教学行为的聚焦守护：解题引导的直接要答案场景、作业批改的迟到结果不覆盖终评、错题讲解评语依据、知识库跨班隔离；建立“规约场景 → 用例 → 执行入口”追溯表。
- 保留 `make check`（unit/integration/api）与 `make check-security`（scan/access/audit/api/unit）双门禁；新增可执行的受控红绿证据，证明失败退出非零、修复后退出 0。
- 新增 GitHub Actions：托管运行器执行功能门禁（unit/integration）和带 `GIT_COMMIT` 的镜像构建。完整安全门禁在独立团队环境运行，使用私有配置。
- Dockerfile 增加 OCI `revision` 标签，Compose 支持 `IMAGE_TAG`/`GIT_COMMIT`；健康接口版本可反映镜像标签，部署版本可反查提交。
- 新增 `scripts/bench.py`：提交、任务查询、检索三接口按多并发档位压测，使用独立合成作业/学生数据和唯一标识，结束后清理；队列初始非空时拒绝混跑。
- 新增查询向量 LRU 缓存（`EMBEDDING_CACHE_SIZE`，默认 256，0 关闭），只缓存文本到向量的确定性结果，不改变检索命中、权限或班级隔离；同脚本同参数给出前后对比。
- 新增 `scripts/observe.py` 与 `scripts/alert_receiver.py`：从既有消息、后端 JSON 日志和 RabbitMQ 管理 API 派生三技能调用量、每技能错误率、API P95 与队列深度；生成可按窗口重跑的面板；告警规则包含持续条件、负责人、动作和面板，实际 POST 到接收器并留证。
- 新增 `scripts/seed_observability.py` 生成三条可归属的合成技能调用，在清理前生成面板快照。
- 新增 Class10 记录和 `reports/` 固定材料；同步 README、项目状态与运维/测试规范中的配置和入口。

## Capabilities

### New Capabilities

- `delivery-gates`: 功能/安全双门禁、受控红绿、CI 与镜像提交追溯、三接口压测、派生观测面板、真实告警送达及证据边界。

### Modified Capabilities

- 无。检索缓存不改变 `knowledge-retrieval` 的命中、采信、权限或协议契约；四项教学行为的既有主规格保持不变，本 change 只补测试与证据。

## Impact

- 构建与部署：三个 Dockerfile 的标签、Compose 版本配置、Makefile 入口与托管 CI。完整安全门禁在独立团队环境中运行。
- 后端：仅 `OpenAiEmbeddingGateway` 增加有界查询向量缓存和 `AppProperties.Embedding.cacheSize` 配置；不新增 HTTP 接口、数据库表或迁移，不改变检索结果、SSE 或授权。
- 工具：新增压测、观测、告警接收和观测数据种子脚本及脚本测试；证据写入忽略目录，仓库只保留脱敏摘要和链接。
- 配置：新增可选 `EMBEDDING_CACHE_SIZE`（默认 256，0 关闭）；`APP_VERSION` 默认取镜像标签；`.env.example` 与运维规范同步说明。新增经用户确认、到期 2026-12-18 的开发依赖例外 `security-exceptions.json`；必须显式传入，原始 5 个 high 保留，不写成 0 告警。
- 权限与隐私：观测只使用技能名、状态、耗时和队列深度，不导出用户正文、提示词、令牌或材料内容；压测使用合成班级资源和唯一标题，结束后按 ID 清理。
- 未改变：现有业务行为、数据库迁移、认证方式、准入阈值、MCP 白名单和审计保留规则。

## Non-goals

- 不引入 Prometheus/Grafana/Alertmanager 或新的应用指标接口；本 change 的观测是从既有事实派生的轻量工具，不宣称已有生产监控平台。
- 三名用户反馈与手机验收已移出本轮范围。另一成员独立复现和交叉评审仍由执行人记录。
- 不通过降低阈值、扩大权限、跳过扫描或伪造数据让门禁变绿；开发依赖例外只按用户确认的公告/包/到期日显式生效，原始告警保留。
- 不为性能数字修改业务契约；缓存只用于确定性查询向量，不缓存授权、评分或用户私有结果。
