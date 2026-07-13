# 自选与深度归因可靠性设计

## 目标

完整支持股票、基金和板块自选行情，消除自选页面的归因 N+1 请求，并让归因报告在搜索失败、断连和同代码多类型标的场景下仍然可信、可恢复。

## 设计

### 标的与行情

- 标的身份统一为 `(code, type)`；股票、基金要求六位数字，板块要求 `BK` 加四位数字。
- 新增 Eastmoney 板块行情适配器，`SECTOR` 不再进入“暂不支持”兜底。
- `QuoteService` 保存 30 秒、以 `(type, code)` 为键的内存快照缓存。命中缓存时不访问外部行情源；未命中项按类型批量请求。
- 基金适配器通过应用级 `quoteTaskExecutor` 并发获取单基金估值，最大并发为 4，保持输入顺序返回。

### 自选列表

- `WatchlistService` 以一次 DAO 批量查询取得自选中每个 `(code, type)` 最新的已完成归因摘要。
- `WatchlistItemResponse` 增加 `attributionSummary`，前端删除逐卡片 `/api/attribution/latest` 调用。
- 前端将自选 API 调用和列表加载逻辑从视图组件抽出，保留卡片与分组渲染职责。

### 归因

- `AttributionAgent` 对联网搜索结果按规范化 URL 去重，去重发生在排序、模型输入和持久化之前。
- 联网搜索记录总问题数、成功问题数、失败问题数；全部失败为失败状态，部分失败为部分成功，并把用户可读警告写入报告。
- 报告查询接口、DAO 索引和前端摘要都使用 `(instrument_code, instrument_type)`，避免代码复用导致摘要串台。
- 启动归因返回 `taskId` 与 `reportId`。SSE 断开时前端轮询该报告，直到 `COMPLETED` 或 `FAILED`，并显示恢复/失败状态。
- 若异步执行器拒绝任务，立即将刚创建的报告标为 `FAILED`，不会遗留 `GENERATING`。

## 数据迁移

- `attribution_report` 增加 `warning_message`。
- 建立 `(instrument_code, instrument_type, id DESC)` 索引；保留旧 code 索引以兼容历史数据访问。
- 所有迁移通过 `DatabaseInitializer` 的幂等列/索引初始化完成。

## 验收

- 板块 `BK0477` 能添加并显示有效行情。
- 多只基金冷加载并发受限，重复刷新在 TTL 内不重复请求。
- 自选列表请求数固定为一个，不随自选数量增加归因请求。
- 相同 URL 的 Tavily 命中只保存一次，报告正确显示搜索失败警告。
- 同代码不同类型标的各自只展示本类型归因。
- SSE 断连不再永久停留在加载页。
