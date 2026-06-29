# Event Research System Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade FinScope from a working research prototype into a high-quality event research system with a real research runtime backbone, validated task/idea lifecycle APIs, and maintainable frontend/backend structure.

**Architecture:** Keep the modular monolith. Add missing research backbone objects and orchestration services in backend modules, close lifecycle/state management through explicit request DTOs and service validation, and split the frontend by feature so the shell no longer owns every concern.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate + SQLite, React, TypeScript, Vite, Vitest.

---

## Style Contract

- All newly created or modified Spring beans in this plan use constructor injection only.
- No `@Resource` or field `@Autowired` in touched production files.
- Controllers expose typed request/response contracts only.
- Services own business rules and state validation.
- Repositories own SQL only.
- Frontend moves from single-file composition to feature-oriented modules.
- Every behavior change starts with a failing test.

## File Map

### Create

- `backend/finscope-domain/src/main/java/com/finscope/domain/research/ThemeProfile.java`
- `backend/finscope-domain/src/main/java/com/finscope/domain/research/SourceProfile.java`
- `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRun.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/research/ResearchRunRepository.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/ThemeProfileService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/SourcePlanner.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateLearningTaskStatusRequest.java`
- `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateContentIdeaStatusRequest.java`
- `backend/finscope-service/src/test/java/com/finscope/service/research/SourcePlannerTest.java`
- `frontend/src/app/AppShell.tsx`
- `frontend/src/shared/api/client.ts`
- `frontend/src/shared/types/index.ts`
- `frontend/src/shared/components/Table.tsx`
- `frontend/src/shared/components/ToastHost.tsx`
- `frontend/src/shared/brief/markdown.ts`
- `frontend/src/features/dashboard/DashboardView.tsx`
- `frontend/src/features/sources/SourcesView.tsx`
- `frontend/src/features/articles/ArticleView.tsx`
- `frontend/src/features/articles/ArticleCard.tsx`
- `frontend/src/features/articles/InsightCardPreview.tsx`
- `frontend/src/features/briefs/BriefsView.tsx`
- `frontend/src/features/briefs/BriefReaderView.tsx`
- `frontend/src/features/events/EventsView.tsx`
- `frontend/src/features/topics/TopicsView.tsx`
- `frontend/src/features/learning/LearningView.tsx`
- `frontend/src/features/content-studio/ContentStudioView.tsx`
- `frontend/src/features/agents/AgentRunsView.tsx`
- `frontend/src/features/settings/SettingsView.tsx`

### Modify

- `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/source/SourceRepository.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/research/LearningTaskRepository.java`
- `backend/finscope-dao/src/main/java/com/finscope/dao/research/ContentIdeaRepository.java`
- `backend/finscope-service/src/main/java/com/finscope/service/article/ArticleIngestCoordinator.java`
- `backend/finscope-service/src/main/java/com/finscope/service/article/UrlIngestService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/brief/BriefService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/BriefResearchContextService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/EventClusterService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/EvidenceService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/LearningTaskService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/ContentIdeaService.java`
- `backend/finscope-service/src/main/java/com/finscope/service/research/EventClassifier.java`
- `backend/finscope-web/src/main/java/com/finscope/web/controller/LearningTaskController.java`
- `backend/finscope-web/src/main/java/com/finscope/web/controller/ContentIdeaController.java`
- `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`
- `backend/finscope-service/src/test/java/com/finscope/service/brief/BriefGeneratorTest.java`
- `frontend/src/App.tsx`
- `frontend/src/App.test.tsx`
- `frontend/src/styles.css`

## Task 1: Research Backbone Domain And Schema

**Files:**
- Create domain/repository/service/controller files for `ThemeProfile`, `SourceProfile`, `ResearchRun`, `ThemeProfileService`, `SourcePlanner`, `ResearchService`, `ResearchController`
- Modify `DatabaseInitializer.java`
- Test `SourcePlannerTest.java` and `FinScopeApiIntegrationTest.java`

- [ ] **Step 1: Write failing planner and API tests**

Add:
- a unit test verifying `SourcePlanner` filters sources by theme, enabled flag, and max count
- an integration test verifying `POST /api/research/runs` creates a run record and returns selected themes/sources

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-service -am -DfailIfNoTests=false -Dtest=SourcePlannerTest test
mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#researchRunCanBeCreatedFromThemes test
```

Expected: FAIL because planner/runtime classes and API do not exist.

- [ ] **Step 2: Add research backbone domain models**

Implement plain domain classes with focused fields:
- `ThemeProfile`: code, name, description, briefSection, requiredTiers, preferredTiers, disallowedTiers, creatorEnabled, preferredFormats
- `SourceProfile`: sourceId, sourceName, sourceTier, themes, credibility, enabled
- `ResearchRun`: id, runDate, themeCodes, sourceCount, status, summary, errorMessage, createdAt, updatedAt

- [ ] **Step 3: Add schema and repository**

Extend `DatabaseInitializer` with `research_run` table and indexes.
Implement `ResearchRunRepository` with:
- `save`
- `updateStatus`
- `findAll`
- `findById`

- [ ] **Step 4: Implement theme and source planning services**

Implement `ThemeProfileService` as deterministic in-memory theme registry for:
- `ai_startup`
- `china_macro`
- `company_ipo`

Implement `SourcePlanner` to:
- load all configured sources
- convert to `SourceProfile`
- keep only enabled sources unless `includeDisabled=true`
- match requested themes from source tags/type/credibility
- cap each theme by `maxSourcesPerTheme`

- [ ] **Step 5: Implement research run orchestration**

Implement `ResearchService` and `ResearchController` with:
- `POST /api/research/runs`
- `GET /api/research/runs`

The create flow should:
- validate requested theme codes
- compute planned sources
- persist `ResearchRun`
- return a typed response with selected themes and sources

- [ ] **Step 6: Run tests and verify green**

Run the same two commands from Step 1.
Expected: PASS.

## Task 2: Harden Article And Research Services With Constructor Injection

**Files:**
- Modify touched services/controllers/config listed above
- Test existing targeted suites

- [ ] **Step 1: Write a compile-safety checkpoint**

No new test file needed. Use compilation as the failing guard after refactor.

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-web -am -DskipTests compile
```

Expected before refactor: PASS.
This is the baseline command to re-run after injection cleanup.

- [ ] **Step 2: Refactor touched production beans to constructor injection**

Convert all touched production classes in this plan from field injection to constructor injection.
At minimum:
- `ArticleIngestCoordinator`
- `UrlIngestService`
- `LearningTaskController`
- `ContentIdeaController`
- `BriefResearchContextService`
- `EvidenceService`
- `EventClusterService`
- `AppConfig` stays explicit `@Bean`, no field injection

- [ ] **Step 3: Re-run compile checkpoint**

Run the compile command from Step 1.
Expected: PASS.

## Task 3: Validated Learning Task Lifecycle

**Files:**
- Create `UpdateLearningTaskStatusRequest.java`
- Modify `LearningTaskController.java`
- Modify `LearningTaskService.java`
- Modify `LearningTaskRepository.java`
- Update tests in `FinScopeApiIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests**

Add tests for:
- valid status update from `TODO` to `LEARNING`
- invalid status update returns 400 or domain error
- unknown task id returns not found style error

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#learningTaskStatusUpdateIsValidated test
```

Expected: FAIL because the endpoint accepts raw maps and unvalidated strings.

- [ ] **Step 2: Add typed request DTO and controller contract**

Replace `Map<String, String>` request body with `UpdateLearningTaskStatusRequest`.

- [ ] **Step 3: Add service validation**

In `LearningTaskService`:
- whitelist `TODO`, `LEARNING`, `REVIEWING`, `DONE`
- reject blanks or unknown values
- fetch task before update and throw explicit not-found error when missing

- [ ] **Step 4: Keep repository focused**

Repository only updates the row after service validation.
Do not move validation into repository.

- [ ] **Step 5: Verify**

Run the test from Step 1.
Expected: PASS.

## Task 4: Validated Content Idea Lifecycle

**Files:**
- Create `UpdateContentIdeaStatusRequest.java`
- Modify `ContentIdeaController.java`
- Modify `ContentIdeaService.java`
- Modify `ContentIdeaRepository.java`
- Update tests in `FinScopeApiIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests**

Add tests for:
- valid status update to `DRAFTING`
- invalid status rejected
- missing record rejected

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#contentIdeaStatusUpdateIsValidated test
```

Expected: FAIL for the same reason as learning task updates.

- [ ] **Step 2: Add typed request DTO and service validation**

Whitelist:
- `IDEA`
- `DRAFTING`
- `READY`
- `PUBLISHED`
- `ARCHIVED`

- [ ] **Step 3: Verify**

Run the test from Step 1.
Expected: PASS.

## Task 5: Strengthen Research Generation Quality

**Files:**
- Modify `LearningTaskService.java`
- Modify `ContentIdeaService.java`
- Modify `EvidenceService.java`
- Modify `EventClassifier.java`
- Add/extend related tests

- [ ] **Step 1: Write failing service/integration tests**

Add assertions that generated tasks and ideas differ by event evidence/context rather than only theme.
At minimum, verify two same-theme events can produce different prompts/angles when evidence differs.

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest#generatedResearchArtifactsUseEventContext test
```

Expected: FAIL because current generation is mostly theme-template based.

- [ ] **Step 2: Enrich generation inputs**

Refine generation to use:
- event title
- novelty state
- evidence type distribution
- evidence claim keywords
- importance score

Do this deterministically. Do not add runtime LLM dependency.

- [ ] **Step 3: Verify**

Run the test from Step 1.
Expected: PASS.

## Task 6: Frontend Action Closure For Learning And Content Studio

**Files:**
- Create feature modules under `frontend/src/features/learning/` and `frontend/src/features/content-studio/`
- Create shared API/types modules
- Modify `App.test.tsx`

- [ ] **Step 1: Write failing frontend tests**

Add tests verifying:
- user can change a learning task status
- user can change a content idea status
- UI refreshes with returned values

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
npx vitest run src/App.test.tsx
```

Expected: FAIL because current UI is read-only for these resources.

- [ ] **Step 2: Add shared API/types extraction**

Move API client and shared types out of `App.tsx` into dedicated shared modules.

- [ ] **Step 3: Implement interactive lifecycle UI**

Learning page:
- show status select or action buttons per task
- call `POST /api/learning-tasks/{id}/status`
- refresh local state

Content studio:
- show status controls per idea
- call `POST /api/content-ideas/{id}/status`
- refresh local state

- [ ] **Step 4: Verify**

Run the test command from Step 1.
Expected: PASS.

## Task 7: Split App Shell And Feature Modules

**Files:**
- Create all listed frontend feature/shared files
- Modify `frontend/src/App.tsx`
- Keep `frontend/src/styles.css` compatible or move only what is necessary

- [ ] **Step 1: Write structure-preserving checkpoint**

Use existing UI test suite as the guardrail.

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
npx vitest run src/App.test.tsx
```

Expected before split: PASS.

- [ ] **Step 2: Move shell-only logic into `App.tsx`**

Leave only:
- top-level workspace state wiring
- current view selection
- shell layout composition

- [ ] **Step 3: Move each feature into its own module**

Extract:
- dashboard
- sources
- articles
- briefs
- events
- topics
- learning
- content studio
- agents
- settings

- [ ] **Step 4: Move shared helpers**

Extract:
- `Table`
- toast host
- markdown parsing helpers
- reusable types
- API client

- [ ] **Step 5: Verify**

Run the checkpoint test command again.
Expected: PASS.

## Task 8: Full Regression Verification

**Files:**
- No new files required
- Run full verification over touched backend/frontend surfaces

- [ ] **Step 1: Run backend service tests**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-service -am -DfailIfNoTests=false -Dtest=BriefGeneratorTest,SourcePlannerTest,EventClassifierTest test
```

Expected: PASS.

- [ ] **Step 2: Run full backend web integration suite**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/backend
mvn -nsu -pl finscope-web -am -DfailIfNoTests=false -Dtest=FinScopeApiIntegrationTest test
```

Expected: PASS.

- [ ] **Step 3: Run frontend tests**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
npx vitest run src/App.test.tsx
```

Expected: PASS.

- [ ] **Step 4: Run frontend build**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
npm run build
```

Expected: PASS.
