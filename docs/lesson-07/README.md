# Class07 团队基线验收记录

核对日期：2026-10-31。候选为 `146228f` 后的当前工作区，尚未提交；成员 A/B 的人工评审不由本记录代签。活动 change：`2026-10-31-establish-team-baseline`。

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

## 尚未完成的团队验收

两名成员的评审结论、无历史会话规则检查、规范合规/违规试验、完整历史/镜像/产物秘密扫描、另一成员独立克隆启动、两版本回滚与最终标签均待实际执行。同机临时项目启动不等于另一成员已复现，all 通过也不代替上述交付门禁。

最后按仓库、规约、规范、骨架、交付五维核查；all 通过不代表整课交付完成。所有后续实际结果继续维护本文件，不新建逐次验证目录。
