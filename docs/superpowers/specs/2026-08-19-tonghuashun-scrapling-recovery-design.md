# 同花顺 Scrapling 成分采集恢复设计

## 目标

第一期只增强自动股票发现所依赖的同花顺行业成分采集。现有 urllib 直连在登录重定向、空页面或页面结构不完整时，按成本从低到高升级为 Scrapling Session HTTP 和 Scrapling Browser；恢复后的 HTML 继续交给同一套分页解析、去重、覆盖率和交易范围规则处理。

本期不改变同花顺作为热门板块唯一排名权威，不让东方财富参与热门排名，也不降低“完整成分才能进入量化候选池”的证据门槛。

## 第一阶段显著增强的能力

1. **登录重定向恢复**：直连跳转 `/account/login` 时，使用可复用会话重新建立 Cookie 与请求指纹。
2. **动态页面恢复**：直连和 Session 都无法得到有效成分表时，使用受限浏览器渲染页面后再解析。
3. **跨分页会话连续性**：同一板块的后续页沿用首次恢复得到的会话，避免每页重新触发限制。
4. **采集路径可审计**：批次记录最终采集模式、是否触发恢复、尝试轨迹和耗时，告警能区分直连失败、Session 失败与 Browser 失败。
5. **候选池完整性提升**：更多板块能在同花顺主来源内达到 95% 以上覆盖率，减少因公开页限制而整板块跳过的情况。
6. **故障隔离**：浏览器不可用、超时或结果仍不完整时，继续走东方财富补全、同花顺完整快照和本轮拒绝，不阻塞其他板块。

## 范围与非目标

### 本期范围

- Python `market-data-service` 内的同花顺行业成分适配器。
- `DIRECT_HTTP -> SESSION_HTTP -> BROWSER` 有界升级策略。
- Scrapling 依赖、运行配置、延迟导入和显式资源关闭。
- 成分批次采集诊断、快照向后兼容和固定 HTML 契约测试。
- 现有股票发现服务对恢复结果、补全来源和快照的编排验证。

### 本期不做

- 不修改 Java 行情 Provider、熔断器或业务 API。
- 不使用登录账号、验证码识别、代理池或主动绕过访问控制。
- 不抓取全 A 股、不增加定时采集中心或通用爬虫平台。
- 不接入东方财富分钟资金流、公司公告、研报、互动问答等新内容域。
- 不根据浏览器捕获的 XHR 自动生成或切换生产协议。

## 架构

### 采集边界

新增 `discovery/acquisition.py`，只负责“给定同花顺 URL，取得页面文本和最终 URL”。它不解析股票、不判断成分完整性，也不了解股票发现业务。

核心模型：

- `AcquisitionMode`：`DIRECT_HTTP`、`SESSION_HTTP`、`BROWSER`。
- `AcquisitionAttempt`：模式、成功状态、耗时和脱敏后的失败原因。
- `AcquisitionResult`：HTML、最终 URL、最终模式、全部尝试轨迹。
- `TonghuashunPageAcquirer`：执行按需升级并维护单个 Session/Browser 生命周期。

`TonghuashunConstituentProvider` 仍负责 URL 生成、分页、解析、去重和覆盖率。它以页面是否登录重定向、是否含有效成分行作为升级信号。无论最终由哪种模式取得 HTML，都调用现有 `_parse_values`，避免出现三套解析结果。

### 升级流程

```text
同花顺成分页
  -> DIRECT_HTTP
     -> 有有效成分：继续分页
     -> 登录重定向 / 空表 / 请求异常：SESSION_HTTP
        -> 有有效成分：后续页优先沿用 Session
        -> 仍无效：BROWSER
           -> 有有效成分：继续解析
           -> 超时 / 异常 / 仍无效：返回 PARTIAL 或抛出采集失败
  -> 现有服务层：东方财富完整补全
  -> 同花顺最近完整成分快照
  -> 跳过板块；全部板块不可用时本轮失败
```

直连健康时不创建 Scrapling Session 或 Browser。Session 只在第一次恢复时惰性创建，并复用于该采集器后续请求。Browser 同样惰性创建，恢复并发由信号量限制为 1。

## 质量与失败语义

- `expected_count > 0` 且 `retrieved_count / expected_count >= 0.95` 时为 `COMPLETE`；否则有数据为 `PARTIAL`，无数据为 `PARTIAL` 并带明确原因。
- Browser 返回 HTTP 200、页面能渲染或出现部分股票都不能单独判定完整。
- 同一代码跨页重复时只保留一条，不通过重复数量抬高覆盖率。
- 恢复模式只描述采集路径，`source_family` 仍为 `TONGHUASHUN`。
- 东方财富补全仍标记 `EASTMONEY`，不得伪装成同花顺。
- 完整快照保存采集诊断；读取旧快照缺少新字段时使用安全默认值。
- 所有异常信息必须脱敏，不记录 Cookie、响应头、API Key 或完整页面正文。

## 配置与资源治理

`Settings` 增加以下配置，并通过 `FINSCOPE_MARKET_DATA_` 环境前缀覆盖：

- `scrapling_enabled=true`
- `scrapling_session_timeout_seconds=15`
- `scrapling_browser_timeout_seconds=20`
- `scrapling_browser_max_concurrency=1`
- `scrapling_idle_timeout_seconds=300`

允许访问的主机硬编码为 `q.10jqka.com.cn`。不接受调用方传入任意 URL。Browser 禁用图片、字体等非必要资源，并使用上下文管理器或 `close()` 在应用关闭时释放；超时后也必须释放当前失败资源。依赖采用固定版本并延迟导入，使配置关闭或直连成功路径不承担浏览器启动成本。

## 接入点

- `pyproject.toml`：增加固定版本的 Scrapling fetchers 依赖。
- `settings.py`：增加开关、超时和并发配置。
- `discovery/acquisition.py`：新增采集模型、协议适配和生命周期管理。
- `discovery/constituents.py`：注入页面采集器，复用既有解析器并输出诊断。
- `app.py`：根据设置装配采集器，并在 FastAPI 生命周期结束时关闭资源。
- `tests/`：使用固定 HTML 和假采集器覆盖升级顺序、会话复用、超时清理和降级编排。

外部 REST 请求与响应保持不变。第一期诊断先进入成分批次和报告 warnings，不新增前端字段，避免为内部恢复路径扩大跨语言契约。

## 测试与验收

### 自动化测试

- 直连成功时只出现一次 `DIRECT_HTTP` 尝试，Scrapling 不初始化。
- 直连登录重定向后 Session 成功，后续分页沿用 Session。
- 直连和 Session 失败后 Browser 成功，HTML 仍由同一解析器得到相同成分。
- Browser 只取回部分成分时仍为 `PARTIAL`，并触发现有补全或快照路径。
- 三种模式均失败时释放 Browser，并且不影响其他板块处理。
- 并发恢复最多运行一个 Browser 任务。
- 旧版成分快照可读取，新版快照能保存采集模式和尝试轨迹。
- 现有科创板、北交所过滤和行情调用门禁保持不变。

### 验收标准

1. 健康直连场景 Browser 启动次数为 0。
2. 固定登录重定向与空页场景可分别被 Session 或 Browser 恢复。
3. 完整性仍以预期数量或 95% 覆盖率判断，恢复不能放宽质量标准。
4. 浏览器超时不会留下活动资源，东方财富和快照降级仍可继续。
5. 报告告警能定位最终采集模式及每次失败原因，且不包含敏感信息。
6. 市场数据服务全量测试通过，锁文件与依赖声明一致。

## 后续阶段

第一期稳定后，再按实际失败样本决定第二期：优先考虑东方财富分钟资金流的协议诊断与历史快照，其次才是公司公告、投资者互动和研报正文等内容型信源。第二期应复用本期的采集诊断模型，但分别建立数据契约和质量门槛，不把通用浏览器抓取直接接入生产候选池。
