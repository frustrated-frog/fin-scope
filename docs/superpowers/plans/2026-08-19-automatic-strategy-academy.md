# Automatic Strategy Academy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an automatic, evidence-graded strategy academy that converts public A-share-compatible candidates into locally backtested learning cards without requiring a beginner to configure factors manually.

**Architecture:** Add domain response objects and a deterministic evidence scorer in the service layer, then compose existing catalog, draft, version, dataset, and experiment services behind two Quant APIs. Replace the catalog-first frontend with an academy-first experience while preserving source synchronization and provenance.

**Tech Stack:** Java 21, Spring Boot 2.7, JdbcTemplate, SQLite, JUnit 5/Mockito, React 18, TypeScript, Vitest, Testing Library.

---

### Task 1: Domain contract and evidence scorer

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/academy/QuantStrategyAcademyCard.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/academy/QuantStrategyAcademyBuildResult.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/academy/QuantStrategyEvidenceScorer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/academy/QuantStrategyEvidenceScorerTest.java`

- [ ] **Step 1: Write failing scorer tests**

Create experiments with strong, moderate, weak, running, and failed results. Assert exact `evidenceLevel`, `shelf`, `score`, dimension scores, reasons, and limitations.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=QuantStrategyEvidenceScorerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because academy domain and scorer types do not exist.

- [ ] **Step 3: Implement minimal domain objects and deterministic scorer**

Use Lombok `@Data` for DTOs. Keep thresholds as named constants and expand every `if`/`for` block with braces. The scorer must never label a non-REAL dataset or unsuccessful experiment as historical evidence.

- [ ] **Step 4: Verify GREEN**

Run the Task 1 Maven command and expect 0 failures.

- [ ] **Step 5: Commit and push**

Run:

```bash
git add backend/finscope-domain backend/finscope-service
git commit -m "feat: 增加策略学院证据分级"
git push -u origin codex/automatic-strategy-academy
```

### Task 2: Idempotent academy orchestration and API

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/QuantStrategyCatalogRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/QuantExperimentRepository.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/quant/academy/QuantStrategyAcademyService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/QuantController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/BuildStrategyAcademyRequest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/academy/QuantStrategyAcademyServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/QuantControllerTest.java`

- [ ] **Step 1: Write failing orchestration tests**

Cover: REAL/READY validation, six-candidate limit, existing-version reuse, active-experiment reuse, draft failure isolation, and card aggregation.

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -pl finscope-service,finscope-web -am -Dtest=QuantStrategyAcademyServiceTest,QuantControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because academy service and endpoints do not exist.

- [ ] **Step 3: Add repository queries and service orchestration**

Add `findLatestVersionIdByCandidate(Long)` and `findLatestByStrategyVersion(Long)`. Implement a field-injected Spring service that scans at most six adaptable candidates, isolates per-candidate failures, and delegates all generation, confirmation, and experiment execution to existing services.

- [ ] **Step 4: Add API endpoints**

Expose `GET /api/quant/academy/cards` and `POST /api/quant/academy/build`. The request DTO contains only `datasetId`; the controller uses field injection and the existing response envelope.

- [ ] **Step 5: Verify GREEN**

Run the Task 2 Maven command and expect 0 failures.

- [ ] **Step 6: Commit and push**

Run:

```bash
git add backend
git commit -m "feat: 自动生成并验证策略学院卡片"
git push
```

### Task 3: Academy-first frontend

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/StrategyCatalogPanel.tsx`
- Modify: `frontend/src/features/strategy/StrategyCatalogPanel.test.tsx`
- Modify: `frontend/src/features/strategy/QuantWorkspace.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing UI tests**

Mock academy cards and assert: evidence staircase, application/observation/learning shelves, beginner explanations, REAL dataset selector, automatic build request, provenance links, loading state, and recoverable error copy.

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- StrategyCatalogPanel.test.tsx`

Expected: FAIL because the academy UI and contracts do not exist.

- [ ] **Step 3: Add TypeScript contracts and build interaction**

Define exact API unions for evidence level, shelf, dimension scores, build result, and card metrics. Load cards, source, and candidates independently so a source failure does not hide verified cards.

- [ ] **Step 4: Build the archive-and-staircase interface**

Implement a quiet evidence staircase hero, one primary build control, three semantic shelves, and a selected-card research dossier. Use existing global tokens; add scoped `.strategy-academy-*` CSS, keyboard focus, reduced motion, and responsive one-column behavior.

- [ ] **Step 5: Verify GREEN and production build**

Run:

```bash
cd frontend && npm test -- StrategyCatalogPanel.test.tsx
npm run build
```

Expected: tests pass and Vite exits 0.

- [ ] **Step 6: Commit and push**

Run:

```bash
git add frontend
git commit -m "feat: 重塑自动策略学院学习界面"
git push
```

### Task 4: Documentation, regression, and visual verification

**Files:**
- Modify: `README.md`
- Modify: `docs/路线图.md`

- [ ] **Step 1: Update product documentation**

Document evidence levels, automatic bounded generation, the distinction between historical evidence and forward validation, and the absence of automatic trading.

- [ ] **Step 2: Run complete verification**

Run:

```bash
cd backend && mvn test
cd ../frontend && npm test
npm run build
```

Expected: all commands exit 0 with no test failures.

- [ ] **Step 3: Render and inspect UI**

Start the existing local stack, capture desktop and narrow viewport screenshots of the strategy academy, and inspect hierarchy, overflow, focus, empty, loading, error, and populated states. Fix any visual defects and rerun the focused UI test plus build.

- [ ] **Step 4: Request code review and resolve findings**

Review the complete diff against this plan, the project development checklist, field injection rules, expanded braces, module placement, and evidence wording. Resolve all critical and important findings.

- [ ] **Step 5: Commit and push**

Run:

```bash
git add README.md docs frontend backend
git commit -m "docs: 补充自动策略学院使用边界"
git push
```

## Plan self-review

- Spec coverage: domain contract, scoring, bounded orchestration, APIs, UI, documentation, and verification each have a task.
- Placeholder scan: no TBD/TODO or undefined follow-up is required for the first release.
- Type consistency: `QuantStrategyAcademyCard`, `QuantStrategyAcademyBuildResult`, evidence levels, shelves, and endpoint paths are identical across tasks.
