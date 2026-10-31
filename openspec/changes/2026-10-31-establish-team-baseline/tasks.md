# Tasks

本轮实现、all 及隔离运行验证已通过，发起方已确认验证完成。按最新决定，后续由另一成员在自己的环境独立复现、审核，再由对方归档提交。无历史会话、合规/违规试验、回滚演练及全历史/镜像扫描不再是本次完成门槛；未执行项目已从范围移除，不记为通过。实际结果集中在 `docs/lesson-07/README.md`。

## 2. 运行基线：健康与版本（成员 A 整体负责，成员 B 评审）

- [x] 2.1 生成可随 JAR/镜像交付的版本元数据，支持受校验的 `APP_VERSION`，更新构建与配置样例的对应说明；Verify：聚焦配置测试覆盖缺省构建版本、合法覆盖、显式空值、65 字符及非法字符，`backend/mvnw.cmd -f backend/pom.xml -DskipTests package` 的产物包含版本，运行不依赖本机 `.git` 或网络 Git。
- [x] 2.2 实现只读数据库/Qdrant 探测及扩展健康响应，保留既有 `status` 和 200/503，模型与 MCP 明示 `not_checked`；Verify：`backend/mvnw.cmd -f backend/pom.xml "-Dtest=HealthControllerTest,DependencyHealthServiceTest,QdrantHealthProbeTest" test` 覆盖正常、数据库失败、Qdrant 失败、参数不改变探测范围与 8 秒超时，断言没有重试、集合写入或模型/工具调用。
- [x] 2.3 保持匿名健康、业务鉴权及现有 MySQL 集成测试的隔离性；按需要为集成中的 Qdrant 健康适配器提供替身，并由真实 Qdrant 测试验证只读就绪接口；Verify：`backend/mvnw.cmd -f backend/pom.xml "-Dtest=SecurityConfigTest,CampusClawIntegrationTest,QdrantIntegrationTest" test` 通过，未登录业务仍 401，学生/跨班仍按原契约拒绝。
- [x] 2.4 将 Compose 后端健康超时明确调整为 10 秒并传递版本配置，保留各服务持久化与启动依赖；Verify：隔离配置下 `docker compose --env-file .env config --quiet` 退出 0，后端健康 timeout 为 10s，正常与停止测试 Qdrant 后的 200/503、恢复后的 200 可实际观察，未改动真实应用卷。

## 3. 运行基线：日志与请求上下文（成员 A 整体负责，成员 B 评审）

- [x] 3.1 实现后端统一 JSON 编码与固定请求完成事件，关闭不安全调试和非 JSON 横幅/堆栈；Verify：`backend/mvnw.cmd -f backend/pom.xml "-Dtest=JsonLogEncoderTest" test` 逐行解析启动、普通、失败、中文/换行日志，断言基础字段及敏感标记不出现，不新增无必要日志依赖。
- [x] 3.2 在安全链之前实现请求 ID 校验、生成和响应头回传，完成日志包含无查询值路径、最终状态与实测耗时；Verify：`backend/mvnw.cmd -f backend/pom.xml "-Dtest=RequestLoggingFilterTest,SecurityConfigTest" test` 覆盖 1/64/65 字符、多值、非法字符、缺失头及 401/403/404，检查请求不改变原业务结果，每次只记录一次完成事件。
- [x] 3.3 完成 MVC 异步和两个现有执行器的 MDC 复制/恢复、失败/超时及客户端断开收尾；Verify：`backend/mvnw.cmd -f backend/pom.xml "-Dtest=RequestLoggingFilterTest,AsyncConfigTest,ConversationServiceTest" test` 覆盖 SSE 正常/失败/重复分派和连续线程复用；检查无串用 ID、重复完成日志或新增 SSE 调试事件，未来新增聚焦测试在同一命令中补列。

## 4. 工程检查与交付模块（成员 B 整体负责，成员 A 评审）

- [x] 4.1 实现 `scripts/check.py` 四模式、跨平台工具定位及默认 unit，明确排除/纳入的三个容器类，支持受校验的测试专用 API 版本覆盖；Verify：`python -m unittest discover -s scripts/tests` 用受控子进程覆盖模式解析、非法环境值、工具缺失、各子命令的退出码与顺序、Docker 不可用、旧报告和跳过用例，测试本身不启动真实应用。
- [x] 4.2 复用现有前端/后端/Python MCP/OpenSpec 检查及真实接口套件，不自动开种子、装全局工具或修改现有数据库；Verify：`python scripts/check.py --mode unit` 返回 0，`python scripts/check.py --mode invalid` 返回 2 且不执行子检查；从其他目录调用同一脚本仍定位同一仓库。
- [x] 4.3 在完整环境执行 integration 与 api 门禁，核对本轮三个容器类的实际计数、接口数据库断言与清理结果；Verify：`python scripts/check.py --mode integration`、`python scripts/check.py --mode api` 均返回 0，无未登记的跳过；故意令受控子检查失败能得到非零，不能引用历史报告替代本轮执行。
- [x] 4.4 保持 README 简短，补实际可用的检查/启动命令、报告位置、健康口径及回滚入口；按课程要求把 `.env.example` 整理为只列键名的样例，格式与可选默认值写在注释/规范；Verify：模板有效配置行只有 `KEY=`，可选项不以空值覆盖安全默认，复制后按文档填写能启动，缺少必需值仍失败；没有本机路径、私密值或虚构命令。

## 5. 跨模块回归

- [x] 5.1 完成四模式联合门禁并核对继承能力没有回归；Verify：`python scripts/check.py --mode all` 返回 0，按 specs 检查登录、班级/本人隔离、材料、流式会话、MCP 工具快照与审计，记录各模式实际结果及前提，任何失败均保持本项未完成。

## 6. 独立复现、审核与归档提交（另一成员负责）

- [ ] 6.1 另一成员从候选提交独立启动应用，使用自己的私有配置和合成数据，按 `docs/lesson-07/README.md` 的待办逐项走通已实现功能。Verify：健康/日志、登录与隔离、材料/检索、MCP、技能/智能体、会话及渲染均有实际结果；记录成员代号、提交、环境类别和问题处理，不用原作者结果代替复现。
- [ ] 6.2 另一成员审核实现、规约/规范、配置说明、已有自动检查及自己的复现结果，检查本次差异无敏感信息并记录结论；发现问题则修正并补跑受影响检查，不重复整套 all，不另做破坏性实验、回滚或全历史扫描。Verify：无未解决问题，不代签，复现和审核均通过。
- [ ] 6.3 由该成员同步 delta 到主规格，执行 `openspec validate --all --strict`、`git diff --check`，归档 change，更新项目状态并提交；按仓库协作方式推送或合并，标签可选。Verify：主规格与归档一致，交付提交可定位。
