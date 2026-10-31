# establish-team-baseline

归档日期：2026-10-31。团队开发与运行基线已同步到主规格：健康检查扩展、版本元数据、JSON 日志与请求 ID、异步 MDC、统一检查入口，以及五类工程规范与团队协作规则。

## 验收来源

- `tasks.md` 15/15 项完成；发起方完成 unit/integration/api/all、隔离健康与配置负向检查，另一成员完成原生进程功能复现与审核。
- 另一成员环境：Windows 11、JDK 17、Node 20、Python 3.10、独立 MySQL 8.0.36、Qdrant 1.15.4 官方 Windows 二进制、原生后端/MCP/前端；使用合成数据，未连接现有数据库。
- 复现覆盖健康 200/503/恢复、JSON 日志与请求 ID、登录与班级隔离、材料索引与三种检索、MCP 与审计、技能/智能体、材料问答、计算器、KaTeX/引用渲染与刷新持久化；接口套件 8 个用例和 4 项清理全部通过。
- 未安装 Docker，未由成员 B 重跑 Compose 启动、镜像构建和三个 Testcontainers 集成类；这些沿用发起方已通过的 all 结果。详见[课程验收记录](../../../../docs/lesson-07/README.md)。
- 归档时执行了 delta 与主规格一致性核对、`openspec validate --all --strict` 和 `git diff --check`。

当前行为以[主规格](../../../specs/)为准；项目状态见[docs/project-status.md](../../../../docs/project-status.md)。
