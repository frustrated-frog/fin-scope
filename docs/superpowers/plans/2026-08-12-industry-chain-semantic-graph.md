# Industry Chain Semantic Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the industry-chain canvas to a V3 semantic graph with richer node and edge types, type-aware profiles, progressive expansion, topic layers, and an advanced inspector while preserving V2 compatibility.

**Architecture:** Keep the revision-level graph as the single published aggregate. Extend its node, edge, and research-content contracts, then derive a frontend semantic projection from the immutable graph plus local expansion and layer state. The canvas remains the primary interface; topic layers alter visual encoding while the inspector renders type-specific detail.

**Tech Stack:** Java 21, Spring Boot 2.7, Jackson, JUnit 5, React, TypeScript, Vitest, CSS.

---

## File responsibility map

- `IndustryChainResearchContent.java`: revision-level generic node profiles and existing stage/company profiles.
- `IndustryChainEdge.java`: optional relationship strength and direction explanation.
- `IndustryChainGraphValidator.java`: V3 enums, profile references, node/edge semantics.
- `IndustryChainSynthesisAgent.java`: strict V3 parsing, generation prompt, and repair contract.
- `industryChainTypes.ts`: frontend mirror of the V3 contract and layer types.
- `industryChainProjection.ts`: pure visibility, parent/child, related-node, and layer-style derivation.
- `industryChainLayout.ts`: deterministic positioning of the currently visible semantic graph.
- `IndustryChainCanvas.tsx`: graph interaction and semantic visual encoding.
- `IndustryChainLayerBar.tsx`: focused topic-layer control.
- `IndustryChainInspector.tsx`: type-aware node dossier and expand/collapse action.
- `IndustryChainView.tsx`: owns expansion, layer, focus, and selection state.
- `industry-chain.css`: semantic node system, layer bar, inspector, responsive behavior.

### Task 1: Extend the V3 backend contract

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainResearchContent.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEdge.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGraphValidator.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGraphValidatorTest.java`

- [ ] **Step 1: Write failing validator tests**

Add a graph containing `MATERIAL`, `EQUIPMENT`, `COMPONENT`, `TECHNOLOGY`, and `APPLICATION` nodes, a `DEPENDS_ON` edge, and a generic node profile. Assert validation succeeds. Add separate assertions that an unknown profile node, invalid maturity, and invalid edge strength fail.

```java
assertDoesNotThrow(() -> validator.validate(v3Graph()));
assertEquals("节点画像引用的节点无效或重复：missing",
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(v3GraphWithMissingProfile())).getMessage());
```

- [ ] **Step 2: Run the test and verify RED**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH \
  mvn -pl finscope-service -am -Dtest=IndustryChainGraphValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertion failure because V3 node profiles and edge fields do not exist.

- [ ] **Step 3: Add the minimal domain contract**

```java
private List<NodeProfile> nodeProfiles = new ArrayList<NodeProfile>();

@Data
public static class NodeProfile {
    private String nodeKey;
    private String definition;
    private String function;
    private List<String> inputs = new ArrayList<String>();
    private List<String> outputs = new ArrayList<String>();
    private List<String> costDrivers = new ArrayList<String>();
    private List<String> valueDrivers = new ArrayList<String>();
    private List<String> barriers = new ArrayList<String>();
    private List<String> coreMetrics = new ArrayList<String>();
    private List<String> risks = new ArrayList<String>();
    private String maturity;
    private String valueLevel;
    private String bottleneckLevel;
    private String localizationLevel;
}
```

Add `strength` and `directionNote` to `IndustryChainEdge` with getters and setters. Extend validator enums exactly as specified in the design, validate unique profile references, and validate optional `PRIMARY|SECONDARY` strength.

- [ ] **Step 4: Run validator tests and verify GREEN**

Run the Step 2 command. Expected: all validator tests pass.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainResearchContent.java \
  backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEdge.java \
  backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGraphValidator.java \
  backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGraphValidatorTest.java
git commit -m "feat: 扩展产业链语义图谱契约"
git push origin codex/fix-pdfbox-font-warning
```

### Task 2: Generate and persist V3 semantic content

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainSynthesisAgentTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/industrychain/IndustryChainRepositoryTest.java`

- [ ] **Step 1: Write failing synthesis and round-trip tests**

Extend the valid JSON fixture with a `TECHNOLOGY` node, `ENABLES` edge, edge strength/direction note, and one `nodeProfiles` entry. Assert:

```java
assertEquals("INDUSTRY_CHAIN_V3", graph.getSchemaVersion());
assertEquals("SCALING", graph.getResearchContent().getNodeProfiles().get(0).getMaturity());
assertEquals("PRIMARY", graph.getEdges().get(0).getStrength());
```

In the repository test, save and reload the graph and assert the new profile and edge semantics survive JSON persistence.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH \
  mvn -pl finscope-service,finscope-dao -am \
  -Dtest=IndustryChainSynthesisAgentTest,IndustryChainRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: assertions fail because synthesis still emits V2 and does not parse the new fields.

- [ ] **Step 3: Implement strict V3 parsing**

```java
private static final Set<String> EDGE_FIELDS = set("edgeKey", "sourceKey", "targetKey", "type",
        "nature", "description", "confidence", "strength", "directionNote", "evidenceRefs");
private static final Set<String> RESEARCH_FIELDS = set("overview", "stageProfiles",
        "companyProfiles", "nodeProfiles");
```

Parse node profiles with bounded text/list helpers, parse the two edge fields, and set schema version to `INDUSTRY_CHAIN_V3`. Keep V2 repository reads compatible because absent JSON fields receive domain defaults.

- [ ] **Step 4: Update the model contract prompt**

Require all nine node types, the five added edge types, complete `nodeProfiles`, and no more than 12 direct children for one parent. Explicitly instruct the model to keep company supply relationships disclosed-only and to use qualitative levels instead of inventing numeric shares.

- [ ] **Step 5: Run targeted tests and verify GREEN**

Run the Step 2 command. Expected: both test classes pass.

- [ ] **Step 6: Commit and push**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java \
  backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainSynthesisAgentTest.java \
  backend/finscope-dao/src/test/java/com/finscope/dao/industrychain/IndustryChainRepositoryTest.java
git commit -m "feat: 生成产业链V3语义内容"
git push origin codex/fix-pdfbox-font-warning
```

### Task 3: Build the frontend semantic projection

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainTypes.ts`
- Create: `frontend/src/features/industry-chain/industryChainProjection.ts`
- Create: `frontend/src/features/industry-chain/industryChainProjection.test.ts`

- [ ] **Step 1: Write failing projection tests**

```ts
expect(projectSemanticGraph(graph, new Set(), 'STRUCTURE').nodes.map((node) => node.nodeKey))
  .toEqual(['stage:up', 'stage:mid', 'stage:down']);
expect(projectSemanticGraph(graph, new Set(['stage:up']), 'STRUCTURE').nodes.map((node) => node.nodeKey))
  .toContain('material:steel');
expect(semanticNodeTone(materialProfile, 'BOTTLENECK')).toBe('high');
expect(relatedNodeKeys(graph, 'technology:harmonic')).toContain('component:reducer');
```

- [ ] **Step 2: Run test and verify RED**

```bash
cd frontend && npm test -- --run src/features/industry-chain/industryChainProjection.test.ts
```

Expected: module-not-found failure.

- [ ] **Step 3: Add V3 frontend types**

Add the five node types, five relationship types, edge semantics, `IndustryChainNodeProfile`, and:

```ts
export type IndustryChainLayer = 'STRUCTURE' | 'VALUE' | 'BOTTLENECK'
  | 'TECHNOLOGY' | 'LOCALIZATION' | 'COMPANY';
```

- [ ] **Step 4: Implement pure projection helpers**

`projectSemanticGraph` always includes ordered stages, includes direct semantic children only for expanded parents, caps each parent's direct children at 12, and retains edges whose endpoints are visible. `semanticNodeTone` maps the active layer to the corresponding profile field without changing topology.

- [ ] **Step 5: Run projection tests and verify GREEN**

Run the Step 2 command. Expected: all projection tests pass.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/features/industry-chain/industryChainTypes.ts \
  frontend/src/features/industry-chain/industryChainProjection.ts \
  frontend/src/features/industry-chain/industryChainProjection.test.ts
git commit -m "feat: 增加产业链语义投影模型"
git push origin codex/fix-pdfbox-font-warning
```

### Task 4: Upgrade layout and canvas interactions

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.ts`
- Modify: `frontend/src/features/industry-chain/industryChainLayout.test.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Test: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] **Step 1: Write failing UI and layout tests**

Assert that the initial canvas excludes semantic children, expanding an upstream stage reveals its material and equipment children, double-click collapses it, and focused layout keeps the selected stage column stable.

```tsx
fireEvent.doubleClick(screen.getByRole('button', { name: /上游材料/ }));
expect(screen.getByRole('button', { name: /特种钢/ })).toBeInTheDocument();
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd frontend && npm test -- --run \
  src/features/industry-chain/industryChainLayout.test.ts \
  src/features/industry-chain/IndustryChainView.test.tsx
```

Expected: tests fail because expansion is company-only.

- [ ] **Step 3: Generalize layout for semantic node groups**

Position stable stage lanes first. Within each lane, order visible nodes by `MATERIAL`, `EQUIPMENT`, `COMPONENT`, `PRODUCT`, `TECHNOLOGY`, `APPLICATION`, `COMPANY`; route `SUBSTITUTES` and `COMPETES_WITH` as dashed cross-links and preserve current stage-flow routing.

- [ ] **Step 4: Generalize canvas expansion**

Replace `expandedCompanyKeys` with `expandedNodeKeys`. Add double-click and an explicit compact expand control, new node labels, layer/tone classes, relationship labels for selected edges, and keep keyboard selection behavior.

- [ ] **Step 5: Run targeted tests and verify GREEN**

Run the Step 2 command. Expected: all targeted tests pass.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/features/industry-chain/industryChainLayout.ts \
  frontend/src/features/industry-chain/industryChainLayout.test.ts \
  frontend/src/features/industry-chain/IndustryChainCanvas.tsx \
  frontend/src/features/industry-chain/IndustryChainView.test.tsx
git commit -m "feat: 支持产业图谱语义展开"
git push origin codex/fix-pdfbox-font-warning
```

### Task 5: Add topic layers and type-aware inspector

**Files:**
- Create: `frontend/src/features/industry-chain/IndustryChainLayerBar.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainInspector.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.tsx`
- Create: `frontend/src/features/industry-chain/IndustryChainInspector.test.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] **Step 1: Write failing interaction tests**

Assert six layer buttons exist, the bottleneck layer marks a high-bottleneck node, switching layers keeps visible node count stable, and a technology-node inspector shows maturity, inputs, outputs, barriers, related nodes, and an expand/collapse action.

- [ ] **Step 2: Run tests and verify RED**

```bash
cd frontend && npm test -- --run \
  src/features/industry-chain/IndustryChainView.test.tsx \
  src/features/industry-chain/IndustryChainInspector.test.tsx
```

Expected: layer controls and generic profile dossier are absent.

- [ ] **Step 3: Implement the layer bar**

```ts
[
  ['STRUCTURE', '产业结构'], ['VALUE', '价值分配'], ['BOTTLENECK', '产业瓶颈'],
  ['TECHNOLOGY', '技术路线'], ['LOCALIZATION', '国产替代'], ['COMPANY', '公司生态']
]
```

Render these values in a single-select `aria-label="产业专题图层"` control.

- [ ] **Step 4: Implement the semantic inspector**

Resolve the generic profile by `nodeKey`, render type-specific headings, profile facts and phrase groups, list related nodes as selectable buttons, and expose `onToggleExpanded` when a node has direct children. Preserve existing stage/company profile fallbacks and evidence rendering.

- [ ] **Step 5: Wire state in the view**

Own `activeLayer` and `expandedNodeKeys` in `IndustryChainView`. Reset both when opening another chain, pass them to canvas and inspector, and keep topology state unchanged while switching layers.

- [ ] **Step 6: Run targeted tests and verify GREEN**

Run the Step 2 command. Expected: all targeted tests pass.

- [ ] **Step 7: Commit and push**

```bash
git add frontend/src/features/industry-chain/IndustryChainLayerBar.tsx \
  frontend/src/features/industry-chain/IndustryChainInspector.tsx \
  frontend/src/features/industry-chain/IndustryChainInspector.test.tsx \
  frontend/src/features/industry-chain/IndustryChainView.tsx \
  frontend/src/features/industry-chain/IndustryChainView.test.tsx
git commit -m "feat: 增加产业图谱专题图层"
git push origin codex/fix-pdfbox-font-warning
```

### Task 6: Deliver the advanced responsive visual system

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Test: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] **Step 1: Add failing style contract assertions**

Read the stylesheet and assert it contains semantic classes for all new node types, six layer states, explicit expand controls, reduced motion, the desktop inspector width, and mobile bottom-drawer behavior.

- [ ] **Step 2: Run style test and verify RED**

```bash
cd frontend && npm test -- --run src/features/industry-chain/IndustryChainCreateStyles.test.ts
```

Expected: missing selector assertions fail.

- [ ] **Step 3: Implement the visual system**

Use the existing graphite/teal foundation. Keep stages dominant; give materials/equipment compact technical cards, technologies cyan-blue route styling, applications quieter terminal styling, and companies small nameplates. Encode high value with a gold top rule, high bottleneck with amber border plus text tag, substitutions with dashed branching edges, and non-active layer nodes with reduced contrast.

- [ ] **Step 4: Implement responsive behavior**

At desktop widths keep directory, canvas, and 340px inspector. Below 1100px overlay the inspector. Below 760px keep the backbone reader and render the inspector as a bottom section; keep topic layers horizontally scrollable and preserve visible focus outlines.

- [ ] **Step 5: Run frontend targeted tests and build**

```bash
cd frontend && npm test -- --run src/features/industry-chain
npm run build
```

Expected: industry-chain tests pass and Vite build succeeds, allowing the existing chunk-size warning.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/features/industry-chain/industry-chain.css \
  frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts
git commit -m "style: 完善产业语义图谱视觉层级"
git push origin codex/fix-pdfbox-font-warning
```

### Task 7: Full verification and real-page acceptance

**Files:**
- Modify only files required by defects found during verification.

- [ ] **Step 1: Run backend full tests**

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH mvn test
```

Expected: reactor `BUILD SUCCESS`, zero failures and errors.

- [ ] **Step 2: Run frontend full tests and production build**

```bash
cd frontend && npm test
npm run build
```

Expected: all Vitest files pass and Vite build succeeds.

- [ ] **Step 3: Run real V3 generation**

Start the packaged backend on an unused port, point a Vite dev server at it, refresh one existing industry chain, and confirm the API returns `INDUSTRY_CHAIN_V3`, at least one new semantic node type, and at least one `nodeProfile`.

- [ ] **Step 4: Inspect desktop and mobile views**

Using the Playwright CLI, verify at 1440×1000 and 390×844: default backbone readability; stage expansion and collapse; all six topic layers; technology and company inspectors; focus mode and search; and no unintended horizontal overflow outside the graph canvas.

- [ ] **Step 5: Verify repository state**

```bash
git diff --check
git status --short --branch
git rev-parse HEAD
git rev-parse origin/codex/fix-pdfbox-font-warning
```

Expected: no whitespace errors, clean worktree, and matching local/remote heads.
