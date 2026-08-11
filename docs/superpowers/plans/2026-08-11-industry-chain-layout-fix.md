# Industry Chain Layout Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复产业链图谱箭头交叉覆盖和展开公司后卡片覆盖后续节点的问题。

**Architecture:** 保持当前无第三方图引擎的轻量实现，把布局计算改为“阶段列内的产品分组”，让展开公司参与父产品所在组的高度计算。边路由按主链、阶段归属、公司父子和普通关系分流为稳定的折线路径，避免穿过节点区域。

**Tech Stack:** React 18、TypeScript 5.6、SVG、Vitest、Testing Library、Vite

---

### Task 1: 用测试固定展开公司的占位行为

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.test.ts`
- Test: `frontend/src/features/industry-chain/industryChainLayout.test.ts`

- [ ] **Step 1: 写失败测试**

在测试图中增加同列第二个产品和第二家公司，展开第一个产品后断言公司位于父产品下方，第二个产品位于所有展开公司下方：

```ts
it('reserves vertical space for expanded companies inside their product group', () => {
  const expandedGraph = withSecondProductAndCompany(graph);
  const layout = layoutIndustryGraph(expandedGraph, {
    expandedCompanyKeys: new Set(['product:gpu'])
  });
  const product = layout.nodes.find((node) => node.nodeKey === 'product:gpu')!;
  const company = layout.nodes.find((node) => node.nodeKey === 'company:300308')!;
  const nextProduct = layout.nodes.find((node) => node.nodeKey === 'product:hbm')!;

  expect(company.y).toBeGreaterThanOrEqual(product.y + product.height + 32);
  expect(nextProduct.y).toBeGreaterThanOrEqual(company.y + company.height + 24);
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainLayout.test.ts`

Expected: FAIL，第二个产品的 `y` 小于展开公司底部，复现截图中的覆盖。

- [ ] **Step 3: 提交测试红灯证据，不提交代码**

保留失败输出用于本轮记录，继续 Task 2。

### Task 2: 实现列内产品分组布局

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.ts`
- Test: `frontend/src/features/industry-chain/industryChainLayout.test.ts`

- [ ] **Step 1: 用列游标替代按类型全局行号**

为每列维护 `columnY`，按阶段列和图谱原始顺序遍历产品；每个产品写入后推进卡片高度、展开按钮高度和组间距。展开时立即写入该产品的公司：

```ts
const PRODUCT_START_Y = 184;
const PRODUCT_HEIGHT = 64;
const COMPANY_HEIGHT = 64;
const TOGGLE_HEIGHT = 28;
const GROUP_GAP = 32;
const COMPANY_GAP = 12;

const columnY = new Map(stages.map((_, column) => [column, PRODUCT_START_Y]));
for (const product of productsInGraphOrder) {
  const y = columnY.get(column)!;
  positionedNodes.push(position(product, column, y, PRODUCT_HEIGHT));
  let nextY = y + PRODUCT_HEIGHT + (companyCounts.has(product.nodeKey) ? TOGGLE_HEIGHT : 0) + GROUP_GAP;
  if (expanded.has(product.nodeKey)) {
    for (const company of companiesByParent.get(product.nodeKey) ?? []) {
      positionedNodes.push(position(company, column, nextY, COMPANY_HEIGHT));
      nextY += COMPANY_HEIGHT + COMPANY_GAP;
    }
    nextY += GROUP_GAP - COMPANY_GAP;
  }
  columnY.set(column, nextY);
}
```

- [ ] **Step 2: 运行布局测试并确认通过**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainLayout.test.ts`

Expected: PASS，展开公司和后续产品的边界至少保留计划中的间距。

### Task 3: 用测试固定分层连线路由

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.test.ts`
- Modify: `frontend/src/features/industry-chain/industryChainLayout.ts`

- [ ] **Step 1: 写失败测试**

为 `PositionedIndustryEdge` 增加 `route` 分类，断言阶段主链为 `stage-flow`、公司父子为 `company-link`、产品归属为 `stage-membership`，并断言路径使用折线命令而不是当前贝塞尔曲线：

```ts
expect(edgeByKey(layout, 'flow:1').route).toBe('stage-flow');
expect(edgeByKey(layout, 'company:gpu').route).toBe('company-link');
expect(edgeByKey(layout, 'belongs:gpu').route).toBe('stage-membership');
expect(edgeByKey(layout, 'belongs:gpu').path).not.toContain(' C ');
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainLayout.test.ts`

Expected: FAIL，当前边没有 `route` 且路径包含贝塞尔命令 `C`。

- [ ] **Step 3: 实现关系分流**

新增 `routeEdge`，根据关系和节点类型生成正交 SVG path：

```ts
type IndustryEdgeRoute = 'stage-flow' | 'stage-membership' | 'company-link' | 'cross-link';

function orthogonalPath(source: PositionedIndustryNode, target: PositionedIndustryNode, channelX: number) {
  const startX = source.x + source.width;
  const startY = source.y + source.height / 2;
  const endX = target.x + target.width;
  const endY = target.y + target.height / 2;
  return `M ${startX} ${startY} L ${channelX} ${startY} L ${channelX} ${endY} L ${endX} ${endY}`;
}
```

阶段主链继续连接左右边界并保持水平；产品归属走列左侧通道；公司关系走列右侧短通道；其他关系按列方向选择外侧端点和通道。每类通道按边序号错开 8px，避免完全重合。

- [ ] **Step 4: 运行布局测试并确认通过**

Run: `cd frontend && npm test -- src/features/industry-chain/industryChainLayout.test.ts`

Expected: PASS，所有边都有稳定路由分类和非贝塞尔路径。

### Task 4: 调整 SVG 呈现层级和箭头间距

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Test: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] **Step 1: 写渲染失败测试**

在 `IndustryChainView.test.tsx` 增加含产品与公司的工作区，展开公司后断言画布输出公司节点，边 path 带有路由类：

```ts
expect(container.querySelector('.ic-edge--stage-flow')).toBeInTheDocument();
expect(container.querySelector('.ic-edge--company-link')).toBeInTheDocument();
expect(await screen.findByRole('button', { name: /中际旭创/ })).toBeInTheDocument();
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx`

Expected: FAIL，边尚未输出路由类。

- [ ] **Step 3: 输出路由类并优化层级**

`IndustryChainCanvas.tsx` 为每条边添加 `ic-edge--${edge.route}`；`industry-chain.css` 降低普通关系对比度、强化阶段主链，让公司短链保持金色但不穿越其他卡片。marker 的 `refX` 调整到箭头尖端停在卡片边界，节点继续位于 SVG 之上。

- [ ] **Step 4: 运行组件测试并确认通过**

Run: `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx`

Expected: PASS，展开交互、选择和搜索行为保持正常。

### Task 5: 全量验证和真实页面回归

**Files:**
- Verify: `frontend/src/features/industry-chain/industryChainLayout.ts`
- Verify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Verify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] **Step 1: 运行前端全量测试**

Run: `cd frontend && npm test`

Expected: 所有测试通过，无失败。

- [ ] **Step 2: 运行生产构建**

Run: `cd frontend && npm run build`

Expected: TypeScript 与 Vite 构建成功。

- [ ] **Step 3: 浏览器回归**

打开 `http://localhost:5173`，进入 `Industry Graph · 产业链图谱`，打开 `AI 算力`，依次展开 `AI芯片` 和 `高带宽内存(HBM)` 的代表公司。验证卡片互不覆盖、主链箭头不穿过阶段卡片、同列连线走卡片外侧、控制台 0 errors，并保存截图到 `output/playwright/ai-compute-industry-chain-layout-fixed.png`。

- [ ] **Step 4: 提交并推送**

```bash
git add frontend/src/features/industry-chain/industryChainLayout.ts \
  frontend/src/features/industry-chain/industryChainLayout.test.ts \
  frontend/src/features/industry-chain/IndustryChainCanvas.tsx \
  frontend/src/features/industry-chain/IndustryChainView.test.tsx \
  frontend/src/features/industry-chain/industry-chain.css
git commit -m "fix: 修复产业链图谱节点和箭头覆盖"
git push
```
