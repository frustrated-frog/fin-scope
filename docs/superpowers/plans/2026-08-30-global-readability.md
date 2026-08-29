# Global Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise FinScope's typography to a readable system-wide scale and add restrained Apple-inspired material, interaction, and accessibility polish without changing page behavior.

**Architecture:** Keep the existing page components and CSS ownership intact. Add one static design-system regression test, normalize undersized declarations in all five production stylesheets, then refine the shared application shell and common controls in `styles.css`; browser QA covers representative dense pages and narrow layouts.

**Tech Stack:** React 18, TypeScript, Vite, Vitest, CSS custom properties and accessibility media queries.

---

### Task 1: Lock the readability contract

**Files:**
- Create: `frontend/src/GlobalReadability.test.ts`

- [ ] **Step 1: Write the failing stylesheet test**

```ts
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

import { expect, test } from 'vitest';

const stylesheets = [
  'src/styles.css',
  'src/features/industry-chain/industry-chain.css',
  'src/features/strategy/QuantVisualizations.css',
  'src/features/strategy/BacktestAuditPanel.css',
  'src/features/strategy/StockDiscoveryAccuracyPanel.css'
];

test('keeps every production text declaration at a readable minimum size', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const undersized: string[] = [];

  for (const stylesheet of stylesheets) {
    const css = readFileSync(`${cwd}/${stylesheet}`, 'utf8');
    for (const match of css.matchAll(/(?:font-size:\s*|font:[^;{}]*?\s)(\d+(?:\.\d+)?)px(?:[\s/;])/g)) {
      if (Number(match[1]) < 11) {
        undersized.push(`${stylesheet}: ${match[0].trim()}`);
      }
    }
  }

  expect(undersized).toEqual([]);
});

test('defines the global type roles and accessibility fallbacks', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const css = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(css).toContain('--font-body:');
  expect(css).toContain('--font-display:');
  expect(css).toContain('--font-data:');
  expect(css).toMatch(/@media\s*\(prefers-reduced-transparency:\s*reduce\)/);
  expect(css).toMatch(/@media\s*\(prefers-contrast:\s*more\)/);
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd frontend && npm test -- GlobalReadability.test.ts`

Expected: FAIL listing existing 7–10px declarations and missing global type-role tokens.

- [ ] **Step 3: Commit the failing test**

```bash
git add frontend/src/GlobalReadability.test.ts
git commit -m "test: 锁定全站字体可读性契约"
git push origin HEAD
```

### Task 2: Normalize the global typography scale

**Files:**
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Modify: `frontend/src/features/strategy/QuantVisualizations.css`
- Modify: `frontend/src/features/strategy/BacktestAuditPanel.css`
- Modify: `frontend/src/features/strategy/StockDiscoveryAccuracyPanel.css`

- [ ] **Step 1: Add explicit font roles and body defaults**

Add to `:root` in `frontend/src/styles.css`:

```css
--font-body: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif;
--font-display: "Iowan Old Style", "Songti SC", STSong, serif;
--font-data: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
--text-caption: 0.75rem;
--text-label: 0.8125rem;
--text-body: 0.9375rem;
--text-title: 1.25rem;
```

Set body text to `font-size: var(--text-body)`, `line-height: 1.65`, and `font-optical-sizing: auto`.

- [ ] **Step 2: Normalize undersized absolute declarations**

Apply the following monotonic mapping to `font-size` and `font` shorthand declarations only; do not alter geometry values:

```text
7–9px → 11px
10px  → 12px
11px  → 13px
12px  → 14px
13px  → 15px
14px  → 16px
15px  → 17px
16px  → 18px
17px  → 19px
18px  → 20px
19px  → 21px
20px  → 22px
```

- [ ] **Step 3: Run the focused test and verify GREEN**

Run: `cd frontend && npm test -- GlobalReadability.test.ts`

Expected: both tests PASS and no stylesheet reports text below 11px.

- [ ] **Step 4: Commit the typography batch**

```bash
git add frontend/src/styles.css frontend/src/features/industry-chain/industry-chain.css frontend/src/features/strategy/*.css
git commit -m "style: 提升全站字体可读性"
git push origin HEAD
```

### Task 3: Refine the shared shell and interaction materials

**Files:**
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/GlobalReadability.test.ts`
- Test: `frontend/src/app/AppShell.test.tsx`

- [ ] **Step 1: Extend the test with shared-shell requirements**

Add assertions for a wider readable sidebar, translucent chrome, 44px control targets, immediate active feedback, and reduced-motion fallback:

```ts
expect(css).toMatch(/\.app-shell\s*{[^}]*grid-template-columns:\s*248px minmax\(0, 1fr\)/s);
expect(css).toMatch(/\.sidebar\s*{[^}]*backdrop-filter:\s*blur\(24px\) saturate\(140%\)/s);
expect(css).toMatch(/(?:button|\.primary-button)[^{]*{[^}]*min-height:\s*44px/s);
expect(css).toMatch(/button:active[^{]*{[^}]*transform:\s*scale\(0\.98\)/s);
expect(css).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*transition-duration:\s*0\.01ms/s);
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && npm test -- GlobalReadability.test.ts`

Expected: FAIL because the shared shell has not yet adopted the new dimensions and material rules.

- [ ] **Step 3: Implement the shell polish**

Update common styles with these rules:

```css
.app-shell { grid-template-columns: 248px minmax(0, 1fr); }
.sidebar {
  backdrop-filter: blur(24px) saturate(140%);
  box-shadow: 18px 0 48px rgba(5, 15, 12, 0.16);
}
.topbar {
  backdrop-filter: blur(24px) saturate(150%);
  box-shadow: 0 12px 34px rgba(23, 36, 31, 0.08);
}
button, input, select, textarea { min-height: 44px; }
button:active { transform: scale(0.98); transition: transform 100ms ease-out; }
```

Keep checkboxes, icon-only controls, chart controls and inline chips at their explicit component dimensions. Add hover elevation only to common cards, use `transform` and `opacity`, and add solid fallbacks for reduced transparency plus stronger borders for increased contrast.

- [ ] **Step 4: Run focused and full verification**

Run: `cd frontend && npm test -- GlobalReadability.test.ts AppShell.test.tsx`

Expected: focused tests PASS.

Run: `cd frontend && npm test && npm run build`

Expected: all tests PASS and Vite production build exits 0.

- [ ] **Step 5: Browser QA**

Open the running application and inspect Dashboard, Market Pulse, Strategy/Stock Discovery, Facts & Knowledge, Market Intel, Watchlist, and Industry Graph at desktop width. Repeat representative pages below 980px. Confirm body copy is readable, local tables scroll instead of shrinking, keyboard focus is visible, and `document.documentElement.scrollWidth === window.innerWidth` outside intentional local scrollers.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/styles.css frontend/src/GlobalReadability.test.ts frontend/src/app/AppShell.test.tsx frontend/tsconfig.tsbuildinfo
git commit -m "style: 升级全站界面层次与交互质感"
git push origin HEAD
```

