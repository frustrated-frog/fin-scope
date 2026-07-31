# Investment Verification Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the article-shaped fact archive with a read-only queue of atomic propositions linked to formed investment recognitions.

**Architecture:** Load active knowledge topics, retain only topics classified as recognitions, then load their existing topic workspaces. A pure projection extracts concise FACT/TIMELINE propositions, deduplicates them per recognition, validates primary-source hosts, and separates unresolved verification work from recorded first-party facts.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, existing `knowledgeApi` and knowledge workspace types.

---

### Task 1: Define the verification queue projection

**Files:**
- Create: `frontend/src/features/knowledge/facts/verificationQueueProjection.ts`
- Create: `frontend/src/features/knowledge/facts/verificationQueueProjection.test.ts`
- Delete after replacement: `frontend/src/features/knowledge/facts/factProjection.ts`
- Delete after replacement: `frontend/src/features/knowledge/facts/factProjection.test.ts`

- [ ] Write a failing test proving only FACT/TIMELINE claims from recognition workspaces become propositions.
- [ ] Run `npm test -- verificationQueueProjection.test.ts` and confirm failure because the projection does not exist.
- [ ] Implement `projectVerificationQueue(workspaces)` with proposition-shape filtering and per-topic deduplication.
- [ ] Add a failing test proving Markdown links, questions, Abstracts, missing URLs and oversized text are excluded.
- [ ] Implement the minimal eligibility predicates.
- [ ] Add a failing test proving an arXiv URL marked `REGULATOR` remains `NEEDS_PRIMARY`, while a regulator/company URL becomes `RECORDED`.
- [ ] Implement URL-host validation without trusting `sourceTier` alone.
- [ ] Run the projection tests and confirm all pass.

### Task 2: Replace the archive UI with a verification queue

**Files:**
- Create: `frontend/src/features/knowledge/facts/VerificationQueue.tsx`
- Create: `frontend/src/features/knowledge/facts/VerificationQueue.test.tsx`
- Delete after replacement: `frontend/src/features/knowledge/facts/FactWorkbench.tsx`
- Delete after replacement: `frontend/src/features/knowledge/facts/FactWorkbench.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] Write failing component tests for the empty state, default unresolved filter, recognition linkage and recorded filter.
- [ ] Run `npm test -- VerificationQueue.test.tsx` and confirm failure because the component does not exist.
- [ ] Implement the queue header, two-state filter, proposition index and focused dossier.
- [ ] Make source materials subordinate to the proposition and show the affected recognition and explicit missing step.
- [ ] Replace old fact-workbench styles with restrained queue styles and responsive stacking.
- [ ] Run component and projection tests and confirm all pass.

### Task 3: Load only formed-recognition workspaces

**Files:**
- Modify: `frontend/src/features/knowledge/KnowledgeView.tsx`
- Modify: `frontend/src/features/knowledge/KnowledgeView.test.tsx`
- Modify: `frontend/src/features/knowledge/KnowledgeNavigation.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] Write a failing integration test proving the facts section loads active topics, requests workspaces only for recognition topics, and does not request `/api/events/paged` or `/api/evidence/paged`.
- [ ] Run `npm test -- KnowledgeView.test.tsx` and confirm the old endpoints cause the expected failure.
- [ ] Replace `factEvents`/`factEvidence` state with recognition workspace state and render `VerificationQueue`.
- [ ] Change navigation copy from “事实与变化 / 核验材料” to “核验队列 / 影响判断”.
- [ ] Update affected App expectations and run the focused integration tests.

### Task 4: Remove obsolete code and verify the product

**Files:**
- Delete: `frontend/src/features/knowledge/facts/FactWorkbench.tsx`
- Delete: `frontend/src/features/knowledge/facts/FactWorkbench.test.tsx`
- Delete: `frontend/src/features/knowledge/facts/factProjection.ts`
- Delete: `frontend/src/features/knowledge/facts/factProjection.test.ts`

- [ ] Search for stale “事实索引”“全部事实” and old component imports; remove remaining references.
- [ ] Run `npm test` and confirm all test files pass with zero failures.
- [ ] Run `npm run build` and confirm TypeScript and Vite complete successfully.
- [ ] Open the real facts section with Playwright and confirm current article-only data produces the honest empty queue rather than a misleading archive.
- [ ] Run `git diff --check`, commit with `feat: 将事实页收缩为投资命题核验队列`, and push the current branch.
