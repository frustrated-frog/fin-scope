# Event Detail Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the cramped event master-detail workbench with a queue page and an application-native event detail page.

**Architecture:** Extend the existing App `View` state with `eventDetail` and reuse `focusedEventId` to carry selection. Split queue-only concerns into `EventsView` and move fetching, interpretation and governance to a new `EventDetailView`; no backend interface changes are required.

**Tech Stack:** React 18, TypeScript, Vitest, existing Fetch API client and CSS tokens.

---

### Task 1: Add navigation state and a failing App test

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] Add a failing test that opens Events, clicks an event card, asserts `事件档案` and the selected title are visible, then clicks `返回事件队列` and asserts the queue is visible.
- [ ] Add `eventDetail` to `View`, render `EventDetailView` when selected, and make queue/open-evidence handlers set `focusedEventId` before changing views.
- [ ] Run `npm test -- --run src/App.test.tsx` and verify the navigation test passes.

### Task 2: Extract the event archive page

**Files:**
- Create: `frontend/src/features/events/EventDetailView.tsx`
- Modify: `frontend/src/features/events/EventsView.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] Add a failing App test that asserts timeline, merge basis, representative evidence, learning tasks, content ideas and governance controls on the detail page.
- [ ] Move event-detail fetching and all governance actions from `EventsView` into `EventDetailView`; cap visible event-page evidence at two sorted representative items and link to the evidence page.
- [ ] Make `EventsView` queue-only and invoke `onOpenEvent` from each event card.
- [ ] Run the App tests and verify all event governance interactions keep calling existing endpoints.

### Task 3: Style the archive page and verify responsive behavior

**Files:**
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`

- [ ] Add stylesheet assertions for a 2/3 + 1/3 desktop archive layout and a 900px single-column breakpoint.
- [ ] Add event archive shell, timeline spine, contextual sidebar, return control and responsive CSS using existing dark/light tokens.
- [ ] Run `npm test -- --run src/App.test.tsx`, `npm run build`, and `git diff --check`.
