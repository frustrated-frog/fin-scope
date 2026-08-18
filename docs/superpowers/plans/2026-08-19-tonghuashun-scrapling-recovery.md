# Tonghuashun Scrapling Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为同花顺行业成分采集增加有界的 `DIRECT_HTTP -> SESSION_HTTP -> BROWSER` 恢复链，在不降低 95% 完整性门槛的前提下提高股票发现候选池可用性。

**Architecture:** 新建独立采集模块封装 Scrapling 的延迟导入、主机限制、会话复用和资源关闭；成分适配器只负责分页、解析、覆盖率与诊断聚合。股票发现服务继续负责东方财富补全和完整快照，并将顺序统一为在线主源恢复、在线补全、完整快照、拒绝。

**Tech Stack:** Python 3.11-3.13、Scrapling 0.4.14、FastAPI lifespan、pytest、uv

---

## 文件结构

- Create: `market-data-service/src/finscope_market_data/discovery/acquisition.py` — 采集模式、尝试诊断、Scrapling 后端和有界升级策略。
- Modify: `market-data-service/src/finscope_market_data/discovery/constituents.py` — 使用采集器、聚合诊断、按 95% 判断完整并持久化诊断。
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py` — 在线补全优先于快照，并关闭成分采集资源。
- Modify: `market-data-service/src/finscope_market_data/settings.py` — Scrapling 开关、超时、并发和空闲时间配置。
- Modify: `market-data-service/src/finscope_market_data/app.py` — 装配采集器并在 lifespan 结束时关闭。
- Modify: `market-data-service/pyproject.toml`、`market-data-service/uv.lock`、`market-data-service/Dockerfile` — 固定依赖并安装浏览器运行时。
- Modify: `market-data-service/README.md` — 本地安装、配置、恢复顺序和限制说明。
- Create: `market-data-service/tests/test_discovery_acquisition.py` — 升级顺序、会话复用、主机限制、并发和资源关闭契约。
- Modify: `market-data-service/tests/test_discovery_constituents.py` — 同一解析器、95% 门槛、诊断和快照兼容测试。
- Modify: `market-data-service/tests/test_discovery_service.py` — 在线补全与快照顺序、全失败隔离测试。
- Modify: `market-data-service/tests/test_api.py` — 默认装配和 lifespan 关闭测试。

### Task 1: 定义采集升级契约

**Files:**
- Create: `market-data-service/tests/test_discovery_acquisition.py`
- Create: `market-data-service/src/finscope_market_data/discovery/acquisition.py`

- [ ] **Step 1: 写直连成功与 Session 恢复的失败测试**

测试使用记录调用次数的假 `ManagedPageFetcher`，期望 API 为：

```python
result = TonghuashunPageAcquirer(
    direct_loader=direct,
    session_factory=lambda: session,
    browser_factory=lambda: browser,
).fetch(url, assess=_assess)

assert result.accepted is True
assert result.mode is AcquisitionMode.SESSION_HTTP
assert [item.mode for item in result.attempts] == [
    AcquisitionMode.DIRECT_HTTP,
    AcquisitionMode.SESSION_HTTP,
]
```

同时断言直连有效时两个 factory 调用次数均为 0；第一次 Session 恢复后，下一页从 Session 开始且复用同一对象。

- [ ] **Step 2: 运行测试确认按预期失败**

Run: `cd market-data-service && uv run pytest tests/test_discovery_acquisition.py -q`

Expected: FAIL，原因是 `finscope_market_data.discovery.acquisition` 尚不存在。

- [ ] **Step 3: 实现最小采集模型与 Direct/Session 升级**

实现以下公开契约：

```python
class AcquisitionMode(str, Enum):
    DIRECT_HTTP = "DIRECT_HTTP"
    SESSION_HTTP = "SESSION_HTTP"
    BROWSER = "BROWSER"

@dataclass(frozen=True)
class AcquisitionAttempt:
    mode: AcquisitionMode
    succeeded: bool
    duration_ms: int
    error: str = ""

@dataclass(frozen=True)
class AcquisitionResult:
    html: str
    final_url: str
    mode: AcquisitionMode
    accepted: bool
    attempts: tuple[AcquisitionAttempt, ...]
    failure_reason: str = ""
```

`TonghuashunPageAcquirer.fetch(url, assess)` 先校验 `https://q.10jqka.com.cn`，再调用直连；`assess(html, final_url)` 返回空字符串代表页面有效，否则返回可审计失败原因。只有配置启用且当前结果无效时才创建 Session。

- [ ] **Step 4: 运行测试确认转绿**

Run: `cd market-data-service && uv run pytest tests/test_discovery_acquisition.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/discovery/acquisition.py market-data-service/tests/test_discovery_acquisition.py
git commit -m "feat: 增加同花顺分级采集策略"
git push
```

### Task 2: 增加 Browser 恢复与资源治理

**Files:**
- Modify: `market-data-service/tests/test_discovery_acquisition.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/acquisition.py`

- [ ] **Step 1: 写 Browser 升级、并发限制和关闭的失败测试**

覆盖以下独立行为：

```python
assert result.mode is AcquisitionMode.BROWSER
assert browser.max_active == 1
acquirer.close()
assert session.closed is True
assert browser.closed is True
```

另写测试让 Browser 抛出 `TimeoutError`，断言 `AcquisitionResult.accepted` 为 `False`、尝试轨迹含脱敏错误，并且 `close()` 可重复调用。

- [ ] **Step 2: 运行目标测试确认失败原因正确**

Run: `cd market-data-service && uv run pytest tests/test_discovery_acquisition.py -q`

Expected: FAIL，原因是 Browser 未升级或资源未关闭。

- [ ] **Step 3: 实现 Browser 升级、信号量和空闲回收**

使用 `threading.BoundedSemaphore(browser_max_concurrency)` 限制 Browser；Session 与 Browser 各自使用互斥锁。每次请求前若距离上次使用超过 `idle_timeout_seconds`，关闭并重新惰性创建对应会话。`close()` 清除引用并安全调用后端的 `close()`。

Scrapling 适配器延迟导入并遵循官方接口：

```python
from scrapling.fetchers import DynamicSession, FetcherSession

manager = FetcherSession(impersonate="chrome", stealthy_headers=True)
client = manager.__enter__()
page = client.get(url, timeout=timeout_seconds)

manager = DynamicSession(
    headless=True,
    disable_resources=True,
    max_pages=1,
)
client = manager.__enter__()
page = client.fetch(url, timeout=round(timeout_seconds * 1000))
```

统一用 `page.body.decode(page.encoding or "utf-8", errors="replace")` 和 `str(page.url)` 转换结果；关闭时调用对应 manager 的 `__exit__`。

- [ ] **Step 4: 运行采集器测试确认通过**

Run: `cd market-data-service && uv run pytest tests/test_discovery_acquisition.py -q`

Expected: PASS，且无未处理线程或资源警告。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/discovery/acquisition.py market-data-service/tests/test_discovery_acquisition.py
git commit -m "feat: 增加同花顺浏览器恢复治理"
git push
```

### Task 3: 接入成分适配器和质量诊断

**Files:**
- Modify: `market-data-service/tests/test_discovery_constituents.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/constituents.py`

- [ ] **Step 1: 写恢复解析、95% 门槛与诊断快照的失败测试**

测试用固定 HTML 让 Direct 返回登录页、Session 返回成分表，断言：

```python
assert result.quality_status == "COMPLETE"
assert result.coverage == 0.95
assert result.acquisition_mode == "SESSION_HTTP"
assert result.recovery_used is True
assert [item.mode.value for item in result.acquisition_attempts] == [
    "DIRECT_HTTP",
    "SESSION_HTTP",
]
```

再让 Browser 返回与直连夹具相同 HTML，断言 `values` 完全相同；保存后读取新版快照断言诊断不丢失；手工写入无诊断字段的 v1 快照并断言可读取。

- [ ] **Step 2: 运行目标测试确认失败**

Run: `cd market-data-service && uv run pytest tests/test_discovery_constituents.py -q`

Expected: FAIL，原因是适配器未接受 `page_acquirer`、完整性仍要求 100% 或批次缺少诊断字段。

- [ ] **Step 3: 实现适配器接入与诊断持久化**

`ConstituentBatch` 增加带默认值的字段：

```python
acquisition_mode: str = "DIRECT_HTTP"
recovery_used: bool = False
acquisition_attempts: tuple[AcquisitionAttempt, ...] = ()
acquisition_duration_ms: int = 0
```

`TonghuashunConstituentProvider` 可注入 `TonghuashunPageAcquirer`。页面评估优先识别 `/account/login`，其次用 `_parse_values(html)` 判断空表；所有成功模式都调用同一个 `_parse_values`。一旦 Session 或 Browser 恢复，后续页由采集器优先复用该模式。

完整条件改为 `expected > 0 and coverage >= 0.95`。快照把尝试轨迹序列化为字典列表，读取缺失字段时回退为 Direct 默认诊断。

- [ ] **Step 4: 运行成分与既有服务测试**

Run: `cd market-data-service && uv run pytest tests/test_discovery_constituents.py tests/test_discovery_service.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/discovery/constituents.py market-data-service/tests/test_discovery_constituents.py
git commit -m "feat: 接入同花顺成分采集恢复"
git push
```

### Task 4: 校正在线补全、快照与关闭顺序

**Files:**
- Modify: `market-data-service/tests/test_discovery_service.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`

- [ ] **Step 1: 写在线补全优先和关闭隔离的失败测试**

先写入可用同花顺快照，再让同花顺主采集返回 `PARTIAL`、东方财富返回 `COMPLETE`，断言最终来源为 `EASTMONEY` 而不是缓存。另用两个可关闭 Provider，其中一个 `close()` 抛错，断言另一个仍被关闭且错误转换为 warning 或安全忽略。

- [ ] **Step 2: 运行目标测试确认失败**

Run: `cd market-data-service && uv run pytest tests/test_discovery_service.py -q`

Expected: FAIL，当前实现会在尝试东方财富前读取缓存，且没有统一关闭入口。

- [ ] **Step 3: 实现降级顺序与 `close()`**

`_resolve_sector` 顺序调整为：首选同花顺批次、其余在线补全 Provider、完整成分快照、跳过。只有 `COMPLETE` 批次才保存快照。

`StockDiscoveryService.close()` 遍历 `constituent_providers`，对存在且可调用的 `close` 逐一执行；一个 Provider 关闭失败不得阻止后续关闭。

- [ ] **Step 4: 运行服务测试确认通过**

Run: `cd market-data-service && uv run pytest tests/test_discovery_service.py -q`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/src/finscope_market_data/discovery/service.py market-data-service/tests/test_discovery_service.py
git commit -m "fix: 校正同花顺成分降级顺序"
git push
```

### Task 5: 装配配置、依赖与应用生命周期

**Files:**
- Modify: `market-data-service/tests/test_api.py`
- Modify: `market-data-service/src/finscope_market_data/settings.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/pyproject.toml`
- Modify: `market-data-service/uv.lock`
- Modify: `market-data-service/Dockerfile`

- [ ] **Step 1: 写设置边界和 lifespan 关闭的失败测试**

断言默认设置为启用、Session 15 秒、Browser 20 秒、并发 1、空闲 300 秒；非法 0 并发和越界超时由 Pydantic 拒绝。为自定义 Discovery Stub 提供 `close()` 记录器，离开 `TestClient` 后断言已关闭。

- [ ] **Step 2: 运行目标测试确认失败**

Run: `cd market-data-service && uv run pytest tests/test_api.py -q`

Expected: FAIL，原因是设置和关闭行为不存在。

- [ ] **Step 3: 实现设置、装配和生命周期关闭**

`Settings` 增加：

```python
scrapling_enabled: bool = True
scrapling_session_timeout_seconds: float = Field(default=15, ge=1, le=60)
scrapling_browser_timeout_seconds: float = Field(default=20, ge=1, le=60)
scrapling_browser_max_concurrency: int = Field(default=1, ge=1, le=2)
scrapling_idle_timeout_seconds: float = Field(default=300, ge=30, le=3600)
```

`create_app` 用这些参数创建 `TonghuashunPageAcquirer` 并注入 `TonghuashunConstituentProvider`。lifespan 的 `finally` 先关闭 Discovery，再关闭 Router；自定义 Discovery 不提供 `close` 时兼容跳过。

- [ ] **Step 4: 固定依赖并更新运行镜像**

在 dependencies 加入 `scrapling[fetchers]==0.4.14`，执行：

```bash
cd market-data-service
uv lock
uv sync --frozen --extra ecosystem --extra dev
```

Dockerfile 在 `uv sync` 后执行 `uv run scrapling install`，确保 Browser 恢复在容器内可用。

- [ ] **Step 5: 运行 API、采集和锁文件验证**

Run: `cd market-data-service && uv run pytest tests/test_api.py tests/test_discovery_acquisition.py tests/test_discovery_constituents.py -q`

Run: `cd market-data-service && uv lock --check`

Expected: 两条命令均退出 0。

- [ ] **Step 6: 提交并推送**

```bash
git add market-data-service/pyproject.toml market-data-service/uv.lock market-data-service/Dockerfile market-data-service/src/finscope_market_data/settings.py market-data-service/src/finscope_market_data/app.py market-data-service/tests/test_api.py
git commit -m "feat: 装配Scrapling采集运行时"
git push
```

### Task 6: 文档、全量回归与第一期验收

**Files:**
- Modify: `market-data-service/README.md`
- Modify: `docs/superpowers/plans/2026-08-19-tonghuashun-scrapling-recovery.md`

- [ ] **Step 1: 更新运行文档**

README 写明：

- `uv sync --extra ecosystem --extra dev` 后需要 `uv run scrapling install`。
- 五个 `FINSCOPE_MARKET_DATA_SCRAPLING_*` 配置项及默认值。
- 恢复顺序和 Browser 只在前两级失败后启动。
- 不包含登录、验证码、代理和访问控制绕过。
- Browser 依赖缺失时会记录诊断并继续东方财富/快照降级。

- [ ] **Step 2: 执行全量市场数据服务测试**

Run: `cd market-data-service && uv run pytest -q`

Expected: 所有测试 PASS，0 failures。

- [ ] **Step 3: 执行静态和依赖一致性检查**

Run: `git diff --check && cd market-data-service && uv lock --check && uv run python -m compileall -q src tests`

Expected: 退出 0，无空白错误、锁漂移或语法错误。

- [ ] **Step 4: 对照设计逐项自检**

检查以下事实并记录在本计划勾选状态中：直连不初始化 Scrapling、Session/Browser 按序升级、95% 门槛未降低、Browser 并发为 1、资源可关闭、在线补全后才读快照、交易范围过滤测试仍通过、API 契约无变化。

- [ ] **Step 5: 提交并推送**

```bash
git add market-data-service/README.md docs/superpowers/plans/2026-08-19-tonghuashun-scrapling-recovery.md
git commit -m "docs: 补充Scrapling采集运行说明"
git push
```

## 自检结果

- 设计中的 Direct、Session、Browser、补全、快照和拒绝路径分别映射到 Task 1-4。
- 资源并发、空闲回收、超时和应用关闭映射到 Task 2 与 Task 5。
- 95% 完整性、同解析器、诊断快照兼容映射到 Task 3。
- 依赖安装、容器运行和运维说明映射到 Task 5-6。
- 不包含 Java API、内容域抓取、登录、验证码、代理或自动协议修复。
- 类型名和字段名在所有任务中保持一致，无未定义占位符。
