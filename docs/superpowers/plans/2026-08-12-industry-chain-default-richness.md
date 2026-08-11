# 产业链默认丰富展示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让所有产业链默认在图谱中直接展示核心结构信息，同时使旧 V2 图谱通过通用环节摘要获得足够内容密度。

**Architecture:** 在 `industryChainProjection.ts` 集中实现与行业无关的默认语义节点选择和 V2 环节摘要提取。画布只消费投影结果与摘要，不根据产业链名称分支；布局根据环节摘要动态增加卡片高度，移动端复用同一份摘要。

**Tech Stack:** React、TypeScript、Vitest、CSS、Vite

---

### Task 1: 默认核心语义节点投影

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainProjection.ts`
- Test: `frontend/src/features/industry-chain/industryChainProjection.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
it('shows core semantic nodes by default and keeps companies collapsed', () => {
  const projected = projectSemanticGraph(graph, new Set(), 'STRUCTURE');
  expect(projected.nodes.map((item) => item.nodeKey)).toEqual(expect.arrayContaining([
    'stage:up', 'stage:mid', 'stage:down', 'material:steel', 'equipment:cnc', 'component:reducer'
  ]));
  expect(projected.nodes.map((item) => item.nodeKey)).not.toContain('company:a');
});

it('distributes the default semantic budget across stages', () => {
  const projected = projectSemanticGraph(largeGraph, new Set(), 'STRUCTURE');
  expect(projected.nodes.length).toBeLessThanOrEqual(25);
  expect(visibleStageChildren(projected, 'stage:up').length).toBeGreaterThan(0);
  expect(visibleStageChildren(projected, 'stage:mid').length).toBeGreaterThan(0);
  expect(visibleStageChildren(projected, 'stage:down').length).toBeGreaterThan(0);
});
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainProjection.test.ts`

Expected: FAIL because the current projection returns only stage nodes before explicit expansion.

- [ ] **Step 3: Implement fair default projection**

```ts
const MAX_DEFAULT_NODES = 25;

function defaultSemanticKeys(graph: IndustryChainGraph, stages: IndustryChainNode[]) {
  const budget = Math.max(0, MAX_DEFAULT_NODES - stages.length);
  const queues = stages.map((stage) => directSemanticNeighbors(graph, stage.nodeKey)
    .filter((node) => node.type !== 'COMPANY'));
  return takeRoundRobin(queues, budget).map((node) => node.nodeKey);
}
```

Order each queue by semantic value: primary edge, high bottleneck/value profile, confidence, node type, then name. Explicit expansion continues to reveal at most 12 direct neighbors and may include companies.

- [ ] **Step 4: Run projection tests and verify GREEN**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainProjection.test.ts`

Expected: all projection tests PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/src/features/industry-chain/industryChainProjection.ts frontend/src/features/industry-chain/industryChainProjection.test.ts
git commit -m "feat: 默认展开产业链核心语义节点"
git push origin codex/fix-pdfbox-font-warning
```

### Task 2: V2 环节摘要上图

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainProjection.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Modify: `frontend/src/features/industry-chain/industryChainLayout.ts`
- Test: `frontend/src/features/industry-chain/industryChainProjection.test.ts`
- Test: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] **Step 1: Write failing summary tests**

```ts
it('extracts compact stage highlights without industry-specific rules', () => {
  expect(stageGraphHighlights(v2Graph, 'stage:up')).toEqual([
    { label: '核心瓶颈', value: '高精密减速器寿命与一致性仍待提升' },
    { label: '价值获取', value: '依靠精密制造和规模交付获取溢价' },
    { label: '关键指标', value: '国产化率' }
  ]);
});
```

Add a component assertion that the three labels are visible without selecting a node.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainProjection.test.ts src/features/industry-chain/IndustryChainView.test.tsx`

Expected: FAIL because `stageGraphHighlights` and in-card highlights do not exist.

- [ ] **Step 3: Implement generic highlight extraction**

```ts
export type IndustryChainStageHighlight = { label: string; value: string };

export function stageGraphHighlights(graph: IndustryChainGraph, stageKey: string) {
  const profile = graph.researchContent?.stageProfiles.find((item) => item.nodeKey === stageKey);
  if (!profile) return [];
  return compactHighlights([
    ['核心瓶颈', profile.bottleneck],
    ['价值获取', profile.valueCapture],
    ['关键指标', profile.coreMetrics[0]],
    ['行业壁垒', profile.barriers[0]],
    ['关键变量', profile.keyVariables[0]]
  ], 3);
}
```

Render the highlights inside stage cards only when that stage has no default semantic child nodes. Use `<dl className="ic-node-highlights">` so labels and values remain accessible. Increase stage node height from 72 to 154 only for cards with highlights and move the semantic lane start below the tallest stage card.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainProjection.test.ts src/features/industry-chain/IndustryChainView.test.tsx`

Expected: focused tests PASS.

- [ ] **Step 5: Commit and push**

```bash
git add frontend/src/features/industry-chain/industryChainProjection.ts frontend/src/features/industry-chain/IndustryChainCanvas.tsx frontend/src/features/industry-chain/industryChainLayout.ts frontend/src/features/industry-chain/industryChainProjection.test.ts frontend/src/features/industry-chain/IndustryChainView.test.tsx
git commit -m "feat: 将旧产业链环节要点呈现在图谱"
git push origin codex/fix-pdfbox-font-warning
```

### Task 3: 内容密度视觉系统与响应式验收

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Modify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] **Step 1: Write the failing style contract tests**

```ts
expect(css).toContain('.ic-node-highlights');
expect(css).toContain('.ic-node-highlight-label');
expect(css).toContain('@media (max-width: 760px)');
expect(css).toContain('text-wrap: balance');
```

- [ ] **Step 2: Run style tests and verify RED**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainCreateStyles.test.ts`

Expected: FAIL because the richer stage-card visual contract is missing.

- [ ] **Step 3: Implement the visual hierarchy**

Add a restrained “technical dossier” treatment: stage title remains primary, highlights use compact label/value rows, one amber accent is reserved for the bottleneck row, and the card uses subtle inner separators rather than nested boxes. On mobile, render the same rows below each stage header with full-width readable text; preserve reduced-motion, reduced-transparency and focus-visible behavior.

- [ ] **Step 4: Run full verification**

Run: `cd frontend && npm test && npm run build`

Expected: all frontend tests PASS and Vite build succeeds.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 5: Browser verification**

At 1440×1000, verify the graph shows V3 default semantic nodes or V2 stage highlights before any click. At 390×844, verify `document.documentElement.scrollWidth === window.innerWidth`, the stage summaries remain readable, and the browser console has no errors.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/features/industry-chain/industry-chain.css frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts
git commit -m "style: 提升产业图谱默认内容层级"
git push origin codex/fix-pdfbox-font-warning
```
