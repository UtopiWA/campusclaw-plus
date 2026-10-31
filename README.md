# CampusClaw-Plus

基于 CampusClaw 扩展的智能学习诊断与个性化辅导系统，面向班级教学中的教师与学生。

**当前可用：** 登录与班级隔离、教学材料管理与检索、技能和智能体配置、持久化流式对话、MCP 工具调用与审计。

**扩展目标：** 知识图谱与掌握度画像、个性化学习计划、自适应练习、班级共性错误分析，以及诊断、规划和辅导角色的协作。相关业务尚未实现，当前处于团队启动阶段。

## 文档

- [项目范围与成员分工](docs/project-scope.md)：完整目标、现有缺口与分阶段交付。
- [项目状态](docs/project-status.md)：当前实现、验收记录与待办。
- [项目规则](AGENTS.md) 与 [主规格](openspec/specs/)：开发约束和已实现行为契约。
- [接口回归说明](apitest/README.md)：测试环境、执行方式和报告。

## 技术栈

Vue 3 / Vite、Java 17 / Spring Boot 3、MySQL 8、Qdrant、Python MCP 服务，使用 Docker Compose 部署。

## 快速启动

需要 Docker Compose 和可用的模型服务，在仓库根目录执行：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，设置数据库口令、JWT 密钥、模型地址/密钥/名称、实际嵌入维度和 MCP 凭据。其中 `MCP_TOKEN_ENCRYPTION_KEY` 为 Base64 编码的 32 字节随机密钥，可用 `openssl rand -base64 32` 生成。

空数据库首次演示时，将 `DEMO_SEED_ENABLED=true` 并设置 `DEMO_SEED_PASSWORD`，以创建演示账号；默认关闭种子时不会自动创建账号。演示账号为 `teacher-a`、`student-a1`、`student-b1`，口令由该环境变量提供。

```powershell
docker compose --env-file .env up -d --build
```

- 应用：<http://localhost:8080>
- 健康检查：<http://localhost:8080/health>，当前检查应用与数据库。
- API 文档：<http://localhost:8080/swagger-ui/index.html>

停止服务并保留数据：

```powershell
docker compose down
```

材料、数据库和向量保存在 Docker volumes 中；`docker compose down -v` 会删除这些数据。MCP 示例服务只在 Compose 内部网络提供，教师在助手配置页登记 `http://mcp-demo:8090`，使用与 `MCP_AUTH_TOKEN` 相同的 Token。

## 检查与测试

前端与后端检查在仓库根目录执行，需要 Node.js 22+、Java 17 和可访问的 Docker Engine：

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend test -- --run
npm.cmd --prefix frontend run build
backend\mvnw.cmd -f backend\pom.xml test
openspec validate --all --strict
```

后端集成测试启动独立的 MySQL/Qdrant 容器，报告位于 `backend/target/surefire-reports/`。运行中应用的接口验收另按 [接口回归说明](apitest/README.md) 准备环境和数据，报告位于 `apitest/reports/`。

## 开发与安全

项目保留 CampusClaw 的提交历史，新增业务通过 OpenSpec change 评审、实现与验收。工程规范和团队运行基线将逐步补齐。

`.env`、真实凭据与个人学习数据不得提交。演示种子仅用于本地或课堂环境，公开部署应关闭演示初始化。当前未提供 LICENSE，许可选择由团队确认。
