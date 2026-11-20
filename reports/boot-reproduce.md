# Class10 启动与镜像追溯

日期：2026-11-17（UTC）/ 2026-11-18（本地）
第 1～4 节记录原实现的团队 Docker 验证。修复提交的干净目录复现在第 5 节。

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

## 5. 修复提交的干净目录复现

2026-11-18，从 `c8e8abf34a94fdd47dd8e673bfe550416a0c51c4` 通过 `git archive` 生成干净副本。使用 Docker Engine 29.8.2 Linux、独立项目名、空数据卷和仅绑定回环的测试端口。测试配置使用新生成的合成凭据和本地 OpenAI 兼容替身，按第 1 节直接构建三个 Dockerfile，标签取 `c8e8abf`，再运行 `docker compose --env-file .env.team up -d --no-build --wait`。本地合成模型由临时 Compose 覆盖文件挂进同一个测试网络。

| 镜像用途 | 本地 Image ID |
| --- | --- |
| backend / grading-worker | `sha256:e23103ecd57ced828c119a1727fc90975988c224bfdb3024e8ad61b860968fd5` |
| frontend | `sha256:4cea03b33e061e439017e008a6c2cc8e1b09f7d6b1fcde60424b3dbbbfb055ac` |
| mcp-demo | `sha256:f73f244aa055fa9449e308370e8ced1b7e54fa751105fd3656f4d25983bf7a91` |

三个镜像的 OCI revision 均匹配完整提交。七服务运行，五个服务健康检查通过。前端页面返回 200，`/health` 经前端代理返回 200，版本为 `c8e8abf`，数据库和 Qdrant 均为 up。

教师、同班学生和跨班学生通过合成账号登录。上传讲义达到 READY。学生提交返回 202 和任务 ID，停 worker 时保持 PENDING，恢复后进入 SUCCEEDED。跨班材料读取返回 404，跨班检索 200 且 0 命中，本班有 1 条命中。现有 `four_step_demo.py` 限定标准团队项目名和数据库名，本轮用临时脚本按同一业务判据验证。

执行 `docker compose down --volumes --remove-orphans` 清理本轮专用项目，确认容器、卷及网络均无残留。临时配置和镜像标签已删除，脱敏原始摘要在忽略目录 `backend/target/class10-review/cc10-de9b1dc2/`。本节记录技术复现，另一成员的独立启动、真实模型质量与交叉评审另记。
