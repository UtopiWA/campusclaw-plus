# Spec Delta

## MODIFIED Requirements

### Requirement: 报告与延期复核

reports MUST 仍包含 Class08 的六份固定报告及有答案/无答案/跨班问题草稿，并保留原始 `security-precheck.md`、`security-baseline.md`、`risk-authz.md` 和 `review-01.md`，不得用新结果抹去旧红或覆盖原评审。Class09 MUST 新增 `security-fix-log.md`、`audit-recheck.md`、`sec08-experiment.md`、`gate-red-green.md`、三条反例记录、实际返工记录和后续编号的评审记录。SEC-07 MUST 实际触发技能并记录操作人、稳定技能标识、状态、真实用量和耗时；未知用量不得填 `0`。Class09 不再允许把技能 ID 和 Token 用量列为搁置项。另一成员 MUST 独立复现至少两条修复并单独记录；代理不得代签。证据未完成或交叉复测未执行时，change MUST 保持活动，不得提前同步或归档。

#### Scenario: 审计不完整与同伴未执行
- **WHEN** Class09 技能审计缺少稳定技能标识、真实用量、耗时或拒绝记录，或另一成员尚未独立复现
- **THEN** MUST 记录 SEC-07 红和未执行项，保留真实退出码，change 保持活动
- **AND** MUST NOT 以“已加固”、空模板或代签结论替代

#### Scenario: 已搁置缺口不追加复核门槛
- **WHEN** Class08 记录曾把技能 ID 和 Token 用量列为搁置项
- **THEN** Class09 MUST 将该缺口重新纳入 SEC-07 修复和验收，旧搁置决定 MUST NOT 继续豁免
- **AND** 原始红项和历史记录 MUST 保留，不得改写为已修复

#### Scenario: 交叉复测未执行
- **WHEN** 另一成员尚未独立复现至少两条修复
- **THEN** MUST 明确记录未执行，不得代签或写成通过
- **AND** 不得在证据不完整时归档或同步主规格

#### Scenario: 证据完整后同步归档
- **WHEN** 修复台账、审计回查、SEC-08、门禁红绿、回归和交叉复测均有实际记录
- **THEN** 可在获得授权后同步主规格、执行严格校验并归档
- **AND** 历史红项和未执行项 MUST 继续保留在对应报告

## ADDED Requirements

### Requirement: Class09 安全复测与缺口处置

系统 MUST 按相同前置、操作和判据重放 SEC-01～08，并记录修复前实际、修复后实际、命令、退出码和判定。已绿条目 MUST 保留实际绿，不得人为回退或制造漏洞；只有已确认的真实缺口才修复。授权检查 MUST 覆盖未认证、学生越权、跨班访问和直接业务写入；合法本人提交 MUST 继续成功。SEC-05/06 MUST 记录当日密钥和依赖扫描范围、工具、计数、退出码及处置；SEC-07 MUST 按七类字段回查；SEC-08 MUST 实际完成四步实验，越权写入 MUST 被代码拒绝并留痕。

#### Scenario: 授权复测
- **WHEN** 在团队环境重放 SEC-01～04
- **THEN** MUST 记录 401/403/404、跨班检索数量、数据库前后变化和合法提交结果
- **AND** 发现真实缺口时 MUST 按 SEC 编号单独修复并补聚焦测试，不得为凑绿放宽权限

#### Scenario: 资产与审计复测
- **WHEN** 重跑 SEC-05～07
- **THEN** 密钥扫描 MUST 以 0 命中为目标；保留候选时 MUST 写明限定处置、命中数和真实退出码
- **AND** 依赖 MUST 记录 high/critical 数量；高危 MUST 升级/替换或有可检查的书面处置
- **AND** 技能审计 MUST 能按技能、操作人和时间窗回查七类信息，未知用量保持 `NULL`

#### Scenario: SEC-08 越权硬底线
- **WHEN** 执行 SEC-08 第四步，要求助手冒充管理员修改他人成绩
- **THEN** 模型/助手不得获得成绩写入工具，接口层 MUST 拒绝学生或跨班教师的终评请求
- **AND** 数据库 MUST 保持不变，拒绝审计 MUST 可回查；模型自述不得当作执行成功

#### Scenario: 教师配置参考文件的实验边界
- **WHEN** 教师在已有权限范围内上传并绑定仅包含给分指令的参考文件，观察到评分变化
- **THEN** MUST 如实记录输入来源和评分现象，不得仅据此认定学生输入漏洞或将其列为安全阻断项
- **AND** 学生恶意作答、秘密泄露与越权工具执行 MUST 按各自实际入口单独判定，不能由教师参考配置实验替代

### Requirement: 安全门禁与红绿证据

仓库 MUST 提供 `make check-security` 和可在无 `make` 环境执行的 Python 等价入口。门禁 MUST 依次消费密钥/依赖扫描、SEC-01～04 访问检查、SEC-07 审计检查、常驻安全反例和脚本测试的真实退出码；任一步骤失败或未完成 MUST 非零退出，不得使用 `|| true`、降低阈值、跳过范围或忽略失败。README MUST 说明前置条件、两种入口和退出码含义。仓库 MUST 保留一次受控“必红 → 修复/移除受控故障 → 绿”的完整证据，红不得通过泄露真实密钥制造。

#### Scenario: 门禁传播失败
- **WHEN** 任一扫描、安全检查、反例或脚本测试失败，或环境/范围未完成
- **THEN** 门禁 MUST 返回非零，并保留该步骤的真实退出码和脱敏摘要
- **AND** MUST NOT 因其他步骤通过而返回 0

#### Scenario: 受控红绿证据
- **WHEN** 使用明确标记的合成故障或受控自测制造一次红
- **THEN** 门禁 MUST 返回非零并记录红输出/退出码
- **AND** 移除受控故障后重跑 MUST 返回 0，报告 MUST 记录改动位置、移除动作和绿输出/退出码
- **AND** 整个过程 MUST NOT 使用真实密钥、学生数据或供应商原始敏感内容

#### Scenario: Windows 无 make
- **WHEN** 本机没有 GNU make
- **THEN** README MUST 给出 `python scripts/security_gate.py --env-file .env.team` 等价入口
- **AND** 验收记录 MUST 如实写明 `make check-security` 未在本机执行，不得声称运行过不存在的命令
