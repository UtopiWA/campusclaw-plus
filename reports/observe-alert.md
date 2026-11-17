# Class10 观测面板与真实告警

日期：2026-11-17（UTC）/ 2026-11-18（本地）
入口：`python scripts/observe.py --env-file .env.team --window 5m`
面板快照：[技能面板](observe-dashboard.html)、[告警窗口面板](observe-alert-window.html)
原始 JSON：`backend/target/observe/`（忽略目录）

## 1. 指标口径

| 面板 | 事实来源 | 口径 |
| --- | --- | --- |
| 三技能调用量分线 | `messages` 中带 `skill_id` 的助手消息，按时间桶 | 每个技能一条线；本项目的技能名由教师配置，演示使用“解题引导/作业批改/错题讲解”三条合成技能 |
| 每技能错误率 | 同一批助手消息的 `status` | `failed / total`，不维护第二套计数；`security_audit_events` 的失败单独列为补充 |
| API P95 | 后端既有 `http_request_completed` JSON 日志 | 只取 `/api/` 路径；报告总 P95 和不含 SSE 的 P95 |
| 队列深度 | RabbitMQ management API | `grading.tasks` 的 `messages`；不可达时显示不可用，不写 0 |

不采集用户正文、提示词、令牌、材料内容或供应商原始响应。

## 2. 三技能面板

`scripts/seed_observability.py --env-file .env.team --observe-window 5m` 创建三条本轮唯一技能，绑定本班智能体，以学生私人会话各调用一次；在删除会话前生成面板并清理。

- seed 结果：`backend/target/observe/seed-20261117T172632Z-aeb83f.json`，退出 0。
- 面板原始：`backend/target/observe/observe-20261117T172633Z.json` 和 `observe-20261117T172632Z-aeb83f.html`。
- 实际：解题引导、作业批改、错题讲解各 1 次调用，均 `COMPLETED`，错误率 0%；API 请求 161 条，P95 103 ms，队列深度 0。
- 会话、技能和智能体绑定在快照后删除/恢复；面板保留脱敏快照。

## 3. 告警规则

规则：**API 5xx 比例在 5 分钟窗口内 > 1%，且 API 请求数 ≥ 20**。
负责人：Class10 值班成员；动作：检查后端错误日志与依赖状态，按 runbook 处置；面板：本次生成的 HTML 路径。

控制失败：临时停止 Qdrant（可恢复的本地团队服务），以学生令牌调用 `POST /api/retrieval/search`（`mode=vector`）得到 12 次 503；期间 `/health` 也为 503。随后立即重新启动 Qdrant，等待 `/health` 恢复 200。

## 4. 实际触发与送达

```powershell
python scripts/alert_receiver.py --port 18081 --output backend/target/observe/alert-deliveries.jsonl
python scripts/observe.py --env-file .env.team --window 5m `
  --alert-webhook http://127.0.0.1:18081/alerts --alert-min-requests 20 `
  --html backend/target/observe/alert-window.html
```

| 项目 | 实际值 |
| --- | --- |
| 观察窗口 | 5 分钟 |
| API 请求数 | 202 |
| 5xx 数 | 14（本轮 12 次受控 503 + 窗口内既有 2 次） |
| 5xx 比例 | 6.93% |
| 触发 | true |
| 送达 | true，接收器 HTTP 202 |
| 触发/送达时间（接收器） | `2026-11-17T17:28:22.302626+00:00` |
| 恢复 | Qdrant 重启后 10 秒内 `/health` 200 |

接收记录：`backend/target/observe/alert-deliveries.jsonl`；原始观测：`backend/target/observe/observe-20261117T172822Z.json`；面板：[告警窗口面板](observe-alert-window.html)。

## 5. 边界

- 这是按既有事实派生的轻量观测工具，不是 Prometheus/Grafana/Alertmanager 长期平台；没有新增应用指标接口。
- 告警接收器是本地 HTTP 接收端，实际送达证据是 2xx 和接收文件；未向外部人员发送消息。
- 4xx/403 不进入 5xx 告警分子。
- 未执行：长期运行、跨实例抓取、通知渠道（邮件/IM）接入；后续如需要另立 change。
