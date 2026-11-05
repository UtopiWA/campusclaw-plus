# 提交事务与一致性检查

2026-11-06；候选 `45b2f7a`，运行及代码复核由协作代理完成。

## 需要保证的事实

学生提交时，作答、批改任务和事务发件箱必须一起提交。RabbitMQ 不可用或 worker 未运行不应丢失已提交作答；模型调用在 worker 的事务外执行，最终结果按有效 attempt 回写。

## 已执行

| 路径 | 证据 | 结论 |
| --- | --- | --- |
| worker 停止时提交 | `72756d460899`：202 无分数，查询 PENDING，submission/task 各一条 | Web 请求与评分解耦 |
| worker 恢复 | 同一任务自动 SUCCEEDED，没有再次提交 | 已提交任务可继续消费 |
| 重复消息 | 再投递 3 次，唯一结果整行不变 | 未覆盖已有结果 |
| 发件箱写入失败 | 独立 MySQL 集成用例强制 outbox 异常 | submission、task、outbox 均回滚，无孤立记录 |

集成命令与 2 项通过结果见[并发报告](risk-concurrency.md)。失败通过测试替身在真实 Spring/MySQL 事务中触发，没有破坏运行中团队库。控制点位于 `SubmissionService`、`GradingOutboxPublisher`、`GradingTaskCompletionService`，对应 `CampusClawIntegrationTest`。

本轮未重新执行 DLQ、模拟 RUNNING 超时回收或全部 API 套件；已有实际结果见[课程验收第 8 节](../docs/lesson-08/README.md#8-依赖升级与独立团队-docker-部署2026-11-04)，不冒充为本轮新执行。

## 数据与清理

自查每创建一个资源就写入本机 `fixtures.json`。清理只作用于本轮登记 ID：先确认任务终态，按外键依赖删除本轮作业关联记录，再经 API 删除会话、配置、材料。为回收本轮合成 MCP 服务，定向删除该次服务的审计；普通业务 API 的审计删除保护没有改变。原始结果与清理状态留在忽略目录，不进入公开 Git。

该轮两个自查 run 的清理均完成，worker 恢复运行。异常退出时若 `cleanup` 未完成，应依据私有登记检查后处理，不能用全库删除补救。另一成员复现仍暂缓。
