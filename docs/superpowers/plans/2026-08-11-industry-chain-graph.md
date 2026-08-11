# Industry Chain Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an evidence-backed industry-chain graph workspace with versioned SQLite storage, asynchronous generation, an elegant layered React graph, and stock-focus navigation.

**Architecture:** Introduce a focused `industrychain` domain that stores immutable graph revisions containing nodes, directed edges, and evidence. A bounded refresh executor reuses `SearchEvidenceGateway`, freezes source content, asks the existing LLM client for strict graph JSON, validates it, and atomically publishes a revision. The frontend renders a dependency-free layered SVG/HTML graph so the first release adds no graph framework and remains easy to test.

**Tech Stack:** Java 21, Spring Boot 2.7, JdbcTemplate/SQLite, Jackson, JUnit 5, React 18, TypeScript, Vite, Vitest, Testing Library, CSS/SVG.

---

## File map

Backend files to create:

- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChain.java`: chain metadata.
- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainRevision.java`: asynchronous revision state.
- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainGraph.java`: published graph aggregate.
- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainNode.java`: typed node.
- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEdge.java`: typed directed edge.
- `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEvidence.java`: frozen evidence row.
- `backend/finscope-dao/src/main/java/com/finscope/dao/industrychain/IndustryChainRepository.java`: revision-aware persistence.
- `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGraphValidator.java`: graph and evidence contract.
- `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainEvidenceCollector.java`: shared-search collection.
- `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java`: strict LLM conversion.
- `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainRefreshExecutor.java`: asynchronous workflow.
- `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainService.java`: application use cases.
- `backend/finscope-web/src/main/java/com/finscope/web/controller/IndustryChainController.java`: REST endpoints.
- `backend/finscope-web/src/main/java/com/finscope/web/request/CreateIndustryChainRequest.java`: create input.
- `backend/finscope-web/src/main/java/com/finscope/web/response/IndustryChainResponse.java`: stable API projection.

Frontend files to create:

- `frontend/src/features/industry-chain/industryChainTypes.ts`: API/view types.
- `frontend/src/features/industry-chain/industryChainLayout.ts`: deterministic layered layout and focus projection.
- `frontend/src/features/industry-chain/IndustryChainView.tsx`: workspace state and API orchestration.
- `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`: SVG edges and HTML nodes.
- `frontend/src/features/industry-chain/IndustryChainInspector.tsx`: node/edge details.
- `frontend/src/features/industry-chain/IndustryChainView.test.tsx`: workspace tests.
- `frontend/src/features/industry-chain/industryChainLayout.test.ts`: layout tests.
- `frontend/src/features/industry-chain/industry-chain.css`: visual system and responsive layout.

Existing files to modify:

- `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- `frontend/src/shared/types/index.ts`
- `frontend/src/app/AppShell.tsx`
- `frontend/src/App.tsx`
- `frontend/src/features/watchlist/StockSupplyChainPanel.tsx`
- `frontend/src/styles.css`

## Task 1: Graph domain and validator

**Files:** domain types and `IndustryChainGraphValidator`, plus `IndustryChainGraphValidatorTest`.

- [ ] **Step 1: Write failing validator tests**

Cover a valid three-stage path and rejection of self-loops, unknown endpoint keys, unknown evidence references, cyclic stage flow, and non-disclosed `SUPPLIES_TO` edges.

```java
@Test
void rejectsInferredCompanySupplyRelationship() {
    IndustryChainGraph graph = validGraph();
    graph.getEdges().get(0).setType("SUPPLIES_TO");
    graph.getEdges().get(0).setNature("INFERRED");
    assertThrows(IllegalArgumentException.class, () -> validator.validate(graph));
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=IndustryChainGraphValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because the graph types and validator do not exist.

- [ ] **Step 3: Implement minimal domain and validator**

Use the exact enums encoded as strings in the design and require at least three ordered stages plus an upstream-to-terminal path. Domain data objects follow the repository's current explicit accessor style.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 建立产业链图谱领域契约`

## Task 2: Versioned SQLite persistence

**Files:** `DatabaseInitializer`, `IndustryChainRepository`, `IndustryChainRepositoryTest`.

- [ ] **Step 1: Write failing repository tests**

Tests create a chain and revision, publish a valid graph, verify that listing returns the current version, and verify that marking a later revision failed leaves the earlier graph unchanged.

```java
IndustryChainRevision first = repository.createRevision(chain.getId());
repository.publish(first, graph("AI算力", "v1"));
IndustryChainRevision failed = repository.createRevision(chain.getId());
repository.fail(failed, "SYNTHESIS_FAILED", "生成失败");
assertEquals("v1", repository.findPublishedGraph(chain.getId()).orElseThrow().getSchemaVersion());
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=IndustryChainRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because repository and tables do not exist.

- [ ] **Step 3: Add schema and repository**

Create the five tables from the design with foreign keys, unique stable keys per revision, an index on `industry_chain_revision(chain_id,id DESC)`, and a partial unique index for one `RUNNING` revision per chain. `publish(...)` inserts graph rows, sets `industry_chain.current_revision_id`, and completes the run inside one transaction.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 持久化产业链图谱修订`

## Task 3: Evidence collection and strict synthesis

**Files:** collector, synthesis agent, and their tests.

- [ ] **Step 1: Write failing collector and agent tests**

The collector must perform several bounded deep-search queries, deduplicate canonical URLs, acquire full text for only the first three results, and assign `E1...En`. The agent test supplies strict JSON and asserts nodes, edges, evidence references, and one repair attempt after invalid output.

```java
when(llm.complete(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(validGraphJson());
IndustryChainGraph graph = agent.synthesize("AI算力", evidence());
assertEquals(3, graph.getStages().size());
validator.validate(graph);
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=IndustryChainEvidenceCollectorTest,IndustryChainSynthesisAgentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure for missing collector and agent.

- [ ] **Step 3: Implement bounded evidence and synthesis**

Queries cover industry overview, upstream inputs, core manufacturing, downstream applications, and representative A-share companies. The agent consumes only frozen evidence, returns strict root fields `summary`, `limitations`, `nodes`, `edges`, and passes the standalone validator before returning.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 生成可追溯产业链图谱`

## Task 4: Async service and REST contract

**Files:** service, executor, controller, request/response, `AppConfig`, and service/controller tests.

- [ ] **Step 1: Write failing service and controller tests**

Cover create-and-queue, duplicate name reuse, active revision conflict, stale revision expiry, successful publication, failed refresh preserving old graph, list, detail, refresh, revisions, and `focus?stockCode=` filtering.

```java
mockMvc.perform(post("/api/industry-chains")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"AI算力\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.chain.name").value("AI算力"))
        .andExpect(jsonPath("$.data.revision.status").value("RUNNING"));
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=IndustryChainServiceTest,IndustryChainControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure for missing API classes.

- [ ] **Step 3: Implement service and endpoints**

Add a named `industryChainExecutor` with one worker and a bounded queue. Normalize names by trimming repeated whitespace, reject blank or over-60-character names, and never place raw downstream errors in responses.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 提供产业链图谱异步接口`

## Task 5: Deterministic frontend layout

**Files:** `industryChainTypes.ts`, `industryChainLayout.ts`, `industryChainLayout.test.ts`.

- [ ] **Step 1: Write failing layout tests**

Assert that stages sort by `stageOrder`, products sit inside their stage lane, collapsed companies produce a company-count summary, focused nodes return upstream/downstream reachable keys, and cyclic malformed input does not recurse forever.

```ts
const layout = layoutIndustryGraph(graphFixture);
expect(layout.nodes.find(node => node.key === 'product:gpu')?.column).toBe(1);
expect(focusNeighborhood(graphFixture, 'company:300308', 2)).toContain('product:optical-module');
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainLayout.test.ts`

Expected: FAIL because the layout module does not exist.

- [ ] **Step 3: Implement layout helpers**

Return stable coordinates and edge paths without reading the DOM. Keep focus traversal iterative with a visited set. Do not add a graph dependency in the first release.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 计算产业链分层图布局`

## Task 6: Workspace behavior

**Files:** `IndustryChainView.tsx`, `IndustryChainCanvas.tsx`, `IndustryChainInspector.tsx`, `IndustryChainView.test.tsx`, shared `View` type, `AppShell`, and `App`.

- [ ] **Step 1: Write failing workspace tests**

Cover empty suggestions, create request, async polling, selecting a chain, selecting a node, search positioning, full/focus mode, company expansion, retry, and old-graph visibility during refresh.

```tsx
await user.click(screen.getByRole('button', { name: 'AI 算力' }));
expect(api).toHaveBeenCalledWith('/api/industry-chains', expect.objectContaining({ method: 'POST' }));
expect(await screen.findByRole('region', { name: 'AI 算力产业链图谱' })).toBeInTheDocument();
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx`

Expected: FAIL because components do not exist.

- [ ] **Step 3: Implement workspace and navigation**

Add `industryChain` to `View`, add “Industry Graph / 产业图谱 / IC” under the decision group, render the view from `App`, and keep all API loading local to the feature. The canvas uses SVG for directed paths and absolutely positioned semantic buttons for nodes.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2 plus `cd frontend && npm test -- src/app/AppShell.test.tsx`; expected PASS.

Commit: `feat: 增加产业链图谱工作台`

## Task 7: High-craft visual system and responsive reader

**Files:** `industry-chain.css`, `frontend/src/styles.css`, visual assertions in `IndustryChainView.test.tsx`.

- [ ] **Step 1: Add failing visual-contract assertions**

Assert the workspace exposes named navigation, graph region, inspector, relation legend, focus controls, and mobile stage sections. Add stylesheet checks for reduced motion and the mobile breakpoint.

```ts
expect(styles).toMatch(/@media \(prefers-reduced-motion: reduce\)/);
expect(styles).toMatch(/@media \(max-width: 760px\)/);
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx`

Expected: FAIL because visual contracts and stylesheet do not exist.

- [ ] **Step 3: Implement the confirmed design language**

Use the deep navy canvas, restrained grid, stage color tokens, amber stock focus, dashed inferred edges, 180–260ms transitions, inspector slide-in, and a vertical mobile stage reader. Preserve light-theme legibility and keyboard focus rings.

- [ ] **Step 4: Verify GREEN and production build**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx && npm run build`

Expected: tests PASS and Vite build succeeds.

Commit: `feat: 打磨产业链图谱视觉体验`

## Task 8: Stock-focus handoff

**Files:** `StockSupplyChainPanel.tsx`, its tests, `App.tsx`, and `IndustryChainView.tsx`.

- [ ] **Step 1: Write failing navigation tests**

Assert that a stock snapshot exposes “在完整图谱中查看”, the action switches to the industry workspace with the stock code, and the graph either focuses a mapped company or shows a bounded not-mapped message.

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- src/features/watchlist/StockSupplyChainPanel.test.tsx src/features/industry-chain/IndustryChainView.test.tsx`

Expected: FAIL because no handoff callback exists.

- [ ] **Step 3: Add explicit handoff state**

Pass an `onOpenIndustryGraph(stockCode)` callback from `App` through the watchlist stock-detail boundary. Store a one-shot focus intent in `App`, consume it in `IndustryChainView`, and do not infer a company edge when the backend has no mapping.

- [ ] **Step 4: Verify GREEN and commit**

Run the command from Step 2; expected PASS.

Commit: `feat: 联通自选股票与产业图谱`

## Task 9: Full verification and documentation

**Files:** `README.md`, relevant Chinese architecture/feature documentation.

- [ ] **Step 1: Update docs**

Document the new workspace, API endpoints, graph semantics, the distinction between industry logic and disclosed supply relationships, and the commands needed to run tests.

- [ ] **Step 2: Run backend verification**

Run: `cd backend && mvn test`

Expected: all backend tests PASS.

- [ ] **Step 3: Run frontend verification**

Run: `cd frontend && npm test && npm run build`

Expected: all frontend tests PASS and production build succeeds.

- [ ] **Step 4: Review diff and safety**

Run: `git diff --check && git status --short && rg -n 'api-key|fc-[A-Za-z0-9]+' docs backend frontend --glob '!application.yml'`

Expected: no whitespace errors, only intended files, and no newly copied credentials.

- [ ] **Step 5: Commit and push**

Commit: `docs: 补充产业链图谱使用说明`

Push: `git push github codex/industry-chain-graph`
