# 2026-11-03-add-async-assignment-grading

可靠异步作业批改、消息队列与 AI 代码审核流程。

状态：**功能已提交至 `45b2f7a`，用户初验通过；安全工具/文档尚未提交，待另一成员独立复现与评审，保持活动**。最新扫描与验收见 lesson-08 第 8～12 节；尚未同步主规格、归档或创建标签。技能 ID 和 Token 用量暂缓处理，不作为本次复核或归档前置条件，原始 SEC-07 记录保留。

- 动因与范围：[proposal.md](proposal.md)
- 设计：[design.md](design.md)
- 能力规约：[assignment-grading](specs/assignment-grading/spec.md)、[reliable-task-execution](specs/reliable-task-execution/spec.md)、[security-precheck](specs/security-precheck/spec.md)
- 任务拆分：[tasks.md](tasks.md)
  - 第一部分（成员 B / 王家兴）：change 与审核规则、独立验收、扫描、交叉评审、最终证据与归档。
  - 第二部分（成员 A / 王培鑫）：RabbitMQ/worker、迁移、接口、发件箱、消费者、模型评分、前端、测试和扫描工具。
- 验收记录：[docs/lesson-08/README.md](../../../docs/lesson-08/README.md)
- 评审记录：[docs/lesson-08/review.md](../../../docs/lesson-08/review.md)
- 剩余待办：[docs/lesson-08/handoff.md](../../../docs/lesson-08/handoff.md)

人工验收反馈已纳入本 change：文件评分依据、教师终评、单次提交及页面/顶部消息调整。2026-11-05 起按用户决定改为参考答案支持的 AI 综合评分，创建不提取评分项；最新验证见 lesson-08 第 10 节，第 9 节保留历史。

不在本次范围：完整题库、知识图谱、学习计划、班级分析、端到端 exactly-once、全站异步。
