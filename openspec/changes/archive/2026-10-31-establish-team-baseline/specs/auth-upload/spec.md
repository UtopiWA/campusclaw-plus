## MODIFIED Requirements

### Requirement: Docker Compose 部署与健康检查
系统 MUST 提供 Docker Compose 标准启动方式、持久化数据库与上传目录，并提供无需认证的 `GET /health` 健康检查。健康响应 MUST 保留 `status=ok|unhealthy`，增加非空 `version`、UTC ISO-8601 `time` 与 `dependencies`；`database` 和 `qdrant` 的 `up|down` 决定 HTTP 200/503，`chat`、`embedding` 和 `mcp` MUST 显式报告 `not_checked`，不得冒充已验证连通。探测 MUST 只读、无自动重试，在受控超时测试中 8 秒内结束，不得因健康访问执行模型生成、工具调用或遍历教师配置。

#### Scenario: Compose 启动后可访问
- **WHEN** 操作者按 README 配置环境变量并执行 `docker compose up --build`
- **THEN** 应用容器 MUST 启动且健康检查通过
- **AND** 浏览器 MUST 能访问登录页

#### Scenario: 匿名健康检查成功
- **WHEN** 未携带认证令牌的客户端请求 `GET /health` 且数据库与 Qdrant 可用
- **THEN** 系统 MUST 返回 HTTP 200、`status=ok`、部署版本、有效 UTC 时间，且 `dependencies.database=up`、`dependencies.qdrant=up`
- **AND** `dependencies.chat`、`embedding` 和 `mcp` MUST 为 `not_checked`，请求 MUST NOT 被重定向到登录页

#### Scenario: 容器重建后数据保留
- **WHEN** 已存在种子数据和教师上传材料后，操作者执行 `docker compose down` 再执行 `docker compose up` 且未删除持久化目录或 volume
- **THEN** 预置用户 MUST 仍可登录
- **AND** 已上传材料、知识条目和文件 MUST 仍可被本班用户访问

#### Scenario: 数据库不可达或连接池获取失败
- **WHEN** 应用已经启动，数据库不可达或健康探测在既有连接池的获取时限内无法取得连接，客户端匿名请求 `GET /health`
- **THEN** 系统 MUST 返回 HTTP 503、`status=unhealthy`、`dependencies.database=down`，仍包含版本和时间
- **AND** 响应 MUST NOT 包含数据库地址、账号、连接字符串、异常栈或原始异常信息

#### Scenario: Qdrant 不可达或未就绪
- **WHEN** 应用已经启动，Qdrant 只读就绪探测超时、返回非成功状态或连接失败
- **THEN** `GET /health` MUST 返回 HTTP 503、`status=unhealthy` 和 `dependencies.qdrant=down`
- **AND** 即使数据库可用，系统 MUST NOT 将整体状态标为 `ok`，也 MUST NOT 写入、创建或删除 collection

#### Scenario: 上游持续不响应
- **WHEN** 受控测试令数据库或 Qdrant 探测持续不响应，客户端请求 `GET /health`
- **THEN** 系统 MUST 在 8 秒内返回脱敏 HTTP 503，失败依赖标为 `down`
- **AND** MUST NOT 自动重试，后续独立探测在依赖恢复后 MUST 能重新报告 `up`

#### Scenario: 探测入口不扩大业务权限
- **WHEN** 未登录用户访问健康检查后请求 `/api/materials`，或班级 A 用户继续访问班级 B 的业务资源
- **THEN** 健康入口 MUST NOT 创建会话或改变权限，业务请求仍 MUST 分别返回 HTTP 401 或既有跨班 HTTP 404
- **AND** 健康响应 MUST NOT 包含任何用户、班级、材料、会话或 MCP 服务配置数据

#### Scenario: 部署版本配置非法
- **WHEN** 操作者显式设置的 `APP_VERSION` 不匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`，包括空值或超过 64 个字符
- **THEN** 应用 MUST 启动失败并报告配置项名称，不得回显非法原值
- **AND** 未设置该覆盖项时 MUST 使用构建产物中的项目版本，不得读取本机 Git 目录或远程仓库作为运行依赖

#### Scenario: 查询参数不能切换探测范围
- **WHEN** 客户端在 `GET /health` 附带任意查询参数，例如要求指定远端地址、执行模型生成或检查其他班级服务
- **THEN** 系统 MUST 忽略这些参数并维持固定探测范围，MUST NOT 据其访问新的地址或业务资源
- **AND** 日志 MUST NOT 保存这些查询值

#### Scenario: 必需配置缺失
- **WHEN** 数据库、JWT、模型必需配置或 MCP 加密密钥缺失或非法，操作者启动应用
- **THEN** 应用 MUST 按既有配置校验拒绝启动，而非依靠健康响应将错误配置降级为可用
- **AND** 非敏感配置的既有安全默认值 MUST 按规范登记，不得将演示口令或密钥补成硬编码默认值
