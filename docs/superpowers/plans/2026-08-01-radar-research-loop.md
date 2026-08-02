# Radar Research Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent event tracking, observations, timelines, research links, evidence trust, and in-app notifications to the radar without changing classification, clustering, or priority scoring and without adding slow work to the list request.

**Architecture:** Introduce a deterministic `RadarEventWorkspaceService` backed by five SQLite tables and batch repository reads. Keep list payloads lightweight; build timeline and trust detail only for an opened event. Link a radar event to an existing research run after the run is created, while the current asynchronous interpretation path remains unchanged.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring JDBC, SQLite, JUnit 5/Mockito, React, TypeScript, Vitest, Testing Library.

---

### Task 1: Persist radar workspace state and observations

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventWorkspace.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventWorkspaceRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarEventWorkspaceRepositoryTest.java`

- [ ] **Step 1: Write failing repository tests**

Cover upserting read/follow/disposition state, creating one default observation per normalized content, toggling observation status, batch summaries, and deleting user observations while retaining system history.

```java
RadarEventWorkspace.State state = repository.updateState(7L, true, "ACTIVE", true, "fp-1");
assertTrue(state.isFollowed());
assertNotNull(state.getReadAt());
assertEquals(1, repository.ensureDefaultObservation(7L, "观察公司公告").size());
assertEquals(1, repository.ensureDefaultObservation(7L, " 观察公司公告 ").size());
```

- [ ] **Step 2: Run the DAO test and verify RED**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=RadarEventWorkspaceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because the workspace domain and repository do not exist.

- [ ] **Step 3: Add schema and minimal repository**

Create `radar_event_user_state`, `radar_event_observation`, `radar_event_timeline`, `radar_event_research_link`, and `radar_event_notification` with the columns and unique keys defined in the design. Expose repository methods:

```java
State updateState(Long eventId, boolean markRead, String disposition, Boolean followed, String fingerprint);
Map<Long, Summary> findSummaries(List<Long> eventIds);
List<Observation> ensureDefaultObservation(Long eventId, String content);
Observation addObservation(Long eventId, String content);
Observation setObservationStatus(Long eventId, Long observationId, String status);
void deleteObservation(Long eventId, Long observationId);
List<Observation> findObservations(Long eventId);
```

Validate disposition against `ACTIVE|LATER|IGNORED`, observation status against `OPEN|DONE`, trim content, reject blank content, and cap it at 300 characters.

- [ ] **Step 4: Run the DAO test and verify GREEN**

Run the command from Step 2. Expected: all repository tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 持久化雷达事件跟踪状态"
git push
```

### Task 2: Add workspace state APIs and batch list summaries

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventWorkspaceService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventWorkspaceServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/ResearchRadarServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/ResearchRadarApiIntegrationTest.java`

- [ ] **Step 1: Write failing service and API tests**

Assert that list cards contain `read`, `followed`, `disposition`, `openObservationCount`, `observationCount`, `researchRunCount`, and `unreadNotificationCount`; verify one batch repository call for all card IDs. Cover endpoints:

```text
PATCH  /api/research-radar/events/{id}/state
GET    /api/research-radar/events/{id}/observations
POST   /api/research-radar/events/{id}/observations
PATCH  /api/research-radar/events/{id}/observations/{observationId}
DELETE /api/research-radar/events/{id}/observations/{observationId}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=RadarEventWorkspaceServiceTest,ResearchRadarServiceTest,ResearchRadarApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement state commands and card composition**

Use a request DTO with nullable fields so PATCH only updates supplied values. Opening detail calls `markRead(eventId, currentFingerprint)` and ensures the default observation without waiting on external work. Filter `UNREAD|FOLLOWED|LATER|IGNORED` after ranking using the batch summary; the default `ALL` excludes ignored events.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 接通雷达事件跟踪接口"
git push
```

### Task 3: Build deterministic timelines and evidence trust

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventTimelineService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEvidenceTrustService.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventWorkspaceRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventTimelineServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEvidenceTrustServiceTest.java`

- [ ] **Step 1: Write failing timeline and trust tests**

Verify idempotent timeline events for first seen, each signal, evidence update, interpretation completion, follow/read/observation actions. Verify trust output contains normalized independent source count, tier counts, citation numerator/denominator, concentration label, and deterministic numeric conflicts.

```java
TrustView trust = service.assess(signals, evidence, interpretation);
assertEquals(2, trust.getIndependentSourceCount());
assertEquals(2, trust.getCitationCoveredCount());
assertEquals(3, trust.getCitationTotalCount());
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarEventTimelineServiceTest,RadarEvidenceTrustServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement deterministic services**

Normalize sources by URL host when present and otherwise by trimmed lowercase source name. Extract only number-plus-unit tokens for conflict comparison; report a conflict only when the same unit has two distinct values from different normalized sources. Do not return a single confidence score. Generate and read the timeline only in event detail.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service backend/finscope-dao
git commit -m "feat: 增加雷达时间线与证据可信度"
git push
```

### Task 4: Link radar events to research runs and conclusions

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarResearchLinkService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Modify: `frontend/src/features/news/researchRadarTypes.ts`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarResearchLinkServiceTest.java`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing backend and frontend tests**

Assert `POST /events/{eventId}/research-links/{runId}` is idempotent and rejects unknown IDs. In the frontend, opening research from event 10 must preserve event ID; after `/api/research/runs` returns run 41, expect the link POST.

- [ ] **Step 2: Run focused tests and verify RED**

Run backend: `cd backend && mvn -pl finscope-service -am -Dtest=RadarResearchLinkServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run frontend: `cd frontend && npm test -- --run src/App.test.tsx`

- [ ] **Step 3: Implement link persistence and handoff**

Change the news callback to `onResearch(eventId, question)`. Store the pending radar event ID in `App`; after a research run is created, call the link endpoint, then clear the pending ID. Detail reads linked run status and the latest report summary through existing repositories without copying report bodies.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run both commands from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend frontend/src/App.tsx frontend/src/features/news
git commit -m "feat: 关联雷达事件与研究结论"
git push
```

### Task 5: Add deterministic notifications and daily summary

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventWorkspaceRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventWorkspaceService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchRadarController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventWorkspaceServiceTest.java`

- [ ] **Step 1: Write failing notification tests**

Cover idempotent notification creation for followed event fingerprint changes, preserving ignored state, marking one/all read, and a daily summary count that uses the current local date and database rows only.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=RadarEventWorkspaceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement notification methods and endpoints**

Expose `GET /notifications`, `POST /notifications/{id}/read`, and `POST /notifications/read-all`. During radar load, compare followed events with `last_viewed_fingerprint` and insert one version-change notification per fingerprint; do not call external services or create background tasks.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: 增加雷达关注提醒与每日摘要"
git push
```

### Task 6: Build the radar tracking interface

**Files:**
- Modify: `frontend/src/features/news/researchRadarTypes.ts`
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Modify: `frontend/src/features/news/RadarEventDetailDrawer.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing UI tests**

Cover status filters, marking detail read, following/unfollowing, later/ignored actions, observation CRUD/status, four detail tabs, timeline order, trust metrics, research links, notification list/read-all, and absence of new requests during initial list rendering beyond the radar snapshot and category request.

- [ ] **Step 2: Run the news test and verify RED**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

- [ ] **Step 3: Implement focused components**

Keep `NewsView` responsible for snapshot/filter state. Extract `RadarStateFilters`, `RadarNotificationPanel`, `RadarTimeline`, `RadarObservationList`, and `RadarTrustPanel` into separate files. The drawer fetches detail once, mutates through explicit callbacks, and only polls while interpretation is queued/running.

- [ ] **Step 4: Run focused tests and production build**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx && npm run build`

Expected: tests pass and Vite produces the production bundle.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: 完善雷达事件跟踪工作台"
git push
```

### Task 7: Full verification and scope audit

**Files:**
- Modify only if verification reveals a regression in files already changed by Tasks 1–6.

- [ ] **Step 1: Run backend full tests**

Run: `cd backend && mvn test`

Expected: Reactor build success with zero failures and zero errors.

- [ ] **Step 2: Run frontend full tests**

Run: `cd frontend && npm test -- --run`

Expected: all Vitest files and tests pass.

- [ ] **Step 3: Run frontend production build**

Run: `cd frontend && npm run build`

Expected: TypeScript compilation and Vite build succeed.

- [ ] **Step 4: Audit protected radar responsibilities**

Run:

```bash
git diff --name-only HEAD~6..HEAD | rg 'NewsClassification|RadarEventMatchAgent|RadarClusteringService|RadarCanonicalTitleAgent|RadarPriorityService'
```

Expected: no output.

- [ ] **Step 5: Confirm clean pushed branch**

Run: `git diff --check && git status --short --branch && git push`

Expected: no unstaged changes; local and remote branch synchronized.
