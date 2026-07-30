# Attribution Driver AI Interpretation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将归因报告每条原因从事实摘录升级为事实与 AI 定价解读分层展示。

**Architecture:** 在现有 `AttributionDriver` JSON 子对象中加入五个可选解释字段，由同一次归因 LLM 调用生成并随 `drivers_json` 自动持久化。前端仅在新字段存在时渲染轻量 AI 解读面板，旧报告和确定性降级报告保持原展示。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、JUnit 5、React、TypeScript、Vitest、CSS

---

### Task 1: Extend the driver contract and LLM synthesis

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionDriver.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java`

- [x] **Step 1: Write the failing parser test**

Extend `parsesPlainLanguageNarrativeAndDriverRole` with the five fields and assertions:

```java
"marketInterpretation":"市场在交易订单预期落空",
"expectationShift":"原本预期切入商业航天 → 现在确认仍以传统业务为主",
"priceImpact":"成长想象空间收缩，提高风险溢价并压低估值",
"explanatoryPower":"HIGH",
"explanatoryPowerReason":"公告直接否定核心题材，且与当日下跌方向一致"
```

```java
AttributionDriver driver = report.getDrivers().get(0);
assertEquals("市场在交易订单预期落空", driver.getMarketInterpretation());
assertEquals("原本预期切入商业航天 → 现在确认仍以传统业务为主", driver.getExpectationShift());
assertEquals("成长想象空间收缩，提高风险溢价并压低估值", driver.getPriceImpact());
assertEquals("HIGH", driver.getExplanatoryPower());
assertEquals("公告直接否定核心题材，且与当日下跌方向一致", driver.getExplanatoryPowerReason());
```

- [x] **Step 2: Write the failing prompt-boundary test**

Add a test named `asksForBoundedMarketInterpretationInsteadOfRepeatingFacts`:

```java
String prompt = agent.synthUserPrompt(instrument("STOCK"), -4.2D,
        Collections.<AttributionEvidence>emptyList());
assertTrue(prompt.contains("市场为什么在意"));
assertTrue(prompt.contains("原本预期 → 现在预期"));
assertTrue(prompt.contains("盈利预期、估值倍数、风险溢价或资金行为"));
assertTrue(prompt.contains("不得虚构数字、业务暴露或投资者行为"));
```

- [x] **Step 3: Run the focused test and verify RED**

Run:

```bash
cd backend && mvn -pl finscope-service -am -Dtest=AttributionAgentNarrativeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the five `AttributionDriver` properties do not exist.

- [x] **Step 4: Add the optional domain fields**

Add Lombok-backed fields with comments to `AttributionDriver`:

```java
private String marketInterpretation;
private String expectationShift;
private String priceImpact;
private String explanatoryPower;
private String explanatoryPowerReason;
```

No repository or schema change is needed because the complete driver object is already serialized in `drivers_json`.

- [x] **Step 5: Extend the prompt and parser**

Add the fields to the strict JSON example in `synthUserPrompt`, then add these rules:

```text
facts 只写证据明确支持的事实；AI 解读不得重复事实原句。
marketInterpretation 回答市场为什么在意；expectationShift 使用“原本预期 → 现在预期”。
priceImpact 必须落到盈利预期、估值倍数、风险溢价或资金行为中的至少一种。
explanatoryPower 综合证据直接性、时间贴近度、价格方向一致性与反证。
不得虚构数字、业务暴露或投资者行为；推断使用“可能、意味着、市场倾向于”等边界措辞。
```

Parse the strings and normalize the strength only when present:

```java
driver.setMarketInterpretation(node.path("marketInterpretation").asText("").trim());
driver.setExpectationShift(node.path("expectationShift").asText("").trim());
driver.setPriceImpact(node.path("priceImpact").asText("").trim());
String explanatoryPower = node.path("explanatoryPower").asText("").trim();
driver.setExplanatoryPower(StringUtils.isBlank(explanatoryPower) ? "" : normLevel(explanatoryPower));
driver.setExplanatoryPowerReason(node.path("explanatoryPowerReason").asText("").trim());
```

Do not populate these fields in `fallbackSynthesize`.

- [x] **Step 6: Run the focused backend test and verify GREEN**

Run the command from Step 3. Expected: all `AttributionAgentNarrativeTest` tests pass.

- [x] **Step 7: Commit and push the backend batch**

```bash
git add backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionDriver.java backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java
git commit -m "feat: 增加归因原因AI解读"
git push
```

### Task 2: Render a clearly separated AI interpretation layer

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [x] **Step 1: Write the failing presentation test**

Add a test named `separates AI market interpretation from factual evidence`. Return one driver with all five fields and one fact, then assert:

```typescript
expect(await screen.findByText('AI 解读')).toBeInTheDocument();
expect(screen.getByText('市场在交易什么')).toBeInTheDocument();
expect(screen.getByText('预期发生了什么变化')).toBeInTheDocument();
expect(screen.getByText('为什么会影响股价')).toBeInTheDocument();
expect(screen.getByText('解释力度')).toBeInTheDocument();
expect(screen.getByText('事实依据')).toBeInTheDocument();
expect(screen.getByText('公司公告明确否认商业航天主营业务')).toBeInTheDocument();
```

Also assert the AI panel is identifiable:

```typescript
expect(screen.getByLabelText('AI 市场解读')).toBeInTheDocument();
```

- [x] **Step 2: Run the focused frontend test and verify RED**

Run:

```bash
cd frontend && npm test -- AttributionReaderView.test.tsx -t "separates AI market interpretation from factual evidence"
```

Expected: FAIL because the new fields and labels are not rendered.

- [x] **Step 3: Extend the TypeScript contract**

Add optional properties to `AttributionDriver`:

```typescript
marketInterpretation?: string;
expectationShift?: string;
priceImpact?: string;
explanatoryPower?: 'HIGH' | 'MID' | 'LOW';
explanatoryPowerReason?: string;
```

- [x] **Step 4: Add focused presentation helpers**

In `AttributionReaderView.tsx`, add a predicate and strength labels:

```typescript
const explanatoryPowerLabels: Record<string, string> = { HIGH: '强', MID: '中', LOW: '弱' };
const hasAiInterpretation = (driver: AttributionDriver) => Boolean(
  driver.marketInterpretation || driver.expectationShift || driver.priceImpact ||
  driver.explanatoryPower || driver.explanatoryPowerReason
);
```

Import `AttributionDriver` as a type if it is not already imported.

- [x] **Step 5: Render the AI layer before facts**

Inside each driver, after `plainExplanation`, render one semantic section when `hasAiInterpretation(driver)` is true:

```tsx
<section className="attribution-driver-ai" aria-label="AI 市场解读">
  <div className="attribution-driver-ai-heading"><span>AI</span><strong>AI 解读</strong></div>
  <div className="attribution-driver-ai-grid">
    {driver.marketInterpretation && <article><span>市场在交易什么</span><p>{driver.marketInterpretation}</p></article>}
    {driver.expectationShift && <article><span>预期发生了什么变化</span><p>{driver.expectationShift}</p></article>}
    {driver.priceImpact && <article><span>为什么会影响股价</span><p>{driver.priceImpact}</p></article>}
    {(driver.explanatoryPower || driver.explanatoryPowerReason) && <article><span>解释力度</span><p>{driver.explanatoryPower ? `${explanatoryPowerLabels[driver.explanatoryPower] || driver.explanatoryPower} · ` : ''}{driver.explanatoryPowerReason}</p></article>}
  </div>
</section>
```

Replace the fact prefix repeated on every list item with one visible `事实依据` label and a dedicated fact list. Keep `detail`, transmission, counter-evidence, and observation window unchanged.

- [x] **Step 6: Add responsive visual hierarchy**

In `styles.css`, use an inset surface with a cyan left accent, compact `AI` badge, and a two-column grid. Add a media rule below 900px that switches `.attribution-driver-ai-grid` to one column. Keep the outer driver as the only bordered card and do not add shadows or gradients.

- [x] **Step 7: Run focused frontend tests and build**

```bash
cd frontend && npm test -- AttributionReaderView.test.tsx WatchlistView.test.tsx && npm run build
```

Expected: selected tests pass and the Vite production build exits 0.

- [x] **Step 8: Commit and push the frontend batch**

```bash
git add frontend/src/shared/types/index.ts frontend/src/features/watchlist/AttributionReaderView.tsx frontend/src/features/watchlist/AttributionReaderView.test.tsx frontend/src/styles.css
git commit -m "feat: 丰富归因原因AI解读展示"
git push
```

### Task 3: Full verification and plan closure

**Files:**
- Modify: `docs/superpowers/plans/2026-07-30-attribution-driver-ai-interpretation.md`

- [x] **Step 1: Run backend verification**

```bash
cd backend && mvn test
```

Expected: Maven exits 0 with no failed tests.

- [x] **Step 2: Run frontend verification**

```bash
cd frontend && npm test && npm run build
```

Expected: all Vitest tests pass and Vite exits 0.

- [x] **Step 3: Check repository state**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only this plan's checkbox update remains.

- [x] **Step 4: Mark the plan complete and push**

Change completed steps from `[ ]` to `[x]`, then run:

```bash
git add docs/superpowers/plans/2026-07-30-attribution-driver-ai-interpretation.md
git commit -m "docs: 完成归因原因解读实施记录"
git push
```
