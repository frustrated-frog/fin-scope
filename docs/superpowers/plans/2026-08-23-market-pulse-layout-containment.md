# Market Pulse Layout Containment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep every Market Pulse section within the workspace width so the top controls and right-side event column remain visible.

**Architecture:** Constrain the Market Pulse page's single Grid track to its parent width with `minmax(0, 1fr)`, allow warning URLs to wrap, and switch the decision area to one column at `1260px` after accounting for the fixed sidebar. Protect all three width contracts with a focused CSS test.

**Tech Stack:** React 18, TypeScript, Vitest, Testing Library, Vite

---

### Task 1: Add regression coverage

**Files:**
- Create: `frontend/src/features/market-pulse/MarketPulseResponsive.test.ts`
- Test: `frontend/src/features/market-pulse/MarketPulseResponsive.test.ts`

- [ ] **Step 1: Assert the page Grid has a shrinkable explicit column**

Read `src/styles.css`, extract the `.market-pulse-page` rule, and assert:

```ts
expect(pageRule).toContain('grid-template-columns: minmax(0, 1fr)');
expect(warningRule).toContain('overflow-wrap: anywhere');
expect(styles).toMatch(/@media\s*\(max-width:\s*1100px\)\s*{\s*\.market-intel-hero/s);
expect(marketPulseStyles).toMatch(/@media\s*\(max-width:\s*1260px\)\s*{\s*\.market-pulse-hero[\s\S]*?\.market-pulse-decision-grid\s*{[^}]*grid-template-columns:\s*1fr/s);
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `npm test -- --run src/features/market-pulse/MarketPulseResponsive.test.ts`

Expected: FAIL because `.market-pulse-page` currently relies on an auto-sized implicit Grid column.

### Task 2: Implement the minimal layout constraint

**Files:**
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/market-pulse/MarketPulseResponsive.test.ts`

- [ ] **Step 1: Constrain the page Grid track**

Add the explicit column declaration to `.market-pulse-page`:

```css
.market-pulse-page {
  grid-template-columns: minmax(0, 1fr);
}
```

Add `overflow-wrap: anywhere` to `.market-pulse-warning` so provider URLs cannot recreate horizontal overflow through unbreakable text.

Raise the Market Pulse responsive breakpoint from `1100px` to `1260px` so the decision grid stacks before its two columns reach their combined minimum width.

- [ ] **Step 2: Run focused tests and verify GREEN**

Run: `npm test -- --run src/features/market-pulse/MarketPulseResponsive.test.ts`

Expected: the layout containment test PASS.

### Task 3: Verify and deliver

**Files:**
- Verify: `frontend/src/styles.css`
- Verify: `frontend/src/features/market-pulse/MarketPulseResponsive.test.ts`

- [ ] **Step 1: Run the full frontend suite**

Run: `npm test -- --run`

Expected: all frontend tests PASS.

- [ ] **Step 2: Run the production build**

Run: `npm run build`

Expected: TypeScript compilation and Vite production build exit with status 0.

- [ ] **Step 3: Verify in a real browser**

Open Market Pulse at a `1280px` viewport and verify the page and decision grid widths do not exceed the workspace content width, the refresh control is inside the viewport, and the event panel is visible beside the sector panel.

- [ ] **Step 4: Commit and push**

```bash
git add docs/superpowers/specs/2026-08-23-market-pulse-layout-containment-design.md \
  docs/superpowers/plans/2026-08-23-market-pulse-layout-containment.md \
  frontend/src/features/market-pulse/MarketPulseResponsive.test.ts \
  frontend/src/styles.css
git commit -m "fix: 修复市场机会页面宽度溢出"
git push
```
