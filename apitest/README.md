# CampusClaw 接口回归测试

该目录提供材料、配置、会话与 MCP 的 Python 接口回归测试。用例按 URL/方法组织；`run_all.py` 负责共享登录状态、测试数据和报告。已实现的异步作业批改使用仓库根目录 `scripts/lesson08_acceptance.py` 单独验收；诊断和学习计划尚在规划中。

## 前置条件

1. 安装 Python 3.11 或更高版本及依赖：

   ```powershell
   python -m pip install -r apitest/requirements.txt
   ```

   requirements 使用 `PyMySQL[rsa]`，包含 MySQL 8 的 SHA-2 完整认证依赖；不要只安装基础包后依赖服务端已有认证缓存。依据见 [PyMySQL 官方安装说明](https://pymysql.readthedocs.io/en/latest/user/installation.html)。Class08 首次成员复现的隔离环境和清理命令见[交接](../docs/lesson-08/handoff.md)。

2. 使用仓库根目录的私有 `.env.team` 启动完整团队 Compose 环境：

   ```powershell
   docker compose --env-file .env.team up -d --build
   ```

3. 确认 A 班至少有一份 `READY` 材料和知识切片。接口套件会读取该材料，但不会删除或修改它。

   跨班 MCP 检查另需 B 班教师 `teacher-b`，密码与 `APITEST_TEACHER_PASSWORD`（或其演示密码回退值）一致。默认演示种子只创建 `teacher-a/student-a1/student-b1`，不会创建该教师；请在专用测试环境准备，套件不会自动创建或修改账号。A 班还需一个已启用智能体，用例会临时更新并恢复它。

4. `.env.team` 至少提供 `DEMO_SEED_PASSWORD`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE` 和与 Compose 中 `mcp-demo` 一致的 `MCP_AUTH_TOKEN`。可选配置如下：

   | 变量 | 默认值 | 用途 |
   | --- | --- | --- |
   | `APITEST_ENV_FILE` | 仓库 `.env` | 显式选择私有配置；团队验收设为 `.env.team` |
   | `APITEST_BASE_URL` | 所选文件的 APP_PORT；未设置时 8080 | 前端代理入口，团队默认 18080 |
   | `APITEST_TEACHER_USERNAME` | `teacher-a` | 教师测试账号 |
   | `APITEST_STUDENT_USERNAME` | `student-a1` | 学生测试账号 |
   | `APITEST_TEACHER_PASSWORD` | 回退到 `DEMO_SEED_PASSWORD` | 教师密码 |
   | `APITEST_STUDENT_PASSWORD` | 回退到 `DEMO_SEED_PASSWORD` | 学生密码 |
   | `APITEST_DB_HOST` | `127.0.0.1` | MySQL 主机 |
   | `APITEST_MCP_BASE_URL` | `http://mcp-demo:8090` | 后端容器可访问的 MCP 内部根地址；可带末尾 `/mcp` |
   | `APITEST_HTTP_TIMEOUT` | `20` | 普通请求超时秒数 |
   | `APITEST_STREAM_TIMEOUT` | `180` | 流式请求超时秒数 |

## 执行

```powershell
$env:APITEST_ENV_FILE='.env.team'
python apitest/run_all.py
```

测试覆盖登录、本班关键字检索、MCP 登记/连接/工具发现、错误 Token、学生 403、跨班 404、技能创建、智能体提示词和绑定更新、会话创建、SSE 工具快照、注入结果整体过滤、调用审计以及有审计服务的删除冲突。每个用例同时检查关键响应字段；涉及持久化的用例还会查询 MySQL。

套件使用唯一运行标识创建临时技能和会话。无论成功或失败，结束阶段都会恢复原智能体配置，并仅删除能够通过 ID、班级、用户及运行标识共同确认的测试数据。生成的报告位于 `apitest/reports/`，该目录不会提交到 Git。
