# Attribution Narrative Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make completed attribution reports appear in history immediately and explain daily price moves through a rich, plain-language causal narrative.

**Architecture:** Keep the existing attribution research runtime and evidence gates. Add an optional persisted `AttributionNarrative` value object produced by the synthesis step, extend drivers with presentation-specific role and plain-language explanation, and render the narrative as a first-reading layer while preserving the existing detailed evidence layer and old-report fallback.

**Tech Stack:** Java 8, Spring Boot 2.7, Jackson, SQLite/JdbcTemplate, JUnit 5, React, TypeScript, Vitest, Testing Library, Vite.

---

## File map

- Create `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionNarrative.java`: focused value object for the causal story.
- Modify `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionDriver.java`: add driver role and plain-language explanation.
- Modify `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionReport.java`: attach the optional narrative.
- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`: add `narrative_json` idempotently.
- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionRepository.java`: serialize and deserialize narrative data.
- Modify `backend/finscope-dao/src/test/java/com/finscope/dao/attribution/AttributionRepositoryTest.java`: verify persistence compatibility.
- Modify `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`: request, parse, and deterministically fall back to the narrative contract.
- Create `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java`: verify parsing, prompt routing, and fallback behavior.
- Modify `frontend/src/shared/types/index.ts`: mirror the narrative and driver contract.
- Modify `frontend/src/features/watchlist/AttributionReaderView.tsx`: refresh history on completion and render the layered narrative.
- Modify `frontend/src/features/watchlist/AttributionReaderView.test.tsx`: cover the regression and new presentation.
- Modify `frontend/src/styles.css`: style narrative flow, context cards, and role badges.

### Task 1: Refresh attribution history after a live report completes

**Files:**
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`
- Test: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [ ] **Step 1: Write the failing regression test**

Add a test that renders with `taskId`, returns a completed report from `/reports/301`, returns the current and previous receipts from `/history`, and expects the history endpoint and both summaries to appear.

```tsx
test('loads history when a live attribution becomes completed', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path.includes('/history')
      ? [
          { id: 301, status: 'COMPLETED', reportDate: '2026-07-30', summary: '本次归因' },
          { id: 299, status: 'COMPLETED', reportDate: '2026-07-29', summary: '上次归因' }
        ]
      : { id: 301, status: 'COMPLETED', reportDate: '2026-07-30', summary: '本次归因', drivers: [] }
  ) as never);

  render(<AttributionReaderView taskId="live-task" reportId={301} code="603618" type="STOCK" onBack={vi.fn()} />);

  expect(await screen.findByText('本次归因')).toBeInTheDocument();
  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/attribution/history?code=603618&type=STOCK&limit=50'
  ));
  expect(screen.getByText('上次归因')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the regression test and verify RED**

Run: `cd frontend && npm test -- AttributionReaderView.test.tsx -t "loads history when a live attribution becomes completed"`

Expected: FAIL because the history effect exits while `taskId` is present.

- [ ] **Step 3: Implement completion-driven history loading**

Extract `loadHistory` with a monotonically safe state update, call it immediately for persisted reports, and call it when the current report first reaches `COMPLETED`. On failure, preserve the existing history instead of replacing it with an empty list.

```tsx
const loadHistory = useCallback(async () => {
  const items = await api<AttributionReport[]>(historyUrl);
  setHistory((previous) => mergeHistory(previous, Array.isArray(items) ? items : [], report));
}, [historyUrl, report]);
```

Use a ref keyed by completed report ID so polling cannot trigger duplicate history requests.

- [ ] **Step 4: Run the focused frontend test**

Run: `cd frontend && npm test -- AttributionReaderView.test.tsx`

Expected: all tests in the file PASS.

- [ ] **Step 5: Commit and push the independent bug fix**

```bash
git add frontend/src/features/watchlist/AttributionReaderView.tsx frontend/src/features/watchlist/AttributionReaderView.test.tsx
git commit -m "fix: 修复归因完成后历史不刷新"
git push origin main
```

### Task 2: Persist the narrative report contract

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionNarrative.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionDriver.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionReport.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/attribution/AttributionRepositoryTest.java`

- [ ] **Step 1: Write the failing repository round-trip test**

Create a completed report containing a narrative and a `TRIGGER` driver, update it, reload it, and assert every new field.

```java
@Test
void persistsNarrativeAndPlainLanguageDriverFields() {
    AttributionReport report = save("603618", "STOCK", LocalDate.of(2026, 7, 30), "主线", -5.76, "GENERATING");
    AttributionNarrative narrative = new AttributionNarrative();
    narrative.setPlainSummary("延期传闻触发担忧，前期涨幅放大抛压。");
    narrative.setEvent("英伟达机架延期传闻出现");
    narrative.setInstrumentLink("公司处于算力硬件相关链条");
    narrative.setWhyToday("传闻与板块回撤在当日集中共振");
    narrative.setCausalSteps(Arrays.asList("延期传闻", "需求预期下调", "板块承压", "股价下跌"));
    narrative.setAmplifiers(Collections.singletonList("前期累计涨幅较大"));
    narrative.setDampeners(Collections.singletonList("公司尚未确认实际订单影响"));
    AttributionDriver driver = new AttributionDriver();
    driver.setClaim("延期传闻");
    driver.setRole("TRIGGER");
    driver.setPlainExplanation("市场担心相关硬件需求推迟。");
    report.setNarrative(narrative);
    report.setDrivers(Collections.singletonList(driver));
    report.setStatus("COMPLETED");

    repository.updateResult(report);
    AttributionReport restored = repository.findById(report.getId()).orElseThrow(AssertionError::new);

    assertEquals("传闻与板块回撤在当日集中共振", restored.getNarrative().getWhyToday());
    assertEquals("TRIGGER", restored.getDrivers().get(0).getRole());
    assertEquals("市场担心相关硬件需求推迟。", restored.getDrivers().get(0).getPlainExplanation());
}
```

- [ ] **Step 2: Run the DAO test and verify RED**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=AttributionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because the narrative contract does not exist.

- [ ] **Step 3: Add the domain types and schema column**

Implement `AttributionNarrative` with nullable strings and lists initialized to empty `ArrayList`s. Add `narrative` to `AttributionReport`, and `role` plus `plainExplanation` to `AttributionDriver`. Add `narrative_json TEXT` to new tables and `ensureColumn("attribution_report", "narrative_json", "TEXT")` for existing databases.

- [ ] **Step 4: Add repository JSON persistence**

Read `narrative_json` in `reportMapper`, include it in both `INSERT` and `UPDATE`, and add focused helpers:

```java
private String writeNarrative(AttributionNarrative narrative)
private AttributionNarrative parseNarrative(String raw)
```

Blank or malformed legacy JSON must return `null`; serialization failure must throw `IllegalStateException` like the existing driver serializer.

- [ ] **Step 5: Run DAO tests**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=AttributionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with the original history tests and the new round-trip test.

- [ ] **Step 6: Commit and push the contract batch**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加归因叙事数据契约"
git push origin main
```

### Task 3: Generate the Serenity-inspired causal narrative

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java`

- [ ] **Step 1: Write failing synthesis parsing and routing tests**

Test a JSON response containing all narrative fields and driver presentation fields, then test that stock, fund, and sector prompts contain their distinct transmission requirements.

```java
@Test
void parsesPlainLanguageNarrativeAndDriverRole() {
    AttributionReport report = new AttributionReport();
    boolean parsed = agent.parseSynthResult(report, "{\"summary\":\"综合结论\",\"narrative\":{" +
            "\"plainSummary\":\"先讲清主因\",\"event\":\"事件\",\"instrumentLink\":\"标的关联\"," +
            "\"whyToday\":\"今日共振\",\"causalSteps\":[\"事件\",\"预期\",\"价格\"]," +
            "\"amplifiers\":[\"板块走弱\"],\"dampeners\":[\"尚无订单确认\"]}," +
            "\"drivers\":[{\"claim\":\"触发因素\",\"role\":\"TRIGGER\"," +
            "\"plainExplanation\":\"市场担心需求后移\",\"evidenceUrls\":[]}]}" );

    assertTrue(parsed);
    assertEquals("今日共振", report.getNarrative().getWhyToday());
    assertEquals("TRIGGER", report.getDrivers().get(0).getRole());
}
```

- [ ] **Step 2: Run the service test and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=AttributionAgentNarrativeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because synthesis does not parse or request the narrative contract.

- [ ] **Step 3: Extend the strict JSON prompt and parser**

Make `parseSynthResult` and a prompt-building seam package-private for focused tests. Require the model to output `narrative`, `role`, and `plainExplanation`. Add the daily-attribution chain and type-specific instructions:

```text
STOCK: 事件 → 盈利或风险预期 → 公司暴露 → 板块/资金 → 股价
FUND: 行业或核心持仓 → 组合暴露 → 净值/交易价格
SECTOR: 政策或需求 → 龙头反应 → 成分扩散 → 板块
```

Normalize unknown roles to `BACKGROUND`, and accept only `TRIGGER`, `AMPLIFIER`, `BACKGROUND`, or `COUNTER`.

- [ ] **Step 4: Add deterministic narrative fallback**

After synthesis, ensure a minimum narrative exists. Use current evidence and primary driver only; exclude `historicalContext` evidence from `event` and `whyToday`. Populate no more than four causal steps and do not invent type-specific exposure when it is unavailable.

- [ ] **Step 5: Run service attribution tests**

Run: `cd backend && mvn -pl finscope-service -am -Dtest='AttributionAgentNarrativeTest,AttributionHarnessTest' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

- [ ] **Step 6: Commit and push the generation batch**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java
git commit -m "feat: 生成通俗归因因果叙事"
git push origin main
```

### Task 4: Render a layered 30-second attribution explanation

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`
- Modify: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing narrative presentation test**

Return a complete narrative and drivers with different roles. Assert that the page renders the plain summary, causal steps in order, “为什么是它”, “为什么是今天”, amplifiers, dampeners, and a localized driver-role label.

```tsx
expect(await screen.findByText('今天为什么跌')).toBeInTheDocument();
expect(screen.getByText('市场担心相关硬件需求后移')).toBeInTheDocument();
expect(screen.getByText('为什么是它')).toBeInTheDocument();
expect(screen.getByText('为什么是今天')).toBeInTheDocument();
expect(screen.getByText('放大跌幅的因素')).toBeInTheDocument();
expect(screen.getByText('缓冲或反方因素')).toBeInTheDocument();
expect(screen.getByText('直接触发')).toBeInTheDocument();
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && npm test -- AttributionReaderView.test.tsx -t "renders the plain-language causal narrative"`

Expected: FAIL because narrative fields are not typed or rendered.

- [ ] **Step 3: Add TypeScript contract and presentation helpers**

Add `AttributionNarrative`, `AttributionDriverRole`, `narrative`, `role`, and `plainExplanation`. Add small pure helpers for direction-aware headings and localized role labels; keep existing reports valid because every new field is optional.

- [ ] **Step 4: Render the narrative before detailed drivers**

Add a narrative hero followed by an ordered causal flow and two context cards. Display driver role and `plainExplanation`, and retain the existing facts, transmission path, counter-evidence, observation window, and evidence sidebar. When narrative is absent, render the existing summary and primary-driver layout unchanged.

- [ ] **Step 5: Add responsive styles**

Use existing attribution colors and surfaces. The causal flow should be a wrapping grid on desktop and a vertical sequence below 900px. Cards must not rely on color alone: every role includes visible text.

- [ ] **Step 6: Run the watchlist frontend tests and build**

Run: `cd frontend && npm test -- AttributionReaderView.test.tsx WatchlistView.test.tsx && npm run build`

Expected: all selected tests PASS and Vite exits 0.

- [ ] **Step 7: Commit and push the presentation batch**

```bash
git add frontend/src/shared/types/index.ts frontend/src/features/watchlist/AttributionReaderView.tsx frontend/src/features/watchlist/AttributionReaderView.test.tsx frontend/src/styles.css
git commit -m "feat: 增强归因报告通俗叙事"
git push origin main
```

### Task 5: Full verification and documentation closure

**Files:**
- Modify: `docs/superpowers/plans/2026-07-30-attribution-narrative-report.md`

- [ ] **Step 1: Run complete backend verification**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS with zero failed tests.

- [ ] **Step 2: Run complete frontend verification**

Run: `cd frontend && npm test && npm run build`

Expected: all Vitest tests PASS and Vite production build exits 0.

- [ ] **Step 3: Check the diff and working tree**

Run: `git diff --check && git status --short`

Expected: no whitespace errors and only the plan checkbox updates, if any, remain uncommitted.

- [ ] **Step 4: Mark plan tasks complete and push the final documentation update**

Update each completed checkbox from `[ ]` to `[x]`, then run:

```bash
git add docs/superpowers/plans/2026-07-30-attribution-narrative-report.md
git commit -m "docs: 完成归因叙事实施记录"
git push origin main
```
