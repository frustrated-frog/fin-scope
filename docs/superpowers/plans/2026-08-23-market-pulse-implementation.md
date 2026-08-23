# Market Pulse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent end-of-day Market Pulse workspace that classifies market regime, ranks sector rotation, confirms direct sector-linked Radar events, and enriches the existing verified stock-discovery candidates.

**Architecture:** The first production slice reuses the existing Java market-data gateway, `QuantDailyBarSource`, Radar repository, sector catalog, and stock-discovery ledger. Deterministic Java services calculate and freeze point-in-time research snapshots in dedicated SQLite tables; the web layer only reads or triggers the use case, and React renders an independent decision workspace. Missing breadth or history is represented as partial quality instead of being invented.

**Tech Stack:** Java 21, Spring Boot 2.7, JdbcTemplate/SQLite, Jackson, JUnit 5/Mockito, React, TypeScript, Vite, Vitest.

---

## File map

### Stable enums

- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketPulseQualityStatus.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketTrendState.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketLiquidityState.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketRiskAppetiteState.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketRotationState.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketStage.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/SectorRotationStage.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/enums/marketpulse/MarketEventConfirmationState.java`.

### Domain contracts

- Create focused Lombok DTOs in `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/`: `MarketRegimeSnapshot`, `MarketRegimeFeatures`, `SectorRotationSnapshot`, `SectorRotationItem`, `MarketEventConfirmation`, `MarketPulseCandidate`, `MarketPulseWorkspace`, `MarketPulseRefreshResult`.

### Persistence

- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java` to invoke the dedicated schema migrator.
- Create `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseSchemaMigrator.java`.
- Create `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseRepository.java`.
- Create `backend/finscope-dao/src/test/java/com/finscope/dao/marketpulse/MarketPulseRepositoryTest.java`.

### Services

- Create `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketRegimeClassifier.java`.
- Create `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/SectorRotationScoringService.java`.
- Create `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketEventConfirmationService.java`.
- Create `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseCandidateService.java`.
- Create `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseService.java`.
- Add matching focused tests under `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/`.

### Web

- Create `backend/finscope-web/src/main/java/com/finscope/web/controller/MarketPulseController.java`.
- Create `backend/finscope-web/src/test/java/com/finscope/web/controller/MarketPulseControllerTest.java`.

### Frontend

- Create `frontend/src/features/market-pulse/marketPulseTypes.ts`.
- Create `frontend/src/features/market-pulse/MarketPulseView.tsx`.
- Create `frontend/src/features/market-pulse/MarketPulseView.css`.
- Create `frontend/src/features/market-pulse/MarketPulseView.test.tsx`.
- Modify `frontend/src/shared/types/index.ts`, `frontend/src/app/AppShell.tsx`, and `frontend/src/App.tsx`.

## Task 1: Domain contracts and schema

- [ ] **Step 1: Write the repository test first**

Create `MarketPulseRepositoryTest` with an in-memory SQLite/JdbcTemplate fixture matching adjacent DAO tests. The first tests must assert that saving the same business date replaces the same snapshot rather than creating duplicates, and that `findRecentDates(5)` returns descending unique dates:

```java
@Test
void savesOneFrozenWorkspacePerBusinessDate() {
    repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21)));
    repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21)));

    assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM market_regime_snapshot", Integer.class));
    assertTrue(repository.findWorkspace(LocalDate.of(2026, 8, 21)).isPresent());
}
```

- [ ] **Step 2: Run the failing DAO test**

Run:

```bash
cd backend && mvn -pl finscope-dao -Dtest=MarketPulseRepositoryTest test
```

Expected: compilation failure because Market Pulse contracts and repository do not exist.

- [ ] **Step 3: Add enums and Lombok domain contracts**

Enums must use the values defined in the design. DTOs must use `@Data`; Spring beans are not created in this task. `MarketPulseWorkspace` must expose:

```java
@Data
public class MarketPulseWorkspace {
    private LocalDate businessDate;
    private MarketRegimeSnapshot regime;
    private List<MarketRegimeSnapshot> recentRegimes = new ArrayList<>();
    private List<SectorRotationItem> sectors = new ArrayList<>();
    private List<MarketEventConfirmation> eventConfirmations = new ArrayList<>();
    private List<MarketPulseCandidate> candidates = new ArrayList<>();
    private MarketPulseQualityStatus qualityStatus;
    private List<String> warnings = new ArrayList<>();
    private LocalDateTime generatedAt;
}
```

- [ ] **Step 4: Add dedicated schema and repository**

The migrator creates `market_regime_snapshot`, `sector_rotation_snapshot`, `sector_rotation_item`, `market_event_confirmation`, and `market_opportunity_run` with the unique keys from the design. Store feature/evidence collections as JSON and map them through the injected project `ObjectMapper`. Repository writes one local transaction and validates expected update counts.

- [ ] **Step 5: Run DAO tests and commit**

Run:

```bash
cd backend && mvn -pl finscope-dao -Dtest=MarketPulseRepositoryTest test
```

Expected: PASS.

Commit:

```bash
git add backend/finscope-common backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加市场机会快照持久化"
git push
```

## Task 2: Deterministic market and sector classification

- [ ] **Step 1: Write classifier tests**

Create `MarketRegimeClassifierTest` and `SectorRotationScoringServiceTest`. Cover shrinking repair, high-volatility sell-off, insufficient market bars, accelerating sector, overheated sector, fading sector, and stable tie ordering. Example:

```java
@Test
void classifiesPostSellOffShrinkingRepairFromFrozenFeatures() {
    MarketRegimeFeatures features = features(-0.06D, 0.012D, 0.72D, 0.43D, 0.031D);

    MarketRegimeSnapshot result = classifier.classify(LocalDate.of(2026, 8, 21), features);

    assertEquals(MarketStage.POST_SELL_OFF_REPAIR, result.getMarketStage());
    assertEquals(MarketLiquidityState.SHRINKING, result.getLiquidityState());
    assertTrue(result.getEvidence().stream().anyMatch(value -> value.contains("成交额")));
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
cd backend && mvn -pl finscope-service -Dtest=MarketRegimeClassifierTest,SectorRotationScoringServiceTest test
```

Expected: compilation failure for missing classifiers.

- [ ] **Step 3: Implement market classifier**

Use versioned constants inside the classifier, complete braces for every `if`/`for`, and emit evidence for each classification. Critical missing fields return `INSUFFICIENT_DATA` and `PARTIAL`; they never become neutral values.

- [ ] **Step 4: Implement sector scoring**

Score finite inputs only. Relative returns, positive breadth, flow rank and persistence raise the score; crowding penalizes it. Stage rules are deterministic and ordered so `OVERHEATED` wins over `ACCELERATING`, while missing five-day history becomes `INSUFFICIENT_DATA`.

- [ ] **Step 5: Run focused tests and commit**

Run:

```bash
cd backend && mvn -pl finscope-service -Dtest=MarketRegimeClassifierTest,SectorRotationScoringServiceTest test
```

Expected: PASS.

Commit:

```bash
git add backend/finscope-service
git commit -m "feat: 增加市场状态与行业轮动规则"
git push
```

## Task 3: Event confirmation and verified candidate projection

- [ ] **Step 1: Write event and candidate tests**

`MarketEventConfirmationServiceTest` must cover all four quadrants and refuse ranking eligibility when direct sector evidence is absent. `MarketPulseCandidateServiceTest` must accept only final-ranked, `HEALTHY`, upward-conclusion candidates whose sector is not `WEAK`, `FADING`, or `INSUFFICIENT_DATA`.

```java
@Test
void excludesUnhealthyOrWeakSectorCandidatesInsteadOfFillingFiveSlots() {
    List<MarketPulseCandidate> values = service.assemble(
            discoveryRun(), List.of(healthyCandidate(), unhealthyCandidate()),
            List.of(rotation("医药生物", SectorRotationStage.ACCELERATING)));

    assertEquals(1, values.size());
    assertEquals("600001.SH", values.get(0).getInstrumentCode());
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
cd backend && mvn -pl finscope-service -Dtest=MarketEventConfirmationServiceTest,MarketPulseCandidateServiceTest test
```

Expected: compilation failure for missing services.

- [ ] **Step 3: Implement conservative event confirmation**

First production mapping only accepts direct normalized sector-name mentions in Radar title, summary, entities, or interpretation text. Persist mapping source `DIRECT_MENTION`; do not add semantic guesses. Market reaction derives from the same-day sector snapshot.

- [ ] **Step 4: Implement candidate projection**

Add read-only repository methods to `StockDiscoveryRepository` for candidates by run ID. Parse frozen sector names with the injected `ObjectMapper`; keep no more than five candidates ordered by final rank then deep score. Build Why now, risks and invalidation from frozen fields and sector state; never invoke LLM in the gate.

- [ ] **Step 5: Run focused tests and commit**

Run:

```bash
cd backend && mvn -pl finscope-service -Dtest=MarketEventConfirmationServiceTest,MarketPulseCandidateServiceTest test
```

Expected: PASS.

Commit:

```bash
git add backend/finscope-dao backend/finscope-service
git commit -m "feat: 接通事件确认与研究候选"
git push
```

## Task 4: Market Pulse orchestration and REST API

- [ ] **Step 1: Write orchestration and controller tests**

`MarketPulseServiceTest` covers a successful refresh, duplicate business date, unavailable index history, sector-catalog failure, no Radar matches, no stock-discovery run, and zero candidates. `MarketPulseControllerTest` covers latest, date-specific, dates, and refresh endpoints.

- [ ] **Step 2: Run failing tests**

Run:

```bash
cd backend && mvn -pl finscope-service,finscope-web -am -Dtest=MarketPulseServiceTest,MarketPulseControllerTest test
```

Expected: compilation failure for missing service/controller.

- [ ] **Step 3: Implement orchestration**

`MarketPulseService` uses `@Resource` fields for `QuantDailyBarSource`, `SectorMarketService`, `RadarRepository`, `StockDiscoveryRepository`, the classifiers and `MarketPulseRepository`. Remote reads complete before repository writes. A refresh failure returns an explicit failed `MarketPulseRefreshResult` only when the established project error contract requires it; otherwise it throws for the global handler while prior snapshots remain readable.

- [ ] **Step 4: Implement API**

Add:

```java
@GetMapping("/latest")
public ApiResponse<MarketPulseWorkspace> latest()

@GetMapping("/dates")
public ApiResponse<List<LocalDate>> dates(@RequestParam(defaultValue = "20") int limit)

@GetMapping("/{businessDate}")
public ApiResponse<MarketPulseWorkspace> detail(@PathVariable LocalDate businessDate)

@PostMapping("/refresh")
public ApiResponse<MarketPulseRefreshResult> refresh()
```

Controller only validates `limit` and delegates. Use the existing global exception handling and `ApiResponses.success`.

- [ ] **Step 5: Run backend tests and commit**

Run:

```bash
cd backend && mvn -pl finscope-service,finscope-web -am -Dtest=MarketPulseServiceTest,MarketPulseControllerTest test
```

Expected: PASS.

Commit:

```bash
git add backend
git commit -m "feat: 提供市场机会工作台接口"
git push
```

## Task 5: Market Pulse React workspace

- [ ] **Step 1: Write the failing view test**

Create `MarketPulseView.test.tsx` with API fixtures. Assert the state seal, five-day rhythm, sector stages, event confirmation, zero-candidate copy, a qualified candidate, stale warning, and date switching.

```tsx
test('shows market context before research candidates', async () => {
  render(<MarketPulseView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('急跌后的缩量修复')).toBeInTheDocument();
  expect(screen.getByText('行业轮动')).toBeInTheDocument();
  expect(screen.getByText('研究候选')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
cd frontend && npm test -- MarketPulseView.test.tsx
```

Expected: FAIL because the view does not exist.

- [ ] **Step 3: Add types, view and styles**

The view fetches `/api/market-pulse/latest` on mount, `/api/market-pulse/dates?limit=20` for history, and the date endpoint on selection. Render in this order: state header, rhythm, sector table/cards, event confirmations, candidates. Preserve Chinese A-share red/up and green/down semantics, accessible tab/button labels, and a single-column 390px layout.

- [ ] **Step 4: Add navigation**

Add `'marketPulse'` to `View`, add `Market Pulse / 市场状态与机会 / MP` as the first item in the decision group, import/render `MarketPulseView` in `App.tsx`, and set the title to `Market Pulse · 市场机会`.

- [ ] **Step 5: Run frontend test and build, then commit**

Run:

```bash
cd frontend && npm test -- MarketPulseView.test.tsx
cd frontend && npm run build
```

Expected: PASS and successful Vite production build.

Commit:

```bash
git add frontend
git commit -m "feat: 增加市场机会决策页面"
git push
```

## Task 6: Full verification and documentation alignment

- [ ] **Step 1: Run all project verification**

```bash
cd backend && mvn test
cd ../frontend && npm test
cd ../frontend && npm run build
```

Expected: all backend and frontend tests pass, and Vite build exits successfully.

- [ ] **Step 2: Run project-style checks**

Search newly changed Java files for constructor-injected Spring beans and single-line `if`/`for`; inspect every new type location against `web -> service -> dao/rpc -> domain/common`. Run `git diff --check` and confirm no API key value appears in the diff.

- [ ] **Step 3: Update implementation status in the design**

Append an implementation note to `docs/superpowers/specs/2026-08-23-market-pulse-design.md` listing the delivered vertical slice and explicitly preserving later data-source expansions as future enhancements.

- [ ] **Step 4: Commit and push final verified state**

```bash
git add docs backend frontend
git commit -m "docs: 补充市场机会工作台实现说明"
git push
```
