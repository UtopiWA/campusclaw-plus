# delivery-gates Specification

## Purpose

为团队交付提供可重复执行的功能/安全双门禁、可追溯的镜像构建、三接口压测、派生观测面板和真实告警送达证据，并明确哪些结果来自实际执行、哪些仍需人工完成。

## ADDED Requirements

### Requirement: 功能与安全双门禁

系统 MUST 提供稳定入口执行功能门禁（规约、前端、非容器后端、MCP、脚本、容器集成和真实接口）和安全门禁（密钥/依赖扫描、授权、审计、接口反例和脚本测试）。任一步骤失败或环境缺失时 MUST 返回非零；MUST NOT 使用 `|| true`、跳过范围、降低阈值或历史报告把失败写成成功。

#### Scenario: 功能门禁通过
- **WHEN** 运行 `make check` 或等价的 `python scripts/check.py --mode all`
- **THEN** 规约、前端测试与构建、非容器后端、MCP、脚本、独立容器集成和接口套件 MUST 依次执行
- **AND** 全部通过时退出 0；任一失败时退出 1；工具或前置环境缺失时退出 2，并明确未执行分组

#### Scenario: 安全门禁通过
- **WHEN** 运行 `make check-security` 或等价的 `python scripts/security_gate.py --env-file <file>`
- **THEN** scan、access、audit、api、unit 五阶段 MUST 执行并各自记录退出码
- **AND** 只有全部为 0 时总退出 0；出现失败或未完成时 MUST 非零，且不得读取旧摘要冒充本轮

#### Scenario: 受控红绿
- **WHEN** 在隔离工作区改坏一个期望或比较条件并运行门禁
- **THEN** 必须记录改坏位置、失败用例、非零退出码；恢复后必须记录同一命令的绿色输出和退出 0
- **AND** 不得使用真实密钥、真实用户数据或降低安全阈值制造红

### Requirement: 显式开发依赖安全例外

当扫描发现暂时无修复版本的开发依赖 high/critical 告警时，系统 MUST 通过显式传入的例外文件处理，而不是降低扫描级别、排除 dev 依赖或跳过扫描。例外 MUST 包含批准人/日期、到期日、补偿措施、公告 ID、受影响包和严重级别；每一个 high/critical 包及 `via` 公告 MUST 被覆盖。原始告警 JSON、audit 失败码和 `ACCEPTED_EXCEPTION` 标记 MUST 保留，不得把例外写成 0 告警；例外过期、未批准或出现未覆盖公告时 MUST 失败。

#### Scenario: 已批准例外覆盖全部告警
- **WHEN** 所有 high/critical 告警都匹配已批准且未过期的例外，且用户显式传入例外文件
- **THEN** 扫描 MUST 继续执行其余检查并允许门禁通过
- **AND** 原始 audit 输出和 high 计数 MUST 保留在忽略目录，输出 MUST 标记接受例外

#### Scenario: 未覆盖、过期或未批准
- **WHEN** 出现新公告、新 high/critical 包、例外过期、缺少批准人/补偿措施，或未传入例外文件
- **THEN** 扫描 MUST 非零退出，不得执行其余检查后声称通过
- **AND** MUST NOT 自动把例外登记当成零告警

### Requirement: CI 与镜像提交追溯

系统 MUST 在主干推送或拉取请求触发时在托管运行器执行功能检查并构建三个镜像。完整安全门禁 MUST 在具有私有配置、Docker 和模型环境的独立团队环境执行。镜像 MUST 携带提交的 OCI `revision` 标签，并使用与 Compose 一致的服务名及构建标签。运行健康接口 MUST 默认返回镜像标签，显式 `APP_VERSION` 继续遵守现有覆盖和校验规则。

#### Scenario: 推送触发检查与构建
- **WHEN** 向主干推送或提交拉取请求
- **THEN** CI MUST 执行功能门禁的 unit/integration 范围并构建三个镜像
- **AND** 运行记录 MUST 保存运行编号、提交 SHA、镜像标签和 image ID 供反查

#### Scenario: 手动导出镜像包
- **WHEN** 成员手动触发 CI 并启用镜像上传，且检查及构建通过
- **THEN** CI MUST 上传当前提交的三个镜像包，支持 `docker load` 导入后按原标签复现

#### Scenario: 缺少安全门禁环境
- **WHEN** 在团队环境运行门禁时缺少私有配置、Docker 或扫描/模型依赖
- **THEN** 安全门禁 MUST 非零退出并报告缺失项
- **AND** MUST NOT 以跳过阶段或空凭据伪造通过

#### Scenario: 版本可反查
- **WHEN** 使用 `GIT_COMMIT` 和 `IMAGE_TAG` 构建并启动部署
- **THEN** 镜像 inspection MUST 返回对应 `org.opencontainers.image.revision`
- **AND** 未显式覆盖时 `/health.version` MUST 与本次镜像标签一致；未提交修改的构建 MUST 标明 dirty 状态

### Requirement: 四项教学行为守护

测试 MUST 为解题引导、作业批改、错题讲解和知识库行为提供至少一条能区分正确与错误实现的检查，并建立规约场景到用例/执行入口的追溯。模型提示词检查 MUST 与真实模型行为分开记录，不得把替身或提示词断言冒充模型表现。

#### Scenario: 直接索要答案
- **WHEN** 学生使用“解题引导”技能并直接索要最终答案、正确选项或完整过程
- **THEN** 系统提示 MUST 仍要求苏格拉底式引导并禁止最终答案/选项/完整过程
- **AND** 测试 MUST 断言该限制和用户问题同时存在，不以模型口头拒绝代替

#### Scenario: 迟到批改与终评
- **WHEN** 教师已提交终评，后台迟到或重复的自动批改结束
- **THEN** 终态结果 MUST 不被迟到完成/失败替换
- **AND** 查询 MUST 同时保留原始 AI 结果和独立终评，展示优先级不丢失

#### Scenario: 错题讲解依据
- **WHEN** 模型对一道错题生成评语
- **THEN** 提示 MUST 要求指出具体依据、错误或漏项及改进建议，参考文件 MUST NOT 被当作固定评分表
- **AND** 服务端 MUST 继续校验非空、长度和分数范围，不因提示词而放宽结构校验

#### Scenario: 知识库跨班隔离
- **WHEN** 用户读取其他班级材料或检索其他班级内容
- **THEN** 材料读取 MUST 返回既有 404，检索 MUST 返回既有 200 空集合或本班结果
- **AND** MUST NOT 返回其他班级正文、标识或引用

### Requirement: 三接口压测与缓存改进

系统 MUST 提供可入库的压测脚本，分别对提交、任务查询和检索加压，并输出每个并发档位的吞吐、P50/P95、错误率和状态分布。脚本 MUST 使用唯一合成数据，结束前清理并恢复 worker；队列初始非空且未确认清理时 MUST 拒绝。至少一项索引或缓存改进 MUST 用同一脚本、同一参数和数据规模复测，记录前后值和副作用。

#### Scenario: 三接口分档压测
- **WHEN** 以多个并发档位运行压测脚本
- **THEN** 每个档位请求数 MUST 不小于并发设置，并报告提交、任务查询、检索的在途峰值、QPS、P50/P95、错误率和状态计数
- **AND** 提交 MUST 使用本轮独立作业，不得用幂等重放或 409 去重冒充新任务接收能力

#### Scenario: 缓存同口径复测
- **WHEN** 分别以 `EMBEDDING_CACHE_SIZE=0` 和默认值重启后端，运行相同脚本和参数
- **THEN** 必须记录检索吞吐/P95 的前后值、改进提交和副作用
- **AND** 缓存 MUST NOT 改变班级隔离、READY 回表、材料白名单或评分/终评正确性

#### Scenario: 清理与失败边界
- **WHEN** 压测因依赖失败或超时未完成
- **THEN** 脚本 MUST 记录稳定错误类别并尽力清理本轮可归属数据
- **AND** 队列初始非空或不可读时 MUST 拒绝启动；无法确认任务终态或清理失败时 MUST 非零退出并保留待处理资源标识，仍尝试恢复本轮停止的 worker

### Requirement: 派生观测面板与真实告警

系统 MUST 能按时间窗从既有消息、后端 JSON 日志和 RabbitMQ 管理 API 派生三技能调用量、每技能错误率、API P95 和队列深度，生成至少三张可复核图。告警 MUST 写清观察量、持续条件、负责人、动作和面板，并实际发送到接收端；未送达 MUST NOT 记为通过。观测 MUST NOT 导出用户正文、提示词、凭据或材料内容。

#### Scenario: 三技能与错误率可见
- **WHEN** 本班存在三条不同教学技能的调用
- **THEN** 面板 MUST 分别显示每条技能的调用量和错误率
- **AND** 错误率 MUST 由失败计数/总计数导出，不维护第二套事实；4xx MUST NOT 计入 5xx 错误率

#### Scenario: API P95 与队列深度
- **WHEN** 后端产生 API 请求和 RabbitMQ 队列消息
- **THEN** 面板 MUST 显示 API P95（并区分 SSE）和队列深度
- **AND** 日志或管理 API 不可达时 MUST 标记不可用，不得写成 0

#### Scenario: 告警实际触发并送达
- **WHEN** 窗口内 API 5xx 比例超过阈值且请求数达到下限
- **THEN** 告警 MUST 触发并实际 POST 到接收器，记录触发/送达时间、规则、观察值和面板路径
- **AND** 接收器未返回 2xx 或未配置 webhook 时 MUST 标记未送达
