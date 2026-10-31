# request-observability Specification

## Purpose

为团队增量开发提供一致且脱敏的后端请求观察方式，使成员能够通过单行 JSON 日志与同一请求 ID 串联同步和异步行为，在认证拒绝、依赖故障和流式失败时仍能定位结果，并避免请求正文、身份凭据及内部地址进入公开证据。

## Requirements

### Requirement: JSON 日志与请求生命周期
后端运行日志 MUST 以单行合法 JSON 输出，至少包含 `timestamp`、`level`、`logger`、`event` 和 `requestId`；无请求上下文的启动/调度日志 MUST 使用 `requestId=null`。后端请求 MUST 在处理前确定 `X-Request-Id`，并在正常和错误响应头中回传；受控的 HTTP 完成日志 MUST 包含方法、无查询值的路径、最终状态、非负整数 `durationMs`，流式请求仅在实际完成或失败时记录一次完成事件。不得修改既有业务正文或 SSE 协议。

#### Scenario: 正常请求可关联
- **WHEN** 客户端携带匹配 `[A-Za-z0-9_-]{1,64}` 的单一 `X-Request-Id` 发起本班合法请求
- **THEN** 后端 MUST 在响应头和本请求的模块日志中使用该 ID，HTTP 完成事件 MUST 只有一条并记录最终状态及实测耗时
- **AND** 每条日志 MUST 能逐行独立解析为 JSON，中文和换行内容 MUST 正确转义而非拆成多行

#### Scenario: 缺失或非法请求 ID
- **WHEN** 客户端未提供请求 ID，或提供空值、多个值、超过 64 个字符或不符合允许字符集的值
- **THEN** 后端 MUST 生成新的 UUID 格式 ID，不得原样回传或记录非法输入
- **AND** 接口自身的认证及参数校验结果 MUST 不受该替换影响

#### Scenario: 未认证和角色拒绝也可观察
- **WHEN** 未登录用户请求受保护接口，或学生调用教师管理接口
- **THEN** 后端 MUST 分别保持既有 HTTP 401/403，响应头包含请求 ID，完成日志记录相同状态
- **AND** MUST NOT 因请求未进入 Controller 而遗漏该记录，也 MUST NOT 创建业务数据

#### Scenario: 资源不存在与跨班拒绝
- **WHEN** 用户访问不存在或跨班的受保护资源并得到既有 HTTP 404
- **THEN** 请求 ID 和完成日志 MUST 仍可关联该请求
- **AND** 日志 MUST NOT 增加查询其他班级数据的操作或泄露资源真实存在性

#### Scenario: 外部依赖失败
- **WHEN** 同步检索、Chat 或 MCP 依赖失败，后端按既有契约返回脱敏错误或工具失败状态
- **THEN** 日志 MUST 记录请求 ID、稳定事件/失败代码与已知状态，MUST NOT 输出供应商原始错误或完整堆栈消息
- **AND** 请求失败 MUST NOT 造成下一请求继承该请求 ID

#### Scenario: SSE 正常结束和中途失败
- **WHEN** 同一会话请求切换到异步线程并正常结束或发生可恢复流式失败
- **THEN** 异步日志与原响应头 MUST 使用相同请求 ID，正常或错误终结后只记录一次完成事件并清理线程上下文
- **AND** `meta/delta/done/error`、正文持久化和失败消息语义 MUST 沿用现有会话契约，MUST NOT 向 SSE 增加调试事件

#### Scenario: 后台任务不串用上次请求上下文
- **WHEN** 上传派生任务使用线程池执行后，线程再次处理另一上传或无请求的恢复调度
- **THEN** 派生任务 MUST 使用提交时复制的请求 ID，无请求调度 MUST 使用空上下文，线程结束后 MUST 恢复之前的上下文
- **AND** 不同用户、班级和任务的请求 ID MUST NOT 因线程复用而混淆

#### Scenario: 敏感内容与日志注入
- **WHEN** 受控测试在登录密码、Bearer Token、材料/问题正文、MCP 参数、查询值或远端异常中放入可识别的敏感标记及换行内容
- **THEN** 后端日志 MUST NOT 包含这些标记、完整请求/响应体、密钥、内部地址或本机绝对路径
- **AND** 安全字段中的换行 MUST 转义，启动横幅、原始请求调试及多行堆栈输出 MUST NOT 破坏 JSON 行格式
