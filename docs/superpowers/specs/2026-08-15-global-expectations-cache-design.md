# 全球预期官方变化与 Redis 缓存设计

## 目标

将 Global Expectations 从 JVM 内存采样器修正为 Polymarket 官方只读数据观察器。当前概率来自 Gamma `outcomePrices`，1 小时和 24 小时变化直接采用 Gamma 的 `oneHourPriceChange`、`oneDayPriceChange`，5 分钟变化与趋势图来自 CLOB 公共历史价格。Redis 保存最近成功的历史和页面快照，服务重启后仍可恢复，不依赖本地运行时长。

## 数据口径

- 当前 YES 概率：Gamma `outcomePrices` 的 Yes 对应值，转换为百分数显示。
- 1 小时变化：Gamma `oneHourPriceChange`，由价格小数转换为概率百分点。
- 24 小时变化：Gamma `oneDayPriceChange`，由价格小数转换为概率百分点。
- 5 分钟变化：以当前 YES 概率减去 CLOB 历史中“不晚于当前时刻减 5 分钟”的最近点；没有合格基线时返回空值。
- 趋势图：CLOB `batch-prices-history` 最近 24 小时的分钟级历史点。请求最多包含筛选后的 20 个 Yes token，符合官方单批上限。
- 所有变化统一显示为概率百分点 `pp`，不解释成收益率百分比。

## 数据流与模块边界

1. `finscope-rpc` 的 Gamma 客户端读取活跃市场、Yes token ID、当前概率及官方变化字段。
2. Service 先完成关键词匹配并按成交量选出最多 20 个观察市场。
3. `finscope-rpc` 的 CLOB 客户端一次批量读取这些 Yes token 的最近 24 小时历史。
4. `finscope-service` 计算 5 分钟变化、构造趋势点，并直接采用 Gamma 的 1 小时和 24 小时变化。
5. `finscope-dao` 通过项目现有 `StringRedisTemplate` 保存完整成功页面快照和按 token 的历史快照，TTL 为 26 小时。
6. Controller 和前端接口契约保持不变；前端将“积累中/内存轨迹”文案改为“暂无历史/官方价格轨迹”。

原 `GlobalExpectationSnapshotCache` 及 Service 内的 `latest` 内存事实来源删除。进程内只保留刷新互斥等控制状态，不保存业务历史。

## Redis 键与失效

- `finscope:global-expectations:view`：完整成功页面 JSON，TTL 26 小时。
- `finscope:global-expectations:history:{tokenId}`：单个 Yes token 的规范化历史 JSON，TTL 26 小时。

读取、写入、反序列化失败均记录脱敏告警。禁止使用 `KEYS` 或 `SCAN` 清理；旧数据由 TTL 回收。Redis 是故障兜底与重复请求缓存，不替代 Polymarket 官方数据源。

## 降级状态

- Gamma 与 CLOB 都成功：`LIVE`，更新历史和完整页面缓存。
- Gamma 成功、CLOB 失败：使用 Redis 中对应 token 的历史计算 5 分钟变化和曲线，数据标记 `PARTIAL`；1 小时和 24 小时仍采用本次 Gamma 官方字段。
- Gamma 失败：读取 Redis 完整页面，标记 `STALE`。
- Redis 也无快照：返回明确的 `UNAVAILABLE` 占位，不伪造市场概率或静态样例。

## 不做

- 不接钱包、签名、API Key、交易下单或私有接口。
- 不把高频价格写入 SQLite。
- 不使用 WebSocket 逐笔行情；当前 60 秒刷新满足观察型产品定位。
- 不自动映射 A 股或输出交易建议。

## 验收

1. 首次成功刷新无需等待 5 分钟，即可从官方数据得到 1 小时、24 小时变化，并在历史存在时得到 5 分钟变化。
2. Gamma 官方变化字段按概率百分点正确换算，不再由本地采样计算。
3. CLOB 批量历史最多请求 20 个 Yes token，并正确选择 5 分钟基线。
4. Redis 保存 26 小时历史和完整页面快照，JVM 重启不清空缓存。
5. CLOB 或 Gamma 单独失败时按设计降级，页面不显示伪造的零值或静态三条数据。
6. 后端单元测试、前端测试和生产构建通过。
