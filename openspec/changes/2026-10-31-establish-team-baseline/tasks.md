# Tasks

运行与检查基线已实现，unit/integration/api/all 与隔离运行验收通过，结果见 `docs/lesson-07/README.md`。双人评审、无历史会话、完整秘密扫描和独立交付仍保留未完成，不以自动测试代签。

## 1. 规约评审与五类规范（两人共同确认）

- [ ] 1.1 两人评审 proposal、design、三份 specs 与能力—场景对应表，确认健康探测范围、8 秒超时、日志隐私和模块责任；Verify：`openspec validate 2026-10-31-establish-team-baseline --strict` 通过，课程总验收记录保存两名成员代号及评审结论，项目状态只保留摘要和链接，未决范围问题为零。
- [ ] 1.2 编写产品、后端、安全、测试、部署运维五类规范，覆盖 design 第 1 节具体条目、测评操作/缺陷判据及适用的 AI/数据/评审补充要求，登记继承例外及默认值；Verify：逐一检查 `docs/standards/01-product.md` 至 `05-operations.md`，产品状态/导入边界、后端筛选/事务/排序、安全附件/令牌/白名单、测试判定信号、运维停机/备份目标均有明确结论，并由另一成员复核，不提前新增业务。
- [ ] 1.3 将规范读取规则接入 `AGENTS.md`，复用既有 OpenSpec 技能并补必要的团队检查/交付技能；Verify：无历史会话仅接收仓库位置和 change 名，能只读列出五类规范、模块负责人、实际入口与执行顺序，结果记入课程总验收记录，不得以本次对话代替该独立检查。

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

## 5. 跨模块回归与课程验证（主责成员 B，成员 A 交叉验证）

- [x] 5.1 完成四模式联合门禁并核对继承能力没有回归；Verify：`python scripts/check.py --mode all` 返回 0，按 specs 检查登录、班级/本人隔离、材料、流式会话、MCP 工具快照与审计，记录各模式实际结果及前提，任何失败均保持本项未完成。
- [ ] 5.2 在隔离副本完成一项合规基线改动、一次跨层或移除授权的违规检查，并换无历史会话再次验证规范读取与产物一致性；Verify：记录正确版本通过、违规版本被指定检查或评审规则拒绝，撤去违规补丁且 `git diff` 不含试验漏洞；由两名成员确认，不虚构新会话或评审结果。
- [ ] 5.3 检查公开交付边界，覆盖 Git 跟踪文件与全部历史、镜像层、前端构建产物及本次日志/提交证据；Verify：按安全规范执行敏感标记/凭据检查，公开记录只保留结论和相对路径，不输出真实密钥或完整个人数据；发现问题先阻断并整改，不自动重写历史。

## 6. 独立复现与交付准备（成员 B 组织，两人完成）

- [ ] 6.1 另一成员实际独立克隆候选版本、填写私有配置、一条 Compose 命令启动；Verify：记录成员代号、候选提交、环境类别和启动耗时，匿名健康字段正确，正常及错误请求的 JSON 日志/request ID 可关联；不得用实现者同机重启替代另一成员复现。
- [ ] 6.2 在确认隔离的 Compose 项目和数据卷中启动候选与上一应用版本、完成应用回滚并核对旧数据可读；Verify：按部署运维规范执行演练并记录两版标识、命令、耗时与各自健康/最小业务结果，未改历史迁移、未删除真实数据；任一版本不可定位或启动失败则本项未完成。
- [ ] 6.3 将两人评审、规范验证、四模式检查、独立启动与回滚的脱敏结论集中写入 `docs/lesson-07/README.md`，项目状态只链接该记录；Verify：记录包含候选版本、成员代号、命令、结果、耗时和继承适配，`openspec validate --all --strict` 与 `git diff --check` 通过；不创建逐次验证目录，不以文件存在替代实际验收。
- [ ] 6.4 最后对照 design 的“最终五维核查”逐项检查仓库、规约、规范、骨架和交付；Verify：课程总记录每一维都有结果与证据，未通过或不适用的继承条目有明确处置、不得留作默认通过。此后另按同步/归档流程处理 change，由成员创建标签并将对应提交补入同一记录；标签未存在前，整课交付仍为待完成，不得据本项擅自提交、推送或打标签。
