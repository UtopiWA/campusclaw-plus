# 并发与重复投递检查

2026-11-06；实测当前团队 Docker，另用 Testcontainers 检查不同答案并发。未对旧个人项目施加负载。

## 操作与结果

```powershell
python scripts/security_precheck.py --env-file .env.team --checks tasks
```

需要演示账号、模型/嵌入服务和 RabbitMQ 管理入口。脚本先确认没有 PENDING/RUNNING 任务，再暂时停止本团队 `grading-worker`；测试期间勿进行其他人工提交。发现已有活动任务就退出，不停止 worker。正常或异常退出均尝试恢复；未结束的本轮任务保留登记，避免删除 worker 正在处理的数据。

本机全项运行 `72756d460899` 已执行相同路径：

| 检查 | 实际结果 |
| --- | --- |
| 同一学生、同一作业、相同答案，4 请求并发 | 4 个 202，无分数/结果字段，返回同一 taskId |
| 数据库数量 | 1 条 submission、1 条 task |
| 恢复 worker | 无需再次提交，原任务 SUCCEEDED |
| 同一 task 再投递 3 条消息 | 结果仍 1 条，整行与投递前相同 |
| 已提交后改答案重交 | 409 |
| B 班访问该任务 | 404 |

四次提交耗时为 67.7 / 78.4 / 74.4 / 70.8 ms；后台执行 2010 ms。只说明该次小样本的提交没有等待评分，不作为吞吐或延迟承诺。

## 不同答案同时提交

独立 MySQL 容器执行：

```powershell
backend/mvnw.cmd -f backend/pom.xml -Dapi.version=1.44 "-Dtest=CampusClawIntegrationTest#gradingOutboxFailureRollsBackSubmissionAndTask+gradingConcurrentDifferentAnswersOnlyAllowOneSubmission" test
```

实际 2 项通过，0 失败/错误/跳过。并发用例中两个不同答案只接受一个，另一个 409，submission/task 各一条；另一个用例是[发件箱回滚](risk-transaction.md)。`api.version=1.44` 仅为本机 Testcontainers 连接 Docker 的兼容参数，不修改应用部署。

## 代码结论与边界

`SubmissionService` 在作业行锁内处理幂等与单次提交；数据库约束补充保护。`GradingWorker` 通过仓储原子认领，`GradingTaskCompletionService` 隔离 attempt，结果唯一约束阻止同一任务产生多份成绩；本轮没有修改这些机制。该轮观察不代替高负载、跨多 worker 的压力测试，未执行项不写通过。
