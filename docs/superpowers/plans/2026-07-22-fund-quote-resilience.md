# Fund Quote Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复基金盘中估值的双主机故障切换，并重排自选基金卡片的信息层级。

**Architecture:** 将当前基金适配器改为可配置端点的 `FundValuationLast` 批量 Provider，注册主、备两个估值适配器和一个确认净值兜底适配器，由现有 MarketDataGateway 完成对冲与按代码补齐。前端基金卡片改为确认净值主行加盘中估值辅助行，避免窄卡片中的日期换行。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、JUnit 5、React、TypeScript、Vitest、Testing Library、CSS Grid/Flexbox。

---

### Task 1: FundValuationLast 主适配器

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundQuoteAdapter.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quote/FundQuoteAdapterTest.java`

- [ ] **Step 1: 写批量接口解析失败测试**

构造包含 `data` 数组的响应，断言 021894 的 `confirmedNav=2.6222`、`confirmedNavChangePct=14.6`、`price=2.6322`、`changePct=0.38`，并断言一次请求包含两个基金代码。

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn -pl finscope-rpc -am -Dtest=FundQuoteAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，因为现有适配器仍请求单基金 `fundgz` JSONP。

- [ ] **Step 3: 实现批量 JSON 解析**

将 `fetch` 改为一次请求：

```java
String url = endpoint + "?FCODES=" + encode(String.join(",", codes))
        + "&FIELDS=" + FIELDS;
JsonNode root = objectMapper.readTree(requester.get(url));
if (!root.path("success").asBoolean(false) || !root.path("data").isArray()) {
    throw new IOException("FundValuationLast returned an invalid payload");
}
```

逐条映射 `FCODE/SHORTNAME/NAV/NAVCHGRT/PDATE/GSZ/GSZZL/GZTIME`，估值字段可以为空，但确认净值必须为正数。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn -pl finscope-rpc -am -Dtest=FundQuoteAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 注册备用基金估值 Provider

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundQuoteBackupAdapter.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundNavHistoryAdapter.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundDataRequester.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundQuoteAdapter.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketdata/MarketDataProviderContractTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayQuoteTest.java`

- [ ] **Step 1: 写 Provider 身份与故障切换测试**

断言主源编码为 `EASTMONEY_FUND_VALUATION`、优先级 10；备用源编码为 `EASTMONEY_FUND_VALUATION_BACKUP`、优先级 20；确认净值源编码为 `EASTMONEY_FUND_CONFIRMED_NAV`、优先级 30。网关测试让主源抛错、备用源返回完整 Quote，断言结果为 `FRESH_FALLBACK` 且 sourceCode 为备用编码。

- [ ] **Step 2: 运行测试确认 RED**

Run: `mvn -pl finscope-rpc,finscope-service -am -Dtest=MarketDataProviderContractTest,MarketDataGatewayQuoteTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，因为备用适配器尚不存在。

- [ ] **Step 3: 实现可配置基类与备用组件**

主适配器暴露受保护构造器：

```java
protected FundQuoteAdapter(String endpoint, String providerCode, int priority) {
    this.endpoint = endpoint;
    this.providerCode = providerCode;
    this.priority = priority;
    this.requester = this::request;
}
```

备用组件使用 `fundcomapi.eastmoney.com`，并覆写独立 Provider 编码与更低优先级。确认净值组件独立解析 `pingzhongdata`，避免主估值失败时过早终止网关的热备切换。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `mvn -pl finscope-rpc,finscope-service -am -Dtest=MarketDataProviderContractTest,MarketDataGatewayQuoteTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 3: 基金卡片信息层级

**Files:**
- Modify: `frontend/src/features/watchlist/WatchlistView.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/watchlist/WatchlistView.test.tsx`

- [ ] **Step 1: 写布局语义测试**

断言确认日期视觉文本为 `07-21` 且 `time[datetime="2026-07-21"]` 存在；断言估值可用时显示 `盘中估值`、`2.6322`、`+0.38%` 和 `10:33`，不可用时只出现一个 `--`。

- [ ] **Step 2: 运行测试确认 RED**

Run: `npm test -- --run src/features/watchlist/WatchlistView.test.tsx`

Expected: FAIL，因为当前完整日期嵌在标签中且估值仍为双列。

- [ ] **Step 3: 实现纵向布局**

使用 `.fund-nav-block`、`.fund-nav-label-row`、`.fund-nav-main-row`、`.fund-estimate-label-row` 和 `.fund-estimate-values`，日期显示为 `MM-DD` 并设置 `white-space: nowrap`。盘中时间位于辅助标签行，估值和涨跌位于下一行，使 200px 级卡片仍能完整展示。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `npm test -- --run src/features/watchlist/WatchlistView.test.tsx`

Expected: PASS。

### Task 4: 完整验证与提交

**Files:**
- Verify all changed files.

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn test`

Expected: BUILD SUCCESS，0 failures/errors。

- [ ] **Step 2: 运行前端全量测试与构建**

Run: `npm test -- --run && npm run build`

Expected: 全部测试通过且 Vite build 成功。

- [ ] **Step 3: 运行真实接口与故障切换验收**

启动打包后的临时后端，检查 021894 同时包含确认净值和盘中估值；通过测试 Requester 模拟主域失败，确认备用 Provider 结果为 `FRESH_FALLBACK`。

- [ ] **Step 4: 检查变更并提交**

Run: `git diff --check && git status --short`

Commit message: `fix: 恢复基金估值高可用并优化卡片布局`
