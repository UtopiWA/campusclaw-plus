# 密钥、依赖与静态扫描

2026-11-06；候选 `45b2f7a` 加本轮工具/文档。执行者为协作代理。原始日志位于本机忽略目录 `backend/target/security-check/`、`backend/target/scan-logs/`。

## 入口和范围

准备当前前端构建、运行中的团队七服务，以及包含 Ruff/pip-audit 的 Python 环境：

```powershell
npm.cmd --prefix frontend run build
docker pull ghcr.io/gitleaks/gitleaks:v8.24.3
python scripts/check_security.py --env-file .env.team
```

入口串行执行 SEC-05 与已有 `scan.py` 的 SEC-06；前一项未完成也会记录后一项的实际结果。`--only secrets` / `--only dependencies` 可单独执行，分别保存摘要。退出 0 为选定范围未发现问题，1 为存在候选/告警，2 为工具、范围或环境未完成。退出 1 必须看命中原因，不能自动当作误报或改成 0。

| 密钥扫描范围 | 实际范围/结果 |
| --- | --- |
| Git 历史 | 非浅克隆，全部本地 ref 可达的 17 个提交，Gitleaks 0 命中 |
| 当前公开文件 | 跟踪文件和未跟踪但未忽略文件；本轮完成后 466 个，Gitleaks 0 命中 |
| 前端构建 | 当前 `frontend/dist`，Gitleaks 0 命中 |
| 运行日志 | 当前团队 backend/frontend/worker/mysql/qdrant/rabbitmq/mcp-demo 的全部可取得日志，不使用 tail 截断；Gitleaks 0 命中 |

扫描器固定 Gitleaks v8.24.3，使用 `git --log-opts=--all` 和 `dir`，`--redact=100`；容器无网络，只读挂载公开副本和 `.git`，不挂载私有 `.env.team`。摘要只保留规则、位置、提交等元数据。缺构建、浅历史、任一服务日志不可用、扫描失败或报告缺失均记未完成；不会拿旧文件当新结果。工具语义见 [Gitleaks 官方说明](https://github.com/gitleaks/gitleaks/blob/v8.24.3/README.md)。

范围不包含已删除/不可达提交、远程未获取的分支、Docker 镜像层、过期日志或其他项目；本报告不宣称这些范围已扫。

## 精确比对候选处置

除 Gitleaks 规则外，还将所选私有配置的当前密钥/口令值与公开文件、产物和日志作精确比对，不输出原值。实际有 **1 个候选**：`configured-DEMO_SEED_PASSWORD`，`mcp-server/server.py` 的 `serverInfo.name`（当前第 138 行）。

核查发现配置的演示口令与公开演示服务名称中的文本重合，该位置是 MCP 服务身份名称，不是凭据赋值、认证校验或配置注入。SEC-05 人工核查判绿，范围仅本报告；原始候选与入口退出 1 均保留，没有加白名单、删报告或改阈值。此判断不意味着演示口令适合公开部署；生产环境须独立凭据并关闭种子。报告不披露实际口令。

## 依赖与静态检查

本轮默认扫描实际完成，退出 0：

| 项目 | 结果 |
| --- | --- |
| 前端 ESLint / npm audit | 通过 / 零告警 |
| Python Ruff / pip-audit | 通过 / 零告警 |
| Java Checkstyle / SpotBugs | 通过 / 通过 |
| Java OSV | 124/124 查询完成，所有严重级别零告警（high/critical 各 0） |
| OWASP/NVD 补充 | 未执行，不是默认入口必选项 |

新增脚本自身测试与 Ruff 另见[课程验收第 12 节](../docs/lesson-08/README.md#12-安全自查与文档补齐2026-11-06)。未降低漏洞门槛，也未将早期 Spring 告警接受方案改写为已接受；它们已由此前升级整改。

## 审计红项

全项自查 `72756d460899` 的真实计算器调用：操作人 `student-a1`、状态 `ok`、耗时 14 ms；会话消息保留技能名称。工具审计没有技能 ID，模型用量没有采集，`tokenUsage=null`，不得记为 0。本轮安全基线 **SEC-07 红**，与上述同文候选分开处理。

合成审计已随本轮资源清理，不能按旧编号查询后误判缺日志。用户已决定暂时搁置技能 ID 和 Token 用量，不纳入本次复核、修复任务或归档前置条件；原始红项保留。后续若启动改进，再明确技能关联和模型 usage 的采集/保存契约；当前没有增加业务字段。总表及其他证据见[安全自查](security-precheck.md)。
