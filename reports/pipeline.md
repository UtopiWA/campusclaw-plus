# Class10 CI 与制品追溯

日期：2026-11-18
状态：修复提交 `c8e8abf` 的远端 CI 已通过。主干尚未配置必需检查。

## 1. 工作流

| 文件 | 运行器 | 内容 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | GitHub 托管 `ubuntu-24.04` | Java 17、Node 22、Python 3.11、OpenSpec 1.13.1 → 安装前端和 Python 测试依赖 → unit/integration → 构建 backend、frontend、mcp-demo 镜像并记录 image ID/标签 |
| `scripts/security_gate.py` | 独立团队环境 | 使用私有配置，对同一候选版本执行 scan、access、audit、api、unit |

- 完整安全门禁保留在独立团队环境。公开仓库的自托管 workflow 已从候选代码移除。
- 手动运行 CI 时可选 `upload_images` 下载三个镜像的合包，用 `docker load -i campusclaw-plus-<SHA前7位>.tar.gz` 导入。普通推送和 PR 保存构建记录。镜像注册表发布及 digest 记录单独执行。
- 2026-11-18 通过 [分支接口](https://api.github.com/repos/UtopiWA/campusclaw-plus/branches/master) 和 [分支规则接口](https://api.github.com/repos/UtopiWA/campusclaw-plus/rules/branches/master) 核对：`master` 的 `protected=false`，没有生效的必需检查或规则集。CI 已能自动报告结果，阻止失败合入仍需配置。

## 2. 原实现本地执行记录

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

## 4. 远端失败与修复

| 提交 | 运行 | 结果 |
| --- | --- | --- |
| `659bdf6` | [CI 44147473727](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44147473727) | Unit gate 退出 2，后续集成和构建跳过 |
| `35bd6c2` | [CI 44148061626](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44148061626) | 同上 |
| `35bd6c2` | [旧安全门禁 44148061640](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44148061640) | 核查时在等待带指定标签的运行器 |
| `c8e8abf34a94fdd47dd8e673bfe550416a0c51c4` | [CI #3 · 44282282173](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44282282173) | 主干推送触发；Unit gate、Integration gate、三镜像构建与标识上传通过 |

前两次失败的公开注释只提供退出码。代码核对发现 Linux checkout 中 wrapper 为 `100644`，门禁直接执行会失败。现改为经 `sh` 调用，并在 CI 安装新脚本测试使用的 PyMySQL 依赖。MCP 构建目录仍为 `mcp-server`，镜像名改为 Compose 使用的 `mcp-demo`。

成功运行的三个镜像标签为 `campusclaw-plus-{backend,frontend,mcp-demo}:c8e8abf`。逐镜像 ID 及 OCI 标签保存在 [image-provenance-44282282173](https://github.com/UtopiWA/campusclaw-plus/actions/runs/44282282173/artifacts/14416784335) 的 `image-info-44282282173.txt` 中。GitHub 公开 API 已确认该制品上传成功，逐镜像文本需登录下载。制品归档摘要为 `sha256:cb4b236f2627e5d48c8517c30693d3ca9cebebda37053f8bae96c52435aa49ad`。该摘要对应记录制品，镜像注册表发布尚未执行。

## 5. 修复版本地复核

- Windows 本地功能门禁通过。前端 158 项、后端 256 项、MCP 8 项。脚本更新后单独复跑 55 项通过。
- 独立 Linux 容器从仅含跟踪文件的副本安装依赖，使用 Java 17、Node 22 和 Python 3.12，保留 `mvnw` 的 0644 权限。运行 `python scripts/check.py --mode unit`，规约、前端测试及构建、后端 256 项、MCP 和脚本检查均通过。
- 集成门禁配置 `TESTCONTAINERS_API_VERSION=1.44`，44 项通过。三个 Dockerfile 均成功构建，镜像合包通过 `docker load` 导入验证。
- 使用合成配置验证 Compose 默认版本、显式覆盖和空值传递，以及三个镜像名。
- 完整安全门禁单独在团队验收环境执行，现有记录见 [Class10 记录](../docs/lesson-10/README.md)。
