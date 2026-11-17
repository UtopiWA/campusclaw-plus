# Design

## 1. 门禁与 CI

- 功能门禁沿用 `scripts/check.py`：`unit` 覆盖规约、前端、非容器后端、MCP 和脚本；`integration` 使用独立 Testcontainers；`api` 复用真实接口套件。`make check` 固定为 `--mode all`，失败停止后续组并返回非零。
- 安全门禁沿用 `scripts/security_gate.py` 五阶段；本次只补执行证据和 CI 接入，不改变阶段范围。
- 受控红使用临时改坏的测试期望，在隔离分支/工作区制造真实失败；记录改坏位置、失败用例、退出码，再恢复并复绿。安全门禁的合成故障继续使用已有 `--fault-stage`，不伪造真实漏洞。
- 开发依赖例外：`security-exceptions.json` 只覆盖用户 2026-11-18 确认的 `GHSA-fc5q-39xc-vjpf`（5 个传播包），必须显式 `--exceptions`、已批准且未过期；`scan.py` 保留原始 audit JSON/失败码并标记 `ACCEPTED_EXCEPTION`，任何未覆盖的新 high/critical、过期或未批准例外都失败。例外到期 2026-12-18，不改变扫描级别或排除 dev 依赖。
- GitHub Actions 分两层：
  - `ci.yml` 在 GitHub 托管运行器执行 unit、integration 和三个镜像构建；镜像带 `GIT_COMMIT` 标签并记录 image ID/标签。
  - `security-gate.yml` 绑定团队自托管运行器，要求 `CAMPUSCLAW_ENV_FILE` 指向私有配置；缺失文件直接退出 2。完整 access/audit/api 依赖真实或受控模型环境，不使用托管运行器的空凭据假装通过。
- 实际推送、运行编号和镜像推送仍由获得授权的成员执行；代理只提交配置和本地等价证据。

## 2. 镜像与版本追溯

- 三个 Dockerfile 接收 `ARG GIT_COMMIT`，写入 `org.opencontainers.image.revision` 标签；不把 `.env`、密钥或开发源码复制进运行镜像。
- Compose 使用 `IMAGE_TAG`（默认 `local`）标记后端、前端和 MCP 镜像，`GIT_COMMIT` 传给构建；后端/worker 的 `APP_VERSION` 默认取 `IMAGE_TAG`，健康接口返回同一值。
- 提交标签和镜像标签分别承担“构建自哪个提交”和“运行的是哪个构建”；工作区有未提交修改时使用带 `-dirty` 的运行标签并在证据中注明，不把脏构建写成干净提交产物。
- 备选方案：只用 `latest` 或只改 Compose 镜像名。拒绝，因为无法反查提交/构建。

## 3. 四项教学行为守护

| 行为 | 本轮守护 | 边界 |
| --- | --- | --- |
| 解题引导 | `AgentPromptBuilderTest` 断言直接索要最终答案时系统提示仍要求苏格拉底式引导、禁止最终答案/选项 | 模型是否遵守由真实模型评测另验；提示词测试不能冒充模型行为 |
| 作业批改 | `GradingTaskCompletionServiceTest` 迟到完成/失败不能替换终态；`GradingTaskQueryServiceTest` 同时保留迟到 AI 结果和教师终评 | 终评优先展示由查询契约和前端共同保证；测试不修改真实成绩 |
| 错题讲解 | `GradingModelServiceTest` 断言评语必须指出具体依据、错误/缺漏和改进建议，参考文件不是固定评分表 | 不声称模型评语一定教学正确；只验证提示词与服务端结构 |
| 知识库 | 既有 `RetrievalServiceTest` 覆盖白名单、READY 回表、跨班过滤；四步演示/接口套件覆盖 404 与空集合 | 跨班读取和检索分别断言，不改变既有 404/200 协议 |

## 4. 观测工具

- 数据来源保持单一事实：
  - 技能调用与完成/失败：`messages` 中带 `skill_id` 的助手消息；
  - 拒绝/失败补充：`security_audit_events` 中 `SKILL_EXECUTE` 失败行；
  - API P95/状态：后端既有 `http_request_completed` JSON 日志；
  - 队列深度：RabbitMQ management API。
- `observe.py` 按窗口查询并生成 HTML/JSON；三张图分别为技能调用分线、每技能错误率、API P95 与队列深度。HTML 在忽略目录，`seed_observability.py` 在清理前生成快照。
- 告警规则固定为“窗口内 API 5xx 比例 > 阈值且请求数达到下限”，包含负责人、动作和面板路径；`alert_receiver.py` 是本地 HTTP 接收器，实际 202 才记为送达。403/4xx 不计入错误率分子。
- 不新增指标接口或抓取平台，原因是 Class10 当前需求可用既有事实满足；若未来需要长期监控，另立 change 接入 Prometheus/Grafana。
- 隐私：不导出用户正文、提示词、令牌、材料内容；技能名为本班配置名称，属于可公开合成数据。

## 5. 压测与缓存

- `bench.py` 上传本轮唯一合成讲义，等待 READY；教师创建 N 个唯一作业，学生在 worker 保留时逐个提交；任务查询使用本轮 taskId；检索使用合成切片查询。
- 默认 worker 保留并等待任务终态后再清理，避免把待消费消息留给下一轮；仅在隔离环境使用 `--stop-worker`。队列初始非空且未确认清理时直接退出。
- 清理按精确 ID/标题前缀核对后删除 outbox、results、reviews、tasks、submissions、assignments，再通过 API 删除合成讲义；任何核对失败停止删除。
- 改进：`OpenAiEmbeddingGateway` 增加有界 LRU（`EMBEDDING_CACHE_SIZE`，默认 256，0 关闭）。键只包含查询文本，向量只取决于文本和当前模型；不包含班级/用户，不涉及评分或授权。配置变更/进程重启自然失效，避免陈旧模型切换。
- 正确性：检索命中仍按 class_id、READY 和材料白名单回表；缓存不绕过任何过滤。教师改分不读取 embedding 缓存，因此无需改分失效逻辑。
- 复测：相同脚本、相同档位/请求数，分别以 `EMBEDDING_CACHE_SIZE=0` 和 `256` 重启后端，记录吞吐、P50/P95、错误率。

## 6. 失败处理与证据

- 门禁/CI 失败非零；缺私有配置、Docker、模型或扫描工具时退出 2，不跳过。
- 观测/压测清理失败保留工作区并打印稳定类别；不把未知队列深度或缺失日志写成 0。
- 所有原始输出在 `backend/target/` 忽略目录；`reports/` 只写脱敏摘要、命令和可定位路径。
- 未执行项（真实 CI 推送运行、镜像推送、真实手机/三名用户、另一成员独立复现、交叉评审）保持未完成。
