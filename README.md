# CampusClaw-Plus

基于 CampusClaw 扩展的智能学习诊断与个性化辅导系统，面向班级教学中的教师与学生。

**当前可用：** 登录与班级隔离、教学材料管理与检索、技能和智能体配置、持久化流式对话、MCP 工具调用与审计、文本作业异步批改。

**扩展目标：** 知识图谱与掌握度画像、个性化学习计划、自适应练习、班级共性错误分析，以及诊断、规划和辅导角色的协作。相关业务尚未实现；Class08 和依赖升级的验收及交付状态见[项目状态](docs/project-status.md)。

## 文档

- [项目范围与成员分工](docs/project-scope.md)：完整目标、现有缺口与分阶段交付。
- [项目状态](docs/project-status.md)：当前实现与待办；验收记录：[Class07](docs/lesson-07/README.md)、[Class08](docs/lesson-08/README.md)；[Class08 评审](docs/lesson-08/review.md)。
- [项目规则](AGENTS.md) 与 [主规格](openspec/specs/)：开发约束和已实现行为契约。
- 工程规范：[产品](docs/standards/01-product.md)、[后端](docs/standards/02-backend.md)、[安全](docs/standards/03-security.md)、[测试与评审](docs/standards/04-testing.md)、[运维](docs/standards/05-operations.md)。
- [接口回归说明](apitest/README.md)：测试环境、执行方式和报告。

## 技术栈

Vue 3 / Vite、Java 17 / Spring Boot 4、MySQL 8、Qdrant、RabbitMQ、Python MCP 服务，使用 Docker Compose 部署。

## 快速启动

需要 Docker Compose 和可用的模型服务，在仓库根目录执行：

```powershell
Copy-Item .env.example .env.team
```

首次创建后编辑私有 `.env.team`，填写数据库口令、JWT 密钥、模型地址/密钥/名称、实际嵌入维度、MCP 凭据和 RabbitMQ 用户/口令；已有配置无需重新复制。其中 `MCP_TOKEN_ENCRYPTION_KEY` 为 Base64 编码的 32 字节随机密钥，可用 `openssl rand -base64 32` 生成。

空数据库首次演示时，将 `DEMO_SEED_ENABLED=true` 并设置 `DEMO_SEED_PASSWORD`，以创建演示账号；默认关闭种子时不会自动创建账号。演示账号为 `teacher-a`、`student-a1`、`student-b1`，口令由该环境变量提供。

```powershell
docker compose --env-file .env.team up -d --build
```

- 应用：<http://localhost:18080>
- 健康检查：<http://localhost:18080/health>，返回版本、UTC 时间与数据库/Qdrant 状态；两者就绪返回 200，否则 503，模型与 MCP 标为 `not_checked`。
- API 文档：<http://localhost:18080/swagger-ui/index.html>

默认项目名 `campusclaw-plus`，七个服务使用独立网络和具名数据卷，后端与 worker 共用一个镜像；不会复用旧 `campusclaw` 项目的容器或数据。网页/MySQL/RabbitMQ 管理端口为 `18080/13306/15673`，均只绑定本机；覆盖键分别为 `APP_PORT/MYSQL_HOST_PORT/RABBITMQ_MANAGEMENT_PORT`。每次启停均指定同一配置文件，不混用个人项目的 `.env`。镜像、卷与构建缓存随 Docker 引擎的数据盘存放，磁盘位置由 Docker Desktop 设置管理。

停止服务并保留数据：

```powershell
docker compose --env-file .env.team down
```

材料、数据库和向量保存在 Docker volumes 中；`docker compose down -v` 会删除这些数据。MCP 示例服务只在 Compose 内部网络提供，教师在助手配置页登记 `http://mcp-demo:8090`，使用与 `MCP_AUTH_TOKEN` 相同的 Token。

### 异步作业批改

Compose 同时启动 RabbitMQ 和独立 `grading-worker`。教师在 `/assignments` 创建带评分依据的文本作业，学生提交后收到 202 和任务标识；后台 worker 调用模型做分项判断，服务端按评分依据确定性计分。任务状态通过 `/api/grading/tasks/{taskId}` 只读查询，页面轮询到终态停止。

```powershell
docker compose --env-file .env.team stop grading-worker
docker compose --env-file .env.team start grading-worker
```

RabbitMQ management：<http://127.0.0.1:15673>，使用私有配置中的 RabbitMQ 账号。停 worker 后任务保持待处理，恢复后自动完成；三条核心判据、队列/死信观测和失败记录见 [Class08 验收记录](docs/lesson-08/README.md)。

`.env.example` 列出的 `GRADING_*` 参数由 Compose 同步传入 Web/worker；worker 不运行材料恢复或 Qdrant 初始化。仅重建后端后若前端出现 502，执行 `docker compose --env-file .env.team restart frontend` 刷新 Nginx 上游解析。

## 检查与测试

在仓库根目录执行，需要 Node.js 22.22.2+、Java 17、Python 3.11+ 和 OpenSpec CLI；容器检查另需 Docker Engine：

```powershell
npm.cmd --prefix frontend ci
python scripts/check.py
python scripts/check.py --mode integration
$env:APITEST_ENV_FILE='.env.team'
python scripts/check.py --mode api
python scripts/scan.py
```

默认 `unit` 检查规约、前端、非容器后端及 Python；`integration` 使用独立 MySQL/Qdrant 容器。`api` 需按 [接口说明](apitest/README.md) 准备运行中应用、账号和材料；`all` 顺序执行三组，失败即停止。`scan.py` 执行代码规范、依赖漏洞和静态检查，Java 依赖默认使用 OSV，OWASP 为可选补充。退出码：0 通过、1 检查失败、2 参数/环境缺失。原始输出在 `backend/target/check-logs/`，后端报告在 `backend/target/surefire-reports/`，接口报告在 `apitest/reports/`。

Class08 验收入口为 `python scripts/lesson08_acceptance.py basic --env-file .env.team`，支持 `pending/wait/duplicate/report`；默认从所选文件读取网页、数据库和 RabbitMQ 管理端口，地址也可用 `--base-url/--management-url` 覆盖。扫描另需 Ruff/pip-audit，建议安装在独立虚拟环境；实际结果与未执行项见 [Class08 评审](docs/lesson-08/review.md)。

## 开发与安全

项目继承 CampusClaw 的代码与功能，Git 历史从团队项目启动提交开始保留。新增业务通过 OpenSpec change 评审、实现与验收。运行基线实现与验收进度见[项目状态](docs/project-status.md)，日志字段、版本覆盖和回滚流程见[运维规范](docs/standards/05-operations.md)。

`.env`、`.env.team` 等私有配置、真实凭据与个人学习数据不得提交。演示种子仅用于本地或课堂环境，公开部署应关闭演示初始化。项目采用 [MIT 许可证](LICENSE)。
