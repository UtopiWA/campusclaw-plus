# Class09 返工记录 01

日期：2026-11-14  
范围：SEC-07 助手消息审计字段接入后的聚焦测试。

## 问题

`ConversationStreamingService` 改为调用 `ConversationService.saveAssistantMessage` 的新重载（增加 skillId、usage、模型耗时和 requestId）后，`ConversationStreamingServiceTest` 仍按旧签名 stub/verify。运行聚焦测试时 7 项失败，`done` 事件缺失或 `error` 未按预期出现；这不是生产逻辑失败，而是测试替身没有覆盖新契约。

## 定位与修复

- 定位：`backend/src/test/java/com/campusclaw/conversation/ConversationStreamingServiceTest.java`；失败集中在 `saveAssistantMessage(...)` 的 mock 参数数量和顺序。
- 修复：按新签名更新 stub/verify，成功路径保留 `any()` 的 usage/耗时/requestId 匹配，失败路径和工具快照路径使用明确的 `null`/`List.of()`；未放宽生产断言。
- 可执行改法：

```text
把旧 7/8 参数 stub 改为：
saveAssistantMessage(9L, 6L, 2L, content, skillName, skillId, usage, duration,
                     requestId, citations, toolCalls, status)
```

- 回归：`GatewayClientTest,ConversationStreamingServiceTest,AgentConversationServiceTest,ConversationServiceTest` 26/26 通过；随后 `GatewayClientTest,ConversationStreamingServiceTest,AuditQueryServiceTest,AuditControllerTest` 19/19 通过。

## 另一条测试返工

前端 `MaterialsView` 的 XSS 测试最初断言 `row.html()` 不含 `onerror=`，但 Vue 会把恶意标题作为文本转义后放在 `h3`，同时按钮的 `aria-label` 仍包含原始字符串，导致字符串级断言误报。改为断言实际 DOM 无 `script`/`img`/`[onerror]` 节点、标题子节点为文本节点；这是“检查实际渲染 DOM”的正确判据，不是放宽安全要求。

## 并发返工：材料标题被后台索引覆盖

- 问题：门禁 API 阶段首次运行 XSS 用例时，PATCH 响应返回恶意标题，但 MySQL 标题仍是上传时的旧值；后台索引任务在并发事务中保存整行 `Material`，覆盖了标题更新。
- 定位：`Material` 实体整行更新与 `MaterialService.update` 的标题更新存在丢失更新。
- 修复：给 `Material` 增加 Hibernate `@DynamicUpdate`，索引任务只更新实际变化的状态字段，不再把旧标题写回；同条件重跑 API 套件通过。
- 证据：`backend/target/check-logs/api.log` 保留首次失败；`reports/injection-cases.md` 记录修复后结果。

## 保留

- 失败测试日志保留在 `backend/target/class09-focused-tests.log` 和后续通过日志；未修改生产代码来迎合测试。
- 宿主 Windows 上的 Vitest runner 异常另见 `docs/lesson-09/README.md`，通过干净 Node 22 Linux 容器复跑，不记为该宿主通过。
