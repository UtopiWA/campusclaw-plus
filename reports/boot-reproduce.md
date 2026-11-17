# Class10 启动与镜像追溯

日期：2026-11-17（UTC）/ 2026-11-18（本地）
范围：本机团队 Docker 的重新构建、启动和版本反查。**未**在另一干净目录或另一台机器独立启动；该人工步骤保持未完成。

## 1. 构建命令

```powershell
$short = git rev-parse --short HEAD
$tag = "class10-$short-dirty"
$env:GIT_COMMIT = git rev-parse HEAD
$env:IMAGE_TAG = $tag
docker build --build-arg GIT_COMMIT=$env:GIT_COMMIT -t "campusclaw-plus-backend:$tag" ./backend
docker build --build-arg GIT_COMMIT=$env:GIT_COMMIT -t "campusclaw-plus-frontend:$tag" ./frontend
docker build --build-arg GIT_COMMIT=$env:GIT_COMMIT -t "campusclaw-plus-mcp-demo:$tag" ./mcp-server
docker compose --env-file .env.team up -d --no-build --wait --wait-timeout 300
```

说明：`docker compose build` 在本机 Docker Compose v5 上以 bake 退出 1；改用三个直接 `docker build` 后镜像构建成功，Compose 用 `--no-build` 启动。该现象只记录本机，不改变 Dockerfile 契约。工作区有 Class10 未提交修改，因此运行标签带 `-dirty`；镜像中的 `revision` 是 HEAD `b30ab9d`，不代表干净提交产物。

## 2. 镜像标识

| 镜像 | Image ID | `org.opencontainers.image.revision` |
| --- | --- | --- |
| `campusclaw-plus-backend:class10-b30ab9d-dirty` | `sha256:fb5fff4d5eda74e371b9edb7a7732195cfe7f805057b0ddaf7ab4101f08f44e6` | `b30ab9d333b1f2dbb441dc46143a8d84a9d352bf` |
| `campusclaw-plus-frontend:class10-b30ab9d-dirty` | `sha256:71a4d68dbf1eed323de511a4aca5260d76d3c75dbbcd1528ddf215c8d49f9140` | `b30ab9d333b1f2dbb441dc46143a8d84a9d352bf` |
| `campusclaw-plus-mcp-demo:class10-b30ab9d-dirty` | `sha256:18f951c69831cb580e6852ca954cedde377dcdb83c31f531e13dc94e99749b99` | `b30ab9d333b1f2dbb441dc46143a8d84a9d352bf` |

前端最终镜像基于 `nginx:1.27-alpine`，运行镜像不含 Node 源码；后端只含 JRE 和 JAR；MCP 的 `server.py` 是运行必需代码，属于已登记的语言差异。

## 3. 启动结果

`docker compose --env-file .env.team ps`：

| 服务 | 状态 |
| --- | --- |
| mysql | Up (healthy) |
| qdrant | Up (healthy) |
| rabbitmq | Up (healthy) |
| mcp-demo | Up (healthy) |
| backend | Up (healthy) |
| grading-worker | Up (未配置独立 healthcheck) |
| frontend | Up (未配置独立 healthcheck) |

`GET /health`：

```json
{"status":"ok","version":"class10-b30ab9d-dirty","time":"2026-11-17T17:49:50Z",
 "dependencies":{"database":"up","qdrant":"up","chat":"not_checked","embedding":"not_checked","mcp":"not_checked"}}
```

`/health` 的 `version` 与镜像标签一致；health 只声明数据库/Qdrant 就绪，模型、MCP 仍未探测，不能当作全部依赖正常。

## 4. 启动边界

- 本轮复用现有团队 MySQL 数据卷和演示账号，没有验证“干净目录/另一台机器 + 独立卷”。
- 前端 502 复现：重建 backend 后需 `docker compose restart frontend` 刷新 Nginx 上游解析；已按 README 执行。
- 未执行镜像推送、远端拉取、回滚演练或跨机器启动。
- 七服务均处于运行状态，但只有配置 healthcheck 的服务显示 healthy；不把“Up”写成“全部健康”。
