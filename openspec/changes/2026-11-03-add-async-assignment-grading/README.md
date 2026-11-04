# 2026-11-03-add-async-assignment-grading

可靠异步作业批改、消息队列与 AI 代码审核流程。

状态：**功能及依赖整改已在独立团队 Docker 中技术验证，待用户人工验收及另一成员复核，保持活动**。此前未获接受的依赖风险已通过升级整改，最新扫描与验收见 lesson-08 第 8 节；尚未同步主规格、归档、提交或创建标签。

- 动因与范围：[proposal.md](proposal.md)
- 设计：[design.md](design.md)
- 能力规约：[assignment-grading](specs/assignment-grading/spec.md)、[reliable-task-execution](specs/reliable-task-execution/spec.md)
- 任务拆分：[tasks.md](tasks.md)
  - 第一部分（成员 B / 王家兴）：change 与审核规则、独立验收、扫描、交叉评审、最终证据与归档。
  - 第二部分（成员 A / 王培鑫）：RabbitMQ/worker、迁移、接口、发件箱、消费者、模型评分、前端、测试和扫描工具。
- 验收记录：[docs/lesson-08/README.md](../../../docs/lesson-08/README.md)
- 评审记录：[docs/lesson-08/review.md](../../../docs/lesson-08/review.md)
- 剩余待办：[docs/lesson-08/handoff.md](../../../docs/lesson-08/handoff.md)

不在本次范围：教师复核/改分、完整题库、知识图谱、学习计划、班级分析、端到端 exactly-once、全站异步。
