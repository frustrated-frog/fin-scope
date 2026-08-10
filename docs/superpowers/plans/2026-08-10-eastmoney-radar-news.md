# 东方财富雷达新闻源 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task.

**Goal:** 将东方财富全市场 7×24 快讯接入现有研究资料网关，并确保其信号进入雷达热点聚合。

**Architecture:** 在 `finscope-rpc` 新增一个 Spring 自动注册的 `NEWS_FLASH` Provider，负责请求东方财富 JavaScript 包装接口并转换为统一 `ResearchMaterial`。继续复用 `ResearchMaterialGateway` 的多来源刷新与快照降级，以及 `RadarHotspotProductionPipeline` 的信号捕获、跨来源聚类、评分和持久化，不增加雷达侧特殊分支。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、JUnit 5、Mockito、Maven

---

### Task 1: 锁定东方财富 Provider 契约

**Files:**
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/research/material/EastmoneyNewsResearchMaterialProviderTest.java`

**Step 1: Write the failing tests**

覆盖以下行为：

- `var ajaxResult={...}` 包装可以解析。
- 请求地址使用全市场 7×24 快讯、限制不超过 50，并携带正确 `Referer`。
- `id/title/digest/showtime/url_unique` 映射为统一材料字段，HTTP 链接升级为 HTTPS。
- 查询词不匹配的记录被过滤。
- `newsid/simtitle/simdigest/ordertime/url_w` 可作为回退字段。
- 无效时间只让该条材料的 `publishedAt` 为空，不阻断整批。
- 缺少 `LivesList` 时抛出 `INVALID_RESPONSE`。

**Step 2: Run test to verify it fails**

Run: `cd backend && mvn -pl finscope-rpc -Dtest=EastmoneyNewsResearchMaterialProviderTest test`

Expected: FAIL because `EastmoneyNewsResearchMaterialProvider` does not exist.

### Task 2: 实现东方财富全市场 7×24 快讯 Provider

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/EastmoneyNewsResearchMaterialProvider.java`

**Step 1: Write minimal implementation**

实现 `ResearchMaterialProvider`，契约如下：

- `providerCode()` 返回 `EASTMONEY_NEWS_FLASH`。
- `providerFamily()` 返回 `EASTMONEY`。
- `reliabilityFamily()` 返回 `EASTMONEY_NEWS_FLASH`，避免与东方财富行情接口共享熔断状态。
- `materialTypes()` 仅包含 `NEWS_FLASH`，批量上限 50、最小间隔 1 秒、超时 10 秒、来源等级 `T2`。
- 请求 `https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_{limit}_1_.html`，携带网页端 Referer。
- 只提取 JavaScript 中最外层 JSON 对象，不执行脚本。
- 缺少标题或稳定 ID 时跳过单条；缺少摘要时使用标题。
- 解析错误统一转换为不可重试的 `ProviderContractException(INVALID_RESPONSE)`，保留已有采集异常语义。

**Step 2: Run provider tests**

Run: `cd backend && mvn -pl finscope-rpc -Dtest=EastmoneyNewsResearchMaterialProviderTest test`

Expected: PASS.

**Step 3: Run related RPC regression tests**

Run: `cd backend && mvn -pl finscope-rpc -Dtest=EastmoneyNewsResearchMaterialProviderTest,ClsNewsResearchMaterialProviderTest,ThsNewsResearchMaterialProviderTest test`

Expected: PASS.

**Step 4: Commit and push provider batch**

```bash
git add backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/EastmoneyNewsResearchMaterialProvider.java \
  backend/finscope-rpc/src/test/java/com/finscope/rpc/research/material/EastmoneyNewsResearchMaterialProviderTest.java
git commit -m "feat: 接入东方财富全市场快讯"
git push github main
```

### Task 3: 证明东方财富信号进入雷达跨来源聚合

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`

**Step 1: Write the failing aggregation assertion**

将生产流水线测试中的一个来源替换为 `EASTMONEY_NEWS_FLASH`，保留另一个现有来源；捕获 `replaceEventSignals` 的关联列表并断言：

- 两条同事件信号只生成一个雷达事件。
- 事件关联中同时存在现有来源和 `EASTMONEY_NEWS_FLASH`。
- 事件的独立来源数量为 2。

**Step 2: Run radar production test**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarHotspotProductionPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS if the existing generic aggregation path correctly accepts the new provider code; otherwise make the smallest production correction and rerun.

**Step 3: Commit and push aggregation coverage**

```bash
git add backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java
git commit -m "test: 覆盖东方财富雷达热点聚合"
git push github main
```

### Task 4: 全量验证与收尾

**Files:**
- Verify only; do not stage or modify the existing `StrategyController.java` change.

**Step 1: Run backend tests**

Run: `cd backend && mvn test`

Expected: PASS.

**Step 2: Inspect final diff and repository status**

Run: `git status --short --branch && git log -3 --oneline && git diff --check`

Expected: only the user's pre-existing `StrategyController.java` remains unstaged; all task commits are pushed to `github/main`.

