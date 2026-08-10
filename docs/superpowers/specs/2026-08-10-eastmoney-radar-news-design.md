# 东方财富雷达新闻源设计

## 目标

将东方财富全市场 7×24 快讯作为独立 `NEWS_FLASH` 来源接入现有研究资料网关，使其与财联社、同花顺快讯共同进入雷达信号捕获、事件聚类、热点评分和前 20 条排序流程。

## 边界

- 接入东方财富全市场 7×24 快讯，不按自选股逐个搜索。
- 不修改雷达聚类、评分、排序和页面接口契约。
- 不把新闻抓取放入 Python 行情侧车。
- 不把东方财富设为唯一来源；来源失败时沿用现有最近成功快照降级。

## 数据源与契约

新增 `EastmoneyNewsResearchMaterialProvider`，实现现有 `ResearchMaterialProvider`：

- `providerCode`: `EASTMONEY_NEWS_FLASH`
- `providerFamily`: `EASTMONEY`
- `reliabilityFamily`: `EASTMONEY_NEWS_FLASH`
- `materialTypes`: `NEWS_FLASH`
- `sourceTier`: `T2`
- 单次上限：50 条
- 最小请求间隔：1 秒
- 单次超时：10 秒

Provider 调用东方财富网页端 7×24 快讯接口。接口返回 JavaScript 赋值包装 `var ajaxResult={...}`，适配器只提取其中 JSON 对象，不执行脚本。

字段映射：

| 东方财富字段 | `ResearchMaterial` |
|---|---|
| `id`，回退 `newsid` | `externalId` |
| `title`，回退 `simtitle` | `title` |
| `digest`，回退 `simdigest`、标题 | `content` |
| `showtime`，回退 `ordertime` | `publishedAt` |
| `url_unique`，回退 `url_w`、`url_m` | `url` |

HTTP 原文链接统一升级为 HTTPS。缺少标题或稳定 ID 的记录跳过；缺少摘要时使用标题，避免生成不满足网关契约的材料。

## 数据流

Provider 作为 Spring Bean 被现有 `ResearchMaterialGateway` 自动收集。新闻来源定时任务并行刷新每个 `NEWS_FLASH` Provider，并为东方财富保存独立来源快照。`NewsFeedService` 读取快照后按 URL 或来源 ID 去重，再由雷达生产流水线转换成 `radar_signal`。

东方财富信号与其他平台信号使用完全相同的 48 小时有效窗口和聚类规则。同一事件在不同平台具有不同 URL 时不会在新闻快照层误删，而会在雷达聚类层按主体、动作、指标和文本相似度合并，从而增加事件的来源广度、扩散度和研究优先级。

## 异常与降级

- HTTP、超时和响应解析错误转换为 `ProviderContractException`。
- 缺少 `LivesList` 视为响应结构漂移，不返回伪造空成功。
- 单条记录字段不完整时只跳过该条，不阻断同批有效记录。
- Provider 失败由现有网关回退到最近一次成功来源快照；没有快照时记录来源告警，其他来源继续生产。

## 测试

1. Provider 固定响应测试验证 JavaScript 包装解析、字段映射、HTTPS 链接和来源元数据。
2. 查询词测试验证不匹配内容不会进入结果。
3. 结构漂移测试验证缺少 `LivesList` 时抛出契约异常。
4. 边界字段测试验证 `newsid`、`simtitle`、标题正文回退和无效时间处理。
5. 雷达生产测试加入东方财富与现有来源的同事件信号，验证聚合后的事件包含东方财富信号并增加独立来源数量，而不是生成孤立新闻卡片。

## 验收标准

- 东方财富出现在新闻来源刷新结果中。
- 东方财富快讯落入 `radar_signal`，且 `providerCode` 为 `EASTMONEY_NEWS_FLASH`。
- 与财联社或同花顺描述同一事件时，被聚合为同一个雷达事件。
- 东方财富失败不影响其他新闻来源和雷达页面读取最近成功结果。
- 相关单元测试、服务测试及项目构建通过。
