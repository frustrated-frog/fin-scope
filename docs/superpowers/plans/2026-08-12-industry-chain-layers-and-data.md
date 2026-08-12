# Industry Chain Layers and Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every industry-chain semantic layer self-explanatory and regenerate complete V3 graphs for AI 算力 and 半导体.

**Architecture:** Centralize layer labels and legends in a small presentation module shared by the layer bar and canvas. Keep ordinary layers topology-stable, while the company layer adds all company nodes and visible relations. Preserve strict graph validation and expand only the bounded V3 LLM timeout budget before regenerating local data through existing APIs.

**Tech Stack:** React, TypeScript, CSS, Vitest, Java 21, Spring Boot, JUnit 5, SQLite, Playwright CLI

---

### Task 1: Define semantic-layer presentation

**Files:**
- Create: `frontend/src/features/industry-chain/industryChainLayers.ts`
- Create: `frontend/src/features/industry-chain/industryChainLayers.test.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainLayerBar.tsx`

- [ ] **Step 1: Write failing tests for labels, missing ratings and legends**

```ts
expect(industryChainNodeLayerLabel(materialProfile, 'VALUE', 'MATERIAL')).toBe('中价值');
expect(industryChainNodeLayerLabel(materialProfile, 'TECHNOLOGY', 'MATERIAL')).toBe('成熟稳定');
expect(industryChainNodeLayerLabel(undefined, 'LOCALIZATION', 'MATERIAL')).toBe('暂无评级');
expect(industryChainLayerDefinition('BOTTLENECK').legend.map((item) => item.label))
  .toEqual(['关键卡点', '一般约束', '低约束', '暂无评级']);
```

- [ ] **Step 2: Run the test and confirm RED**

Run: `cd frontend && npm test -- industryChainLayers`

Expected: FAIL because `industryChainLayers.ts` does not exist.

- [ ] **Step 3: Implement layer definitions and label mapping**

Create a typed `INDUSTRY_CHAIN_LAYERS` record containing label, hint, description and legend. Export `industryChainLayerDefinition(layer)` and `industryChainNodeLayerLabel(profile, layer, nodeType)`; return exact Chinese labels from the approved design and return `暂无评级` when a required profile is absent.

- [ ] **Step 4: Render the current guide in the layer bar**

Render an `.ic-layer-guide` after the six buttons with the active description and legend chips. Preserve `aria-pressed` and add `aria-live="polite"` to the guide.

- [ ] **Step 5: Run tests and confirm GREEN**

Run: `cd frontend && npm test -- industryChainLayers IndustryChainView`

Expected: layer tests and existing view tests pass after updating their expectations for the guide.

### Task 2: Make company topology and node ratings visible

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainProjection.ts`
- Modify: `frontend/src/features/industry-chain/industryChainProjection.test.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] **Step 1: Write failing projection and view tests**

```ts
expect(projectSemanticGraph(graph, new Set(), 'COMPANY').nodes.map((item) => item.nodeKey))
  .toContain('company:a');
expect(screen.getByText('关键卡点')).toBeInTheDocument();
expect(screen.getByText('暂无评级')).toBeInTheDocument();
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `cd frontend && npm test -- industryChainProjection IndustryChainView`

Expected: FAIL because company nodes remain collapsed and node rating text is absent.

- [ ] **Step 3: Add company nodes only for the company layer**

In `projectSemanticGraph`, when `activeLayer === 'COMPANY'`, add every `COMPANY` node to `visibleKeys` before filtering edges. Other layers must preserve the existing default and expanded topology.

- [ ] **Step 4: Add a real rating badge to each node**

Use `industryChainNodeLayerLabel` in `IndustryChainCanvas`. Render `.ic-node-layer-badge` only outside `STRUCTURE`; include its semantic tone class and textual label. Remove duplicate pseudo-element labels for high value and bottleneck nodes.

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run: `cd frontend && npm test -- industryChainProjection IndustryChainView industryChainLayers`

Expected: all focused tests pass.

### Task 3: Polish the guide and rating hierarchy

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Test: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] **Step 1: Add a failing CSS contract assertion**

```ts
expect(styles).toMatch(/\.ic-layer-guide\s*{/);
expect(styles).toMatch(/\.ic-layer-legend\s*{/);
expect(styles).toMatch(/\.ic-node-layer-badge\s*{/);
expect(styles).toMatch(/@media\s*\(max-width:\s*760px\)[\s\S]*?\.ic-layer-guide[^}]*overflow-x:\s*auto/s);
```

- [ ] **Step 2: Run the CSS test and confirm RED**

Run: `cd frontend && npm test -- IndustryChainCreateStyles`

Expected: FAIL because the new selectors are absent.

- [ ] **Step 3: Implement the visual hierarchy**

Use a compact guide row with restrained borders, tabular legend dots and readable 8–9px labels. Position rating badges inside cards without covering names. Increase `.ic-layer-bar + .ic-graph-grid` height offset to match the guide and add narrow-screen wrapping/horizontal scrolling.

- [ ] **Step 4: Run the CSS and component tests**

Run: `cd frontend && npm test -- IndustryChainCreateStyles IndustryChainView`

Expected: both files pass.

- [ ] **Step 5: Commit and push the frontend batch**

```bash
git add frontend/src/features/industry-chain
git commit -m "feat: 完善产业链专题图层交互"
git push
```

### Task 4: Expand the bounded V3 synthesis budget

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainSynthesisAgentTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGenerationExecutor.java`

- [ ] **Step 1: Write a failing timeout-budget test**

```java
int[] timeout = {0};
int[] tokens = {0};
LlmChatClient llm = new LlmChatClient() {
    @Override public boolean isConfigured() { return true; }
    @Override public String modelName() { return "test-model"; }
    @Override public String complete(String systemPrompt, String userPrompt) { return validJson(); }
    @Override public String complete(String systemPrompt, String userPrompt,
                                     int timeoutMs, int maxOutputTokens) {
        timeout[0] = timeoutMs;
        tokens[0] = maxOutputTokens;
        return validJson();
    }
};
new IndustryChainSynthesisAgent(llm, new ObjectMapper(), new IndustryChainGraphValidator())
        .synthesize("AI算力", evidence());
assertEquals(240_000, timeout[0]);
assertEquals(9_000, tokens[0]);
```

- [ ] **Step 2: Run the service test and confirm RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=IndustryChainSynthesisAgentTest test`

Expected: FAIL because the current primary timeout is `90_000`.

- [ ] **Step 3: Implement the bounded budget and diagnostic reason**

Set `PRIMARY_TIMEOUT_MS = 240_000` and `REPAIR_TIMEOUT_MS = 180_000`. Include `compact(error.getMessage(), 240)` in the warning log without logging prompts, responses, URLs or credentials. Keep the database-facing failure message stable.

- [ ] **Step 4: Run related backend tests**

Run: `cd backend && mvn -pl finscope-service -Dtest=IndustryChainSynthesisAgentTest,IndustryChainGenerationExecutorTest,IndustryChainGraphValidatorTest test`

Expected: all related tests pass.

- [ ] **Step 5: Commit and push the backend batch**

```bash
git add backend/finscope-service
git commit -m "fix: 提升产业链V3生成稳定性"
git push
```

### Task 5: Regenerate both real graphs and clean failed revisions

**Files:**
- Runtime data: `/Users/machengqian.1/code/MyProject/data/finance.db`
- Backup: `/Users/machengqian.1/code/MyProject/data/backups/finance-before-industry-chain-v3-20260812.db`

- [ ] **Step 1: Restart the backend with current branch code**

Stop only the process listening on port 8080, then run `cd backend && mvn -pl finscope-web -am spring-boot:run`. Wait for `/api/industry-chains` to return HTTP 200.

- [ ] **Step 2: Refresh AI 算力 and wait for READY**

POST `/api/industry-chains/1/refresh`; poll `/api/industry-chains/1` until terminal status. Require `INDUSTRY_CHAIN_V3`, at least three stages, non-empty evidence, stage profiles and node profiles.

- [ ] **Step 3: Refresh 半导体 and wait for READY**

POST `/api/industry-chains/2/refresh`; apply the same terminal and completeness checks.

- [ ] **Step 4: Back up and clean failed revisions**

Create the backup with SQLite `.backup`. In one foreign-key-enabled transaction delete only `industry_chain_revision` rows for chain 2 with `status='FAILED'`; verify both chains still reference READY revisions and the backup exists.

- [ ] **Step 5: Verify database counts**

Query each current revision for schema version, evidence count, node count by type, edge count, stage profile count, company profile count and node profile count. Confirm no FAILED revision remains for chain 2.

### Task 6: Full verification and visual QA

**Files:**
- No source changes expected unless verification finds a defect.

- [ ] **Step 1: Run full frontend verification**

Run: `cd frontend && npm test -- --run && npm run build && git diff --check`

Expected: all tests and production build pass.

- [ ] **Step 2: Run backend industry-chain verification**

Run: `cd backend && mvn -pl finscope-service,finscope-web -am -Dtest='*IndustryChain*' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all industry-chain tests pass.

- [ ] **Step 3: Perform real-browser QA**

Open AI 算力 and 半导体. For each graph, switch all six layers; confirm guide text changes, badges appear, company nodes appear automatically, node selection opens V3 dossiers, and no card or inspector overlaps.

- [ ] **Step 4: Commit any verification-only tracked artifact and push**

If `frontend/tsconfig.tsbuildinfo` changes because the new test file is included, stage it with the frontend files using `chore: 更新产业链前端构建信息`; otherwise create no extra commit. Never add `output/` screenshots.
