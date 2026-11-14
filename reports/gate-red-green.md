# Class09 安全门禁红绿证据

日期：2026-11-14  
门禁入口：

```powershell
make check-security
# 本机无 GNU make 时：
python scripts/security_gate.py --env-file .env.team
```

门禁阶段：`scan`（密钥/依赖/静态扫描）→ `access`（SEC-01～04）→ `audit`（SEC-07）→ `api`（含三条常驻反例）→ `unit`（脚本测试）。退出码 0 通过、1 检查失败、2 环境/范围未完成。

## 红：SEC-07 修复前

命令：

```bash
.venv-review/Scripts/python.exe -B scripts/security_precheck.py --env-file .env.team --checks audit
```

- run `4760bfcb427e`，exit 1，execution/cleanup complete。
- 结果：`skillId=false`、`tokenUsage=false`、`tokenUsage=null`；只有操作人、技能名称、状态和 MCP 工具耗时。
- 改动位置：`ChatGateway`/`OpenAiChatGateway` 采集 usage；`AgentTurnPlan`/`ConversationService`/`ConversationStreamingService` 持久化稳定技能 ID 和用量；V14 新增列；新增审计查询与拒绝事件。
- 判定：真实红，保留；未用泄露密钥或降低阈值制造红。

## 绿：SEC-07 修复后

- run `365c1eb50877`，exit 0；`fields` 全部 true；真实 usage 253/18/271，模型耗时 207 ms，skillId 11；SEC-03 拒绝记录可查。
- 同一代码路径的聚合门禁（首次完整运行）`20261114T121319Z-65888`：scan 0、access 0、audit 0、api 0、unit 0，总 exit 0。
- SEC-08 修复后 run `b9d157fa2cbc` exit 0：四步硬底线通过，学生和跨班拒绝审计均为 true。

## 红：SEC-08 拒绝审计缺失（修复后新发现）

- run `a3a3fa25eeeb` exit 1：学生终评 403、跨班 404、数据库不变、无成绩写工具，但 `denialAudit=false`。
- 定位：`AssignmentController.review` 在进入 `SubmissionReviewService` 前用普通 `requireTeacher()` 抛 403，服务层没有机会写审计。
- 改动位置：控制器改用带 `GRADE_REVIEW`/`SUBMISSION` 的 `requireTeacher` 重载。
- 绿：run `fe5e50280004` exit 0，`denialAudit=true`。

## 最终门禁

- 命令：`JAVA_HOME=Corretto17 .venv-review/Scripts/python.exe -B scripts/security_gate.py --env-file .env.team`
- 完整运行目录：`backend/target/security-gate/20261114T123734Z-41820/`
- 阶段退出码：

| 阶段 | 退出码 | 实际 |
| --- | --- | --- |
| scan | 0 | Gitleaks 0 命中；依赖 OSV 124/124、0 findings、high/critical 0；npm/pip/Ruff/Checkstyle/SpotBugs 通过 |
| access | 0 | SEC-01～04 绿 |
| audit | 0 | SEC-07 七类字段和 SEC-03 拒绝绿 |
| api | 0 | `apitest` 含三条常驻反例全部通过，清理完成 |
| unit | 0 | 38 个脚本测试通过 |
| **总退出码** | **0** | `summary.json` 记录 |

### 最终事务修复后的定向复核

在完整门禁之后，`SecurityAuditService` 改为 `REQUIRES_NEW`，确保调用方事务回滚时跨班拒绝审计仍保留。重新运行门禁时本机 `pip-audit` 子进程再次出现挂起，已停止该次运行并保留现象；按相同阶段定向复跑当前代码：

| 阶段 | 实际 | 退出码 |
| --- | --- | --- |
| access | run `8b7d2bca555b`，execution/cleanup complete | 0 |
| audit | run `df59a91907d1`，execution/cleanup complete | 0 |
| api | `api-preflight` PASS，`api` PASS | 0 |
| unit | 38 tests OK | 0 |
| scan | 与完整门禁相同的依赖/密钥范围；之后未增加依赖或凭据 | 上次完整运行 0 |

结论：扫描/依赖证据来自完整门禁 run `20261114T123734Z-41820`；最终事务修复后的授权、审计、接口反例和脚本测试已定向复跑为 0。未把挂起的运行写成通过。

- 受控自测：`scripts/security_gate.py --fault-stage unit` 只把已通过阶段改为合成失败，不会把失败改为通过；本轮红证据使用真实 SEC-07/SEC-08 缺口，未使用真实密钥。
- 本机未安装 GNU make；使用 Python 等价入口执行。Makefile 在 `node:22-alpine` 容器中 `make -n check-security`/`make -n check` 输出正确命令；未声称本机运行过 make。

## 说明

- 红绿比较使用相同前置、相同命令和相同判据；修复前的失败记录保留在 `backend/target/security-precheck/`，未覆盖。
- 门禁摘要写入 `backend/target/security-gate/<run>/summary.json`；原始阶段日志同目录。
- 未降低阈值、未添加 `|| true`、未跳过范围。
- 本轮**未提交**；修复文件在工作区，提交需用户明确授权。授权后按 SEC 编号分组提交，红绿报告再补提交哈希。
