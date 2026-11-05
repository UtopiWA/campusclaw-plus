# Class08 当前交接

更新于 2026-11-06。功能基线为 `45b2f7a`，用户初验通过；本轮安全脚本、报告和文档尚未提交，交付版本须包含这些修改。两个 change 仍活动，另一成员尚未独立复现。

技能 ID 和 Token 用量已决定暂缓处理，**不列入本次复核任务，也不作为归档前置条件**；原始 SEC-07 记录保留。

## 另一成员待办

1. **独立启动。** 阅读根目录 [README](../../README.md)、[项目规则](../../AGENTS.md)及两个活动 change，按 Compose 启动七服务。自行准备私有 `.env.team` 和独立数据卷；空库开启种子创建演示账号，完成后关闭，不复用其他项目数据库。
2. **走通页面。** 教师上传讲义并检索，配置 MCP/助手并完成一次对话；上传参考答案、创建作业。学生提交后刷新仍可查看结果，重复修改提交被拒绝；教师改分后，学生优先看到教师终评。页面应正常显示状态及顶部操作提示。
3. **复现异步与授权。** 在无人提交作业时执行下面两条命令：第一条检查 202、停 worker 后 PENDING、恢复自动完成及重复消息去重；第二条至少记录 SEC-01/02 的匿名拒绝和跨班隔离实际结果。使用合成数据，确认脚本清理完成。

   ```powershell
   python scripts/security_precheck.py --env-file .env.team --checks tasks
   python scripts/security_precheck.py --env-file .env.team --checks access
   ```

   先安装 `apitest/requirements.txt`。需要可用的模型/嵌入服务；任务检查会短暂停启本团队 worker。两条命令均不执行 SEC-07。

4. **审核并记录。** 对照[已有评审](review.md)核对规则、七维/逐文件结论和权限、事务、并发、恢复机制。将实际成员、候选提交、环境、命令和结论写入 [review-01](../../reports/review-01.md)，在 `review.md` 追加成员结论或链接。有问题先修复，再补跑受影响检查；没有改动无需重跑整套自动测试。
5. **通过后收尾。** 按实际结果完成两个 change 的剩余任务，同步 delta 到主规格，运行 `openspec validate --all --strict` 和 `git diff --check`，归档两个 change，更新 README/项目状态及活动链接，最后提交。推送和版本标签按团队授权办理。

活动 change：[异步作业批改](../../openspec/changes/2026-11-03-add-async-assignment-grading/proposal.md)、[依赖升级](../../openspec/changes/2026-11-04-upgrade-spring-security-baseline/proposal.md)。既有结果见[验收第 8～12 节](README.md#8-依赖升级与独立团队-docker-部署2026-11-04)与[安全报告](../../reports/security-precheck.md)，不能代替本人的复现。

建议收尾提交信息：`docs: verify and archive Class08 changes`。不提交私有配置、原始日志或运行产物，不将未执行项写成通过。
