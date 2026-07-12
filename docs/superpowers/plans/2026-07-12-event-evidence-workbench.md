# Event Evidence Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the event research workflow while rebuilding evidence into a traceable thesis-and-evidence workbench.

**Architecture:** Add persistent `EventEvidenceThesis` records and a many-to-many thesis/evidence link behind `EventEvidenceThesisService`. This is deliberately separate from the existing Research-page `ResearchThesis`. `EvidenceService` synchronizes a conservative deterministic thesis when it captures evidence. A dedicated workbench endpoint serves the frontend; event layout changes remain frontend-only.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, SQLite, React, TypeScript, Vitest.

---

### Task 1: Persist thesis records and links

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/EventEvidenceThesis.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/EventEvidenceThesisRepository.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/research/EventEvidenceThesisRepositoryTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`

- [ ] Write repository tests covering save/find-by-event and idempotent thesis/evidence linking.
- [ ] Add `research_thesis` and `research_thesis_evidence` schema plus indexes in `DatabaseInitializer`.
- [ ] Implement repository methods `save`, `findByEventId`, `linkEvidence`, and `findEvidenceByThesisId` with joined provenance.
- [ ] Run `mvn -nsu -pl finscope-dao -am -Dtest=ResearchThesisRepositoryTest test` and verify green.

### Task 2: Derive and serve the evidence workbench

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/EventEvidenceThesisService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/EventEvidenceThesisServiceTest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/EvidenceWorkbenchResponse.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/EvidenceService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/EvidenceController.java`

- [ ] Write a service test that FACT evidence creates CONFIRMED thesis, DATA evidence creates PARTIALLY_SUPPORTED thesis, and repeated evidence does not duplicate a link.
- [ ] Implement conservative thesis synchronization and workbench aggregate counters.
- [ ] Call synchronization only after `EvidenceItemRepository.save` has returned its persisted ID.
- [ ] Add `GET /api/evidence/workbench` with existing filter parameters and validate `minConfidence >= 0`.
- [ ] Run service and web targeted tests.

### Task 3: Rebuild evidence UI around judgement

**Files:**
- Create: `frontend/src/features/evidence/EvidenceHealthSummary.tsx`
- Create: `frontend/src/features/evidence/ThesisCard.tsx`
- Modify: `frontend/src/features/evidence/EvidenceView.tsx`
- Modify: `frontend/src/features/evidence/EvidenceView.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`

- [ ] Write a UI test asserting displayed confirmed/partially-supported states, an evidence gap and a source link.
- [ ] Add typed workbench response models and fetch filtered workbench data.
- [ ] Render health, thesis cards, source composition, isolated evidence and guided empty states; preserve event navigation.
- [ ] Run `npm test -- --run frontend/src/features/evidence/EvidenceView.test.tsx` and `npm run build`.

### Task 4: Refine event layout without behavior changes

**Files:**
- Modify: `frontend/src/features/events/EventsView.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/features/events/EventsView.test.tsx`

- [ ] Write a layout-content test that keeps the queue, timeline, merge basis, evidence status, output and governance content available.
- [ ] Reorganize detail sections into primary narrative and contextual sidebar; expose only representative evidence plus a link to the evidence workbench.
- [ ] Update CSS to a 38/62 master-detail layout, preserve responsive single-column behavior below 900px.
- [ ] Run targeted Events tests and full frontend build.

### Task 5: Verify the integrated change

**Files:**
- Modify only files required by Tasks 1–4.

- [ ] Run backend targeted DAO/service/web tests, frontend targeted tests, `npm run build`, and full relevant regression suites.
- [ ] Inspect the final diff to ensure no unrelated dirty working-tree changes were staged or replaced.
- [ ] Record exact verification output in the delivery note.
