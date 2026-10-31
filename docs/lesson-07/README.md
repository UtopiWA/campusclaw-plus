# Class07 团队基线验收记录

核对日期：2026-10-31。运行验收对应实现提交 `f7ca6f7`，后续已合入 MIT LICENSE；成员 A/B 的人工评审不由本记录代签。已归档 change：[2026-10-31-establish-team-baseline](../../openspec/changes/archive/2026-10-31-establish-team-baseline/proposal.md)。

## 本轮实际结果

环境类别：Windows 本地工具链、独立 Testcontainers 及 JDK 17 应用镜像。单元/容器用例使用替身；API 在获准调用真实模型后，于独立临时项目使用两班、四个合成账号及一份合成 READY 数学材料完成。凭据新生成，未读取或复制现有用户密码哈希。已验证从仓库外调用脚本仍定位到同一仓库；本机 JDK 为 20、编译目标为 17，实际应用另在 JDK 17 镜像启动验证。

| 检查 | 实际结果 | 耗时 / 证据 |
| --- | --- | --- |
| unit（含 all 中最终执行） | 退出 0；OpenSpec 7 项通过，Vitest 11 文件/71 项通过，前端构建通过，后端 167 项通过，MCP 8 项、脚本 10 项通过 | 最终分别约 2.8s / 9.6s / 2.9s / 17.0s / 0.2s / 0.3s；`backend/target/check-logs/`、Surefire XML |
| 非法模式 `--mode invalid` | 退出 2，不执行子检查 | 小于 1s；脚本测试同时覆盖未知参数、缺工具、缺前端依赖、Docker 不可用与失败停止 |
| `backend/mvnw.cmd -f backend/pom.xml -DskipTests package` | 退出 0，JAR 内 `META-INF/build-info.properties` 含 `build.version=0.1.0` | 约 4.7s；`backend/target/baseline-package.log`。本命令跳过测试，测试结果以上行单独计 |
| Compose 配置解析 | 用合成配置验证 APP_VERSION 未设置/合法覆盖/显式空值均如实传递，后端 health timeout 均为 10s | 约 1.3s；未使用真实配置输出，未启动容器 |
| 文档与差异检查 | OpenSpec 最终严格校验 7/7，通过；`git diff --check` 通过；本轮文档本地链接可解析，模板有效配置行均为 `KEY=` | 只证明格式、链接及差异检查，不替代双人评审或秘密扫描 |
| integration（含 all 中最终执行） | 退出 0；CampusClawIntegrationTest 17 项、QdrantIntegrationTest 1 项、McpSchemaMigrationTest 1 项，全通过，无失败/错误/跳过 | 最终约 56.8s；本轮 Surefire XML 与 `backend/target/check-logs/backend-integration.log` |
| 正常 Compose 启动 | 重建后的 JDK 17 后端及前端启动成功，HTTP/MySQL 预检通过 | `backend/target/baseline-compose-up-final.log`；配置与既有卷保留 |
| 隔离运行验收 | 健康 200 → 停止测试 Qdrant 后 503（约 1.016s）→ 恢复后 200；版本、UTC、依赖状态、合法/非法 request ID、匿名业务 401 均符合契约 | 独立 `class07-baseline-check` 项目、临时端口/卷，结束后清理；原应用未执行停机/删卷 |
| 隔离配置/日志验收 | 空版本、非法版本及空 JWT 密钥启动均非零且不回显非法值；51 行后端日志均为合法 JSON，8 条请求完成事件可关联 | `backend/target/baseline-drill-*.log`；只记录合成哨兵，不含原配置值 |
| 从模板填写配置启动 | 仅填写 `.env.example` 的必需键并使用全新合成值，独立项目启动成功，health 返回 200、缺省版本 0.1.0；将 CHAT_API_KEY 置空时启动拒绝 | `backend/target/baseline-template-*.log`；未单独计时。模型地址为不可用的本地地址，未调用真实模型；临时项目、卷及合成配置已清理 |
| api（all 中执行对应阶段） | 预检及 8 个业务用例全部通过，4 项清理全部通过，无失败或跳过；覆盖登录、检索、MCP、技能、智能体、会话、真实模型 SSE 与审计 | 预检约 0.5s，用例与清理约 3.7s；`backend/target/check-logs/api.log`、`apitest/reports/` |
| `python scripts/check.py --mode all` | 退出 0，unit → integration → api 全部通过 | 最终各子阶段耗时合计约 94s，不含临时夹具准备；原始日志沿用忽略目录 |

unit 的后端报告无失败、错误或跳过，明确排除三个 Testcontainers 类。旧报告、跳过、零用例与缺少指定容器类报告会被 runner 拒绝；脚本测试使用受控子进程，不启动应用。原始输出仅保存在忽略目录，不作为公开附件。

本次运行发现并修正：审计脚本应读取 `student["user"]["username"]`；MCP 清理回调应在创建成功后立即注册，避免后续断言失败遗留测试服务。新增聚焦用例验证中途失败仍按 ID、班级、运行名称清理。最初失败尝试遗留的一条无审计服务已核对归属并通过 API 删除。两类临时验收项目的容器/卷和临时配置均已清理，现有账号与材料未被替换。

通过结果适用于上述合成验收环境；默认种子不含 `teacher-b`，现有应用若要独立重跑 api/all，仍须按 `apitest/README.md` 准备测试前提。

## 实现与继承适配

- 健康：保留匿名入口、`status` 与 200/503；只读检查数据库/Qdrant，模型和动态 MCP 为 `not_checked`。数据库超时预算沿用连接池，Qdrant 为单次 1 秒请求、不重定向、不重试。
- 版本：Maven build-info 随交付产物携带；可覆盖 APP_VERSION，空值、超长及非法字符拒绝，不依赖运行目录中的 Git。
- 日志：后端 JSON 编码，固定完成事件与合法 request ID 响应头；普通日志不输出原消息、参数或异常堆栈。匹配路由模板替代含 query/资源值的原始路径。
- 异步：MVC 和两类既有执行器传递、恢复 MDC；合成测试覆盖异步错误、超时、断开、重复完成及线程复用，真实 MySQL 集成与真实模型 SSE 回归通过。真实客户端网络断开仍以合成回调覆盖，不扩大为所有生产网络情形均已验证。
- 检查：unit/integration/api/all 统一入口，保留现有测试资源归属与清理；不自动启动种子、改私有 `.env` 或迁移真实数据库。`.env.example` 已改为必需键空值和可选项注释。

## 另一成员待办（可直接转交）

发起方已确认验证通过。请在你自己的开发环境独立复现现有功能并审核，通过后由你完成归档提交。范围为当前已实现能力，不包含作业批改、学习诊断、图谱、计划等规划业务。

### 1. 获取候选版本并启动

从团队仓库克隆或更新到发起方提供的提交，阅读 `AGENTS.md`、`README.md` 和本 change，按规则读取相关规范。以下命令以 PowerShell 为例，在仓库根目录执行：

```powershell
Copy-Item .env.example .env
```

复制模板仅在首次创建配置时执行，已有 `.env` 则直接核对。启动前填写本机私有配置，包括实际模型地址/名称/密钥、嵌入维度和 MCP 配置。首次空库演示添加 `DEMO_SEED_ENABLED=true` 和独立的 `DEMO_SEED_PASSWORD`，不要复用原作者的业务数据库或账号数据。首次启动创建 `teacher-a/student-a1/student-b1`。

```powershell
docker compose --env-file .env up -d --build
```

访问 `http://localhost:8080`。本轮以页面功能复现为主，复用已有自动检查，不要求重新准备 API 专用夹具或执行整套 all；不能仅查看原作者报告或 health 200 就判定复现通过。

### 2. 逐项操作现有功能

使用自建合成材料和测试内容，按顺序验证：

| 功能 | 操作与通过标准 |
| --- | --- |
| 启动与运行基线 | health 返回 200，包含 version、UTC time、database/qdrant 状态；API 文档可打开。检查一次响应的 X-Request-Id 和后端 JSON 日志，能关联请求。模型/MCP 的 not_checked 不代表功能已验收。 |
| 登录、角色与班级 | 教师和学生均可登录、退出；学生不能使用教师管理操作。A 班教师上传材料后，B 班学生登录不应看到该材料。 |
| 材料生命周期与检索 | 上传一份含勾股定理等内容的 UTF-8 Markdown，等待 READY；查看、下载、重建索引、keyword/vector/hybrid 检索及片段上下文均可用；若遇失败能按页面提示重试。用一份临时材料确认删除可用。 |
| MCP 服务与审计 | 教师登记 `campus-tool`，地址 `http://mcp-demo:8090`，Token 与本机 MCP_AUTH_TOKEN 一致；编辑、连接发现、工具列表及断开/重连可用。调用后审计显示实际用户名、工具状态和耗时；无审计的临时服务可删除，有审计时应拒绝删除。 |
| 技能和智能体配置 | 分别配置检索技能、计算器技能（`calculator@campus-tool`），设置明确且不同的触发条件；绑定到智能体及 READY 材料，保存后重新进入配置仍在，启用/停用操作可用；未绑定的临时技能、无会话历史的临时助手可删除。 |
| 材料问答与计算 | 以学生身份新建会话，分别询问材料中的知识和“计算2的20次幂”。前者有相关材料依据，后者确实调用计算器并得到 1048576；无材料引用的纯工具回答允许正常完成。 |
| 对话展示与恢复 | 流式回复可见；含 Markdown/公式的回答正常渲染，引用默认折叠且能展开。刷新/重进后消息仍在，进入会话定位到最新位置；删除有未发送草稿的会话后，新会话没有旧草稿，同班他人看不到该私人会话。 |

任何一项不可用，记录问题并修正，再复测对应功能；涉及代码行为修改时补跑受影响的自动检查。无需重新执行无关测试，不做故意制造漏洞、新会话验证、回滚演练或全历史/镜像扫描。

### 3. 审核并记录结论

核对实现、规约/规范、配置说明和已有测试结果是否一致，本次提交差异中是否包含敏感信息。在本文件补充一段简短记录：成员代号、验收提交、环境类别、各功能项结果、问题及处理、最终是否通过。不新增验收台账，不粘贴密钥、完整回答或本机私有路径。

独立复现与审核结论见下方的原生进程记录；成员 B 已确认结果并同意归档。

### 独立复现记录（2026-10-31，原生进程）

记录对象：成员 B（王家兴）的开发环境。候选提交 `4d1bbbf`（工作区仅有验收文档改动，业务代码未改）。本次运行由协作代理执行，成员 B 已确认以下实际结果并同意按原生进程证据归档。

环境类别：Windows 11；JDK 17（Corretto 17.0.10）；Node 20.19；Python 3.10；独立 MySQL 8.0.36 实例（127.0.0.1:3307，仅合成库）；Qdrant 1.15.4 官方 Windows 二进制（127.0.0.1:6333）；后端原生进程 8081，前端 Vite 5173，MCP 示例服务 8090。未安装 Docker，未连接现有 MySQL 数据。

| 项目 | 实际结果 |
| --- | --- |
| 启动与健康 | `GET /health` 返回 200，`version=0.1.0`，数据库/Qdrant=up，模型/MCP=not_checked；停止 Qdrant 后返回 503 且 `qdrant=down`，恢复后重新 200 |
| 版本配置 | 非法值与空 `APP_VERSION` 均启动失败（退出 1），输出未回显非法值；JSON 日志仅记录异常类型，未直接打印 `APP_VERSION` 键名 |
| JSON 日志/请求 ID | 检查时 129 行日志全部可逐行解析为 JSON，87 条请求完成事件；自定义 ID 在 200/401/403/404 响应头与完成事件一一对应，路径不含查询值，未出现模型/MCP/数据库/JWT 凭据 |
| 登录与隔离 | teacher-a/student-a1/student-b1/补建的 teacher-b 登录成功；学生上传材料 403；B 班访问 A 班材料 404；学生访问 `/assistants` 被前端守卫重定向；接口套件跨班 MCP 404、学生 403 |
| 材料 | 合成 Markdown 上传 201 并索引 READY；keyword/vector/hybrid 均命中本班材料；切片上下文返回相邻片段；下载 689 字节且为附件；重建索引后 READY；临时材料删除 204、随后读取 404 |
| MCP/审计 | 接口套件完成登记、连接、工具发现、错误 Token、跨班 404、审计查询；调用审计记录实际用户、工具状态和耗时；注入探针结果被整体过滤并记 DENIED |
| 技能/智能体 | 接口套件完成教师创建/更新、学生 403、绑定原子替换与恢复；材料问答命中“材料整理”技能 |
| 材料问答 | 真实模型 SSE 返回 `meta/delta/done`，引用来自材料 3，正文包含公式与引用标记 `[1]`，消息持久化为 COMPLETED |
| 计算器 | 技能 `calculator@verify-calc` 调用成功，工具摘要 `2 ** 20 = 1048576`，SSE 回答为 `1048576`，会话持久化 2 条消息 |
| 页面渲染 | Chrome 无头实测：登录成功、材料页显示合成材料、学生角色守卫生效、KaTeX 渲染 1 处、引用默认折叠可通过“展开片段”展开、刷新后消息仍在、计算器会话显示工具摘要 |
| 接口套件 | `apitest/run_all.py` 8 个用例与 4 项清理全部通过，退出 0；报告位于忽略目录 `apitest/reports/` |
| 单元检查 | 此前 `scripts/check.py --mode unit` 退出 0：OpenSpec 7、Vitest 11 文件/71 项、后端 167、MCP 8、脚本 10，无失败/跳过 |

未执行与偏差：

- 未安装 Docker，未执行 `docker compose` 启动、镜像构建和三个 Testcontainers 集成类；这些依赖发起方已通过的 all 结果，未由成员 B 独立重跑。
- 原生进程路径不是 README 的 Compose 启动路径；Node 20/Python 3.10 也低于 README 标注的 Node 22+/Python 3.11+，但本次检查通过。
- 未发现已知功能失败；Docker/Compose 与集成测试为未验证项，不记作通过。

结论：功能复现通过（原生进程，独立 MySQL/Qdrant，合成数据）；成员 B 已确认接受该偏差并同意归档。Docker Compose 与 Testcontainers 未由成员 B 独立重跑，沿用发起方已通过的 all 结果，不记作成员 B 的独立验证。

### 4. 通过后归档并提交

确认全部现有功能可用且无未解决问题，勾选对应任务；按已有 OpenSpec 流程同步 delta 到主规格，执行 `openspec validate --all --strict` 和 `git diff --check`，归档 change，更新项目状态并提交/推送或发起 PR。归档后修正状态文档中的活动 change 链接。标签可选，不追加其他验证任务。
