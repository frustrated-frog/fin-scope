# Deep Research Runtime + Eval Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有研究流水线升级为可 checkpoint、可恢复、受预算约束并能对真实运行做确定性离线评测的研究智能体运行时。

**Architecture:** 在现有 `ResearchService` 外围增加 Java 原生 runtime 服务；SQLite 保存单行 checkpoint 与追加式 event stream，runtime 用乐观版本控制执行权，用状态哈希和动作指纹执行预算/防循环。Eval Harness 从研究运行、报告、checkpoint 和事件构造不可变快照，按版本化规则计算六项指标并持久化结果。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring JDBC、SQLite、JUnit 5、Mockito、React、TypeScript、Vitest。

---

### Task 1: Runtime 领域模型与策略内核

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/runtime/ResearchRuntimeCheckpoint.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/runtime/ResearchRuntimeEvent.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/runtime/ResearchRuntimeView.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/ResearchRuntimePolicy.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/RuntimeGuardDecision.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/runtime/ResearchRuntimePolicyTest.java`

- [ ] **Step 1: Write policy tests first**

```java
@Test
void stopsWhenActionBudgetIsExhausted() {
    ResearchRuntimeCheckpoint state = checkpoint(12, 12, 0);
    RuntimeGuardDecision decision = policy.beforeAction(state, "query:fed:round-3", 0);
    assertFalse(decision.isAllowed());
    assertEquals("BUDGET_EXHAUSTED", decision.getTerminationReason());
}

@Test
void stopsAfterTwoConsecutiveNoProgressTransitions() {
    ResearchRuntimeCheckpoint state = checkpoint(4, 12, 2);
    RuntimeGuardDecision decision = policy.beforeAction(state, "query:new", 0);
    assertFalse(decision.isAllowed());
    assertEquals("NO_PROGRESS", decision.getTerminationReason());
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `cd backend && mvn -q -pl finscope-service -am -Dtest=ResearchRuntimePolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because runtime types do not exist.

- [ ] **Step 3: Implement immutable guard decisions and mutable persisted state**

`ResearchRuntimePolicy.beforeAction` must evaluate in this order: terminal state, max actions, no-progress threshold, repeated fingerprint threshold. It returns `allowed()` or `terminated(reason)` and does not write the database.

```java
public RuntimeGuardDecision beforeAction(ResearchRuntimeCheckpoint state,
                                         String actionFingerprint,
                                         int repeatedCount) {
    if (state.isTerminal()) return RuntimeGuardDecision.terminated("ALREADY_TERMINAL");
    if (state.getConsumedActions() >= state.getMaxActions()) return RuntimeGuardDecision.terminated("BUDGET_EXHAUSTED");
    if (state.getNoProgressCount() >= 2) return RuntimeGuardDecision.terminated("NO_PROGRESS");
    if (repeatedCount >= 2) return RuntimeGuardDecision.terminated("REPEATED_ACTION");
    return RuntimeGuardDecision.allowed();
}
```

- [ ] **Step 4: Run policy tests and verify GREEN**

Run the command from Step 2. Expected: all `ResearchRuntimePolicyTest` tests pass.

### Task 2: SQLite checkpoint、event stream 与 CAS

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/runtime/ResearchRuntimeRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/research/runtime/ResearchRuntimeRepositoryTest.java`

- [ ] **Step 1: Write repository tests first**

```java
@Test
void compareAndSetRejectsStaleCheckpointVersion() {
    ResearchRuntimeCheckpoint created = repository.initialize(9L, 12);
    assertTrue(repository.compareAndSetStatus(9L, created.getStateVersion(), "RUNNING", "plan"));
    assertFalse(repository.compareAndSetStatus(9L, created.getStateVersion(), "RUNNING", "collect"));
}

@Test
void eventsUseMonotonicSequenceWithinRun() {
    repository.initialize(9L, 12);
    repository.appendEvent(event(9L, "NODE_STARTED"));
    repository.appendEvent(event(9L, "NODE_COMPLETED"));
    assertEquals(Arrays.asList(1, 2, 3), sequences(repository.findEvents(9L)));
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -q -pl finscope-dao -am -Dtest=ResearchRuntimeRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because schema/repository are absent.

- [ ] **Step 3: Add schema and repository**

Create `research_runtime_checkpoint` with one row per run and `research_runtime_event` with `UNIQUE(research_run_id, sequence_no)`. CAS updates use:

```sql
UPDATE research_runtime_checkpoint
SET status=?, current_node=?, state_version=state_version+1, updated_at=?
WHERE research_run_id=? AND state_version=?
```

Event append obtains `COALESCE(MAX(sequence_no),0)+1` in the same synchronized repository method. SQLite transactions and the unique constraint are the final concurrency guard.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: repository tests pass.

### Task 3: Runtime service、checkpoint transitions 与 resume

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/ResearchRuntimeService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/RuntimeNodeStart.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/runtime/ResearchRuntimeServiceTest.java`

- [ ] **Step 1: Write transition and resume tests first**

```java
@Test
void resumeClaimsInterruptedRunOnce() {
    when(repository.findCheckpoint(7L)).thenReturn(Optional.of(interrupted(4)));
    when(repository.compareAndSetStatus(7L, 4, "RUNNING", "collect_sources")).thenReturn(true, false);
    assertEquals(1, service.resume(7L).getResumeCount());
    assertThrows(BusinessConflictException.class, () -> service.resume(7L));
}

@Test
void completedNodeIsSkippedDuringResume() {
    when(repository.hasCompletedNode(7L, "plan_sources")).thenReturn(true);
    RuntimeNodeStart start = service.startNode(7L, "plan_sources", "plan:7");
    assertTrue(start.isAlreadyCompleted());
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -q -pl finscope-service -am -Dtest=ResearchRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement transition service**

Expose `initialize`, `startNode`, `completeNode`, `failNode`, `terminate`, `resume` and `view`. Every accepted transition appends an event and advances checkpoint version. `completeNode` receives `stateHash` and `progressDelta`; identical hashes increment `noProgressCount`, changed hashes reset it.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: all transition and resume tests pass.

### Task 4: Adapt existing research orchestration to Runtime

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchStartupRecoveryService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchStartupRecoveryServiceTest.java`

- [ ] **Step 1: Add failing integration-style service tests**

```java
@Test
void createsCheckpointBeforeSchedulingResearch() {
    service.createRun(runDate, themes, 3, false);
    verify(runtime).initialize(501L, 12);
    verify(executor, never()).execute(null);
}

@Test
void resumeSchedulesOnlyAfterRuntimeClaimsExecution() {
    when(runtime.resume(501L)).thenReturn(runningCheckpoint());
    service.resume(501L);
    assertNotNull(executor.captured);
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -q -pl finscope-service -am -Dtest=ResearchServiceHarnessTest,ResearchStartupRecoveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Wrap real nodes**

Initialize runtime immediately after `research_run` persistence. Use stable node IDs: `plan_sources`, `collect_source:{sourceId}:{index}`, `assess_evidence:{round}`, `expand_query:{round}:{index}`, `compose_report`, `verify_output`, `complete`. Check `RuntimeNodeStart.isAlreadyCompleted()` before side effects. The state hash is `articleCount:eventCount:evidenceCount:reportAvailable`.

- [ ] **Step 4: Change startup recovery semantics**

Startup recovery marks open checkpoints `INTERRUPTED` and preserves their current node; it may still fail legacy runs that have no checkpoint. This maintains backward compatibility without auto-running work at startup.

- [ ] **Step 5: Verify GREEN**

Run the command from Step 2 and the existing `ResearchRunPlanServiceTest`.

### Task 5: Deterministic Eval Harness

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/eval/ResearchEvaluation.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/eval/ResearchEvaluationMetric.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/eval/ResearchEvaluationRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/eval/ResearchEvaluationService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/eval/ResearchEvaluationScorer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/eval/ResearchEvaluationScorerTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/research/eval/ResearchEvaluationRepositoryTest.java`

- [ ] **Step 1: Write scoring tests first**

```java
@Test
void passingRunScoresAllSixMetricsDeterministically() {
    ResearchEvaluation evaluation = scorer.score(snapshot(
            "COMPLETED", 4, 3, validTrace(), checkpoint(8, 12, 1)));
    assertEquals(100, evaluation.getScore());
    assertEquals("PASS", evaluation.getGateStatus());
    assertEquals(6, evaluation.getMetrics().size());
}

@Test
void completedRunWithoutReportTriggersCriticalGate() {
    ResearchEvaluation evaluation = scorer.score(snapshot("COMPLETED", 0, 0, validTrace(), checkpoint(3, 12, 0)));
    assertEquals("FAIL", evaluation.getGateStatus());
    assertTrue(evaluation.getCriticalIssues().contains("COMPLETED_WITHOUT_REPORT"));
}
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -q -pl finscope-service -am -Dtest=ResearchEvaluationScorerTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement scoring formulas**

Use integer scores and fixed version `deep-research-rules-v1`: completion 20, evidence 25, source diversity 15, trace integrity 20, budget safety 10, recovery 10. Persist `inputFingerprint`; repository returns the existing evaluation for the same `(research_run_id, evaluator_version, input_fingerprint)`.

- [ ] **Step 4: Add persistence tests and implementation**

Add `research_eval_run` and `research_eval_metric` tables, then run the DAO test directly. Expected: repeated upsert returns one eval run and six metric rows.

- [ ] **Step 5: Verify GREEN**

Run both scorer and repository tests.

### Task 6: REST API and enriched run detail

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/ResearchRunDetailResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`

- [ ] **Step 1: Write failing API tests**

```java
mvc.perform(get("/api/research/runs/1/runtime"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.data.checkpoint.researchRunId").value(1))
   .andExpect(jsonPath("$.data.events").isArray());

mvc.perform(post("/api/research/runs/1/evaluations"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.data.evaluatorVersion").value("deep-research-rules-v1"))
   .andExpect(jsonPath("$.data.metrics.length()").value(6));
```

- [ ] **Step 2: Verify RED**

Run: `cd backend && mvn -q -pl finscope-web -am '-Dtest=FinScopeApiIntegrationTest#researchRuntimeAndEvaluationAreInspectable' -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement endpoints**

Add GET runtime, POST resume, POST evaluations and GET latest evaluation. Add nullable `runtime` and `latestEvaluation` fields to `ResearchRunDetailResponse` so legacy runs remain readable.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: the API scenario passes.

### Task 7: Research 页 Runtime 与 Eval UI

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/features/research/ResearchRuntimePanel.tsx`
- Create: `frontend/src/features/research/ResearchRuntimePanel.test.tsx`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/features/research/ResearchView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing component tests**

```tsx
it('shows checkpoint budget and deterministic evaluation metrics', async () => {
  render(<ResearchRuntimePanel runtime={runtimeView} evaluation={evaluation} onEvaluate={onEvaluate} onResume={onResume} />);
  expect(screen.getByText('8 / 12')).toBeInTheDocument();
  expect(screen.getByText('Trace 完整性')).toBeInTheDocument();
  expect(screen.getByText('86')).toBeInTheDocument();
});
```

- [ ] **Step 2: Verify RED**

Run: `cd frontend && npm test -- --run src/features/research/ResearchRuntimePanel.test.tsx`

- [ ] **Step 3: Implement UI and actions**

The panel renders checkpoint phase/status, consumed/max actions, resume count, termination reason, latest events and six metric rows. `onEvaluate` calls POST evaluations then refreshes detail; `onResume` calls POST resume then resumes existing polling. Both actions keep the previous detail on error.

- [ ] **Step 4: Verify GREEN**

Run the component test and `ResearchView.test.tsx`.

### Task 8: Full verification and documentation sync

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-26-deep-research-runtime-eval-harness.md`

- [ ] **Step 1: Document runtime and eval commands**

Add API examples and explain deterministic metrics, checkpoint recovery and the boundary between business nodes and runtime policy.

- [ ] **Step 2: Run full backend verification**

Run: `cd backend && env JAVA_HOME=/Users/machengqian/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home /opt/homebrew/bin/mvn -q clean test`

Expected: exit code 0, no failures or errors.

- [ ] **Step 3: Run full frontend verification**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: all Vitest files pass and Vite exits 0.

- [ ] **Step 4: Verify repository hygiene**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended source, test and documentation files are modified.

## Plan self-review

- Every PRD capability maps to a task: checkpoint/CAS (Tasks 1–3), research adapter/recovery (Task 4), six-metric eval (Task 5), API (Task 6), UI (Task 7), verification/docs (Task 8).
- All new production behavior starts with a named failing test and an exact command.
- Type names and endpoint paths are consistent with the technical design.
- No placeholder step or unrelated refactor is included.
