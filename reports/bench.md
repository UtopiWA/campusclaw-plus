# Class10 三接口压测与缓存复测

日期：2026-11-17（UTC）/ 2026-11-18（本地）
环境：Windows 11 + Docker Desktop 29.9.2；团队项目 `campusclaw-plus`；MySQL/Qdrant/RabbitMQ 健康；后端镜像 `class10-b30ab9d-dirty`；真实 Chat/Embedding 服务可用。
以下为原始小样本记录。最新复核发现请求数小于部分标称并发，原饱和点与高并发提速结论撤回。原始 JSON 在 `backend/target/bench/`（忽略目录）。

## 1. 脚本与数据边界

```powershell
python scripts/bench.py --env-file .env.team --levels 1,4,8 --requests 16
```

- 每轮上传一份本轮唯一合成 Markdown 讲义并等待 READY，创建 N 个唯一作业；学生在 worker 保留时逐个作业提交，任务查询使用本轮 `taskId`，检索使用本轮切片内容。
- 不用幂等重放、409 去重或重复答案冒充新任务接收能力。
- 默认保留 worker 并等待本轮任务进入 `SUCCEEDED/FAILED` 后再清理；清理前核对标题前缀和 ID，删除 outbox、results、reviews、tasks、submissions、assignments，再经 API 删除讲义。
- 脚本要求初始队列可读且为空，每档请求数至少等于最大并发设置。以 `peakInFlight` 判断实际达到的并发。清理失败返回非零，无法确认终态时保留资源标识。

## 2. 旧样本（keyword 检索）

命令：`python scripts/bench.py --env-file .env.team --levels 16,32,64 --requests 16`
原始结果：`backend/target/bench/bench-20261117T173016Z-75da8e.json`

| 接口 | c=16 | c=32 | c=64 |
| --- | --- | --- | --- |
| 提交 QPS / P95 | 244.83 / 51.16 ms | 245.81 / 57.19 ms | 203.04 / 66.40 ms |
| 任务查询 QPS / P95 | 471.27 / 22.79 ms | 398.06 / 25.04 ms | 372.02 / 27.19 ms |
| 检索 QPS / P95 | 357.42 / 34.16 ms | 278.17 / 43.62 ms | 177.82 / 55.03 ms |

各列是当时配置的线程数，每档只有 16 个请求，实际并发上限为 16。各列差异不足以判断饱和点。该轮使用 keyword 检索，未调用 Embedding。

## 3. 缓存前后同口径复测

改进：`OpenAiEmbeddingGateway` 增加进程内向量 LRU，默认 256 条，设为 0 可关闭。网关以输入文本为键，检索和索引共用该缓存，班级隔离、READY 回表及材料白名单仍在检索层检查。

旧命令（两轮相同，现会因请求数不足被拒绝）：

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
- 每档只有 8 个请求。小样本中重复检索变快，32/64 并发收益尚无实测依据。
- 同轮提交/任务查询有小幅波动，不属于缓存目标；没有把波动写成改进。

## 4. 瓶颈与副作用

- hybrid/vector 检索缓存未命中时需要调用 Embedding 供应商；瓶颈占比留待有效并发复测。
- 副作用：默认在进程内保留最多 256 条输入文本及其向量。索引批次可能挤出常用查询，重启时缓存清空。
- 未覆盖：没有做多实例/跨进程缓存；没有验证更大数据量或长期内存压力；未把本机数字当课程统一阈值。

## 5. 结论

复测使用第 1 节命令，在同一独立环境分别以缓存 0 和 256 启动后端。报告实际在途峰值、吞吐、P95、错误率及清理结果。需要覆盖更高并发时同时增加请求数，并先确认模型额度和批改任务容量。
