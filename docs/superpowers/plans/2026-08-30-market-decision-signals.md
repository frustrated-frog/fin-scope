# Market Decision Signals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Market Pulse with market buying/selling pressure, breadth momentum/thrust, and ten-session sector rotation trails without duplicating index quotes or stock discovery.

**Architecture:** Python remains the sole calculator for full-market daily-bar internals and Tonghuashun industry histories. Versioned Python contracts carry deterministic numeric facts into strict Java RPC adapters; Java assembles market-language summaries and workspace snapshots; React renders the new decision panels and time trails. Missing amount or history data degrades individual metrics to null/empty values instead of making the whole page unavailable.

**Tech Stack:** Python 3.11, Pydantic, pandas, pytest; Java 21, Spring Boot 2.7, Jackson, JUnit 5; React, TypeScript, Vitest, SVG/CSS.

---

### Task 1: Market pressure and breadth momentum contract

**Files:**
- Modify: `market-data-service/src/finscope_market_data/models.py`
- Modify: `market-data-service/src/finscope_market_data/breadth.py`
- Modify: `market-data-service/tests/test_breadth.py`
- Modify: `market-data-service/tests/test_api.py`
- Modify: `market-data-service/README.md`

- [x] **Step 1: Write failing Python tests**

Add assertions proving that spot and historical daily-bar data calculate advancing amount, declining amount, advancing amount ratio, net advancing amount, TRIN, McClellan-style 19/39 EMA spread, ten-day breadth-thrust ratio, and momentum status. Cover zero declining amount as an explicit null TRIN case.

- [x] **Step 2: Run the focused tests and verify RED**

Run: `cd market-data-service && .venv/bin/pytest tests/test_breadth.py tests/test_api.py -q`

Expected: failures because `market-breadth-v3`, `volume_pressure`, and `breadth_momentum` are not defined.

- [x] **Step 3: Implement the v3 Python contract**

Introduce `MarketVolumePressure` and `MarketBreadthMomentum` Pydantic models, add pressure/momentum fields to each history point, and upgrade `MarketBreadthSnapshot.schema_version` to `market-breadth-v3`. Calculate amount pressure from the same valid stock rows used for breadth counts. Calculate the EMA spread from net advances and the ten-day EMA of `advance / (advance + decline)`; classify the current state as `BULLISH_THRUST`, `RECOVERING`, `NEUTRAL`, or `WEAKENING` using deterministic thresholds.

- [x] **Step 4: Run focused tests and verify GREEN**

Run: `cd market-data-service && .venv/bin/pytest tests/test_breadth.py tests/test_api.py -q`

Expected: all focused tests pass.

- [x] **Step 5: Update the market-data README and commit**

Document v3 fields and formulas without claiming predictive certainty.

Commit: `feat: 增加市场买卖压力与宽度动量`

### Task 2: Strict Java v3 mapping and market summaries

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketVolumePressure.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthMomentum.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthSnapshot.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketInternalHistoryPoint.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSource.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSourceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketBreadthService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketBreadthServiceTest.java`

- [x] **Step 1: Write failing Java contract and summary tests**

Require the RPC adapter to accept only `market-breadth-v3`, map all pressure/momentum fields, reject ratios outside `[0,1]`, reject non-finite values, and preserve nullable TRIN. Require the service interpretation/change summary to mention improving or weakening amount pressure and breadth momentum when present.

- [x] **Step 2: Run focused Maven tests and verify RED**

Run: `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl finscope-rpc,finscope-service -am -Dtest=PythonMarketBreadthSourceTest,MarketBreadthServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation or assertion failures for missing v3 types and fields.

- [x] **Step 3: Implement domain models, strict parser, and summaries**

Use Lombok `@Data` for the two domain DTOs. Extend the existing RPC parser with bounded numeric validation. Keep the business-language interpretation in `MarketBreadthService`; do not put Chinese presentation strings in Python.

- [x] **Step 4: Run focused Maven tests and verify GREEN**

Run the focused Maven command from Step 2.

Expected: all focused Java tests pass.

- [x] **Step 5: Commit**

Commit: `feat: 接入市场压力与动量合同`

### Task 3: Ten-session industry rotation trails

**Files:**
- Modify: `market-data-service/src/finscope_market_data/sector_history.py`
- Modify: `market-data-service/tests/test_sector_history.py`
- Modify: `market-data-service/tests/test_api.py`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/SectorRotationPoint.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/SectorHistoryItem.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/SectorRotationItem.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/PythonSectorHistorySource.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketpulse/PythonSectorHistorySourceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseSectorService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseSectorServiceTest.java`

- [x] **Step 1: Write failing trail tests in Python**

Require `sector-history-v2` entries to contain up to ten ascending trail points. Each point contains `business_date`, cross-sectional 20-day excess return as `relative_strength`, and the five-session change in that excess return as `relative_momentum`. Verify no date after the requested business date is emitted.

- [x] **Step 2: Run Python sector tests and verify RED**

Run: `cd market-data-service && .venv/bin/pytest tests/test_sector_history.py tests/test_api.py -q`

Expected: failures for the missing v2 trail contract.

- [x] **Step 3: Implement deterministic cross-sectional trails**

Retain normalized industry close histories during the concurrent fetch, calculate each date's 20-day return across the available industry universe, subtract the cross-sectional mean, and calculate five-session relative-strength momentum. Return only complete finite points and at most ten dates.

- [x] **Step 4: Write and verify failing Java trail tests**

Require strict v2 parsing, ascending bounded trail dates, finite values, and service propagation into `SectorRotationItem`.

Run: `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl finscope-rpc,finscope-service -am -Dtest=PythonSectorHistorySourceTest,MarketPulseSectorServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation or assertion failures for missing trail types and v2 support.

- [x] **Step 5: Implement Java trail mapping and verify GREEN**

Add a stable domain trail point DTO in `finscope-domain`, parse the v2 contract in RPC, and copy the trail into the sector rotation projection. Keep existing fallback scoring behavior when trails are unavailable.

Run both focused Python and Java commands.

Expected: all focused tests pass.

- [x] **Step 6: Commit**

Commit: `feat: 增加行业相对强度轮动尾迹`

### Task 4: Decision-focused Market Pulse UI

**Files:**
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/SectorOpportunityMap.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/styles.css`

- [x] **Step 1: Write failing React tests**

Require the market-width tab to render `买卖压力`, `宽度动量`, advancing amount share, net advancing amount, TRIN, McClellan value, thrust ratio, and state text. Require rotation mode to render SVG trail paths, a ten-session tail description, and selected-sector direction/speed details without showing stock candidates or index quote cards.

- [x] **Step 2: Run focused frontend tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/market-pulse/MarketPulseView.test.tsx`

Expected: assertions fail because the new panels and trail SVG do not exist.

- [x] **Step 3: Implement responsive panels and SVG trails**

Add typed contract fields, a compact pressure/momentum card, and extend the 60-day internals chart with pressure and oscillator tracks. Replace rotation-mode point-only plotting with paths plus current-point labels, while retaining the heatmap and stock-discovery handoff. Use CSS grid breakpoints and readable typography consistent with the existing Market Pulse visual system.

- [x] **Step 4: Run focused tests and production build**

Run: `cd frontend && npm test -- --run src/features/market-pulse/MarketPulseView.test.tsx && npm run build`

Expected: focused tests and build pass.

- [x] **Step 5: Commit**

Commit: `feat: 构建市场决策增强视图`

### Task 5: Full verification and documentation closure

**Files:**
- Modify: `docs/superpowers/plans/2026-08-30-market-decision-signals.md`

- [x] **Step 1: Run all Python tests**

Run: `cd market-data-service && .venv/bin/pytest -q`

- [x] **Step 2: Run all backend tests with JDK 21**

Run: `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test`

- [x] **Step 3: Run all frontend tests and build**

Run: `cd frontend && npm test -- --run && npm run build`

- [x] **Step 4: Inspect the real local API sample**

Start or call the local Python service and confirm that a real available business date returns pressure, momentum, and ten-point industry trails without future dates. Record only counts and non-sensitive metrics.

- [x] **Step 5: Perform project-rule self-review**

Check field injection, Java braces, DTO placement, dependency direction, changed-file scope, secret handling, and `git diff --check`.

- [x] **Step 6: Mark this plan complete, commit, and push**

Commit: `docs: 完成市场决策增强验证`

Push the current branch after every independently verified commit and once more after the final verification commit.

## Verification record

- Python: 231 tests passed; one existing Starlette deprecation warning.
- Java 21: all seven Maven reactor modules succeeded; final web module reported 166 tests, zero failures, zero errors, one skipped.
- Frontend: 84 test files and 447 tests passed; the production build succeeded with the existing chunk-size advisory.
- Real local breadth sample for 2026-08-28: `market-breadth-v3`, 374 valid symbols, 60 history points, pressure/TRIN/McClellan/thrust values populated.
- Real Tonghuashun sample for 2026-08-28: two industries returned ten-point trails ending on the requested date. The provider's thread-unsafe first initialization was reproduced, regression-tested, and fixed before closure.
- Project review: field injection retained, Java control-flow braces expanded, DTOs kept in `finscope-domain`, RPC access kept in `finscope-rpc`, no API keys or configuration secrets changed, and `git diff --check` passed.
