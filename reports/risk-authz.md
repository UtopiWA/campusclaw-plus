# 授权风险检查

2026-11-06，`45b2f7a` 加当前检查脚本；协作代理实测，不替代成员独立复现。

## 复现

按 [README](../README.md) 启动独立团队 Docker；演示账号口令须与私有 `.env.team` 中的 `DEMO_SEED_PASSWORD` 一致，初始化后无需重新开启种子。需要 B 班合成种子材料“B 班数学教研示例”；不存在时脚本明确未完成，不借用真实材料。

```powershell
python -m pip install -r apitest/requirements.txt
python scripts/security_precheck.py --env-file .env.team --checks access
```

此模式不停止 worker、不调用 Chat 评分或生成；会上传小份合成材料并等待索引，仍需要可用的嵌入服务。脚本只允许 `campusclaw-plus` / `campusclaw_plus`，端口取自所选私有配置。

## 实际结果

运行 `7e596fa885e3`，执行、清理均完成，退出 0。

| 场景 | 观察结果 | 持久化检查 |
| --- | --- | --- |
| 匿名请求六个业务接口 | 全部 401 | 不使用登录令牌 |
| A 学生读取 B 种子材料；B 学生读本轮 A 材料 | 均 404 | B 种子只读取编号，不取正文、不修改 |
| A/B 学生搜索本轮唯一关键词 | A 命中 1 条，B 命中 0 条 | A 命中确属本轮材料 |
| 学生 PUT 技能、助手 | 均 403 | 教师 API 前后相同，数据库 `source_text/prompt_text` 前后相同 |
| 学生 POST 作业、PUT 教师终评 | 均 403 | 指定标题非法作业增量 0；本轮作业终评增量 0 |

配置字段的前后 SHA-256 同为 `26b6cfec7e4d32f85731194becbe98141ecd3ae82aefa1fb4fc7f6979d4d0d2c`，公开仅保留指纹，不保留正文。教师终评的负向请求使用不存在提交编号；角色检查在资源处理前返回 403，不将其解释为对所有提交归属组合的覆盖。

## 判断依据

身份由 `CurrentUserService` 回查，管理入口由角色检查与服务层归属检查保护。证据入口为 `SecurityConfig`、`SkillService`、`AssistantService`、`MaterialService`、`AssignmentService` 及对应集成测试；自动脚本在 [security_precheck.py](../scripts/security_precheck.py) 中同时核对响应和数据库。

覆盖结论限于本表；过期/篡改令牌、同班其他人的私人会话等沿用已有测试证据，本轮没有重新执行全部权限组合。另一成员需自己运行并记录结果，不能复制本报告作为其复现。
