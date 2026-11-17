# Class10 三接口压测与缓存复测

日期：2026-11-17（UTC）/ 2026-11-18（本地）
环境：Windows 11 + Docker Desktop 29.9.2；团队项目 `campusclaw-plus`；MySQL/Qdrant/RabbitMQ 健康；后端镜像 `class10-b30ab9d-dirty`；真实 Chat/Embedding 服务可用。
脚本：`scripts/bench.py`；原始 JSON 在 `backend/target/bench/`（忽略目录）。压测数据为本轮唯一合成讲义、作业、提交和任务，结束后按 ID 清理并恢复 worker。

## 1. 脚本与数据边界

```powershell
python scripts/bench.py --env-file .env.team --levels <档位> --requests <每档请求数>
```

- 每轮上传一份本轮唯一合成 Markdown 讲义并等待 READY，创建 N 个唯一作业；学生在 worker 保留时逐个作业提交，任务查询使用本轮 `taskId`，检索使用本轮切片内容。
- 不用幂等重放、409 去重或重复答案冒充新任务接收能力。
- 默认保留 worker 并等待本轮任务进入 `SUCCEEDED/FAILED` 后再清理；清理前核对标题前缀和 ID，删除 outbox、results、reviews、tasks、submissions、assignments，再经 API 删除讲义。
- 队列初始非空且未确认清理时脚本拒绝运行；本轮 `grading.tasks` 初始深度 0。

## 2. 饱和点观察（keyword 检索，初步跑）

命令：`python scripts/bench.py --env-file .env.team --levels 16,32,64 --requests 16`
原始结果：`backend/target/bench/bench-20261117T173016Z-75da8e.json`

| 接口 | c=16 | c=32 | c=64 |
| --- | --- | --- | --- |
| 提交 QPS / P95 | 244.83 / 51.16 ms | 245.81 / 57.19 ms | 203.04 / 66.40 ms |
| 任务查询 QPS / P95 | 471.27 / 22.79 ms | 398.06 / 25.04 ms | 372.02 / 27.19 ms |
| 检索 QPS / P95 | 357.42 / 34.16 ms | 278.17 / 43.62 ms | 177.82 / 55.03 ms |

提交在 16～32 并发附近趋于平台，64 并发吞吐下降、P95 上升；任务查询和 keyword 检索在 16 并发后下降。该轮检索为 keyword，不调用 Embedding，不能用于缓存前后比较。

## 3. 缓存前后同口径复测

改进：`OpenAiEmbeddingGateway` 增加有界查询向量 LRU；`EMBEDDING_CACHE_SIZE=0` 关闭，默认 256。缓存只以查询文本为键，不包含班级/用户，不改变检索过滤、READY 回表、材料白名单或评分/终评逻辑。

命令（两轮相同）：

```powershell
python scripts/bench.py --env-file .env.team --levels 16,32,64 --requests 8
```

部署之间仅切换 `EMBEDDING_CACHE_SIZE` 并重建/重启 backend 与 worker；其他参数、数据和请求数相同。

| 检索档位 | 缓存关闭（0）QPS / P95 | 缓存开启（256）QPS / P95 |
| --- | --- | --- |
| c=16 | 21.97 / 362.83 ms | 29.79 / 265.92 ms |
| c=32 | 26.33 / 297.60 ms | 90.47 / 83.38 ms |
| c=64 | 19.88 / 393.37 ms | 101.74 / 74.00 ms |

- 关闭原始：`backend/target/bench/bench-20261117T173813Z-ac170c.json`
- 开启原始：`backend/target/bench/bench-20261117T173936Z-b6eda5.json`
- c=32 检索吞吐约 3.4 倍、c=64 约 5.1 倍；P95 从约 300～393 ms 降到 74～83 ms（c=16 仍受首发未命中和样本量影响）。
- 同轮提交/任务查询有小幅波动，不属于缓存目标；没有把波动写成改进。

## 4. 瓶颈与副作用

- 瓶颈：hybrid/vector 检索每条请求等待真实 Embedding 供应商；在 c=32/64 时供应商延迟成为主因，缓存命中后吞吐明显上升。提交主要等待数据库事务和 outbox，任务查询主要受线程/连接池与后端调度影响。
- 副作用：进程内最多保留 256 条查询文本→向量；不持久化、不跨用户授权，进程重启或配置变更后自然失效。没有缓存评分、终评、材料正文或授权结果。
- 未覆盖：没有做多实例/跨进程缓存；没有验证更大数据量或长期内存压力；未把本机数字当课程统一阈值。

## 5. 结论

三接口分档压测已入库并可重复；至少一项缓存改进用相同脚本、相同档位/请求数完成前后复测，结果支持“重复查询的 Embedding 调用是主要瓶颈”的归因。原始数据保留在忽略目录，未提交真实用户正文或供应商响应。
