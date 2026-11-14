# 当前项目状态

最后核对：2026-11-14。本文件区分已验收能力与尚未实现的团队扩展；完整目标和分工见 [项目范围](project-scope.md)。

## 当前进度

继承版本：`30cdb0e7c7b5e91b2a854ed6426b5cc9a34982f1`。首轮团队提交及协作配置已由成员确认完成。[团队基线 change](../openspec/changes/archive/2026-10-31-establish-team-baseline/proposal.md)、五类规范和读取入口已编写；unit/integration/api/all、隔离健康与配置负向检查通过。另一成员已在原生进程环境完成功能复现并审核，change 已归档；Docker Compose 与 Testcontainers 沿用发起方结果，详见[课程验收记录](lesson-07/README.md)和[归档说明](../openspec/changes/archive/2026-10-31-establish-team-baseline/README.md)。

| 工作 | 当前状态 |
| --- | --- |
| 个人项目归档与验收 | 已完成，无遗留未完成验收项 |
| 团队范围与文档入口 | 已纳入首轮提交，后续按评审调整 |
| 首轮团队提交 | 已完成 |
| 成员接入与主干保护 | 用户确认已完成 |
| 五类工程规范与工具读取规则 | `docs/standards/01-product.md`～`05-operations.md`、AGENTS 读取规则及团队检查技能已纳入验收归档的团队基线 |
| 团队基线 change | 15/15 项完成；另一成员完成功能复现、审核并归档 |
| 收尾流程 | 已完成：另一成员原生进程复现、审核、同步规格并归档；Compose/Testcontainers 沿用发起方结果 |
| Class08 异步作业批改 | 已完成并归档：功能提交 `45b2f7a`，独立 Docker 复核、页面/异步/授权检查与交叉评审通过；技能 ID 和 Token 用量继续搁置，见[复核记录](../reports/review-01.md) |
| 诊断、计划、推荐与班级分析 | 讲义标签、私人题库、掌握统计及模型计划已实现并同步归档；本轮成员复核按用户决定取消，不记为通过；见[学习模块](learning/README.md) |

Class07 已完成基线不追加无历史会话、规范破坏试验、回滚和全历史/镜像扫描。已取消项未记为通过；其审核结论沿用[课程验收记录](lesson-07/README.md)，不新增台账。

2026-11-13学习模块收尾：讲义标签及继承、私人AI题库、规则掌握度、受控模型计划和页面分页已技术验证并同步主规格。最终前端146项，后端单元240项、独立MySQL39项，以及默认扫描、真实合成模型/API与五屏宽检查按各次影响范围通过，详见[验证记录](learning/review.md)。本次归档只重新检查规格和文档，没有重跑业务测试或部署。

本轮成员独立复现及交叉审核按用户明确决定取消，不记为验收通过；38项执行任务完成。团队V13及原数据保留，A班13份READY讲义、16道题库题目和6份班级作业不变；技术验收的专用环境已清理。

当前无活动change。[师生首页](../openspec/changes/archive/2026-11-14-add-role-home-dashboards/README.md)已于2026-11-14按用户验收及归档指令同步归档，5/5技术任务完成；另一成员交叉评审未执行，不记为通过，见[首页记录](home/review.md)。未执行Git提交。[学习诊断闭环](../openspec/changes/archive/2026-11-10-add-learning-diagnosis-loop/README.md)已于2026-11-13同步归档。Class08 相关两个 change 已于 2026-11-06 归档：

- [异步作业批改](../openspec/changes/archive/2026-11-03-add-async-assignment-grading/proposal.md)：参考答案综合评分、单次提交、教师终评与顶部反馈已实现；创建不调用模型、不提取评分项，旧成绩不变。验收及清理见 [第 10～14 节](lesson-08/README.md#14-独立-docker-复核与收尾2026-11-06)；指定演示数据已清理，参考材料保留。[安全自查](../reports/security-precheck.md)已纳入原 change；SEC-07 两项缺口搁置，原始红项保留，不作为本次复核或归档前置条件。另一成员已按[交接](lesson-08/handoff.md)独立复现并归档。
- [依赖升级与团队部署](../openspec/changes/archive/2026-11-04-upgrade-spring-security-baseline/proposal.md)：Boot 4/Jackson 3 迁移及 `campusclaw-plus` 七服务已完成；OSV 124/124、npm/pip 审计零告警，默认 scan 全部通过。网页 18080、MySQL 13306、RabbitMQ 管理 15673，原项目容器/数据未变。技术结果见[本轮验收](lesson-08/README.md#8-依赖升级与独立团队-docker-部署2026-11-04)，另一成员已按此 Docker 配置独立复现，任务 4.1/4.2 完成并归档。

功能与安全工具提交为 `e7507b6`，首次部署模板/验收依赖/说明提交为 `af9cb93`；独立 Docker 复核、规格同步与两个 change 归档已提交至 `c1d1415`，文档状态与本机路径修正提交为 `99023c5`。未创建 Class08 标签。此前风险接受方案未获批准，相关告警后来通过实际升级整改，不将历史拒绝改写成接受。

首次复现准备已对齐模板库名/端口、补齐MySQL RSA验收依赖及Python隔离/清理说明；34项脚本测试、合成Compose解析、独立安装及4项Python依赖零告警通过。随后在本机连接 Engine，从空库冷启动七服务并完成独立复核，详见[第14节](lesson-08/README.md#14-独立-docker-复核与收尾2026-11-06)；验收后按交接撤除本次专用栈。

## 实现范围

`/home` 提供教师班级统计、掌握度条形图、作业提交环图与待处理事项；学生查看待交作业、只读计划进度、掌握状态环图和本人最近成绩。首页无模型调用、无数据迁移或新配置；操作和口径见[首页说明](home/README.md)。

已实现 Bearer JWT、教师/学生角色与服务端班级隔离、材料管理与异步索引、keyword/vector/hybrid 检索、问答采信、技能与智能体配置、私人持久化流式会话、MCP 管理/调用/审计、OpenAPI、Python 接口回归工具，以及 Class08 的文本作业异步批改（RabbitMQ、事务性发件箱、独立 worker、结果查询）。

讲义页管理标签并筛选讲义。作业和题库采用逐行列表，点击后进入详情；保留全班名单、一次提交和教师终评。题库单列侧栏入口，教师管理题目，学生搜索、AI批改和重答；教师不能读题库作答或给终评。学生“我的学情”分为掌握情况和学习计划两个子页，标签表和已生成计划始终显示分页；界面导航、返回及资源入口无下划线，正文链接保留。学习计划由模型选择具体讲义和题目、安排每日时间；后端检查候选ID、标签与预算。

V12新增题库、本人最新作答指针和内容用途标志，V13增加计划预算及认领字段，复用参考答案快照、标签继承及原队列。重答清除旧正文和评分，旧任务元信息保留；最新成功成绩进入掌握度，未做/待批改不计零分。现有讲义及标签保留，无新增必填配置。

## 当前边界与后续交付

- 已移除客观模板、示例导入和旧练习/错题入口；参考讲义由教师正常上传与标注，题库不自动填充示例。
- 掌握度是最近20次得分比例均值；班级先算个人再等权平均，展示参与人数和学生明细；不展示错误类型或教学建议。
- 计划按本人画像、目标和可用时间生成；学生新作答后主动调整未完成任务，保留已完成事实。题库按次AI批改，计划按需调用模型并缓存，具体练习以新提交的成功批改更新进度。
- 公网演示尚未部署；全学科、动态班级和完整全站审计按需另立change。后续变更仍执行项目默认评审规则，本轮复核取消不扩展为长期豁免。

Class09～11 的非功能性交付工作按用户决定暂缓；公网演示仍属最终目标，不视为已经部署完成。

## 归档与验收状态

| Change | 任务状态 | 归档记录 |
| --- | --- | --- |
| 师生首页 | 5/5技术任务，用户验收通过；按本次指令归档，另一成员交叉评审未执行 | [归档说明](../openspec/changes/archive/2026-11-14-add-role-home-dashboards/README.md) |
| 学习诊断闭环 | 38项执行完成；原4.2成员复核按用户决定取消，不记为通过 | [归档说明](../openspec/changes/archive/2026-11-10-add-learning-diagnosis-loop/README.md) |
| 团队开发与运行基线 | 15/15，验收通过 | [归档说明](../openspec/changes/archive/2026-10-31-establish-team-baseline/README.md) |
| MCP 工具与审计 | 31/31，验收通过 | [归档说明](../openspec/changes/archive/2026-10-28-add-mcp-tools-and-audit/README.md) |
| Markdown 感知切分 | 8/8，验收通过；MySQL/Qdrant 集成测试 18 项通过，无失败/错误/跳过 | [归档说明](../openspec/changes/archive/2026-10-28-fix-markdown-aware-knowledge-chunking/README.md) |
| RAG 采信收紧 | 7/7，验收通过 | [归档说明](../openspec/changes/archive/2026-10-28-tighten-rag-evidence-acceptance/README.md) |
| Class08 异步作业批改 | 46/46，独立 Docker 复核通过 | [归档说明](../openspec/changes/archive/2026-11-03-add-async-assignment-grading/README.md) |
| 依赖升级与团队部署 | 14/14，独立复现与交叉评审通过 | [归档说明](../openspec/changes/archive/2026-11-04-upgrade-spring-security-baseline/proposal.md) |

个人阶段三个 change 均于 2026-10-28 同步归档，无个人阶段遗留验收项。团队基线 change 已于 2026-10-31 同步归档；已归档行为见[主规格](../openspec/specs/)，本轮验收边界见[课程验收记录](lesson-07/README.md)。

## 验证与兼容边界

- 验证范围与执行命令见各 change 的归档说明；主规格严格校验与同步一致性检查通过。
- RAG 阈值未经人工标注集校准，显式引用也不做逐事实蕴含校验。
- 纯工具回答可没有材料引用；材料和工具都不可用才走生成前固定拒答。
- 本次收敛了旧检索规格的单页聊天描述，主规格统一为材料页直接检索与私人助手会话分离；会话零重合拒答也明确属于流式完成后的正文归一化。
- 旧 entries 不会因重建 chunks 自动重解析；原文件、知识条目与检索切片是不同层次。
- 历史工程基线已通过 all、隔离运行检查和另一成员原生进程复现；本轮新增学习功能有独立容器/页面技术证据，但不代替成员审核。
- Class08 已在升级后 Docker 中复验核心判据、故障、API 和页面；空库冷启动、旧 V8 数据和旧应用回读通过。默认扫描零告警，OWASP 未运行；成员独立复核、归档及提交已完成，标签未创建，详见[交接](lesson-08/handoff.md)。
