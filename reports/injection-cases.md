# Class09 三条输入安全反例

日期：2026-11-14  
执行：`APITEST_ENV_FILE=.env.team .venv-review/Scripts/python.exe -B apitest/run_all.py`  
结果：接口套件 exit 0；三条安全用例均 PASS，HTML 报告 `apitest/reports/apitest-20261114121256-c506df.html`。

## 1. SQL 注入

- **前置**：`student-a1` 属于 A 班；A 班有 READY 材料；服务端当前班级来自 JWT，不接受请求体 `classId`。
- **操作**：`POST /api/retrieval/search`，keyword 模式，分别发送 `' OR '1'='1`、`%' UNION SELECT id,class_id FROM materials -- `、`'; DROP TABLE materials; -- `，以及伪造 `classId=999999`；另发送字符串 `limit`、空 `query`、非法 `mode`、`limit=0/21`。
- **结果**：注入串未导致 500，也未返回越权行；所有命中逐条查 MySQL，切片和材料均属于 A 班且 READY；相关表计数前后不变；非法类型/范围返回 400。用例位于 `apitest/cases/api/retrieval/search-injection/POST.py`。

## 2. XSS

- **前置**：`teacher-a` 可上传和管理本班材料；测试使用带运行标识的合成材料，不修改已有 READY 材料。
- **操作**：上传文件名含 `<script>alert(1)</script>` 的 Markdown；若文件名校验拒绝则用普通文件名上传后 PATCH 标题为 `<script>alert(1)</script>`；通过列表/详情和 MySQL 回读。
- **结果**：API 原样返回文本，MySQL 标题一致，未做错误的双重转义；前端 `MaterialsView` 和 `assistantMarkdown` 测试断言实际 DOM 只有文本节点，没有 `script`、`img` 或事件属性节点。前端 158/158 测试在干净 Node 22 Linux 容器中通过。用例位于 `apitest/cases/api/materials/xss/PATCH.py`。
- **并发发现与修复**：门禁首次运行该用例时，PATCH 响应已是恶意标题，但 MySQL 仍是上传时的旧标题；定位为后台索引任务保存整行 `Material` 时覆盖了并发的标题更新。给 `Material` 增加 Hibernate `@DynamicUpdate`，让索引任务只更新实际变更的状态列，不覆盖标题；同条件重跑 API 套件通过。该失败记录保留在 `backend/target/check-logs/api.log`。

## 3. CSRF 等价验收

- **前置**：项目使用 `sessionStorage` 保存 Bearer JWT、显式 `Authorization` 头和无状态服务端；未使用 Cookie 会话，`SecurityConfig` 关闭 CSRF。
- **操作**：对合成材料发送带 `Origin: https://evil.example`、`Referer` 和 dummy `Cookie`、但没有 Bearer 令牌的 `PATCH`；随后发送带合法教师 Bearer 的对照请求；检查登录响应是否设置 `Set-Cookie`；再发送仅 Cookie 无 Bearer 的请求。
- **结果**：伪造请求 401，数据库标题不变；合法 Bearer 对照 200 并更新；登录响应无 `Set-Cookie`；仅 Cookie 请求仍 401。用例位于 `apitest/cases/api/materials/csrf/PATCH.py`。
- **口径**：该结果是 Bearer JWT 无状态项目的等价验收，不伪造 SameSite/CSRF token 证据。按课程要求需教师/团队确认此等价口径；确认记录待补。

## 清理

三条用例均使用唯一运行标识创建合成材料；接口套件结束时逆序执行删除，报告中显示两个合成材料清理完成，未修改其他用户或已有 READY 材料。
