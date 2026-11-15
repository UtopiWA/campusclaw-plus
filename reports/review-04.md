# Class09 独立技术复核

日期：2026-11-14。候选提交：`caa9dbf2e3066a599a74cfbbdb54958bf8540f68`，与本机 `origin/master` 一致；评审范围为此前六次提交及本轮未提交修正。执行者：本机 Codex 代理；接收交付成员已于2026-11-16在本会话明确确认本文及CSRF等价验收口径；技术命令由本机代理执行，不追认实施前确认。

**2026-11-14结论（历史，已由下述补充更新）：** SEC-07、SEC-08 第四步、访问控制、完整 API 套件及异步三判据已独立执行。完整安全门禁退出1，原因是本机演示密码与公开字符串重合；没有轮换现有账号密码或豁免扫描。成员确认、主规格同步、归档、提交和推送均未执行。

## 2026-11-15口令轮换复核（历史，最新状态见归档结果）

2026-11-15按授权轮换五个现有演示账号的口令并同步私有 `.env.team`，种子保持关闭。五个账号新口令登录均200，旧演示口令对teacher-a返回401，账号其他字段未变。完整门禁run `20261115T152822Z-5904`的scan/access/audit/api/unit均为0，总退出0；Gitleaks四范围与配置密钥匹配均零命中，依赖扫描通过。教师在已有权限内配置给满分参考文件的现象不认定为安全漏洞，实验数据保留。实际成员确认、主规格同步和归档仍待完成，未提交、推送或创建标签。

### 本轮操作与验证

- 仅更新teacher-a、teacher-b、student-a1、student-a2、student-b1的密码哈希及私有演示口令；每个账号独立生成bcrypt盐，私有恢复备份不进入Git。
- 新口令采用随机选择的四个常见英文词、四位数字和分隔符，只在私有配置中查看；公开文档和日志不记录明文。
- 轮换时确认没有活动批改任务；15张其他业务表指纹及用户非密码字段不变。backend/worker载入新配置，前端代理刷新后health为200；没有构建新镜像或变更旧项目。
- 本轮没有重新执行学生恶意输入实验；用户报告多次测试未发现问题。教师参考配置实验不替代学生入口验证，不再列为漏洞或待接受风险。
- 评分代码、提示词和安全阈值未修改。只更正实验解读，未新增参考文件过滤、额外模型审核或权限豁免。

## 1. 六次提交增加了什么

| 提交 | 实际变化 |
| --- | --- |
| `14e43cf` | V14迁移；稳定技能ID、真实模型Token/耗时；教师本班两路审计查询；配置和终评拒绝留痕 |
| `7a1d56b` | 常驻SQLi、XSS、CSRF等价反例；材料标题与Markdown的DOM安全断言 |
| `5d4ac3a` | SEC-08四步模型实验及202/停启worker/跨班访问演示脚本 |
| `015ad6a` | 聚合安全门禁、Makefile入口、访问与审计预检扩展 |
| `474913e` | Class09规约、修复台账、测试与扫描证据 |
| `caa9dbf` | 把实际实现提交哈希回填到文档 |

AI生成范围依据实现者的 `review-02.md` 记录；本轮代理生成范围为本报告、本轮审计/测试修正和相应文档。实现者自审、脚本检查、独立代理复核与成员确认分别记录，不相互替代。规约文档在代码提交之后补入，无法追认“实施前已人工确认”，任务1.2保留未完成。

## 2. 独立环境与数据保护

- Windows、Docker Engine 29.8.2 / Compose 5.6.0，Java 17，Python 3.11.2，Node 22.21.1 / npm 10.9.4。本机Windows前端测试可以正常执行，与实现者的runner故障环境不同。
- 使用本机已有私有 `.env.team`，项目 `campusclaw-plus`、数据库 `campusclaw_plus`、端口18080/13306/15673；种子保持关闭。重新构建后端/前端并启动七服务；最终七服务均运行，提供健康检查的五个服务均healthy（前端和worker未配置该探针），旧 `campusclaw` 等项目未启动或修改。
- 部署前在忽略目录保存私有备份，V14迁移后16张既有业务表指纹不变，私有配置未改动。没有修改V1～V13或使用实现者的数据卷与账号哈希。
- 本机既有B班教师密码不符合原测试脚本的共享密码假设。新增可选跨班账号配置，以本机随机口令独立生成临时账号的bcrypt哈希；本轮两名临时教师均已按精确ID删除，没有复制或覆盖已有用户哈希。
- 所有合成作业、提交、评分、讲义、技能和会话按本轮登记ID清理。API套件恢复原智能体配置和绑定，合法PUT导致其 `updated_at` 变化；对照备份确认其余内容、3条技能绑定和10条材料绑定不变。其余15张业务表指纹相同，不能把初次严格指纹比较的差异隐去。
- 追加式拒绝审计正常保留。API套件因工具审计保护保留本轮断开的MCP测试服务及审计，不清除真实历史审计；这不是“所有表零变化”。原始日志、备份、HTML和运行JSON均在忽略目录，不纳入提交。

## 3. 发现与处理

| 问题与触发条件 | 修正 | 复核结果 |
| --- | --- | --- |
| `AuditQueryService` 只从消息收集用户名；用户只有拒绝事件时显示未知用户 | 合并两路用户ID后批量读取 | 新增用例修前失败、修后通过，不新增逐行查询 |
| 显式日期包含终日，原判断允许367天 | 按包含起止两日的366天窗口校验 | 新增366/367天边界用例修前失败、修后通过 |
| 缺少任务2.3指定的V13→V14独立迁移测试 | 新增 `SecurityAuditSchemaMigrationTest`，接入integration并从unit排除 | 独立MySQL验证旧消息NULL、新索引/非负约束和技能删除后快照，集成44项通过 |
| 独立事务提交/失败缺少聚焦验证 | 新增 `SecurityAuditServiceTest` | 正常REQUIRES_NEW提交、保存失败回滚、提交失败不向调用者抛出，3项通过 |
| SEC-08可能借用用户的旧拒绝记录，且原判据接受跨班403 | 匹配本轮对象类型、对象ID和准确403/404 | 两项新脚本反例及真实第四步通过 |
| 跨班教师硬编码为 `teacher-b` 且强制共用A班口令 | 增加 `APITEST_OTHER_TEACHER_USERNAME/PASSWORD`，保持原默认值，密码加入脱敏 | 账号配置/回退/脱敏测试及完整API通过；不改账号数据契约 |
| 请求头与multipart新增能力缺少框架自身测试 | 本地HTTP服务器回显UTF-8正文及头，验证互斥参数 | API框架5项通过 |
| 门禁测试未直接验证完整阶段执行及失败传播 | mock子进程验证五阶段全执行、1/2传播、故障参数不可洗绿 | 脚本42项通过 |
| delta把“供应商无usage”误写成实测耗时也必须为空；失败消息与事件描述不一致 | 明确Token未知与实测耗时独立；有部分正文保存FAILED消息，否则补失败事件 | 规约与既有网关/流式实现一致，无SSE协议变化 |

另外修正了任务中的注入用例目录、反例报告文件名，以及API说明中“学习计划尚未实现”的过时描述。没有升级依赖、变更配置默认值或引入新依赖。

## 4. 实际执行

以下命令使用本机隔离Python环境；表中 `python` 表示该环境的解释器。Java检查使用JDK17，集成设置 `TESTCONTAINERS_API_VERSION=1.44`。没有把实现者报告算作本机执行。

| 命令/操作 | 本机实际结果 |
| --- | --- |
| 首轮前端Vitest及后端非容器测试 | 前端158、后端246项通过，作为修改前基线 |
| 两个审计回归用例，修正前运行 | 失败；原红日志保留，随后修正 |
| `python scripts/check.py --mode unit`，最终复跑 | 退出0；规格、前端158项及构建、后端251项（0失败/错误/跳过）、MCP8项、脚本42项通过 |
| `python scripts/check.py --mode integration` | 退出0；四个独立容器测试类共44项，0失败/错误/跳过；包含新V13→V14测试 |
| `python -m unittest discover -s apitest/tests` | 退出0，5项通过 |
| `python scripts/security_precheck.py --env-file .env.team --checks audit` | 退出0；独立run `ecd51d1f812c`，完整门禁再次run `1983a4263f41` 通过，均清理完成 |
| 同入口 `--checks access` | 退出0；独立run `f743ae50322c`，完整门禁再次通过 |
| `python scripts/sec08_experiment.py --env-file .env.team`，修正跨班配置后 | 退出0，run `bd2cd4bba99c`；第四步准确403/404、成绩/终评/任务计数不变、两条本轮拒绝均存在 |
| `APITEST_ENV_FILE=.env.team python apitest/run_all.py` | 修正跨班配置后退出0，全部11个用例及清理通过；完整门禁内再次通过，含SQLi/XSS/CSRF等价反例 |
| `python scripts/four_step_demo.py --env-file .env.team` | 退出0，run `3909e4f7242f`；202且无分数→停worker时PENDING→恢复后SUCCEEDED，模型任务耗时2196ms；跨班材料404、检索0命中、本班5命中 |
| 本轮合成任务调用 `publish_duplicate`，再查任务和MySQL | 退出0，run `69a1bfa9c91a`；再次投递后任务未变、评分结果仍1份，合成资源已清理 |
| `python scripts/check_security.py --env-file .env.team` | 退出1；配置密钥匹配1项；Gitleaks四范围均0命中；依赖/静态检查退出0 |
| `python scripts/security_gate.py --env-file .env.team` | 退出1，run `20261114T142446Z-46328`；scan/access/audit/api/unit分别为1/0/0/0/0，真实失败正确传播 |
| 最后新增测试后的 `ruff check mcp-server apitest scripts`、Maven `checkstyle:check` | 均退出0，Checkstyle零违规 |
| 审计接口8项真实HTTP边界检查 | 退出0；匿名401、学生403、跨班用户空集合、limit 0/101与非法日期400、366天200/367天400 |
| 最后文档变更后的 `check_security.py --only secrets` | 退出1；扫描587个公开文件及原四范围，Gitleaks仍零命中，配置演示口令匹配仍1项 |
| 最终 `openspec validate --all --strict`、`git diff --check` 与相对链接检查 | 退出0；16项规格校验通过，差异无空白错误，21份受影响Markdown无断链或仓库外引用 |
| 审计两路实际MySQL `EXPLAIN` | 事件使用班级复合索引，消息使用技能时间索引并主键连接会话；均有排序，小数据证据不代表大数据压测 |

最终SEC-07样本：供应商输入255、输出18、总量273 Token，模型实测161ms；稳定技能ID、操作人、UTC时间、动作、消息对象、状态、请求标识和MySQL对应字段一致。固定文本/缺失usage的NULL语义由网关和流式测试覆盖，未填0。

失败与重跑保留：首轮SEC-08/API因既有B班教师凭据不匹配失败；首轮四步演示因受限环境无法访问Docker管道失败；一次最终unit在Maven wrapper启动时失败，没有运行后端测试。在允许读取既有工具缓存和Docker管道的执行环境重跑后均通过。没有改预期值掩盖上述失败。`--mode all` 未作为单条命令执行，unit/integration/API分别执行；`make check-security` 未在本机执行，使用Python等价入口。

## 5. 七类核查与七维评审

| 对象 | 保证方式、失效边界及结论 |
| --- | --- |
| 接口入口 | 当前用户服务取得角色/班级，查询limit和日期校验；不信任客户端classId。学生403、匿名401、跨班404或空结果符合对应资源契约 |
| 业务逻辑 | 成功消息、部分FAILED消息和无正文失败事件分开；不新增成绩写工具。教师参考配置影响评分，按2026-11-15澄清不认定漏洞；越权仍按代码和数据判据 |
| 数据库操作 | V14只增不改，无技能外键；消息与元数据同事务。独立审计事务不因外层回滚消失；本轮迁移与真实拒绝回查通过 |
| 公共函数 | 复用现有网关、授权和API清理，不替换认证框架；新增跨班密码进入统一脱敏。multipart与请求头有本地HTTP断言 |
| 错误处理 | 供应商无usage保留NULL；审计保存/提交异常不改变拒绝结果；SSE提交前失败不发done。门禁失败保持非零；子进程没有统一超时，是运行维护限制 |
| 安全与隐私 | 查询只投影元数据；DTO/事件表没有正文、作答、引用和凭据字段。日志Gitleaks零命中；没有输出供应商原文。Bearer无Cookie的CSRF等价口径仍需成员确认 |
| 性能 | 两路各取至多100条再合并，操作人批量读取，无新增逐行用户名查询；EXPLAIN仍有排序。REQUIRES_NEW需要独立连接，未执行连接池压力测试 |

| 维度 | 结论 |
| --- | --- |
| 需求覆盖率 | V14、usage、拒绝与查询、三反例、实验及门禁均已实现；SEC-05本机轮换及复扫已完成，成员确认与同步归档仍待完成 |
| 需求一致性 | 已修正366天端点、仅事件用户名和NULL/FAILED文字偏差；原SSE/HTTP/班级边界保持 |
| 失败路径覆盖 | 查询非法输入、学生拒绝、跨班拒绝、模型缺usage/流失败、审计事务失败、门禁非零传播有测试或实际证据；未做全环境故障注入 |
| 查询性能风险 | 已执行两路EXPLAIN；有限结果合并，无用户名N+1；大历史量排序、独立事务连接消耗未压测 |
| 重复代码比例 | 未计算百分比；预检/实验复用现有验收及清理。两套上传辅助分别服务API框架和安全预检，本轮不跨框架重构 |
| 漏洞风险 | 未发现代码层越权写入；教师参考配置不认定为安全漏洞；当时的公开字符串演示口令问题已于2026-11-15授权轮换，复扫结果见补充 |
| 高风险场景 | 并发、事务、一致性见下一节；技术复核不替代成员签署 |

## 6. 并发、事务与一致性

1. **消息提交与done：** 流式服务不持有模型等待长事务；`saveAssistantMessage`代理事务返回后再发送done。提交后断线不重写成功；提交前失败保存FAILED部分消息或补失败事件。原SSE契约不变。
2. **拒绝事务：** `REQUIRES_NEW`独立提交、事件无业务外键，外层403/404回滚不抹除事件。本机跨班404和学生403均可回查，新单测覆盖保存及提交失败。没有并发连接池压力证据，不能声明在连接池耗尽时无延迟。
3. **查询一致性：** 两路在只读事务内读取、按稳定顺序合并，最多返回limit条；没有新增重复成功事件。历史成功消息仍服从会话删除规则，追加事件不随技能删除。两种保留周期在文档中分开。
4. **材料并发改名：** 检查实现者加入的 `@DynamicUpdate`，后台索引不再通过整行UPDATE覆盖同时修改的标题；真实XSS上传后改名与数据库一致。不是对任意同字段竞争的通用乐观锁保证。
5. **异步批改：** 本次未修改原子认领、结果唯一约束、重试/超时恢复代码。容器集成回归包含原有并发/失败路径；额外真实演示覆盖202解耦、停启不丢及重复唯一结果。未重复制造DLQ或超时事故。

## 7. 2026-11-14待办及后续处置

- **SEC-05历史红项（2026-11-15已轮换并复扫通过）：** Gitleaks v8.24.3扫描31个可达提交、公开文件、前端产物和七服务日志均零命中；配置密钥精确匹配发现 `configured-DEMO_SEED_PASSWORD` 命中 `mcp-server/server.py` 的公开服务名。没有模型/JWT/数据库/MCP真实秘密命中。现有演示口令不适合作为私有凭据，修改服务名来躲扫描无意义。当时尚未获授权轮换；现已按2026-11-15指令更新五个账号及私有配置，完整门禁退出0，原始红项保留。
- **SEC-08判定更正（2026-11-15）：** 对照0分、教师配置给满分参考后100分。文件由教师上传绑定，学生作答为“我不知道。”，没有学生可控参考入口或越权证据，不认定为安全漏洞或归档阻断项。用户报告多次测试学生恶意输入未发现问题；本轮代理未重新执行这些输入用例。详见[实验范围](sec08-experiment.md)。
- **确认待办：** 实际成员确认本文结论及Bearer JWT的CSRF等价验收口径；任务1.2的实施前确认不能追认。当前没有成员风险接受记录。
- **未执行：** 本机GNU make、OWASP/NVD可选补充、单命令all、压力测试、本机重新制造受控故障。实现者的受控红绿记录保留；本机在实际口令红项整改后独立取得全绿，不改写旧红。
- **下一步：** 口令处置与完整门禁复扫已完成；实际成员确认后，再按授权同步三份delta、严格校验、归档和提交。当前主规格未动，change保持活动；未创建提交、推送或标签。

成员确认：2026-11-16接收交付成员在本会话明确回复“确认，补齐8.3后按上述记录同步归档”。确认包括review-04及Bearer JWT的CSRF等价口径；代理只记录该回复，不代签此前尚未发生的评审。

## 8. 逐文件结论

下表覆盖六次提交中的文件及本轮新增/修正文件。代码结论“可用”表示所述技术范围已复核，不表示整个change已获合入或风险接受；需限定处置的门槛仍见第7节。

| 文件 | 结论 | 依据/边界 |
| --- | --- | --- |
| `Makefile` | 可用 | 目标委托Python入口；本机未执行make。 |
| `README.md` | 可用 | 更新当前已推送/本轮未提交及活动状态，链接独立复核和剩余待办。 |
| `apitest/README.md` | 可用 | 更新当前已推送/本轮未提交及活动状态，链接独立复核和剩余待办。 |
| `apitest/cases/api/materials/csrf/PATCH.py` | 可用 | Origin/Cookie无Bearer→401且DB不变、正常对照200；等价口径待成员确认。 |
| `apitest/cases/api/materials/xss/PATCH.py` | 可用 | 本轮恶意标题API与MySQL一致，资源按ID清理。 |
| `apitest/cases/api/retrieval/search-injection/POST.py` | 可用 | 注入及伪造classId不扩大班级，逐命中核对READY/MySQL，非法参数400。 |
| `apitest/common/config.py` | 可用 | 跨班教师独立配置、默认兼容，新增密码加入脱敏；5项框架测试通过。 |
| `apitest/common/fixtures.py` | 可用 | 上传成功即登记按ID清理，不改既有READY讲义。 |
| `apitest/common/http_client.py` | 可用 | 本地HTTP断言自定义头、UTF-8 multipart；raw/JSON互斥，令牌进Authorization头。 |
| `apitest/tests/test_common.py` | 可用 | 5项覆盖HTTP/SSE、上传和请求头、跨班配置及脱敏/HTML转义。 |
| `backend/src/main/java/com/campusclaw/agent/AssistantController.java` | 可用 | 教师配置写拒绝附动作/对象，原创建/更新/删除契约保留。 |
| `backend/src/main/java/com/campusclaw/agent/SkillController.java` | 可用 | 教师配置写拒绝附技能对象，服务层权限不放宽。 |
| `backend/src/main/java/com/campusclaw/audit/AuditAction.java` | 可用 | 动作、对象和状态明确枚举，与迁移及查询映射一致，无动态执行。 |
| `backend/src/main/java/com/campusclaw/audit/AuditController.java` | 可用 | 教师入口与筛选参数，真实预检验证身份和班级边界。 |
| `backend/src/main/java/com/campusclaw/audit/AuditEventView.java` | 可用 | 响应无正文、作答、提示词或工具完整结果。 |
| `backend/src/main/java/com/campusclaw/audit/AuditMessageProjection.java` | 可用 | 只投影消息元数据，避免加载正文。 |
| `backend/src/main/java/com/campusclaw/audit/AuditObjectType.java` | 可用 | 动作、对象和状态明确枚举，与迁移及查询映射一致，无动态执行。 |
| `backend/src/main/java/com/campusclaw/audit/AuditQueryService.java` | 可用 | 修正仅事件用户名及366天包含端点；两路限量稳定合并。 |
| `backend/src/main/java/com/campusclaw/audit/AuditStatus.java` | 可用 | 动作、对象和状态明确枚举，与迁移及查询映射一致，无动态执行。 |
| `backend/src/main/java/com/campusclaw/audit/SecurityAuditEvent.java` | 可用 | 追加安全元数据，不存正文或业务外键，不随技能删除。 |
| `backend/src/main/java/com/campusclaw/audit/SecurityAuditEventRepository.java` | 可用 | 服务端班级限定，元数据投影，有界结果；EXPLAIN有索引及排序。 |
| `backend/src/main/java/com/campusclaw/audit/SecurityAuditService.java` | 可用 | REQUIRES_NEW提交，保存/提交失败不向原调用者抛出；聚焦3项通过。 |
| `backend/src/main/java/com/campusclaw/auth/CurrentUserService.java` | 可用 | 写接口重载在拒绝前记审计，原403不变，读取接口不扩大审计。 |
| `backend/src/main/java/com/campusclaw/conversation/AgentConversationService.java` | 可用 | 准备失败留痕，模型等待不新增数据库长事务。 |
| `backend/src/main/java/com/campusclaw/conversation/AgentPromptBuilder.java` | 可用 | 从已选Skill传稳定ID，模型不参与ID选择。 |
| `backend/src/main/java/com/campusclaw/conversation/AgentTurnPlan.java` | 可用 | skillId新增，保留兼容构造。 |
| `backend/src/main/java/com/campusclaw/conversation/ConversationService.java` | 可用 | 消息及元数据同短事务；旧重载兼容，返回后才可发done。 |
| `backend/src/main/java/com/campusclaw/conversation/ConversationStreamingService.java` | 可用 | 实际模型计时、固定文本NULL；FAILED部分消息/无正文事件，不重写已提交成功。 |
| `backend/src/main/java/com/campusclaw/gateway/ChatGateway.java` | 可用 | 默认usage接口兼容旧complete，未知NULL。 |
| `backend/src/main/java/com/campusclaw/gateway/OpenAiChatGateway.java` | 可用 | usage-only块/非流式解析、include_usage请求及缺失/非法值由fake HTTP/SSE覆盖。 |
| `backend/src/main/java/com/campusclaw/grading/AssignmentController.java` | 可用 | 学生终评在requireTeacher处记录SUBMISSION拒绝，真实403可回查。 |
| `backend/src/main/java/com/campusclaw/grading/SubmissionReviewService.java` | 可用 | 角色/归属拒绝独立留痕，仍有班级校验和提交锁。 |
| `backend/src/main/java/com/campusclaw/persistence/ConversationMessage.java` | 可用 | 新元数据列可空，构造兼容；旧消息升级可读。 |
| `backend/src/main/java/com/campusclaw/persistence/Material.java` | 可用 | DynamicUpdate避免异步索引整行覆盖标题，真实XSS用例通过；非通用乐观锁。 |
| `backend/src/main/resources/db/migration/V14__security_audit.sql` | 可用 | V1～V13未改；NULL兼容、新索引/非负约束及快照经独立MySQL验证。 |
| `backend/src/test/java/com/campusclaw/agent/AgentConfigurationControllerTest.java` | 可用 | 配置成功/拒绝兼容新审计依赖，后端251项通过。 |
| `backend/src/test/java/com/campusclaw/audit/AuditControllerTest.java` | 可用 | 教师参数映射与无正文响应；学生拒绝不查询服务。 |
| `backend/src/test/java/com/campusclaw/audit/AuditQueryServiceTest.java` | 可用 | 合并/边界；新增用户名及366/367天两例修前失败、修后通过。 |
| `backend/src/test/java/com/campusclaw/audit/SecurityAuditSchemaMigrationTest.java` | 可用 | 实际V13建旧行→V14→删除技能，检查NULL/约束/索引/快照。 |
| `backend/src/test/java/com/campusclaw/audit/SecurityAuditServiceTest.java` | 可用 | 3项：独立提交、保存失败回滚、提交失败不掩盖原调用。 |
| `backend/src/test/java/com/campusclaw/conversation/ConversationStreamingServiceTest.java` | 可用 | 固定文本NULL、真实流元数据及提交前后失败语义通过。 |
| `backend/src/test/java/com/campusclaw/gateway/GatewayClientTest.java` | 可用 | fake HTTP/SSE验证usage解析、缺失值及stream_options。 |
| `docs/lesson-09/README.md` | 可用 | 更新当前已推送/本轮未提交及活动状态，链接独立复核和剩余待办。 |
| `docs/lesson-09/handoff.md` | 可用 | 更新当前已推送/本轮未提交及活动状态，链接独立复核和剩余待办。 |
| `docs/project-status.md` | 可用 | 更新当前已推送/本轮未提交及活动状态，链接独立复核和剩余待办。 |
| `frontend/src/utils/assistantMarkdown.test.js` | 可用 | 真实DOM断言Markdown/KaTeX恶意输入不生成可执行内容。 |
| `frontend/src/views/MaterialsView.test.js` | 可用 | 恶意标题显示为文本，无script/img/事件节点；前端158项通过。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/.openspec.yaml` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/design.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/proposal.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/specs/api-regression-harness/spec.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/specs/security-audit/spec.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/specs/security-precheck/spec.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/tasks.md` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `openspec/config.yaml` | 可用 | 规约及路径与实现对照修正；不追认实施前确认，未同步归档。 |
| `reports/audit-recheck.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/gate-red-green.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/injection-cases.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/review-02.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/review-03.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/rework-01.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/risk-authz.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/sec08-experiment.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/security-baseline.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `reports/security-fix-log.md` | 可用 | 保留实现者历史证据；当前本机结果及未通过项见本报告，不覆盖旧红。 |
| `scripts/check.py` | 可用 | 新迁移类接入integration并从unit排除，251/44项分组通过。 |
| `scripts/four_step_demo.py` | 可用 | 实际202/PENDING/停启恢复及跨班404/空检索通过。 |
| `scripts/sec08_experiment.py` | 可用 | 本轮对象/类型及403/404准确匹配，真实第四步通过；教师参考配置现象保留，不判定漏洞。 |
| `scripts/security_gate.py` | 可用 | 实际1/0/0/0/0聚合为1，未洗绿；无统一子进程超时是运行限制。 |
| `scripts/security_precheck.py` | 可用 | 真实授权、七类审计字段及MySQL一致性通过，按登记ID清理。 |
| `scripts/tests/test_sec08_experiment.py` | 可用 | 本轮对象、类型和准确状态，历史拒绝不可替代；纳入42项脚本测试。 |
| `scripts/tests/test_security_gate.py` | 可用 | 五阶段执行、退出1/2传播及故障参数不可洗绿；42项脚本测试通过。 |

## 归档前成员确认（2026-11-16）

接收交付成员明确认可独立技术复核结论及当前CSRF等价口径，并授权补齐任务8.3后同步、归档。命令执行与成员确认仍分别记录。实施前确认（1.2）未完成，保留为流程偏差；git提交（9.6）留给用户后续执行。这两项不得补写为已执行，用户已同意如实保留后归档。

## 归档结果（2026-11-16）

2026-11-16已按接收交付成员确认同步并归档Class09。受控红run `20261115T160013Z-33956`使用 `--fault-stage unit`，阶段0/0/0/0/1、总退出1；移除参数后run `20261115T160417Z-7996`五阶段全0、总退出0。三份主规格已同步，历史失败记录保留。实施前确认缺失作为流程偏差留档，git提交由用户后续执行；未提交、推送或创建标签。

28/30项任务记录为完成；1.2为实施前确认缺失的历史流程偏差，9.6为用户后续提交。接收成员明确认可该记录方式；不补签、不把未执行事项记成通过。当前无活动change，主规格新增security-audit并更新security-precheck/api-regression-harness。详情见[归档记录](../openspec/changes/archive/2026-11-16-add-class09-security-audit-and-gate/README.md)。
