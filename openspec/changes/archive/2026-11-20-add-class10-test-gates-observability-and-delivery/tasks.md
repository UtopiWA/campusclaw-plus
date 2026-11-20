# Tasks

## 1. 基线与双门禁

- [x] 1.1 记录当前提交、工作区、Docker/Java/Python/Node 和七服务健康状态；运行功能三组与安全门禁的现有入口，保留真实退出码。
  Verify：`python scripts/check.py --mode unit`、`--mode integration`、`--mode api` 实际退出 0；`docker compose --env-file .env.team ps` 七服务健康；结果写入 Class10 记录。

- [x] 1.2 功能门禁受控红绿：在隔离工作区改坏一个测试期望，运行门禁记录失败用例和非零退出码；恢复后同命令复绿并记录退出 0。
  Verify：Class10 记录和 `reports/gate-red-green.md` 包含改坏位置、红输出摘要、恢复命令、绿输出摘要；不把坏补丁合入主干。

- [x] 1.3 安全门禁本轮复跑：先记录无例外红项（scan 2、access 0、audit 0、api 1 后重跑 0、unit 0、总退出 2；`npm audit` 5 high/GHSA-fc5q-39xc-vjpf），用户确认开发依赖例外后以显式 `--exceptions security-exceptions.json` 复跑并保留原始告警/失败码。
  Verify：无例外红 summary 与例外运行 summary 均写入证据；原始 5 high 保留，未写成 0 告警。

- [x] 1.4 新增 `security-exceptions.json` 和解析/测试：必须显式传入、批准人/日期/到期日/补偿措施齐全；每个 high/critical 包和 `via` 公告都必须覆盖，过期或新增未覆盖公告即失败。
  Verify：`scripts/tests/test_security_exceptions.py`、`test_security_gate.py` 通过；`scan.py --exceptions` 继续执行其余扫描；不传例外仍非零。

## 2. 四项教学行为守护

- [x] 2.1 补充聚焦测试：直接索要答案仍走引导提示；迟到完成不能替换终态；终评与迟到 AI 结果同时保留；评语要求依据/错误/改进；知识库跨班隔离沿用既有测试。
  Verify：`AgentPromptBuilderTest`、`GradingTaskCompletionServiceTest`、`GradingTaskQueryServiceTest`、`GradingModelServiceTest`、`RetrievalServiceTest` 定向执行通过。

- [x] 2.2 建立“规约场景 → 用例 → 执行入口”追溯表，覆盖四项教学行为和主要失败/边界路径。
  Verify：`docs/lesson-10/README.md` 中的表格逐项可定位到测试文件/入口；没有用例的场景标为缺口。

## 3. CI、镜像与启动追溯

- [x] 3.1 `Makefile` 提供 `check`、`check-security`、`build-images`、`up/down`、`bench`、`observe` 和本地 `pipeline` 目标。
  Verify：目标命令与 README/Class10 记录一致；`check` 使用 `--mode all`，安全门禁使用完整五阶段。

- [x] 3.2 修复托管 CI 的 Linux 检查入口和依赖安装，使三个镜像命名与 Compose 一致；完整安全门禁回到独立团队环境。
  Verify：本地等价检查及配置校验通过，远端结果记入 3.5。

- [x] 3.3 三个 Dockerfile 写入 `org.opencontainers.image.revision`；Compose 支持 `IMAGE_TAG`/`GIT_COMMIT`，`APP_VERSION` 默认取镜像标签。
  Verify：本地构建三镜像并 `docker image inspect` 得到 revision；重启后 `/health.version` 与镜像标签一致；证据写入 Class10 记录。

- [x] 3.4 在干净目录/另一机器按 README 启动完整七服务并走通功能；记录提交、命令、健康结果和镜像标签。
  Verify：需要独立工作副本和私有配置；未执行时保持未完成，不以当前机器结果代替。

- [x] 3.5 修复版推送触发 CI，功能门禁和三个镜像构建均通过；保存运行编号、提交 SHA 和镜像 ID/标签。
  Verify：`reports/pipeline.md` 记录修复提交对应的 GitHub Actions 结果。

## 4. 压测与缓存改进

- [x] 4.1 新增 `scripts/bench.py`：合成讲义/作业、三接口多档位、吞吐/P50/P95/错误率、清理与 worker 恢复、队列混跑保护。
  Verify：`scripts/tests/test_bench.py` 通过；小规模实际运行通过并清理业务表。

- [x] 4.2 新增查询向量缓存（`EMBEDDING_CACHE_SIZE`，默认 256，0 关闭）及配置绑定/缓存测试。
  Verify：`AppPropertiesBindingTest`、`GatewayClientTest`、`RetrievalServiceTest` 通过；缓存不改变检索结果和授权。

- [x] 4.3 用同一脚本/参数在缓存关闭和开启两种部署下复测三接口，记录在途峰值、吞吐/P95、错误率、瓶颈和副作用。
  Verify：`reports/bench.md` 第 6 节记录两轮受控复测。真实环境饱和点和 worker 最大吞吐另验。

## 5. 观测面板与告警

- [x] 5.1 新增 `scripts/observe.py`、`scripts/alert_receiver.py`、`scripts/seed_observability.py` 及脚本测试。
  Verify：`scripts/tests/test_observe.py` 通过；面板生成技能调用分线、每技能错误率、API P95/队列深度；不输出正文/凭据。

- [x] 5.2 创建三条可归属教学技能调用并在清理前生成面板快照。
  Verify：seed 脚本退出 0，面板中三条技能各有一条调用；清理后会话/技能已删除。

- [x] 5.3 制造可计量 5xx 失败，实际触发告警并 POST 到本地接收器，确认 2xx 送达；恢复依赖并记录触发/送达时间。
  Verify：`reports/observe-alert.md` 记录规则、窗口、失败窗口、触发/送达时间和接收记录；未送达不记为通过。

## 6. 证据、规范与评审

- [x] 6.1 新增/更新 Class10 记录和 `reports/` 材料；同步 README、项目状态、运维/测试规范中的入口和配置。
  Verify：文档链接可解析；命令、结果和未执行项与实际一致；`git diff --check` 无输出。

- [x] 6.2 新增 Class10 AI 自审记录，按文件给出“可用/待修改/风险无效”和七维检查。成员确认另见 7.3。
  Verify：`reports/review-05.md` 明确 AI 生成范围、实际命令和未验证项；不由代理代签成员结论。

## 7. 人工验收（代理不代签）

三名用户反馈和手机验收已移出本轮范围。

- [x] 7.3 另一成员独立启动、演示并记录结论；至少三份累计评审中至少两份来源为 AI 自审或脚本发现。
  2026-11-19，用户确认另一成员已审核完毕，本项可结项。见 [Class10 记录](../../../../docs/lesson-10/README.md#8-独立复现和交叉验收)。
