# Class08 当前交接

已提交基线为 `e7507b6`（含业务功能与安全工具），首次部署配置、验收依赖和说明随后提交为 `af9cb93`。2026-11-06 已在本机独立 Docker 环境完成复现、交叉评审与收尾，两个 change 已归档；本文件保留为后续复现流程说明。实际结果见[复核记录](../../reports/review-01.md)。

技能 ID 和 Token 用量暂缓处理，不列入本次复核或归档前置条件；原始 SEC-07 记录保留。

## 1. 安装与首次启动

以下命令在仓库根目录用 PowerShell 执行。按 [README](../../README.md#快速启动)安装并启动 Docker Desktop（WSL 2 / Linux 容器），确认 `docker info` 和 `docker compose version` 正常。还需 Python 3.11+；仅做本页容器验收无需本机 Java/Node。规格同步与归档使用已有 OpenSpec CLI。

1. 首次复制 `.env.example` 为 `.env.team`，填写空白凭据及实际 Chat/Embedding 服务；配置不通过 Git 传递。保留模板中的项目名 `campusclaw-plus`、数据库 `campusclaw_plus` 和宿主端口 18080/13306/15673。端口冲突时修改对应配置项，之后所有命令使用同一文件。
2. 空库将 `DEMO_SEED_ENABLED=true`，设置自己的 `DEMO_SEED_PASSWORD`。按 README 执行 `config --quiet`、`up -d --build --wait --wait-timeout 300` 和 `ps`。首次构建需要下载依赖；报错先解决，不跳过服务继续验收。
3. 打开应用，确认 `teacher-a`、`student-a1`、`student-b1` 均可用所设口令登录，再将种子改回 `false`。按 README 重建 backend 配置并重启 frontend，保留初始化口令不变。不要在授权自查前删除 B 班种子材料。

应用默认 <http://localhost:18080>；RabbitMQ 管理页 <http://127.0.0.1:15673>。health 200 只证明数据库/Qdrant就绪，模型仍需下面的实际操作验证。首次机器出现下载/模型连接问题，检查网络、服务地址和嵌入维度；容器连接宿主机模型使用 `host.docker.internal`。

## 2. 页面验收

先在已忽略目录准备合成参考文件，避免在仓库根目录留下材料：

```powershell
New-Item -ItemType Directory -Force backend/target/review-materials
'直角三角形满足 a²+b²=c²，c 为斜边；两条直角边为 3 和 4 时，斜边为 5。' | Set-Content -LiteralPath backend/target/review-materials/reference.md -Encoding UTF8
```

- 教师上传该文件并等待 READY，检索其中内容；配置 MCP `http://mcp-demo:8090`，Token 填自己 `.env.team` 中的值，连接发现工具、绑定计算器技能/助手，提问“计算2的20次幂”，确认有工具调用且结果为 1048576。
- 教师绑定该参考文件创建作业；学生提交一次，刷新后仍可查看作答和批改结果，再次修改提交被拒绝；教师终评后学生优先看到教师分数与评语。确认状态和顶部操作提示正常。

使用合成材料和作答，记录结果后再清理。首次环境的种子示例不等于本次验收结果，应新建自己的作业。

## 3. 异步与授权验收

创建仓库内的独立 Python 环境，无需激活，也不安装到系统环境：

```powershell
python -m venv .venv-review
.\.venv-review\Scripts\python.exe -m pip install --no-cache-dir -r apitest/requirements.txt
.\.venv-review\Scripts\python.exe -B scripts/security_precheck.py --env-file .env.team --checks tasks
.\.venv-review\Scripts\python.exe -B scripts/security_precheck.py --env-file .env.team --checks access
```

非 Windows 使用 `.venv-review/bin/python`。tasks 会短暂停启本团队 worker，需在无人提交作业时执行，验证 202 无分数、停 worker 后 PENDING、恢复自动完成、重复消息结果不变；access 至少记录 SEC-01/02 的匿名拒绝和跨班隔离实际值。两项均不执行 SEC-07；上传索引和评分仍会调用所配模型服务。

requirements 已包含 MySQL 8 所需的 PyMySQL RSA 认证依赖，不要改成仅安装基础 `pymysql` 或降级数据库认证方式。旧虚拟环境也应重新执行上面的安装命令。

两项应退出 0，控制台显示 `execution=complete cleanup=complete`。脚本只回收本轮登记的合成资源；失败或中断可能留下待处理任务及清理登记，不能只看页面正常就记通过，也不要先删证据再重跑。结果位于已忽略的 `backend/target/security-precheck/<runId>/`。

## 4. 审核、记录与收尾

对照 [项目规则](../../AGENTS.md)和[已有评审](review.md)核对七维/逐文件结论及权限、事务、并发、恢复。将实际成员、交付提交、环境、命令、结果和清理状态写入 [review-01](../../reports/review-01.md)，在 `review.md` 追加结论或链接。有改动补跑受影响检查，没有改动无需重跑整套自动测试。

通过后完成[异步批改](../../openspec/changes/archive/2026-11-03-add-async-assignment-grading/tasks.md)与[依赖升级](../../openspec/changes/archive/2026-11-04-upgrade-spring-security-baseline/tasks.md)的剩余任务，同步 delta 到主规格，执行 `openspec validate --all --strict`、`git diff --check`，归档两个 change，更新 README/项目状态及活动链接，最后提交。建议 message：`docs: verify and archive Class08 changes`；推送与标签按团队授权办理。

## 5. 验收后清理

先将脱敏结论写入上面的记录。私有配置、虚拟环境和运行产物均被 Git 忽略；命令中的 `-B` 避免在源码旁产生 Python 缓存。自动脚本只清理自己的资源，手工新建作业和带审计的 MCP 不会自动消失。

**继续使用应用：** 保留 `.env.team` 和四个数据卷；需要停止时执行 `docker compose --env-file .env.team down`。手工验收数据保留在自己的部署中，不属于已自动清理的内容。

**一次性验收后完全撤除：** 确认这是自己专用的 `campusclaw-plus`、全部结果已记录，以下命令会删除该环境的数据库、上传、向量和队列数据；不要用于要继续保留的环境。

```powershell
docker compose --env-file .env.team ps
docker compose --env-file .env.team down --volumes --remove-orphans --rmi local
```

`--rmi local` 删除无自定义标签的本项目构建镜像；带固定标签的后端镜像如无需保留，可再执行 `docker image rm campusclaw-plus-backend:local`，不加 `--force`。共享基础镜像和构建缓存供后续复用；不执行全局 `system prune`、`volume prune` 或 `builder prune`。命令范围见 [Docker 官方说明](https://docs.docker.com/reference/cli/docker/compose/down/)。

最后核对当前目录为本验收仓库、下面目录确为本轮生成且原始证据已不再需要，再删除本地临时文件：

```powershell
Get-Location
Get-Item -LiteralPath .venv-review, backend/target/security-precheck, backend/target/review-materials | Select-Object FullName
Remove-Item -LiteralPath .venv-review, backend/target/security-precheck, backend/target/review-materials -Recurse -Force
git status --short
```

完全撤除且不再使用时可删除私有 `.env.team`；保留部署则保留原凭据，尤其不要随意更换 MCP 加密键。若测试失败需排查，先保留登记和日志，修复重验后再清理；这不应被写成“已清理”。本机临时文件不提交。
