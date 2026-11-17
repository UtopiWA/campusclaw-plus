# Class10 CI 与制品追溯

日期：2026-11-18
状态：工作流配置和本地等价构建已完成；**实际推送触发、GitHub Actions 运行编号和镜像推送尚未执行**（需要用户/团队授权与远端凭据）。

## 1. 工作流

| 文件 | 运行器 | 内容 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | GitHub 托管 `ubuntu-latest` | checkout → Java 17/Node 22/Python 3.11 → OpenSpec 1.13.1 → `npm ci` → `check.py --mode unit` → `check.py --mode integration`（Testcontainers，`TESTCONTAINERS_API_VERSION=1.44`）→ 三个 `docker build --build-arg GIT_COMMIT=$GITHUB_SHA` → 记录 image ID/标签 |
| `.github/workflows/security-gate.yml` | 团队自托管 `[self-hosted, linux, x64, campusclaw]` | checkout 全历史 → 检查私有 `${{ vars.CAMPUSCLAW_ENV_FILE || '.env.team' }}` 和 Docker → `security_gate.py --env-file "$ENV_FILE"`（scan/access/audit/api/unit）→ 上传 summary |

- 功能与安全是并列的必需检查；安全门禁缺少私有配置、Docker 或扫描/模型依赖时以非零退出，不跳过阶段。
- 真实凭据只从运行器私有文件/秘密注入，不写入工作流或仓库。
- `ci.yml` 的 unit/integration 是 `make check` 的同一脚本入口；完整 `--mode all` 的 API 阶段由安全门禁自托管任务和本地运行覆盖。

## 2. 本地等价执行

| 步骤 | 命令 | 实际结果 |
| --- | --- | --- |
| 功能门禁 | `python scripts/check.py --mode unit` | 绿，退出 0；后端 256、前端 158、脚本 47、MCP 8 |
| 容器集成 | `python scripts/check.py --mode integration` | 绿，退出 0；44 测试 |
| 真实接口 | `python scripts/check.py --mode api` | 绿，退出 0 |
| 安全门禁 | `python scripts/security_gate.py --env-file .env.team --exceptions security-exceptions.json` | run `20261117T181025Z-73908` 五阶段全 0；原始 5 high 保留，详情见 `docs/lesson-10/README.md` |
| 镜像构建 | 三个 `docker build --build-arg GIT_COMMIT=...` | 成功；Image ID 见 `reports/boot-reproduce.md` |

## 3. 版本对应

- 本地候选：`master` @ `b30ab9d` + 未提交 Class10 修改；镜像标签 `class10-b30ab9d-dirty`，OCI revision `b30ab9d333b1f2dbb441dc46143a8d84a9d352bf`。
- `health.version` 返回 `class10-b30ab9d-dirty`，与镜像标签一致。
- 工作区 dirty 是事实：不能把该镜像写成干净提交制品；用户提交后应按新 HEAD 重建并记录新的 image ID/标签。

## 4. 未执行项

- 没有 `git push`、没有 GitHub Actions 运行编号、没有远端镜像摘要；代理没有获得推送授权。
- 没有为 CI 配置真实模型/私有 `.env.team`；自托管安全门禁的实际运行仍需团队运行器和授权。
- 没有验证 CI 在 GitHub 托管运行器上 Testcontainers/镜像构建的实际网络和缓存表现；工作流语法尚未经过远端真实运行。
- 没有把本地构建结果当作 CI 通过证据。
