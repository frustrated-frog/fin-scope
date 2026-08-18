# Tonghuashun Sector Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将自选页板块目录、每日行业热榜、搜索和关注统一切换为同花顺代码与数据，并清理不再兼容的东方财富 BK 板块关注。

**Architecture:** Python market-data-service 负责读取同花顺行业资金榜和行业/概念完整目录，通过版本化 HTTP 契约提供给 Java。Java RPC 适配器作为唯一板块目录 Provider，继续复用现有 MarketDataGateway 的缓存、审计和旧快照兜底；WatchlistService 直接从同花顺板块快照组装关注卡片，不再请求东方财富板块 QuoteAdapter。股票和基金行情链路保持不变。

**Tech Stack:** Python 3.11+、FastAPI、AkShare、Scrapling、Java 21、Spring Boot 2.7、SQLite、React、TypeScript、Vitest。

---

### Task 1: Python 同花顺板块目录与每日资金榜契约

**Files:**
- Create: `market-data-service/src/finscope_market_data/sectors.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Test: `market-data-service/tests/test_sectors.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] **Step 1: Write the failing provider tests**

Create injected fake industry-name, industry-summary and concept-name loaders. Assert that `TonghuashunSectorService.fetch("INDUSTRY")` merges `881xxx` codes with `source_rank`, `main_net_inflow`, `change_pct` and leader name, while `fetch("CONCEPT")` returns all `30xxxx` directory entries without inventing a money-flow rank.

- [ ] **Step 2: Run tests to verify RED**

Run: `cd market-data-service && .venv/bin/pytest tests/test_sectors.py -q`

Expected: FAIL because `finscope_market_data.sectors` does not exist.

- [ ] **Step 3: Implement the minimal service and response models**

Define strict Pydantic models with these fields:

```python
class SectorEntry(BaseModel):
    code: str
    name: str
    category: Literal["INDUSTRY", "CONCEPT"]
    source_rank: int | None = None
    change_pct: float | None = None
    main_net_inflow: float | None = None
    leader_stock_name: str | None = None

class SectorEnvelope(BaseModel):
    schema_version: Literal["sector-market-v1"] = "sector-market-v1"
    source_code: Literal["AKSHARE_TONGHUASHUN_SECTOR"] = "AKSHARE_TONGHUASHUN_SECTOR"
    source_family: Literal["TONGHUASHUN"] = "TONGHUASHUN"
    category: Literal["INDUSTRY", "CONCEPT"]
    retrieved_at: str
    entries: list[SectorEntry]
    warnings: list[str] = []
```

Use `stock_board_industry_name_ths`, `stock_board_industry_summary_ths` and `stock_board_concept_name_ths`. Convert 净流入 from 亿元 to yuan. Industry rows are ranked by net inflow descending; concepts are directory-only.

- [ ] **Step 4: Add and test the versioned API endpoint**

Add `GET /v1/sectors/{category}` with `category` restricted to `INDUSTRY|CONCEPT`. Inject the service through `create_app(..., sectors=...)` so API tests do not access the network.

Run: `cd market-data-service && .venv/bin/pytest tests/test_sectors.py tests/test_api.py -q`

Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add market-data-service/src/finscope_market_data/sectors.py market-data-service/src/finscope_market_data/app.py market-data-service/tests/test_sectors.py market-data-service/tests/test_api.py
git commit -m "feat: 增加同花顺板块目录与每日资金榜"
git push
```

### Task 2: Java 仅接入同花顺板块 Provider

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/SectorMarketEntry.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/PythonTonghuashunSectorMarketProvider.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quote/PythonTonghuashunSectorMarketProviderTest.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/EastmoneySectorMarketProvider.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/SinaSectorMarketProvider.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/EastmoneySectorQuoteAdapter.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing RPC contract test**

Return a fixture containing an industry `881121` with `source_rank=1` and `main_net_inflow=1200000000`. Assert that the Provider calls `/v1/sectors/INDUSTRY`, rejects a non-TONGHUASHUN source family, maps the new fields, and declares `SECTOR_CATALOG` with highest priority.

- [ ] **Step 2: Run tests to verify RED**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonTonghuashunSectorMarketProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the Provider class and domain fields do not exist.

- [ ] **Step 3: Implement the Provider and disable legacy sector sources**

Add `sourceRank` and `mainNetInflow` to `SectorMarketEntry`. Parse only schema `sector-market-v1`, source family `TONGHUASHUN`, matching category and valid six-digit codes. Add typed application settings that disable Eastmoney/Sina sector catalog and Eastmoney sector quote beans; do not alter stock/fund providers.

- [ ] **Step 4: Run RPC tests**

Run: `cd backend && mvn -pl finscope-rpc -am test`

Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-domain backend/finscope-rpc backend/finscope-web/src/main/resources/application.yml
git commit -m "feat: 将板块数据源切换为同花顺"
git push
```

### Task 3: 同花顺代码关注与旧 BK 关注清理

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/instrument/SectorMarketService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/instrument/WatchlistService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/instrument/SectorMarketServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/instrument/WatchlistServiceTest.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/instrument/TonghuashunSectorCutoverMigrator.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/instrument/TonghuashunSectorCutoverMigratorTest.java`

- [ ] **Step 1: Write failing service tests**

Assert that industry leaders and laggards are ordered by `mainNetInflow`, search returns six-digit THS codes, `followSector("881121")` resolves and persists the catalog name, and followed-sector cards use the catalog snapshot rather than `QuoteService`.

- [ ] **Step 2: Verify service RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SectorMarketServiceTest,WatchlistServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL on BK-only validation and change-percent ranking.

- [ ] **Step 3: Implement THS ranking, lookup and follow quote assembly**

Accept exactly six digits for `SECTOR`; keep type separation from stock. Add `SectorMarketService.findByCode` across industry/concept snapshots. Resolve sector name from that lookup when following. For sector cards, synthesize `Quote` values from the matching THS entry with source `AKSHARE_TONGHUASHUN_SECTOR`; keep the ordinary stock/fund `QuoteService` path unchanged.

- [ ] **Step 4: Write failing migration test and implement one-time cleanup**

Create migration version `400` named `tonghuashun sector identity cutover`. In one transaction, delete `watchlist_item` rows whose instrument is `SECTOR` and code starts with `BK`, then delete those orphan sector instruments. Assert idempotence and that stock rows are retained.

- [ ] **Step 5: Run service and DAO tests**

Run: `cd backend && mvn -pl finscope-service,finscope-dao -am test`

Expected: PASS.

- [ ] **Step 6: Commit and push**

```bash
git add backend/finscope-service backend/finscope-dao
git commit -m "feat: 统一同花顺板块关注身份"
git push
```

### Task 4: 自选页呈现同花顺行业资金热榜

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/SectorMarketEntryResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/SectorMarketControllerTest.java`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/watchlist/SectorMarketPanel.tsx`
- Modify: `frontend/src/features/watchlist/WatchlistView.test.tsx`

- [ ] **Step 1: Write failing backend and frontend contract tests**

Assert that entry responses expose `sourceRank` and `mainNetInflow`; assert the page says `同花顺每日行业热榜`, displays `主力净流入`, accepts `881121` follow actions, removes BK-only disable logic and uses the placeholder `搜索同花顺板块名称或代码`.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=SectorMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run: `cd frontend && npm test -- --run src/features/watchlist/WatchlistView.test.tsx`

Expected: both fail on the old response/UI contract.

- [ ] **Step 3: Implement the response and UI**

Industry ranking columns become `资金流入` and `资金流出`; the secondary metric becomes formatted main net inflow and source rank is preserved. Concept search/follow remains available, while its overview explicitly states that the first phase supplies a directory rather than fabricating a money-flow ranking. Remove every `startsWith('BK')` gate.

- [ ] **Step 4: Run focused tests and production build**

Run: `cd backend && mvn -pl finscope-web -am test`

Run: `cd frontend && npm test -- --run && npm run build`

Expected: PASS and Vite build exit 0.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-web frontend
git commit -m "feat: 展示同花顺每日行业资金热榜"
git push
```

### Task 5: 全链路验证与规范自检

**Files:**
- Modify only if verification exposes a defect in the files above.

- [ ] **Step 1: Verify Python**

Run: `cd market-data-service && .venv/bin/pytest -q`

Expected: PASS.

- [ ] **Step 2: Verify backend**

Run: `cd backend && mvn test`

Expected: PASS.

- [ ] **Step 3: Verify frontend**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: PASS.

- [ ] **Step 4: Review repository rules**

Check all modified Spring beans use the existing injection style, all modified Java `if`/`for` statements have braces, external calls remain in `finscope-rpc`, no API key is printed or moved, and `git status --short` contains no unrelated files.

- [ ] **Step 5: Commit and push any verification-only corrections**

```bash
git add <only-the-corrected-files>
git commit -m "fix: 修正同花顺板块切换验证问题"
git push
```
