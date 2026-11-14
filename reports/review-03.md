# Class09 脚本发现记录（review-03）

日期：2026-11-14  
性质：**脚本/自动检查发现的整理，不是成员人工交叉评审**。人工复核需另一成员签字。

## 手段 / 对象 / 判据 / 结果

| 手段 | 对象 | 判据 | 结果 |
| --- | --- | --- | --- |
| Gitleaks v8.24.3 + 精确比对 | Git 历史、公开文件、`frontend/dist`、七服务日志、`.env.team` | 0 命中或书面处置 | 0 命中、0 候选；scan exit 0 |
| Java OSV | Maven 运行时依赖 | 124/124 查询完成；high/critical 0 | 0 findings；exit 0 |
| npm audit / pip-audit / Ruff / Checkstyle / SpotBugs | 前端、Python、Java | 无 high/critical；工具通过 | 通过（门禁 scan 阶段） |
| `security_precheck --checks access` | SEC-01～04 | 401/403/404、跨班检索 0、DB 不变 | exit 0 |
| `security_precheck --checks audit` | SEC-07 七类字段和 SEC-03 拒绝 | skillId、usage、duration、拒绝记录齐全 | 修复前 exit 1；修复后 run `365c1eb50877` exit 0 |
| `apitest/run_all.py` | 功能 + SQLi/XSS/CSRF | 全部通过；合成资源清理 | exit 0；三条安全用例 PASS |
| 前端 Vitest（干净 Node 22 容器） | 材料标题、Markdown 渲染 | 恶意内容不产生元素/事件节点 | 158/158 通过 |
| `sec08_experiment.py` | SEC-08 四步 | 第四步代码拒绝 + DB 不变 + 学生/跨班拒绝审计 | 首次 exit 1（审计缺失）；修复后 run `fe5e50280004` exit 0；最终 run `b9d157fa2cbc` exit 0（两个拒绝审计均 true） |
| `security_gate.py` | 五个阶段 | 0/1/2 聚合；失败传播 | 最终 run `20261114T123734Z-41820`：scan/access/audit/api/unit 全 0，总 exit 0 |

## 发现

1. **SEC-07 红**：初始审计只有 MCP 工具行，缺稳定技能 ID 和模型 usage。已由 V14 + 网关 usage + 新查询修复。
2. **SEC-08 拒绝审计缺失**：学生终评 403 发生在控制器 `requireTeacher()`，服务层没有机会写审计。已改用带 action/object 的重载。
3. **材料标题并发覆盖**：门禁 API 阶段首次 XSS 用例发现 PATCH 响应已是恶意标题、MySQL 仍是旧标题；后台索引整行保存覆盖了标题更新。已给 `Material` 增加 `@DynamicUpdate`，同条件重跑通过。
4. **模型提示注入**：对照 0 分、恶意参考 100 分；属于模型层行为，已登记为残余风险，未用代码伪造通过。
5. **宿主前端 runner**：Windows 宿主 Vitest 无法加载测试；干净 Linux 容器通过。环境问题，不作为代码通过证据。
6. **未发现**密钥泄露、依赖 high/critical、跨班越权写入或数据库计数变化。

## 未执行

- 另一成员人工交叉复测至少两条修复。
- 本机 GNU make 的实际 `make check-security` 执行；已用 Python 等价入口和容器内 `make -n` 校验目标。
- 宿主 Node 修复后的前端 Vitest 重跑。

## 结论

自动检查未发现需要阻塞的代码缺陷；人工评审和最终授权仍待执行。
