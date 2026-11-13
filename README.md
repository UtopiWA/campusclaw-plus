# CampusClaw-Plus

基于 CampusClaw 扩展的智能学习诊断与个性化辅导系统，面向班级教学中的教师与学生。

**当前可用：** 登录与班级隔离、材料检索、助手对话与 MCP、异步作业及教师终评；本轮新增讲义标签与筛选、作业标签继承、全班作答名单与分页、掌握度统计，独立题库及私人AI练习、学习计划；作业和题库使用逐行列表与独立详情，“我的学情”分掌握情况、学习计划两个子页。

**交付状态：** Class08 与依赖升级已验收归档；新增学习模块已完成代理技术验证，待实际成员交叉验收。掌握统计使用明确规则；学习计划由模型在可访问资源范围内安排具体讲义、题目和每日时间，服务端校验后保存；题库作答按次调用AI批改。公网部署等最终交付仍待完成，详见[项目状态](docs/project-status.md)。

## 文档

- [项目范围与成员分工](docs/project-scope.md)：完整目标、现有缺口与分阶段交付。
- [项目状态](docs/project-status.md)：当前实现与待办；验收记录：[Class07](docs/lesson-07/README.md)、[Class08](docs/lesson-08/README.md)；[Class08 评审](docs/lesson-08/review.md)。
- [项目规则](AGENTS.md) 与 [主规格](openspec/specs/)：开发约束和已实现行为契约。
- 工程规范：[产品](docs/standards/01-product.md)、[后端](docs/standards/02-backend.md)、[安全](docs/standards/03-security.md)、[测试与评审](docs/standards/04-testing.md)、[运维](docs/standards/05-operations.md)。
- [接口回归说明](apitest/README.md)：测试环境、执行方式和报告。
- [学习诊断与辅导](docs/learning/README.md)：讲义标签、作业与私人题库、学习计划、掌握度统计和验收步骤。
- [Class08 安全自查](reports/security-precheck.md)：授权、并发/事务、密钥与依赖结果；[独立 Docker 复核](reports/review-01.md)已完成并归档。

## 技术栈

Vue 3 / Vite、Java 17 / Spring Boot 4、MySQL 8、Qdrant、RabbitMQ、Python MCP 服务，使用 Docker Compose 部署。

## 快速启动

Windows 首次安装可按 [Docker Desktop 官方说明](https://docs.docker.com/desktop/setup/install/windows-install/)启用 WSL 2 / Linux 容器，并启动 Docker Desktop。用 `docker info` 确认 Engine 可连接，`docker compose version` 确认 Compose 可用。构建在容器内完成，只启动应用无需本机 Java/Node；运行下述验收脚本另需 Python 3.11+。

需要可用的 Chat 和 Embedding 模型服务，在仓库根目录执行：

```powershell
Copy-Item .env.example .env.team
```

首次创建后编辑私有 `.env.team`，填写所有空白凭据和模型字段。模板已明确团队项目名、`MYSQL_DATABASE=campusclaw_plus` 及三个宿主端口，供 Compose 和验收工具共同使用；已有私有配置无需重新复制或改库。模型地址包含 `/v1`，嵌入维度填实际输出维度；容器访问宿主机模型时使用 `host.docker.internal`，容器内 `localhost` 指向容器自身。首次构建需要网络下载基础镜像和 Maven/npm 依赖。

`AUTH_JWT_SECRET` 至少 32 字节；各口令、MCP Token 使用独立随机值。`MCP_TOKEN_ENCRYPTION_KEY` 为 Base64 编码的 32 字节随机密钥，可用 `python -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"` 生成，填入私有配置，不写入文档或 Git。

空数据库首次演示时，将 `DEMO_SEED_ENABLED=true` 并设置 `DEMO_SEED_PASSWORD`，以创建演示账号；默认关闭种子时不会自动创建账号。演示账号为 `teacher-a`、`student-a1`、`student-b1`，口令由该环境变量提供。

```powershell
docker compose --env-file .env.team config --quiet
docker compose --env-file .env.team up -d --build --wait --wait-timeout 300
docker compose --env-file .env.team ps
```

- 应用：<http://localhost:18080>
- 健康检查：<http://localhost:18080/health>，返回版本、UTC 时间与数据库/Qdrant 状态；两者就绪返回 200，否则 503，模型与 MCP 标为 `not_checked`。
- API 文档：<http://localhost:18080/swagger-ui/index.html>

确认三个演示账号可登录后，将 `.env.team` 中 `DEMO_SEED_ENABLED` 改回 `false`，**保留原 `DEMO_SEED_PASSWORD`** 供登录和验收脚本使用。执行以下命令应用配置；单独 [restart](https://docs.docker.com/reference/cli/docker/compose/restart/) 不会载入修改后的环境变量：

```powershell
docker compose --env-file .env.team up -d --no-deps --wait backend
docker compose --env-file .env.team restart frontend
```

不再修改既有数据库口令、JWT 或 MCP 加密键来“重新初始化”。独立复现记录、Python 环境和验收后清理见 [Class08 交接](docs/lesson-08/handoff.md)与[复核记录](reports/review-01.md)。

默认项目名 `campusclaw-plus`，七个服务使用独立网络和具名数据卷，后端与 worker 共用一个镜像；不会复用旧 `campusclaw` 项目的容器或数据。网页/MySQL/RabbitMQ 管理端口为 `18080/13306/15673`，均只绑定本机；覆盖键分别为 `APP_PORT/MYSQL_HOST_PORT/RABBITMQ_MANAGEMENT_PORT`。每次启停均指定同一配置文件，不混用个人项目的 `.env`。镜像、卷与构建缓存随 Docker 引擎的数据盘存放，磁盘位置由 Docker Desktop 设置管理。

停止服务并保留数据：

```powershell
docker compose --env-file .env.team down
```

材料、数据库和向量保存在 Docker volumes 中；`docker compose down -v` 会删除这些数据。MCP 示例服务只在 Compose 内部网络提供，教师在助手配置页登记 `http://mcp-demo:8090`，使用与 `MCP_AUTH_TOKEN` 相同的 Token。

### 异步作业批改

Compose 同时启动 RabbitMQ 和独立 `grading-worker`。教师先在讲义页上传参考答案（`.txt/.md`），待解析完成后，在 `/assignments` 的“新建作业”弹窗填写标题、详细题目、总分并绑定文件。系统只保存参考答案快照，创建时不调用模型；学生每份作业只可提交一次，提交后展示本人作答及批改结果。后台 worker 让 AI 结合题目、总分、参考答案与作答综合给分并说明理由，服务端校验分数范围和输出结构；教师可在“改分”弹窗填写终评分数和评语，学生优先显示教师终评。

任务状态通过 `/api/grading/tasks/{taskId}` 只读查询，页面刷新可恢复结果；成功/失败操作提示统一从顶部弹出并自动关闭。

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

安全自查入口为 `python scripts/security_precheck.py --env-file .env.team`，可用 `--checks access` 只检查授权；全项会暂时停启本团队 worker 并调用模型，应在无人工提交时执行。密钥与依赖合并入口为 `python scripts/check_security.py --env-file .env.team`，需先构建前端、准备 Gitleaks 镜像，范围及退出码见[扫描说明](reports/security-baseline.md)。SEC-07 的两项审计缺口暂缓处理，原始记录保留，不属于本次同伴复核任务。

## 开发与安全

项目继承 CampusClaw 的代码与功能，Git 历史从团队项目启动提交开始保留。新增业务通过 OpenSpec change 评审、实现与验收。运行基线实现与验收进度见[项目状态](docs/project-status.md)，日志字段、版本覆盖和回滚流程见[运维规范](docs/standards/05-operations.md)。

`.env`、`.env.team` 等私有配置、真实凭据与个人学习数据不得提交。演示种子仅用于本地或课堂环境，公开部署应关闭演示初始化。项目采用 [MIT 许可证](LICENSE)。
