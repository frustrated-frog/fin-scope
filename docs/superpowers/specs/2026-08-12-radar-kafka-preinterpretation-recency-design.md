# 雷达 Kafka 预解读与时效评分设计

## 目标

雷达榜单生产完成并发布快照后，通过 Kafka 异步触发榜单前 20 个事件的 AI 解读。用户打开事件时优先读取已完成解读；Kafka 或模型不可用时，不阻断榜单生产，并保留现有按需生成降级路径。同时修正事件时间语义并加强陈旧新闻衰减，避免重复采集让旧新闻重新获得高时效分。

## 数据流

1. `RadarHotspotProductionPipeline` 完成抓取、聚合、评分和 SQLite 持久化。
2. `RadarHotspotRefreshService` 先调用 `RadarSnapshotProjectionService.prewarm` 发布雷达与首页榜单快照。
3. 仅在快照发布成功后，发布一条 Kafka 批次消息，内容包含批次 ID、完成时间和按研究优先级排序的前 20 个事件 ID。
4. Kafka 消费者逐个请求 `RadarEventInterpretationService`。该服务继续使用事件内容指纹和数据库唯一约束保证幂等，重复消息不会重复调用模型。
5. 单条解读完成后使雷达视图 revision 失效并重发最近快照，前端通过既有 revision 通道获得最新 `interpretationStatus`。

Kafka topic 固定为 `finscope.radar.interpretation.requested`，消费组为 `finscope-radar-interpretation`。消息 key 使用雷达批次 ID，同一批次重复投递可安全消费。

## 边界与降级

- 快照发布是用户可见关键链路，Kafka 发布是非关键链路。发送失败只记录结构化告警。
- 消费者使用手动提交语义；处理发生可重试异常时抛出，由 Kafka 重试。单个事件不存在或已有非重试状态时跳过，不阻断同批其他事件。
- 消费者调用的是现有异步解读入口，不在 Kafka listener 线程中等待模型响应。
- Kafka 可通过 `finscope.radar.kafka.enabled` 关闭；关闭后注册空实现，应用仍可启动。
- Docker Compose 增加单节点 Kafka，后端在 docker profile 中连接 `kafka:9092`；本地默认连接 `127.0.0.1:9092`。

## 时效评分

新闻的业务时间按以下优先级计算：

1. `publishedAt`：来源声明的真实发布时间；
2. `firstSeenAt`：缺少发布时间时的首次采集时间；
3. 不使用每轮采集都会刷新的 `lastSeenAt` 作为时效依据。

热点分中的新意从 12% 提高到 24%，并采用分段衰减：2 小时内接近满分，6 小时后明显下降，24 小时后仅保留少量分数，48 小时归零。研究优先级中把“新颖度”和“时效”合并校准到总分 100：时效最高 25 分，自选相关 25 分，来源多样性 20 分，来源质量 15 分，事件新颖度 15 分。24 小时以上新闻不再获得时效分，只有真实新增信号带来的传播速度与持续性才能使旧事件重新升温。

## 验证

- 单元测试证明快照成功后才发 Kafka，快照失败或 Kafka 失败不阻断生产。
- Kafka 发布器和消费者测试覆盖消息结构、开关、幂等委托与异常隔离。
- 评分测试覆盖真实发布时间优先、缺失时间回退首次发现、旧 `lastSeenAt` 不产生新鲜度、24/48 小时衰减。
- 后端全量测试、前端测试和生产构建通过。
