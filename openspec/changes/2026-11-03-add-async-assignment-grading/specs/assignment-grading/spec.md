## Purpose

让教师在本班创建带评分依据的文本作答作业，学生提交后由后台异步完成模型辅助分项判断和确定性计分，并让本人和本班教师按权限查询可追溯的结果，同时保持班级、本人和数据边界。

## ADDED Requirements

### Requirement: 教师创建本班文本作业与评分依据

教师 MUST 能在自己当前班级创建文本作答作业，提交标题、题目和 1～10 条评分依据；每条评分依据 MUST 包含稳定唯一的 `key`、说明和正整数分值，总分 MUST 由分值求和且不超过 100。作业创建后 MUST 不可编辑；只有教师可创建，学生 MUST 被拒绝；其他班级的作业 MUST 按不存在处理。

#### Scenario: 教师创建合法作业
- **WHEN** 本班教师提交非空标题、题目和 1～10 条唯一 key、正整数分值的评分依据
- **THEN** 系统 MUST 返回 HTTP 201 和作业视图，`maxScore` 等于全部分值之和
- **AND** 作业 MUST 写入教师当前班级，创建者 MUST 来自服务端当前用户
- **AND** 作业 MUST 可被本班学生列出和提交

#### Scenario: 学生创建作业被拒绝
- **WHEN** 学生提交合法作业创建请求
- **THEN** 系统 MUST 返回 HTTP 403
- **AND** 数据库 MUST NOT 新增作业或评分依据

#### Scenario: 非法评分依据被拒绝
- **WHEN** 教师提交空标题/空题目、0 条或超过 10 条评分依据、重复 key、非正整数分值或总分超过 100
- **THEN** 系统 MUST 返回 HTTP 400 和脱敏错误
- **AND** 数据库 MUST NOT 留下该请求的作业、评分依据或任务

#### Scenario: 跨班作业按不存在处理
- **WHEN** 教师或学生按 ID 读取另一班级的作业或提交
- **THEN** 系统 MUST 返回 HTTP 404
- **AND** 响应 MUST NOT 泄露作业是否存在或其字段

### Requirement: 学生提交文本作答并立即返回任务

本班学生 MUST 能向本班作业提交非空文本作答。系统 MUST 在同一个本地数据库事务中保存提交、评分任务和待投递记录，然后返回 HTTP 202、任务标识、`PENDING` 状态和只读状态地址；响应 MUST NOT 包含分数，请求线程 MUST NOT 调用模型或等待批改完成。相同班级、学生和内容的重复提交 MUST 返回已有任务而不是新建版本；不同内容 MUST 创建新的提交版本和任务。

#### Scenario: 首次提交立即返回 202
- **WHEN** 本班学生向本班作业提交合法文本
- **THEN** 系统 MUST 返回 HTTP 202、`taskId`、`status=PENDING`、`statusUrl`、`submissionId` 和 `version`
- **AND** 响应 MUST NOT 包含分数、模型响应或引用
- **AND** 提交、任务和发件箱 MUST 已在同一事务提交，请求完成前 MUST NOT 调用模型

#### Scenario: 相同内容重复提交不新建任务
- **WHEN** 学生对同一作业重复提交完全相同的内容
- **THEN** 系统 MUST 返回已有提交和任务的标识，不增加版本、任务或模型调用
- **AND** 客户端传入相同 `Idempotency-Key` 时 MUST 得到同一结果

#### Scenario: 不同内容创建新版本
- **WHEN** 学生对同一作业提交不同内容
- **THEN** 系统 MUST 创建 `version` 递增的新提交和新任务
- **AND** 旧版本的提交和结果 MUST 保留，不被覆盖

#### Scenario: 非法作答被拒绝
- **WHEN** 学生提交空内容、超过约定长度或非本班作业
- **THEN** 系统 MUST 返回 HTTP 400 或 404，且 MUST NOT 创建提交、任务或发件箱记录

#### Scenario: 教师或他班学生提交被拒绝
- **WHEN** 教师尝试向作业提交，或学生提交另一班级的作业
- **THEN** 教师请求 MUST 返回 HTTP 403，跨班请求 MUST 返回 HTTP 404
- **AND** 数据库 MUST NOT 新增提交或任务

### Requirement: 任务状态与结果只读查询

系统 MUST 提供只读任务状态查询，返回 `taskId`、`submissionId`、`assignmentId`、`status`、`attempts`、`maxAttempts`、时间戳和成功结果或安全失败码。状态 MUST 为 `PENDING`、`RUNNING`、`SUCCEEDED` 或 `FAILED`。查询 MUST NOT 触发执行、增加 `attempts`、调用模型或创建任务；只有提交本人和该班教师可查询，其他学生、跨班用户和未登录用户 MUST 被拒绝。

#### Scenario: 本人查询不同状态
- **WHEN** 提交学生查询自己的 `PENDING`、`RUNNING`、`SUCCEEDED` 或 `FAILED` 任务
- **THEN** 系统 MUST 返回 HTTP 200 和对应状态，`SUCCEEDED` 时包含分数、满分、分项判断和解释
- **AND** `FAILED` 时 MUST 返回稳定安全失败码，不包含模型原始异常、提示词、作答正文或内部地址

#### Scenario: 查询无副作用
- **WHEN** 同一任务被重复查询或被前端轮询
- **THEN** `attempts`、任务状态、结果和模型调用计数 MUST 保持不变
- **AND** 查询 MUST NOT 创建新任务或重新投递消息

#### Scenario: 非本人和跨班查询被拒绝
- **WHEN** 其他学生查询该任务、教师查询其他班级任务或未登录用户查询
- **THEN** 其他学生和跨班教师 MUST 得到 HTTP 404，未登录用户 MUST 得到 HTTP 401
- **AND** 响应 MUST NOT 泄露任务、提交或分数是否存在

### Requirement: 教师查看本班提交与结果

本班教师 MUST 能列出本班作业的提交版本、任务状态、分项判断和结果；教师 MUST NOT 查看其他班级数据，学生 MUST NOT 列出其他学生的提交。列表 MUST 支持分页或受限数量，避免无界扫描。

#### Scenario: 教师查看本班提交
- **WHEN** 本班教师请求作业的提交列表
- **THEN** 系统 MUST 返回本班学生提交的版本、状态、尝试次数和成功结果
- **AND** 结果 MUST 与任务状态查询、数据库记录一致

#### Scenario: 跨班或学生越权被拒绝
- **WHEN** 教师请求其他班级作业的提交列表，或学生请求班级提交列表
- **THEN** 跨班教师 MUST 得到 HTTP 404，学生 MUST 得到 HTTP 403
- **AND** 响应 MUST NOT 包含其他学生作答、分数或标识

### Requirement: 可解释评分与版本边界

模型 MUST 只提供每条评分依据的判断、证据和解释；最终分数 MUST 由服务端按“满分/一半（向下取整）/零分”的确定性规则计算，模型返回的总分 MUST 被忽略。结果 MUST 保存评分依据快照、分项判断、计算得分、模型名称和提示词版本。同一 `(submission_id, version)` MUST 只有一份生效结果；重复消息、重复执行或并发写入 MUST NOT 覆盖或产生第二份结果。结果 MUST 明确标注为模型辅助分项判断加规则计分，教师复核不在本次范围。

#### Scenario: 分项判断生成可解释分数
- **WHEN** 模型返回合法分项判断和解释
- **THEN** 系统 MUST 按每条评分依据的分值计算分项得分和总分，并保存判断、证据、解释和依据快照
- **AND** 总分 MUST 等于确定性规则的求和结果，MUST NOT 等于模型自报总分

#### Scenario: 模型输出非法时不伪造结果
- **WHEN** 模型返回缺失字段、未知 key、非法 verdict、超长文本或非 JSON 内容
- **THEN** 系统 MUST NOT 保存结果或伪造分数
- **AND** 系统 MUST 按可重试失败处理，达到上限后把任务置为 `FAILED` 并记录安全失败码

#### Scenario: 重复投递不覆盖结果
- **WHEN** 同一任务消息被重复投递，或两个消费者并发处理同一提交版本
- **THEN** 数据库唯一约束 MUST 保证只有一份生效结果
- **AND** 后到的执行 MUST 识别已有结果并安全确认，不覆盖分数、判断或时间戳

#### Scenario: 结果标注模型辅助
- **WHEN** 学生或教师查看成功结果
- **THEN** 页面 MUST 标注“模型辅助分项判断 + 规则计分”
- **AND** 页面 MUST NOT 把结果描述为教师已复核或最终成绩

### Requirement: 前端轮询与状态恢复

学生页面 MUST 显示任务的待处理、执行中、完成和失败状态，并在 `SUCCEEDED` 或 `FAILED` 时停止轮询。轮询 MUST 在切换作业、离开页面或组件卸载时清理；刷新后 MUST 能凭持久化的提交/任务关联恢复查询。失败 MUST 显示安全提示，不得自动重新提交；前端限制重复点击不能替代后端入口去重。

#### Scenario: 轮询到终态停止
- **WHEN** 学生提交后任务从 `PENDING` 变为 `RUNNING` 再变为 `SUCCEEDED` 或 `FAILED`
- **THEN** 页面 MUST 更新状态并在终态停止后续轮询
- **AND** 页面 MUST NOT 因轮询重复提交、增加任务或触发模型调用

#### Scenario: 刷新后恢复
- **WHEN** 学生在任务未完成或已完成时刷新页面
- **THEN** 页面 MUST 从作业详情中的本人最新提交/任务恢复状态展示
- **AND** 已终态的任务 MUST 直接展示结果，不重新提交

#### Scenario: 离开页面清理轮询
- **WHEN** 学生切换作业或离开页面
- **THEN** 前端 MUST 清理定时器和未完成请求
- **AND** 后续 MUST NOT 继续查询旧任务或更新已离开的页面
