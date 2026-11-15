# Spec Delta

## MODIFIED Requirements

### Requirement: 课程要求的最小接口覆盖

接口套件 MUST 至少覆盖登录、本班检索、提示词与多工具技能配置、MCP 服务管理和发现、创建会话、发送一条流式消息、读取工具审计，以及 Class09 的 SQL 注入、XSS 和 CSRF 等价安全反例，并使用登录接口签发的 Bearer Token 调用受保护接口。安全反例 MUST 与原有功能用例一起进入常规回归，不得只在一次性脚本中存在。

#### Scenario: 执行最小回归集合
- **WHEN** `run_all.py` 在具备教师、学生、READY 测试材料和可访问测试 MCP 服务的环境中运行
- **THEN** 套件 MUST 覆盖 `POST /api/auth/login`、`POST /api/retrieval/search`、智能体与技能配置、`GET|POST /api/mcp/servers`、`PUT|DELETE /api/mcp/servers/{id}`、`POST /api/mcp/servers/{id}/connect`、`POST /api/mcp/servers/{id}/disconnect`、`GET /api/mcp/servers/{id}/tools`、`GET /api/mcp/tool-calls`、`POST /api/conversations`、`POST /api/conversations/{id}/messages`、检索注入用例、材料 XSS 用例和材料 CSRF 等价用例
- **AND** 检索、MCP 配置、工具缓存、审计和会话 MUST 验证当前班级归属，配置 MUST 验证教师权限，流式会话 MUST 验证学生归属与 Bearer 认证
- **AND** 安全反例 MUST 在失败时使套件非零退出，不得被后续用例或报告掩盖

#### Scenario: 本机配置和秘密管理
- **WHEN** 接口套件需要服务地址、测试账号、数据库连接、模型配置或 MCP Token
- **THEN** 这些值 MUST 从本机环境或未提交的 `.env` 读取，并在缺失时以脱敏错误快速失败
- **AND** 仓库中的脚本、示例、报告和文档 MUST NOT 硬编码真实凭据

## ADDED Requirements

### Requirement: 常驻输入安全反例

`apitest/cases/` MUST 常驻 SQL 注入、XSS 和 CSRF 等价反例，按实际接口目录和方法组织，并在常规回归中执行。反例 MUST 使用可归属的合成数据、登记清理，不得修改已有 READY 材料或其他用户数据；公共请求能力 MUST 支持发送自定义请求头和必要的上传请求。

#### Scenario: SQL 注入不能扩大查询
- **WHEN** 合法学生以 `' OR '1'='1`、UNION 探针或客户端伪造的 `classId` 调用检索
- **THEN** 响应 MUST 不返回跨班材料或切片，也不得返回 500
- **AND** 每条命中 MUST 属于服务端当前用户班级且为 READY；相关表计数 MUST 不变
- **AND** 非法类型的 `limit`、`mode` 或空 `query` MUST 返回 400

#### Scenario: 标题和 Markdown 中的恶意内容按文本处理
- **WHEN** 合成材料的文件名或标题包含 `<script>` 或带事件属性的标签，列表和详情返回该内容
- **THEN** API MUST 原样返回文本，数据库标题 MUST 与响应一致，不做错误的双重转义
- **AND** 前端 MUST 在实际渲染 DOM 中断言内容为文本节点，不产生 `script`、`img` 或事件属性节点
- **AND** Markdown 渲染 MUST 保持 `html:false` 和 KaTeX 非信任模式，恶意链接不得变成可执行 URL

#### Scenario: CSRF 等价拒绝与正常对照
- **WHEN** 另一站点伪造带 `Origin`/`Referer`、无本系统认可的 Bearer 令牌的配置写请求
- **THEN** 系统 MUST 返回 401 且数据库保持不变
- **AND** 带合法 Bearer 令牌的对照请求 MUST 成功；登录响应 MUST NOT 设置会话 Cookie
- **AND** 对使用 `sessionStorage` + Bearer JWT 的无状态项目，报告 MUST 记录等价验收口径并取得教师/团队确认，不得伪造 Cookie、SameSite 或 CSRF token 证据

#### Scenario: 合成资源清理
- **WHEN** 安全反例创建材料、技能、会话或其他合成资源
- **THEN** 用例 MUST 按运行标识登记并在成功或失败后清理
- **AND** MUST NOT 修改或删除已有 READY 材料、其他用户数据或无关资源
