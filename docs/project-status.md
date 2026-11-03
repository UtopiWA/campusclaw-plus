# 当前项目状态

最后核对：2026-11-03。本文件区分已验收的 CampusClaw 继承能力与 CampusClaw-Plus 团队启动工作；完整目标和分工见 [项目范围](project-scope.md)。

## 团队启动状态

继承版本：`30cdb0e7c7b5e91b2a854ed6426b5cc9a34982f1`。首轮团队提交及协作配置已由成员确认完成。[团队基线 change](../openspec/changes/archive/2026-10-31-establish-team-baseline/proposal.md)、五类规范和读取入口已编写；unit/integration/api/all、隔离健康与配置负向检查通过。另一成员已在原生进程环境完成功能复现并审核，change 已归档；Docker Compose 与 Testcontainers 沿用发起方结果，详见[课程验收记录](lesson-07/README.md)和[归档说明](../openspec/changes/archive/2026-10-31-establish-team-baseline/README.md)。

| 工作 | 当前状态 |
| --- | --- |
| 个人项目归档与验收 | 已完成，无遗留未完成验收项 |
| 团队范围与文档入口 | 已纳入首轮提交，后续按评审调整 |
| 首轮团队提交 | 已完成 |
| 成员接入与主干保护 | 用户确认已完成 |
| 五类工程规范与工具读取规则 | `docs/standards/01-product.md`～`05-operations.md` 已成文；AGENTS 按范围加载，已新增团队检查技能；由另一成员审核规则与文档 |
| 团队基线 change | 15/15 项完成；另一成员完成功能复现、审核并归档 |
| 收尾流程 | 已完成：另一成员原生进程复现、审核、同步规格并归档；Compose/Testcontainers 沿用发起方结果 |
| Class08 异步作业批改 | 变更文档与审核规则已建，待评审与实施；未实现 |
| 诊断、计划、推荐与班级分析 | 规划中，未实现 |

按最新决定，不再追加无历史会话、规范破坏试验、回滚和全历史/镜像扫描。已取消项未记为通过；审核结论沿用[课程验收记录](lesson-07/README.md)，不新增台账。

当前活动 change：[2026-11-03-add-async-assignment-grading](../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md) 已完成 proposal/design/specs/tasks、审核规则和验收骨架，待评审与实施。异步批改、消息代理和相关页面尚未实现，不记为已有能力；范围调整和交付边界见该 change。

## 实现范围

已实现 Bearer JWT、教师/学生角色与服务端班级隔离、材料管理与异步索引、keyword/vector/hybrid 检索、问答采信、技能与智能体配置、私人持久化流式会话、MCP 管理/调用/审计、OpenAPI 以及 Python 接口回归工具。

前端使用 `/materials`、教师 `/assistants` 和 `/conversations`，旧 `/search` 重定向到材料页。作业目前只有早期数据骨架；发布、提交、批改、教师复核、错题、学习诊断与计划等业务尚未形成完整链路。Class08 change 计划先补文本作答、异步批改和结果查询，待评审实施，不代表已实现。MCP 调用审计已可用，不等于全站审计与学情统计已完成。

## 归档与验收状态

| Change | 任务状态 | 归档记录 |
| --- | --- | --- |
| 团队开发与运行基线 | 15/15，验收通过 | [归档说明](../openspec/changes/archive/2026-10-31-establish-team-baseline/README.md) |
| MCP 工具与审计 | 31/31，验收通过 | [归档说明](../openspec/changes/archive/2026-10-28-add-mcp-tools-and-audit/README.md) |
| Markdown 感知切分 | 8/8，验收通过；MySQL/Qdrant 集成测试 18 项通过，无失败/错误/跳过 | [归档说明](../openspec/changes/archive/2026-10-28-fix-markdown-aware-knowledge-chunking/README.md) |
| RAG 采信收紧 | 7/7，验收通过 | [归档说明](../openspec/changes/archive/2026-10-28-tighten-rag-evidence-acceptance/README.md) |

个人阶段三个 change 均于 2026-10-28 同步归档，无个人阶段遗留验收项。团队基线 change 已于 2026-10-31 同步归档；已归档行为见[主规格](../openspec/specs/)，本轮验收边界见[课程验收记录](lesson-07/README.md)。

## 验证与兼容边界

- 验证范围与执行命令见各 change 的归档说明；主规格严格校验与同步一致性检查通过。
- RAG 阈值未经人工标注集校准，显式引用也不做逐事实蕴含校验。
- 纯工具回答可没有材料引用；材料和工具都不可用才走生成前固定拒答。
- 本次收敛了旧检索规格的单页聊天描述，主规格统一为材料页直接检索与私人助手会话分离；会话零重合拒答也明确属于流式完成后的正文归一化。
- 旧 entries 不会因重建 chunks 自动重解析；原文件、知识条目与检索切片是不同层次。
- 本轮工程基线已通过 all、隔离运行检查和另一成员原生进程功能复现；学习诊断等新增业务尚未实现。
