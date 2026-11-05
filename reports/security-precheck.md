# Class08 安全自查

2026-11-06；候选为 `45b2f7a` 加本轮检查脚本和文档。执行者为协作代理，另一成员尚未复现、评审。功能代码未在本轮修改。

环境为独立 `campusclaw-plus` 七服务，私有 `.env.team`；使用 A 班 `teacher-a`、`student-a1` 和 B 班 `student-b1`。脚本新建带唯一标识的合成材料、技能、助手、作业和会话，完成后清理；未修改已有账号、业务材料或旧个人项目。

## 结果

| 项目 | 实际检查 | 结论 |
| --- | --- | --- |
| SEC-01 匿名访问 | 六个业务接口全部 401 | 绿 |
| SEC-02 班级边界 | 双向跨班读均 404；本班关键词命中 1 条、跨班 0 条 | 绿 |
| SEC-03 配置写权限 | 学生修改技能、助手均 403；API 与数据库前后相同 | 绿 |
| SEC-04 直接业务写入 | 学生创建作业、写教师终评均 403；非法作业/终评增量均 0 | 绿 |
| SEC-05 密钥 | Gitleaks 的历史、公开文件、前端产物、七服务日志均无命中；私有配置精确比对有 1 个公开服务名同文候选，已人工核查非凭据赋值 | 绿（人工核查后）；原始候选 1、退出码 1 保留 |
| SEC-06 依赖 | OSV 124/124、npm、pip 均零告警，默认静态检查完成 | 绿 |
| SEC-07 技能审计 | 真实计算器会话有操作人、技能名称、状态、耗时；缺技能 ID、Token 用量 | **红：审计信息不完整** |

匿名检查覆盖 `GET /api/auth/me`、`GET /api/materials`、`GET /api/assistants`、`GET /api/conversations`、`GET /api/assignments`、`POST /api/retrieval/search`。最后一项带合法请求体，避免把参数错误误当认证拒绝。

本次是安全基线记录。按用户决定，技能 ID 和 Token 用量暂时搁置，不列入本次同伴复核、修复任务或归档前置条件；原始 SEC-07 红项保留，不伪造用量或改成已修复。整体不能写成“安全全部通过”。可选 SEC-08 提示注入实验未选择、未执行。

## 写接口范围

以下来自当前 Controller；POST 检索、问答另属查询/生成入口，不因使用 POST 就认定为管理写入。

| 模块 | 当前写入口 |
| --- | --- |
| 认证 | `POST /api/auth/login`、`POST /api/auth/logout` |
| 材料 | `POST /api/materials/upload`、`PATCH/DELETE /api/materials/{id}`、`POST /api/materials/{id}/index/rebuild`、`POST /api/materials/{id}/processing/retry` |
| 技能/助手 | `POST /api/skills`、`PUT/DELETE /api/skills/{id}`；助手对应 `/api/assistants` |
| MCP | `POST /api/mcp/servers`、`PUT/DELETE /api/mcp/servers/{id}`、`POST .../{id}/connect`、`POST .../{id}/disconnect` |
| 会话 | `POST /api/conversations`、`DELETE /api/conversations/{id}`、`POST /api/conversations/{id}/messages` |
| 作业 | `POST /api/assignments`、`POST /api/assignments/{id}/submissions`、`PUT /api/assignments/{id}/submissions/{submissionId}/review` |

SEC-03/04 实测配置与作业的代表写入口，不声称本轮逐一请求了表中所有接口。现有角色规则、测试范围见[安全规范](../docs/standards/03-security.md)与课程记录。

## 命令与证据

在仓库根目录、已安装 `apitest/requirements.txt` 的 Python 环境执行：

```powershell
python scripts/security_precheck.py --env-file .env.team
python scripts/security_precheck.py --env-file .env.team --checks access
python scripts/check_security.py --env-file .env.team
```

- 全项运行 `72756d460899`：检查执行及清理完成；退出 1，对应真实 SEC-07 红项。
- 加强数据库前后比较后的访问运行 `7e596fa885e3`：SEC-01～04 与清理完成，退出 0。本表访问数据取自此轮。
- 密钥/依赖入口退出 1：依赖检查为 0，精确比对候选为 1；与 SEC-07 的退出 1 原因不同。
- 原始结果仅本机保留在 `backend/target/security-precheck/<runId>/`、`backend/target/security-check/`，不提交私有配置、日志或完整响应。

专题：[授权](risk-authz.md)、[并发](risk-concurrency.md)、[事务](risk-transaction.md)、[扫描](security-baseline.md)。[检索问题](retrieval-questions.md)仍为草稿；[同伴复核](review-01.md)暂缓，待本轮工作完成后交付另一成员。
