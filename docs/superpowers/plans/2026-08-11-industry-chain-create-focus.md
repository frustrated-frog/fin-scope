# Industry Chain Create Focus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove stacked global focus effects from the industry-chain create field and reduce the visual weight of its add button.

**Architecture:** Keep the existing React markup and creation behavior unchanged. Contain the fix in the industry-chain stylesheet by resetting global input focus rules inside `.ic-create`, then let the form's `:focus-within` rule provide the only focus indicator.

**Tech Stack:** React, TypeScript, CSS, Vitest, Playwright CLI

---

### Task 1: Add a scoped focus-style regression test

**Files:**
- Create: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`
- Test: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { expect, test } from 'vitest';

test('keeps the create field focus treatment on one container layer', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/features/industry-chain/industry-chain.css`, 'utf8');

  expect(styles).toMatch(/\.ic-create input:focus[^}]*outline:\s*0[^}]*box-shadow:\s*none[^}]*transform:\s*none/s);
  expect(styles).toMatch(/\.ic-create:focus-within[^}]*border-color:[^}]*box-shadow:\s*0 0 0 2px/s);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- IndustryChainCreateStyles`

Expected: FAIL because `.ic-create input:focus` does not yet reset the global rules and the container still uses a 3px halo.

### Task 2: Apply the single-layer focus treatment

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css:49-88`
- Test: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] **Step 1: Reset global input focus rules inside the component**

```css
.ic-create input:focus,
.ic-create input:focus-visible {
  border: 0;
  outline: 0;
  color: var(--ic-ink);
  background: transparent;
  box-shadow: none;
  transform: none;
}
```

- [ ] **Step 2: Reduce the container halo and button weight**

```css
.ic-create:focus-within {
  border-color: rgba(90, 216, 210, .48);
  background: rgba(20, 36, 44, .86);
  box-shadow: 0 0 0 2px rgba(90, 216, 210, .08), 0 8px 20px rgba(0, 0, 0, .12);
}

.ic-create button {
  border: 1px solid rgba(90, 216, 210, .28);
  color: #8ddbd6;
  background: rgba(78, 215, 209, .08);
  box-shadow: none;
}

.ic-create button:disabled {
  color: #60747c;
  background: transparent;
  border-color: transparent;
  box-shadow: none;
  opacity: .72;
}
```

- [ ] **Step 3: Run focused tests**

Run: `cd frontend && npm test -- IndustryChainCreateStyles IndustryChainView`

Expected: both test files pass.

- [ ] **Step 4: Verify in a real browser**

Open the Industry Graph page, focus the new-chain input, and capture a screenshot. Confirm there is exactly one focus ring, no input translation, and no dominant disabled button block.

- [ ] **Step 5: Run final verification**

Run: `cd frontend && npm test -- --run && npm run build && git diff --check`

Expected: all tests pass, the production build exits successfully, and the diff check prints no errors.

- [ ] **Step 6: Commit and push**

```bash
git add frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts frontend/src/features/industry-chain/industry-chain.css
git commit -m "fix: 优化产业链输入框焦点样式"
git push
```
