# 财报解读 Agent 深度与性能升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将财报 Agent 升级为更深的多期与三表联动解读，同时把模型输入从约 60 KB 压缩到 24 KB 以内并降低真实生成耗时。

**Architecture:** 服务端先校验并补算当前指标，再用独立选择器从完整审计证据中生成小型模型证据包；模型单次返回扩展 JSON，门禁校验新增章节与维度详情。完整证据仍保存在快照中，前端兼容历史结果并新增核心变化、三表联动和维度详情展示。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、SQLite、React 18、TypeScript、Vitest、JUnit 5、Mockito。

---

## File Map

- Create `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidenceSelector.java`: 稳定筛选模型证据。
- Create `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialEvidenceSelectorTest.java`: 选择优先级、去重和上限测试。
- Create `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialAnalysisPreflight.java`: 基于已保存三张表补算过期指标。
- Create `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialAnalysisPreflightTest.java`: 指标版本预检测试。
- Modify `FinancialEvidencePacket.java` and `FinancialEvidencePacketAssembler.java`: 同时保存完整快照与精简模型载荷。
- Modify `FinancialAnalysisEngine.java`: 暴露公式版本并兼容旧概念别名。
- Modify `FinancialInterpretation.java`: 增加核心变化、三表联动和维度详情。
- Modify `FinancialInterpretationAgent.java`, `FinancialInterpretationGate.java`, `FinancialInterpretationFallbackBuilder.java`: 升级协议、门禁和降级。
- Modify `FinancialInterpretationFacade.java`: 在快照生成前执行指标预检。
- Modify `financialTypes.ts` and `FinancialInterpretationPanel.tsx`: 展示新结构并兼容旧记录。
- Modify existing Java and Vitest tests for regression coverage.

### Task 1: 指标版本预检

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialAnalysisPreflight.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialAnalysisPreflightTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialAnalysisEngine.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationFacade.java`

- [ ] **Step 1: Write failing tests for stale metrics and legacy aliases**

Test that a view containing `financial-metrics-v1` is recalculated and persisted with v2, while a current view is left untouched. Add engine assertions for `TOTAL_CURRENT_LIAB` and `CONTRACT_LIAB` aliases.

```java
assertTrue(preflight.requiresRefresh(staleView));
FinancialReportView refreshed = preflight.ensureCurrent(staleView, Arrays.asList(priorView));
assertEquals(FinancialAnalysisEngine.FORMULA_VERSION,
        refreshed.getMetrics().get(0).getFormulaVersion());
verify(reports).replaceAnalysis(eq(9L), anyList(), anyList());
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
cd backend
mvn -q -pl finscope-service -am \
  -Dtest=FinancialAnalysisPreflightTest,FinancialAnalysisEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because `FinancialAnalysisPreflight` and public formula version do not exist.

- [ ] **Step 3: Implement minimal preflight**

```java
@Component
public class FinancialAnalysisPreflight {
    public FinancialReportView ensureCurrent(FinancialReportView current,
                                             List<FinancialReportView> comparables) {
        if (!requiresRefresh(current)) return current;
        FinancialReportView prior = findPrior(current, comparables);
        FinancialAnalysisResult result = engine.analyze(lines(current), lines(prior));
        result.getMetrics().forEach(value -> value.setReportId(current.getReport().getId()));
        result.getFindings().forEach(value -> value.setReportId(current.getReport().getId()));
        reports.replaceAnalysis(current.getReport().getId(), result.getMetrics(), result.getFindings());
        current.setMetrics(result.getMetrics());
        current.setFindings(result.getFindings());
        current.setDataGaps(result.getDataGaps());
        return current;
    }
}
```

Expose `FinancialAnalysisEngine.FORMULA_VERSION` and use legacy fallbacks through `first(...)` for current liabilities and contract liabilities.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/financials \
        backend/finscope-service/src/test/java/com/finscope/service/financials
git commit -m "fix: 生成财报解读前补算过期指标"
```

### Task 2: 精简模型证据包

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidenceSelector.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialEvidenceSelectorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidencePacket.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidencePacketAssembler.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialEvidencePacketAssemblerTest.java`

- [ ] **Step 1: Write failing selector and packet tests**

Cover these exact rules: all metrics/findings/gaps survive; technical zero balances are removed; Q1 YTD/quarter duplicates collapse; non-core line items are dropped; core multi-point trends survive; result is stably sorted and at most 96 items; `modelPayloadJson` is smaller than `payloadJson`.

```java
List<FinancialEvidence> selected = selector.select(evidence, FinancialReportType.Q1);
assertTrue(selected.stream().anyMatch(value -> "M_REVENUE_YOY".equals(value.getId())));
assertFalse(selected.stream().anyMatch(value -> value.getId().contains("_BALANCE_")));
assertTrue(selected.size() <= FinancialEvidenceSelector.MAX_EVIDENCE);
assertTrue(packet.getModelPayloadJson().length() < packet.getPayloadJson().length());
```

- [ ] **Step 2: Run tests and verify RED**

Run the assembler and selector tests through Maven. Expected: missing selector and model payload API.

- [ ] **Step 3: Implement deterministic selection**

Use explicit priority buckets and a core concept set. Never use random ordering or model selection.

```java
public static final int MAX_EVIDENCE = 96;
public static final String SELECTOR_VERSION = "financial-evidence-selector-v1";

public List<FinancialEvidence> select(List<FinancialEvidence> values,
                                      FinancialReportType reportType) {
    LinkedHashMap<String, FinancialEvidence> selected = new LinkedHashMap<>();
    addTypes(selected, values, "FINDING", "METRIC", "DATA_GAP");
    addCoreTrends(selected, values);
    addCoreLines(selected, values, reportType);
    return selected.values().stream().limit(MAX_EVIDENCE).collect(Collectors.toList());
}
```

Assembler keeps full evidence in `payloadJson`, adds selector version and selected IDs, and creates a separate `modelPayloadJson` containing only selected evidence. Gate indexes and allowed numbers are built from model evidence.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Expected: selection tests pass and fixture model payload is at least 50% smaller than the full payload.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/financials \
        backend/finscope-service/src/test/java/com/finscope/service/financials
git commit -m "perf: 压缩财报 Agent 模型证据包"
```

### Task 3: 扩展深度输出协议

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/financials/FinancialInterpretation.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationGate.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationFallbackBuilder.java`
- Modify tests for Agent, gate and fallback behavior.

- [ ] **Step 1: Write failing protocol tests**

Add `periodChanges`, `crossStatementInsights`, and `Dimension.details`. Assert primary and repaired requests use `modelPayloadJson`, not the full payload. Add invalid nested reference and out-of-packet number tests.

```java
assertEquals(2, accepted.getPeriodChanges().size());
assertEquals(2, accepted.getCrossStatementInsights().size());
assertEquals(2, accepted.getDimensions().get(0).getDetails().size());
assertEquals(packet.getModelPayloadJson(), llm.userPrompts.get(0));
```

- [ ] **Step 2: Run tests and verify RED**

Expected: new properties and prompt rules are absent.

- [ ] **Step 3: Implement domain, prompt, gate and fallback**

```java
public static class Result {
    private List<Claim> periodChanges = new ArrayList<>();
    private List<Claim> crossStatementInsights = new ArrayList<>();
}

public static class Dimension {
    private List<Claim> details = new ArrayList<>();
}
```

Prompt requires 3-5 summaries, 2-5 changes, 2-5 cross-statement insights, and 2-4 details per supported dimension. Gate recursively validates all new claims and limits list sizes. Missing arrays from historical JSON remain empty through field initialization.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Expected: primary, repair, fallback, historical JSON and gate tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-domain backend/finscope-service
git commit -m "feat: 扩展财报 Agent 深度解读协议"
```

### Task 4: 升级前端深度展示

**Files:**
- Modify: `frontend/src/features/financials/financialTypes.ts`
- Modify: `frontend/src/features/financials/FinancialInterpretationPanel.tsx`
- Modify: `frontend/src/features/financials/FinancialInterpretationPanel.test.tsx`
- Modify: `frontend/src/features/financials/FinancialsView.test.tsx`
- Modify: `frontend/src/styles.css` only around existing financial Agent selectors; preserve unrelated user edits.

- [ ] **Step 1: Write failing component tests**

Assert rendering of “核心变化”“三表联动” and dimension detail claims, evidence click behavior, and graceful rendering of historical results without new fields.

```tsx
expect(await screen.findByText('核心变化')).toBeInTheDocument();
expect(screen.getByText('三表联动')).toBeInTheDocument();
expect(screen.getByText('经营现金流与利润出现背离')).toBeInTheDocument();
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
cd frontend
npm test -- FinancialInterpretationPanel.test.tsx FinancialsView.test.tsx
```

Expected: new sections are not rendered.

- [ ] **Step 3: Implement compatible presentation**

Normalize optional arrays at render time:

```tsx
const periodChanges = result.periodChanges ?? [];
const crossStatementInsights = result.crossStatementInsights ?? [];
const details = dimension.details ?? [];
```

Reuse `ClaimSection` for new top-level sections and render dimension details through the existing `EvidenceRefs`, avoiding a second evidence UI.

- [ ] **Step 4: Run targeted tests and production build**

Expected: targeted tests pass and `npm run build` exits 0.

- [ ] **Step 5: Commit only owned hunks**

Stage TypeScript/test files normally. For `styles.css`, inspect the diff and stage only financial Agent hunks so pre-existing user edits are not committed accidentally.

### Task 5: Real-model benchmark and full regression

**Files:**
- Modify only if benchmark exposes a defect; every fix must first add a regression test.
- Update: `docs/技术方案-财报解读Agent.md` with v3 protocol and actual benchmark.

- [ ] **Step 1: Record pre-change baseline**

Baseline from interpretation 6: full/model input approximately 59,632 bytes, 247 evidence items, output 3,204 characters, duration 29,180 ms.

- [ ] **Step 2: Run a real generation against a copied SQLite database**

Build the executable backend, copy `data/finance.db` to a temporary directory, start on port 18080, POST a forced interpretation, and poll until terminal. Never mutate the user's live database during benchmarking.

- [ ] **Step 3: Verify acceptance targets**

Inspect the copied database and API response. Required: `SUCCESS`, `generation_mode=LLM`, zero validation errors, model payload at most 24 KB, at most 96 evidence items, new sections populated where evidence supports them, and duration below 29,180 ms.

- [ ] **Step 4: Run complete regression**

```bash
cd backend && mvn -q -Dlogging.level.root=ERROR test
cd ../frontend && npm test && npm run build
cd ../market-data-service && uv run pytest -q
git diff --check
```

Expected: 577+ Java tests including additions, 176+ frontend tests, 27 Python tests, and all builds pass.

- [ ] **Step 5: Commit docs and final fixes**

```bash
git add docs/技术方案-财报解读Agent.md <owned-files>
git commit -m "feat: 提升财报 Agent 解读深度与生成速度"
```

## Self-Review Result

- Spec coverage: 深度输出、证据压缩、指标预检、历史兼容、门禁、前端、真实压测和全量回归均有对应任务。
- Placeholder scan: 无 TBD、TODO 或未定义步骤。
- Type consistency: `periodChanges`、`crossStatementInsights`、`details` 和 `modelPayloadJson` 在 Java、TypeScript、测试和提示词中名称一致。
- Execution choice: 用户要求不中断并直接完成，使用当前会话内联执行，不创建子 Agent。
