# Fund Holdings Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fund-card detail drawer that combines the latest disclosed top-ten direct stock holdings with fresh batch stock quotes and disclosure-weighted intraday contribution estimates.

**Architecture:** A new RPC provider parses the fixed Eastmoney F10 holdings endpoint into domain-only disclosure objects. A focused service validates the watchlist fund, batch-fetches all stock quotes through the existing `MarketDataGateway`, applies freshness rules, and exposes one aggregate REST contract consumed by a dedicated responsive React drawer.

**Tech Stack:** Java 8, Spring Boot 2.7, Jsoup, JUnit 5, Mockito, React, TypeScript, Vitest, Testing Library, CSS.

---

## File map

- Create `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/FundHoldingDisclosure.java`: immutable disclosure header and holding collection.
- Create `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/FundStockHolding.java`: one disclosed stock holding.
- Create `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundHoldingProvider.java`: external fund-holding capability boundary.
- Create `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/EastmoneyFundHoldingProvider.java`: HTTP request, payload decoding, and Jsoup parsing.
- Create `backend/finscope-rpc/src/test/java/com/finscope/rpc/quote/EastmoneyFundHoldingProviderTest.java`: strict parser and request tests.
- Create `backend/finscope-rpc/src/test/resources/quote/eastmoney-fund-holdings.txt`: deterministic F10 response fixture.
- Create `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingDetail.java`: aggregate service result.
- Create `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingPositionView.java`: merged holding/quote row.
- Create `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingDetailService.java`: validation, batch quote lookup, freshness gate, and contribution calculation.
- Create `backend/finscope-service/src/test/java/com/finscope/service/instrument/FundHoldingDetailServiceTest.java`: calculation and degradation tests.
- Create `backend/finscope-web/src/main/java/com/finscope/web/response/FundHoldingPositionResponse.java`: row-level REST response.
- Create `backend/finscope-web/src/main/java/com/finscope/web/response/FundHoldingDetailResponse.java`: drawer REST response and mapping.
- Modify `backend/finscope-web/src/main/java/com/finscope/web/controller/WatchlistController.java`: add the fund holdings GET endpoint.
- Modify `backend/finscope-web/src/test/java/com/finscope/web/controller/WatchlistControllerTest.java`: endpoint contract coverage.
- Modify `frontend/src/shared/types/index.ts`: fund holding detail types.
- Create `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.tsx`: loading, refresh, dialog accessibility, summary, and holdings presentation.
- Create `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx`: drawer state and interaction tests.
- Modify `frontend/src/features/watchlist/WatchlistView.tsx`: route fund-card clicks to the new drawer and add keyboard semantics.
- Modify `frontend/src/features/watchlist/WatchlistView.test.tsx`: stock/fund click routing and control click regression tests.
- Modify `frontend/src/styles.css`: desktop table, mobile cards, contribution typography, and dialog states.
- Create `frontend/src/features/watchlist/WatchlistFundHoldingsResponsive.test.ts`: CSS regression checks for narrow layouts.

### Task 1: Parse the latest disclosed fund stock holdings

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/FundHoldingDisclosure.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/FundStockHolding.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/FundHoldingProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/EastmoneyFundHoldingProvider.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quote/EastmoneyFundHoldingProviderTest.java`
- Test fixture: `backend/finscope-rpc/src/test/resources/quote/eastmoney-fund-holdings.txt`

- [ ] **Step 1: Add a representative fixed response fixture**

Store a shortened `var apidata={ content:"..." }` payload containing the fund title, `截止至：2026-06-30`, two valid holding rows, and `arryear`. Keep the external script inert as plain test text.

- [ ] **Step 2: Write failing provider tests**

Cover one successful parse and strict failures:

```java
@Test
void parsesLatestDisclosureAndHoldingNumbers() throws Exception {
    EastmoneyFundHoldingProvider provider = providerReturning(fixture());

    FundHoldingDisclosure disclosure = provider.fetch("021894");

    assertEquals("021894", disclosure.getFundCode());
    assertEquals(LocalDate.of(2026, 6, 30), disclosure.getDisclosureDate());
    assertEquals(2, disclosure.getHoldings().size());
    assertEquals("688012", disclosure.getHoldings().get(0).getStockCode());
    assertEquals(0.32d, disclosure.getHoldings().get(0).getWeightPct(), 0.000001d);
}

@Test
void rejectsPayloadWithoutDisclosureDate() {
    EastmoneyFundHoldingProvider provider = providerReturning("var apidata={content:\"<table></table>\"};");
    assertThrows(ProviderContractException.class, () -> provider.fetch("021894"));
}
```

Also verify a non-six-digit code is rejected before HTTP and an empty but structurally valid `tbody` returns an empty disclosure rather than invented rows.

- [ ] **Step 3: Run the provider test and verify failure**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=EastmoneyFundHoldingProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the provider and domain types do not exist.

- [ ] **Step 4: Implement focused domain types and the provider interface**

Use constructors that defensively copy holdings and getters only. The boundary is:

```java
public interface FundHoldingProvider {
    FundHoldingDisclosure fetch(String fundCode);
}
```

`FundStockHolding` fields are `rank`, `stockCode`, `stockName`, `weightPct`, `sharesTenThousand`, and `marketValueTenThousand`. Optional numeric cells use `Double`, never magic zero.

- [ ] **Step 5: Implement strict Eastmoney parsing**

Build requests only against the constant HTTPS host, encode the validated fund code, use `QuoteHttpTransport`, a 2500 ms timeout, a 2 MiB body limit, and the existing Eastmoney Referer. Decode the JavaScript string with Jackson rather than manual backslash replacement, then parse the embedded HTML with Jsoup.

Select the `.boxitem h4.t` header and `table.tzxq tbody tr`. Parse columns 1, 2, 6, 7, and 8 (zero-based after confirming the fixed fixture), strip `%` and thousands separators, and reject non-finite or negative weights. Throw `ProviderContractException` when the response contract is missing; allow an explicitly present empty table.

- [ ] **Step 6: Run provider tests**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=EastmoneyFundHoldingProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 7: Commit and push the RPC batch**

```bash
git add backend/finscope-domain backend/finscope-rpc
git commit -m "feat: 增加基金披露持仓适配器"
git push
```

### Task 2: Aggregate fresh stock quotes and contribution estimates

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingDetail.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingPositionView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/instrument/FundHoldingDetailService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/instrument/FundHoldingDetailServiceTest.java`

- [ ] **Step 1: Write failing aggregation tests**

Construct a disclosure with weights `8.0` and `4.0`, then return fresh stock changes `+2.0` and `-1.0`. Assert contributions `+0.16`, `-0.04`, total `+0.12`, coverage `2/2`, and one `fetchQuotes("STOCK", Arrays.asList(...), true)` call.

Add focused cases:

```java
@Test
void excludesStaleAndInvalidQuotesFromContribution() {
    // first row FRESH_PRIMARY, second row STALE_FALLBACK
    FundHoldingDetail result = service.load("021894", true);
    assertNotNull(result.getPositions().get(0).getEstimatedContributionPct());
    assertNull(result.getPositions().get(1).getEstimatedContributionPct());
    assertEquals(1, result.getEstimatedHoldingCount());
}
```

Also cover an empty disclosure (no quote gateway call), duplicate stock codes (one quote request code), ETF-link warning, and rejecting a code that is not a `FUND` watchlist item.

- [ ] **Step 2: Run the service test and verify failure**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=FundHoldingDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the aggregate service types do not exist.

- [ ] **Step 3: Implement aggregate result types**

`FundHoldingPositionView` carries the disclosure fields plus `latestPrice`, `changePct`, `estimatedContributionPct`, `quoteValid`, `quoteTime`, `qualityStatus`, and `quoteNote`. `FundHoldingDetail` carries the header, coverage counts, sums, `QuoteGatewayResult` quality metadata, `lookThrough=false`, note, and positions.

- [ ] **Step 4: Implement the service and freshness gate**

Use an explicit predicate:

```java
private boolean contributionEligible(Quote quote, QuoteGatewayResult batch) {
    return quote != null
            && quote.isValid()
            && finite(quote.getChangePct())
            && fresh(quote.getQualityStatus())
            && fresh(batch.getQualityStatus());
}

private Double contribution(double weightPct, double changePct) {
    return BigDecimal.valueOf(weightPct)
            .multiply(BigDecimal.valueOf(changePct))
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            .doubleValue();
}
```

Only `FRESH_PRIMARY`, `FRESH_FALLBACK`, and `PARTIAL_FRESH` are fresh. Preserve disclosure order and sum only non-null contributions. The summary total is `null` when coverage is zero.

- [ ] **Step 5: Run service tests**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=FundHoldingDetailServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 6: Commit and push the service batch**

```bash
git add backend/finscope-service
git commit -m "feat: 聚合基金持仓实时贡献"
git push
```

### Task 3: Expose a single fund detail REST contract

**Files:**
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/FundHoldingPositionResponse.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/FundHoldingDetailResponse.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/WatchlistController.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/WatchlistControllerTest.java`

- [ ] **Step 1: Write the failing controller contract test**

Mock `FundHoldingDetailService.load("021894", true)` and assert:

```java
mockMvc.perform(get("/api/watchlist/021894/fund-holdings")
        .param("refresh", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fundCode").value("021894"))
        .andExpect(jsonPath("$.data.disclosureDate").value("2026-06-30"))
        .andExpect(jsonPath("$.data.estimatedHoldingCount").value(2))
        .andExpect(jsonPath("$.data.holdings[0].estimatedContributionPct").value(0.16));
```

Add an assertion that a missing contribution serializes as JSON `null`, not zero.

- [ ] **Step 2: Run the controller test and verify failure**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=WatchlistControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL with 404 for the new route.

- [ ] **Step 3: Implement response mapping and endpoint**

Add the controller method:

```java
@GetMapping("/{code}/fund-holdings")
public ApiResponse<FundHoldingDetailResponse> fundHoldings(
        @PathVariable String code,
        @RequestParam(defaultValue = "true") boolean refresh) {
    return ApiResponses.success(FundHoldingDetailResponse.of(
            fundHoldingDetailService.load(code, refresh)));
}
```

Expose all contract fields from the design. Keep API mapping in response classes rather than returning service objects directly.

- [ ] **Step 4: Run controller and backend focused tests**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=WatchlistControllerTest,FundHoldingDetailServiceTest,EastmoneyFundHoldingProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 5: Commit and push the web contract batch**

```bash
git add backend/finscope-web
git commit -m "feat: 提供基金持仓详情接口"
git push
```

### Task 4: Build the accessible fund holdings drawer

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Create: `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.tsx`
- Test: `frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx`

- [ ] **Step 1: Write failing drawer tests**

Test initial request, summary rendering, partial quote rendering, manual refresh, failure retry, Escape close, backdrop close, focus restoration, and scroll locking. The main success assertion should include:

```tsx
expect(api).toHaveBeenCalledWith('/api/watchlist/021894/fund-holdings?refresh=true');
expect(await screen.findByText('最近披露 2026-06-30')).toBeInTheDocument();
expect(screen.getByText('中微公司')).toBeInTheDocument();
expect(screen.getByText('+0.004 个百分点')).toBeInTheDocument();
expect(screen.getByText('10 / 10')).toBeInTheDocument();
```

For a stale row, assert `--` contribution and visible `旧行情不参与估算` text.

- [ ] **Step 2: Run the drawer test and verify failure**

Run: `cd frontend && npm test -- --run src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx`

Expected: FAIL because the component and types do not exist.

- [ ] **Step 3: Add TypeScript contracts**

Define `FundHoldingPosition` and `FundHoldingDetail` with nullable quote and contribution fields. Reuse the existing `MarketDataQuality` type for top-level and row-level quality fields where possible.

- [ ] **Step 4: Implement the drawer state machine**

Use `loading`, `data`, and `error` state. Fetch on mount with `refresh=true`; use an `AbortController` or an active-request token so a closed or switched fund cannot set stale state. Keep header and close button rendered in all states.

Reuse the proven K-line overlay positioning and accessibility mechanics, but keep component-specific content and CSS classes. The dialog must implement `role="dialog"`, `aria-modal="true"`, labelled title, Escape close, initial close-button focus, focus return, backdrop-only close, and `watchlist-kline-open` scroll lock or a renamed shared dialog lock class.

- [ ] **Step 5: Render summary and holdings without misleading zeros**

Render summary cards for disclosed weight, coverage, and total contribution. When total contribution is `null`, show `--`. Each row renders stock identity and disclosure weight regardless of quote availability; only fresh quotes render latest price, change, and contribution.

- [ ] **Step 6: Run drawer tests**

Run: `cd frontend && npm test -- --run src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx`

Expected: PASS.

- [ ] **Step 7: Commit and push the drawer batch**

```bash
git add frontend/src/shared/types/index.ts frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.tsx frontend/src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx
git commit -m "feat: 增加基金持仓详情弹层"
git push
```

### Task 5: Integrate card routing and polish responsive UI

**Files:**
- Modify: `frontend/src/features/watchlist/WatchlistView.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistView.test.tsx`
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/features/watchlist/WatchlistFundHoldingsResponsive.test.ts`

- [ ] **Step 1: Write failing card routing tests**

Add one fund item and one stock item. Assert stock click requests daily bars while fund click requests fund holdings. Also click remove, attribution, and group controls and assert neither drawer opens accidentally. Add keyboard tests for Enter and Space on the card.

- [ ] **Step 2: Write failing responsive CSS tests**

Assert the desktop table exists above 700 px and mobile holding cards replace table layout below 700 px. Verify there is no required horizontal scrolling and numeric columns use tabular numbers.

- [ ] **Step 3: Run integration tests and verify failure**

Run: `cd frontend && npm test -- --run src/features/watchlist/WatchlistView.test.tsx src/features/watchlist/WatchlistFundHoldingsResponsive.test.ts`

Expected: FAIL because fund cards are not interactive and styles do not exist.

- [ ] **Step 4: Route cards by instrument type**

Replace the stock-only click branch with one semantic opener:

```tsx
function openInstrumentDetail(item: WatchlistItem) {
  if (item.type === 'STOCK') setKlineItem(item);
  if (item.type === 'FUND') setFundHoldingItem(item);
}
```

Ignore events from `button, select, a, input, label`. Add `tabIndex={0}`, a descriptive `aria-label`, and Enter/Space handling to actionable stock/fund cards. Show `点击看持仓` for funds and retain `点击看K线` for stocks.

- [ ] **Step 5: Add restrained desktop and mobile styling**

Follow the existing panel palette, spacing, shadows, red-up/green-down classes, and dialog geometry. Use a low-emphasis disclosure banner, three compact summary cards, a table with sticky identity cues on desktop, and grid-based cards on mobile. Use `font-variant-numeric: tabular-nums` for prices, percentages, and contributions. Do not introduce a new color system or unrelated animation.

- [ ] **Step 6: Run all focused frontend tests**

Run: `cd frontend && npm test -- --run src/features/watchlist/WatchlistView.test.tsx src/features/watchlist/WatchlistFundHoldingsDrawer.test.tsx src/features/watchlist/WatchlistFundHoldingsResponsive.test.ts src/features/watchlist/WatchlistKlineDrawer.test.tsx`

Expected: PASS.

- [ ] **Step 7: Commit and push the integration batch**

```bash
git add frontend/src/features/watchlist frontend/src/styles.css
git commit -m "feat: 串联基金卡片持仓交互"
git push
```

### Task 6: Full verification and final cleanup

**Files:**
- Modify only files already in scope when verification exposes a defect.

- [ ] **Step 1: Run formatting and diff checks**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intentional files are listed.

- [ ] **Step 2: Run all backend tests**

Run: `cd backend && mvn test`

Expected: `BUILD SUCCESS` with all modules passing.

- [ ] **Step 3: Run all frontend tests**

Run: `cd frontend && npm test -- --run`

Expected: all Vitest suites pass.

- [ ] **Step 4: Run the production build**

Run: `cd frontend && npm run build`

Expected: TypeScript and Vite build complete successfully.

- [ ] **Step 5: Perform a live read-only endpoint smoke check when services are available**

Start the existing services only if ports are free, request `/api/watchlist/021894/fund-holdings?refresh=true`, and verify disclosure date, holdings, fresh quote time, and non-stale contribution rules. Do not log API keys or entire upstream payloads.

- [ ] **Step 6: Review scope and remove dead code**

Confirm no unused imports, duplicate contribution formula, direct frontend external calls, ETF look-through claim, or unrelated refactor remains.

- [ ] **Step 7: Commit and push verification fixes if any**

```bash
git add <only-files-corrected-during-verification>
git commit -m "fix: 修正基金持仓详情验收问题"
git push
```

If verification required no code changes, do not create an empty commit.
