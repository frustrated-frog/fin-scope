# Research Agent Workflow Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent research run plan state and expose it in the Research tab.

**Architecture:** The phase adds a `research_run_plan` table, a small domain object, repository, and service for step state. `ResearchService` initializes and updates plan steps while preserving the current deterministic source-fetch and brief generation flow. The web response and React types/view gain `planSteps`, with richer trace metadata shown in the run detail panel.

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite/JdbcTemplate, JUnit 5, Mockito, React, TypeScript, Vitest, Testing Library.

---

## File Structure

- Create `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRunPlanStep.java`
  - Domain DTO for persisted plan step state.
- Create `backend/finscope-dao/src/main/java/com/finscope/dao/research/ResearchRunPlanRepository.java`
  - SQL persistence and row mapping for `research_run_plan`.
- Create `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchRunPlanService.java`
  - Business-level state transitions for plan steps.
- Create `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchRunPlanServiceTest.java`
  - Unit tests for default plan and transitions.
- Modify `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
  - Create `research_run_plan` and indexes.
- Modify `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRunPlan.java`
  - Add `planSteps`.
- Modify `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
  - Initialize and update plan steps during run execution.
- Modify `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`
  - Verify run creation initializes and returns plan steps.
- Modify `backend/finscope-web/src/main/java/com/finscope/web/response/ResearchRunDetailResponse.java`
  - Include `planSteps`.
- Modify `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
  - Pass plan steps into detail response.
- Modify `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`
  - Verify detail API returns `planSteps`.
- Modify `frontend/src/shared/types/index.ts`
  - Add `ResearchRunPlanStep` and enriched `AgentRun` fields.
- Modify `frontend/src/features/research/ResearchView.tsx`
  - Render plan steps and trace metadata.
- Modify `frontend/src/App.test.tsx`
  - Mock `planSteps` and assert UI renders them.

## Task 1: Plan Step Service

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRunPlanStep.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/ResearchRunPlanRepository.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchRunPlanService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchRunPlanServiceTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`

- [ ] **Step 1: Write the failing service test**

```java
@Test
void initializesDefaultPlanAndCompletesSteps() {
    ResearchRunPlanRepository repository = mock(ResearchRunPlanRepository.class);
    when(repository.replaceForRun(eq(501L), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
    when(repository.update(any(ResearchRunPlanStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ResearchRunPlanService service = new ResearchRunPlanService(repository);

    List<ResearchRunPlanStep> steps = service.initializeDefaultPlan(501L, 3);
    assertEquals(Arrays.asList("plan_sources", "fetch_sources", "classify_events", "extract_evidence", "compose_brief", "summarize_run"),
            steps.stream().map(ResearchRunPlanStep::getStepId).collect(Collectors.toList()));
    assertEquals("PENDING", steps.get(0).getStatus());
    assertEquals(1, steps.get(0).getMaxAttempts());

    ResearchRunPlanStep completed = service.complete(steps.get(0), "planned 3 sources", 3);

    assertEquals("COMPLETED", completed.getStatus());
    assertEquals("planned 3 sources", completed.getOutputSummary());
    assertEquals(3, completed.getProgressDelta());
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchRunPlanServiceTest test`

Expected: compilation failure because `ResearchRunPlanStep` and `ResearchRunPlanService` do not exist.

- [ ] **Step 3: Implement domain, repository, service, and schema**

Implement:

```java
public class ResearchRunPlanStep {
    private Long id;
    private Long researchRunId;
    private String stepId;
    private String title;
    private String stepType;
    private String executor;
    private String status;
    private List<String> dependencies = Collections.emptyList();
    private String inputSummary;
    private String outputSummary;
    private String errorType;
    private String errorMessage;
    private boolean fallbackUsed;
    private String fallbackReason;
    private String terminationReason;
    private int attempt;
    private int maxAttempts = 1;
    private int progressDelta;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String metadataJson;
    // getters and setters
}
```

`ResearchRunPlanService.initializeDefaultPlan` creates six ordered steps: `plan_sources`, `fetch_sources`, `classify_events`, `extract_evidence`, `compose_brief`, `summarize_run`.

- [ ] **Step 4: Run the test and verify GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchRunPlanServiceTest test`

Expected: test passes.

## Task 2: Research Service Integration

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRunPlan.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`

- [ ] **Step 1: Write failing integration-style service assertion**

Add to existing `ResearchServiceHarnessTest`:

```java
ResearchRunPlanService planService = mock(ResearchRunPlanService.class);
List<ResearchRunPlanStep> initializedSteps = defaultSteps();
when(planService.initializeDefaultPlan(501L, 3)).thenReturn(initializedSteps);
when(planService.complete(any(ResearchRunPlanStep.class), anyString(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
when(planService.fail(any(ResearchRunPlanStep.class), anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
ReflectionTestUtils.setField(service, "researchRunPlanService", planService);

ResearchRunPlan plan = service.createRun(runDate, Collections.singletonList(ResearchEnums.THEME_MARKET), 3, true);

assertEquals(6, plan.getPlanSteps().size());
verify(planService).initializeDefaultPlan(501L, 3);
verify(planService, atLeastOnce()).complete(any(ResearchRunPlanStep.class), anyString(), anyInt());
```

- [ ] **Step 2: Run RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchServiceHarnessTest test`

Expected: compilation failure because `ResearchRunPlan.getPlanSteps()` and `researchRunPlanService` integration are missing.

- [ ] **Step 3: Implement integration**

Changes:

```java
ResearchRunPlan plan = new ResearchRunPlan();
plan.setRun(saved);
plan.setPlannedSources(plannedSources);
plan.setPlanSteps(planSteps);
return plan;
```

In `execute`, update aggregate steps around existing operations.

- [ ] **Step 4: Run GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchServiceHarnessTest,ResearchRunPlanServiceTest test`

Expected: both tests pass.

## Task 3: API Detail Response

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/ResearchRunDetailResponse.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`

- [ ] **Step 1: Write failing API assertion**

Add an assertion to the research run API test:

```java
mvc.perform(get("/api/research/runs/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planSteps").isArray())
        .andExpect(jsonPath("$.planSteps[*].stepId").value(hasItem("plan_sources")))
        .andExpect(jsonPath("$.planSteps[*].stepId").value(hasItem("fetch_sources")));
```

- [ ] **Step 2: Run RED**

Run: `cd backend && mvn -pl finscope-web -Dtest=FinScopeApiIntegrationTest#researchRunExecutesEndToEndFromThemes test`

Expected: test fails because `planSteps` is missing.

- [ ] **Step 3: Return planSteps**

Inject `ResearchRunPlanService` into `ResearchController` and pass `researchRunPlanService.findByRunId(id)` into `ResearchRunDetailResponse`.

- [ ] **Step 4: Run GREEN**

Run: `cd backend && mvn -pl finscope-web -Dtest=FinScopeApiIntegrationTest#researchRunExecutesEndToEndFromThemes test`

Expected: test passes.

## Task 4: Research Tab UI

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing UI test**

Add plan step mock data to `/api/research/runs/1`, open the Research view, open the run, and assert:

```ts
expect(await screen.findByText('Plan steps')).toBeInTheDocument();
expect(screen.getByText('规划来源')).toBeInTheDocument();
expect(screen.getByText('抓取来源')).toBeInTheDocument();
expect(screen.getByText('fallback: LLM_UNCONFIGURED')).toBeInTheDocument();
```

- [ ] **Step 2: Run RED**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: test fails because plan steps and enriched trace metadata are not rendered.

- [ ] **Step 3: Implement UI**

Add:

```ts
export type ResearchRunPlanStep = {
  id?: number;
  researchRunId?: number;
  stepId: string;
  title: string;
  stepType?: string;
  executor?: string;
  status: string;
  dependencies?: string[];
  outputSummary?: string;
  errorType?: string;
  errorMessage?: string;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  terminationReason?: string;
  attempt?: number;
  maxAttempts?: number;
  progressDelta?: number;
  startedAt?: string;
  endedAt?: string;
};
```

Render `detail.planSteps` above planned sources.

- [ ] **Step 4: Run GREEN**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: test passes.

## Task 5: Full Verification

- [ ] **Step 1: Run backend targeted tests**

Run: `cd backend && mvn -pl finscope-service,finscope-web -Dtest=ResearchRunPlanServiceTest,ResearchServiceHarnessTest,FinScopeApiIntegrationTest#researchRunExecutesEndToEndFromThemes test`

Expected: selected backend tests pass.

- [ ] **Step 2: Run frontend tests**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: selected frontend tests pass.

- [ ] **Step 3: Run frontend build**

Run: `cd frontend && npm run build`

Expected: TypeScript and Vite build pass.

- [ ] **Step 4: Review diff**

Run: `git diff --stat`

Expected: changes are limited to Phase 1 files plus pre-existing user modifications.

- [ ] **Step 5: Verify frontend-backend contract**

Check that `research_run_plan` is persisted by the backend, `ResearchRunDetailResponse.planSteps` returns the persisted steps, `frontend/src/shared/types/index.ts` declares the same shape, `ResearchView` renders the steps, and `App.test.tsx` asserts the visible plan and trace metadata.

## Self-Review

Spec coverage:

- Persistent plan state: Task 1.
- ResearchService integration: Task 2.
- Detail API: Task 3.
- Research tab display: Task 4.
- Verification: Task 5.
- Frontend-backend contract: API response, TypeScript types, view rendering, and tests are synchronized for `planSteps`.

Placeholder scan: no unresolved placeholders remain.

Type consistency:

- `ResearchRunPlanStep` is used consistently by backend domain, response, and frontend type.
- `planSteps` is used consistently in API and UI.
