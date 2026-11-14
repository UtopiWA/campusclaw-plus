# 首页检查记录

日期：2026-11-13～14。范围：`add-role-home-dashboards` 工作区差异。新增实现与测试由AI协助生成，以下为代码自查和实际工具结果，不代签另一成员。

## 已执行

- `python scripts/check.py --mode unit`：规格、前端156项测试及构建、后端240项单元测试、MCP8项及脚本34项测试通过，无失败或跳过。最后一次卡片跳转调整后，前端156项回归、lint、生产构建与浏览器检查再次通过。
- `mvnw -B -Dapi.version=1.44 -Dtest=CampusClawIntegrationTest test`：独立MySQL 8.4，41项通过，0失败/错误/跳过；容器由Testcontainers撤除。覆盖匿名/角色/跨班、旧版本不重复计数、终评优先、私人重答、列表上限，以及首页派生完成后数据库任务仍PENDING。
- 浏览器：Edge，合成API数据，师生首页1440/1000/760/470/375px无横向溢出。已检查作业图切换、键盘图例与筛选、讲义直达、创建/上传弹窗、刷新不重复打开、安全公式渲染、空态/失败/重试；无运行错误，无写请求。合成结果不充当真实接口验收。
- `python scripts/scan.py`：默认七项通过，含ESLint、npm/pip审计、Ruff、Checkstyle、SpotBugs及OSV；Java依赖124/124，零漏洞。未运行可选OWASP或全套API/其他容器回归，本轮不改相关链路。
- 团队Compose：只重建backend/worker/frontend，其他4个服务不变；13张业务表逐表计数及指纹前后相同，私有配置未改。已做本轮私有备份，无DDL变更。
- 真实API：teacher-a、student-a1、student-a2、student-b1均已读取首页；与现有作业/全班名单核对数量和分母，检查当前可访问结果、进度恒等式、身份参数不能覆盖、匿名401及角色403。GET前后13表指纹相同。实际Edge验证师生入口、刷新、手机宽度，以及真实登录默认首页/保留明确回跳。
- 8条新增SQL已在团队MySQL执行EXPLAIN：作业用`idx_assignment_purpose`，提交用学生索引，任务及结果/终评用提交索引或唯一键；窗口/合并后的临时结果需要扫描排序。小标签表选择ALL（8行），连接用point_id索引，无用户字符串拼接，无每项查询。最终6次真实摘要请求中位47ms、最大47ms，仅代表当前小数据环境，未做并发或大班级压测。
- 公开文件检查：585份文件、525条相对链接，失效链接与仓库外引用均为0，Gitleaks零命中；私有配置、备份和原始报告均在忽略范围。最终规格严格校验15项通过，差异检查通过。
- 磁盘只读核对：已区分镜像、容器可写层、数据卷、构建缓存和虚拟磁盘分配。本轮缓存增加约0.20GB，虚拟磁盘文件大小未变；未执行prune、卷删除或压缩。主机绝对目录与原始测量仅保存在本机忽略目录，不进入交付文件。

## 自查

| 维度 | 检查与结论 |
| --- | --- |
| 需求覆盖 | 师生统计、图表、计划、列表及快捷入口对应规格；空态不填示例数据 |
| 一致性 | 班级作业只取gradable且非私人练习；最新作答、终评优先、未知状态及现有掌握规则保持一致 |
| 失败路径 | 页面统一顶部消息及重试；离开页面取消请求，晚到响应不更新已卸载视图；旧计划不受首页失败影响 |
| 查询性能 | 摘要列表SQL限5条、条形图限8条，统计覆盖全班；复用现有批量学情查询，不为每个条目查询；8条EXPLAIN及当前数据延迟已核对，未压测 |
| 重复代码 | 复用身份、学情、计划完成判定、安全渲染和弹窗；环图共用组件，没有引入图表依赖 |
| 安全与隐私 | 服务端取角色/班级/学生，显式白名单投影，不返回作答及评分依据；首页无外部模型调用或业务写入 |
| 并发/事务/一致性 | 汇总在只读事务内；计划完成只改复制后的响应，不写状态。完成判定与原计划流程共用；原认领、发件箱、幂等和成绩写入路径不改动。显示为读取时快照，刷新获取后续变化 |

## 逐文件结论

| 文件 | 自查结论与依据 |
| --- | --- |
| `home/HomeController.java` | 可用：入口复用当前身份，错误角色拒绝 |
| `home/HomeService.java` | 可用：只读汇总、启用标签、状态分组及响应字段裁剪 |
| `home/HomeRepository.java` | 可用：真实SQL、独立MySQL、8条EXPLAIN及跨班/最新成绩口径已复核 |
| `learning/LearningPlanService.java` | 可用：共享完成判定；首页只改复制记录，完整计划行为保留 |
| `learning/LearningRepository.java` | 可用：仅开放既有参数化查询/映射工具供首页复用 |
| `views/HomeView.vue`、`home.css` | 可用：师生布局、加载/失败/空态、取消请求、五屏宽实测 |
| `components/OverviewDonut.vue` | 可用：零总数、可读图例和键盘按钮；纯SVG |
| `components/AppShell.vue`、`router/index.js`、`views/LoginView.vue` | 可用：首页入口和默认跳转；保留明确回跳与旧检索路径 |
| `views/AssignmentsView.vue`、`views/MaterialsView.vue` | 可用：消耗一次快捷操作参数；讲义链接支持无chunk预览 |
| `components/MasteryTable.vue`、`views/LearningView.vue`、`views/TeachingView.vue` | 可用：承接标签/状态筛选，保留原分页和学生明细 |
| `CampusClawIntegrationTest.java` | 可用：真实SQL、旧版本/终评/私人重答、只读进度与角色/班级边界 |
| `views/HomeView.test.js`、`components/OverviewDonut.test.js` | 可用：师生图表、公式、空态/错误、卸载取消、无计划自动生成 |
| `router/index.test.js`、`views/AssignmentsView.test.js`、`views/MaterialsView.test.js`、`views/TeachingView.test.js` | 可用：路由、一次快捷操作、无chunk预览及现有学情回归，不把替身当真实验收 |

## 用户验收与归档

2026-11-14，用户确认验收通过，并要求同步规格及归档。本次据此完成[首页归档](../../openspec/changes/archive/2026-11-14-add-role-home-dashboards/README.md)，五项技术任务完成、五项主规格要求与增量一致。另一成员交叉评审未执行，不记为通过、不代签；本次归档不包含Git合入、提交、推送或打标签，后续仍保留默认评审规则。

归档只修改规格和文档，重新检查严格规格校验、链接和diff；不把前述实现阶段结果写为本次重跑，不调用模型或重建容器。
