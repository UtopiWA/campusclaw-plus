# Class10 AI 自审记录

日期：2026-11-18
评审对象：`master` @ `b30ab9d` 加 Class10 未提交修改
AI 生成范围：本轮新增的 Docker/Compose/Makefile/CI 配置、`bench.py`、`observe.py`、`alert_receiver.py`、`seed_observability.py`、`OpenAiEmbeddingGateway` 查询向量缓存、配置字段、聚焦测试和 Class10 文档。
性质：**这是代理自审，不是另一成员的人工交叉评审，不代签成员结论。** 实际命令见 `docs/lesson-10/README.md`；未执行项保持未完成。

## 1. 实际执行

| 检查 | 结果 |
| --- | --- |
| `python scripts/check.py --mode unit` | 退出 0；后端 256、前端 158、脚本 47、MCP 8 |
| `python scripts/check.py --mode integration` | 退出 0；44 测试 |
| `python scripts/check.py --mode api` | 退出 0；安全门禁内首次失败后同条件重跑通过 |
| 功能门禁受控红→绿 | 退出 1→0；失败用例/日志保留 |
| `python scripts/security_gate.py --env-file .env.team` | 退出 2；scan 2（npm audit 5 high）、access/audit/unit 0、api 首次 1 后重跑 0 |
| 同上加 `--exceptions security-exceptions.json` | run `20261117T181025Z-73908`：scan/access/audit/api/unit 全 0，总退出 0；原始 5 high 保留，不记为 0 告警 |
| `python scripts/bench.py ...` | 小样本与 16/32/64 档位实际运行；清理完成 |
| `python scripts/observe.py ...` | 三技能面板、告警触发与 HTTP 202 送达 |
| 三镜像直接 `docker build` | 成功；revision 标签与 health 版本核对 |

## 2. 逐文件结论

| 文件 | 结论 | 说明 |
| --- | --- | --- |
| `backend/src/main/java/com/campusclaw/gateway/OpenAiEmbeddingGateway.java` | 可用 | 有界 LRU；key 只含查询文本；`cacheSize=0` 不写缓存；未改变过滤/授权；并发下可能重复首查，但结果一致 |
| `backend/src/main/java/com/campusclaw/config/AppProperties.java` | 可用 | 新字段有 `@Min/@Max` 和默认值；不再保留额外构造器，避免构造绑定歧义 |
| `backend/src/main/resources/application.yml` | 可用 | 只新增可选 `embedding.cache-size`；默认 256 |
| `docker-compose.yml` | 可用 | 镜像标签/构建参数/APP_VERSION/缓存配置；未新增端口或秘密；`backend`/`worker` 共用同一镜像 |
| `backend/Dockerfile`、`frontend/Dockerfile`、`mcp-server/Dockerfile` | 可用 | 只加 OCI revision 标签；未复制私有配置；MCP 脚本仍为运行必需 |
| `Makefile` | 可用 | 目标与 Python 入口一致；无 `|| true`；`pipeline` 顺序为功能→安全→构建 |
| `.github/workflows/ci.yml` | 待修改（未实跑） | 语法和命令合理；未在远端真实运行，托管运行器的网络/缓存/Testcontainers 表现未验证 |
| `.github/workflows/security-gate.yml` | 待修改（未实跑） | 正确要求私有文件和 Docker；自托管运行器与模型环境未提供，未执行 |
| `scripts/bench.py` | 可用 | 合成数据、唯一作业、终态等待、精确清理、队列非空保护；未覆盖多实例/长时间浸泡 |
| `scripts/observe.py` | 可用 | 只读既有事实；未知值标不可用；4xx 不入 5xx；本地 HTML 非长期平台 |
| `scripts/alert_receiver.py` | 可用 | 只监听回环、追加 JSONL；不对外发送消息 |
| `scripts/security_exceptions.py`、`security-exceptions.json` | 可用（有期限例外） | 显式 `--exceptions`、批准/到期/补偿措施校验；只覆盖 GHSA-fc5q-39xc-vjpf；保留原始 5 high；新公告/过期即失败 |
| `scripts/scan.py`、`check_security.py`、`security_gate.py` | 可用 | 例外只影响 npm audit 阶段，其余扫描继续；原始 JSON/失败码保留；未传例外仍红 |
| `scripts/seed_observability.py` | 可用 | 三条唯一技能、快照后恢复/删除；未改真实成绩/材料 |
| `scripts/tests/test_bench.py`、`test_observe.py` | 可用 | 覆盖分位数、窗口、空指标和 4xx/5xx 口径 |
| 聚焦测试（AgentPromptBuilder/Grading/Retrieval/Gateway） | 可用 | 不依赖真实模型；断言能区分错误实现 |
| `.env.example`、`docs/standards/05-operations.md`、README/项目状态/Class10 文档 | 可用 | 配置说明同步；未执行项如实记录 |

## 3. 七维检查

| 维度 | 结论 |
| --- | --- |
| 接口入口 | 未新增 HTTP 接口；缓存不改变检索入口/参数/返回；CI/脚本入口使用既有配置，不接受客户端 classId |
| 业务逻辑 | 缓存只跳过重复 Embedding 调用，检索仍执行关键词/向量、RRF、采信门、READY 和材料白名单；四项教学行为测试对应既有契约 |
| 数据库操作 | 未新增表/迁移/查询；压测清理按唯一标题/ID 核对后删除，顺序满足外键；未使用全库删除 |
| 公共函数 | 复用 `TestConfig`/`HttpClient`/`DatabaseClient`；未复制授权或错误映射；未知观测值不写 0 |
| 错误处理 | 门禁失败非零；缺配置/工具退 2；压测异常保留 JSON 并尽力清理；告警未送达标 false |
| 安全与隐私 | 观测不导出正文/提示词/令牌；Gitleaks 0 命中；npm audit 5 high 保留红，未降级；镜像不带私有配置 |
| 性能 | 有界 256 条缓存；压测显示重复查询改善；未做长期内存/多实例验证；缓存不改变正确性路径 |

## 4. 并发、事务与一致性

- 并发：缓存 `get/put` 受同一锁保护，LRU 访问顺序安全；并发首发可能重复调用供应商，但返回向量一致；不缓存授权/评分，避免越权复用。
- 事务：压测提交仍走既有短事务/行锁/outbox；脚本不直接改业务表，只在清理阶段按 ID 删除已终态数据。
- 一致性：任务查询/清理前等待 `SUCCEEDED/FAILED`；若超时脚本报错并保留原始 JSON，不把 PENDING 当完成；队列初始非空拒绝混跑。
- 终评：自审确认迟到自动批改只写 `grading_results`，`GradingTaskCompletionService` 不依赖/不修改 `submission_reviews`；查询同时返回 AI 结果和终评。

## 5. 未验证与残余风险

- 安全门禁字面“高危 0”未达到：`picomatch <=4.0.7`（GHSA-fc5q-39xc-vjpf）无当前修复版本；用户 2026-11-18 确认有期限开发依赖例外，门禁以 `--exceptions` 通过，原始 5 high 和失败码保留。该例外不是漏洞修复，到期 2026-12-18 前必须复查。
- API 首次模型波动未做重复压力确认；真实模型质量、错题讲解教学正确性未评测。
- 未推送/未运行 GitHub Actions；工作流未在远端验证。
- 未在干净目录/另一机器启动；未做镜像推送、回滚、跨机器复现。
- 未做真实手机 H5、三名用户反馈、另一成员独立启动/演示/人工交叉评审。
- 缓存未做多实例/长时间内存压力；未实现跨进程失效。

结论：代码与脚本层自审为“可用/待修改（CI 未实跑）”，不构成成员评审或 Class10 全部通过；开发依赖例外已由用户确认并保留原始告警，人工项（CI 实跑、干净环境、手机/真实用户、交叉评审）仍须用户/团队处理。
