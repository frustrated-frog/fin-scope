# Adaptive Research Agent Implementation Plan

> **Execution mode:** Use `executing-plans` in the current session and complete the tasks sequentially. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a validated adaptive research planner, typed tool registry, evidence-gap-driven task execution, and a live Research Mission Map.

**Architecture:** The LLM proposes a bounded task DAG, while Java validates and persists it before execution. Existing `ResearchService`, Deep Research Runtime and report pipeline remain the deterministic execution authority; the frontend renders persisted mission state through the existing 750ms polling loop.

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite/JdbcTemplate, Jackson, JUnit 5/Mockito, React 18, TypeScript, Vitest, CSS.

---

### Task 1: Persist the mission contract and task graph

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMission.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMissionTask.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMissionGap.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMissionView.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/mission/ResearchMissionRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/research/mission/ResearchMissionRepositoryTest.java`

- [ ] **Step 1: Write a failing repository test**

Create a temporary SQLite database, initialize a pending mission, replace its tasks, start and complete one task, append two gaps, and assert:

```java
assertEquals("RUNNING", repository.findMission(9L).get().getStatus());
assertEquals(Arrays.asList("baseline_scan", "search_counter"),
        repository.findTasks(9L).stream().map(ResearchMissionTask::getTaskKey).collect(Collectors.toList()));
assertEquals(2, repository.findGaps(9L).size());
```

- [ ] **Step 2: Run the DAO test and verify RED**

Run:

```bash
cd backend
mvn -q -pl finscope-dao -am \
  -Dtest=ResearchMissionRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because mission domain and repository types do not exist.

- [ ] **Step 3: Add schema, domain models and repository**

Implement the three tables exactly as defined in the technical design. Repository task updates must use:

```sql
UPDATE research_mission_task
SET status=?, output_summary=?, evidence_delta=?, source_delta=?,
    skip_reason=?, started_at=?, ended_at=?, updated_at=?
WHERE research_run_id=? AND task_key=?
```

Use stable delimiter encoding for list fields and order tasks by `id`, gaps by `assessment_index`.

- [ ] **Step 4: Run the repository test and verify GREEN**

Run the command from Step 2. Expected: all mission repository tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加研究任务图持久化模型"
git push -u origin codex/adaptive-research-agent
```

### Task 2: Add the typed tool registry and plan validator

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchToolDescriptor.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchToolRegistry.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionDraft.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionTaskDraft.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanValidator.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchPlanValidatorTest.java`

- [ ] **Step 1: Write failing validator tests**

Cover a valid DAG and these invalid cases:

```java
assertThrows(IllegalArgumentException.class, () -> validator.validate(draftWithTool("shell")));
assertThrows(IllegalArgumentException.class, () -> validator.validate(draftWithCycle()));
assertThrows(IllegalArgumentException.class, () -> validator.validate(draftWithDuplicateKey()));
assertThrows(IllegalArgumentException.class, () -> validator.validate(draftWithoutCounterTask()));
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -q -pl finscope-service -am \
  -Dtest=ResearchPlanValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing classes.

- [ ] **Step 3: Implement registry and validator**

Registry returns immutable descriptors for:

```text
source_scan
public_news_search
evidence_assess
report_synthesis
```

Validator applies field limits, tool/intent whitelists, dependency existence, Kahn topological sorting and required task checks.

- [ ] **Step 4: Verify GREEN and refactor**

Run the command from Step 2. Expected: valid graph passes and every invalid graph is rejected for its intended reason.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-domain backend/finscope-service
git commit -m "feat: 建立类型化研究工具与计划校验"
git push
```

### Task 3: Implement Planning Agent with deterministic fallback

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/DeterministicResearchPlanner.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningInput.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningResult.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchPlanningAgentTest.java`

- [ ] **Step 1: Write failing planner tests**

Test:

```java
ResearchPlanningResult result = agent.plan(input);
assertEquals("LLM_VALIDATED", result.getPlanningMode());
assertEquals("public_news_search", result.getDraft().task("search_counter").getToolCode());
```

and invalid JSON fallback:

```java
when(llm.complete(anyString(), anyString(), anyInt())).thenReturn("{\"tasks\":[{\"toolCode\":\"shell\"}]}");
assertEquals("DETERMINISTIC", agent.plan(input).getPlanningMode());
assertEquals("PLAN_REJECTED", agent.plan(input).getFallbackReason());
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -q -pl finscope-service -am \
  -Dtest=ResearchPlanningAgentTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement strict JSON parsing and fallback**

Use Jackson `ObjectMapper`, an 8-second timeout and a 2,000-token output cap. Do not repair invalid output. The deterministic planner must emit baseline, support, counter, primary-source, assess and synthesis tasks.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: configured valid model uses `LLM_VALIDATED`; missing/invalid model uses deterministic output with explicit reason.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service
git commit -m "feat: 实现受控研究规划Agent"
git push
```

### Task 4: Add evidence gap assessment and mission orchestration

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchEvidenceGapAnalyzer.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/EvidenceSufficiency.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchEvidenceGapAnalyzerTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchMissionServiceTest.java`

- [ ] **Step 1: Write failing gap tests**

Assert recommendation order:

```java
assertEquals("COUNTER", analyzer.assess(cardsWithSupportOnly()).getRecommendedIntent());
assertEquals("PRIMARY", analyzer.assess(cardsFromOneSource()).getRecommendedIntent());
assertEquals("NONE", analyzer.assess(sufficientCards()).getRecommendedIntent());
```

- [ ] **Step 2: Verify RED**

Run the two new test classes and confirm missing analyzer/service failures.

- [ ] **Step 3: Implement structured assessment and lifecycle**

Expose counts from `EvidenceSufficiency`, compute a stable SHA-256 state hash, persist every assessment, and implement:

```java
initializePending(run, thesis, maxActions)
plan(run, thesis, themeCodes)
startTask(runId, taskKey)
completeTask(runId, taskKey, summary, evidenceDelta, sourceDelta)
skipRemainingSearches(runId, "SUFFICIENT_EVIDENCE")
failActiveTask(runId, message)
completeMission(runId, partial)
```

- [ ] **Step 4: Verify GREEN**

Expected: sufficient evidence skips only pending search tasks; completed tasks remain immutable.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service
git commit -m "feat: 增加证据缺口分析与任务编排"
git push
```

### Task 5: Drive the research runtime from mission tasks

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchSearchSourceFactory.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchStartupRecoveryService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchStartupRecoveryServiceTest.java`

- [ ] **Step 1: Add failing orchestration tests**

Assert that:

```java
verify(missionService).startTask(501L, "baseline_scan");
verify(missionService).assess(501L, "baseline_scan");
verify(fetchService).fetch(argThat(source -> source.getName().contains("反方")));
verify(missionService).completeMission(501L, false);
```

Add a resume test where `search_support` is already completed and verify its fetch is not repeated.

- [ ] **Step 2: Verify RED**

Run `ResearchServiceHarnessTest` and confirm missing mission interactions.

- [ ] **Step 3: Integrate mission lifecycle**

Initialize pending mission in `createRun`. At execution:

- plan before baseline;
- wrap configured-source collection in `baseline_scan`;
- assess after baseline and every search;
- execute pending search tasks using `ResearchSearchSourceFactory`;
- skip remaining search tasks when sufficient;
- wrap assess/report tasks;
- preserve Runtime guard and idempotent resume behavior.

- [ ] **Step 4: Verify GREEN**

Run service and startup recovery tests. Expected: task state mirrors the real execution path.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-service
git commit -m "feat: 接入自适应研究任务执行闭环"
git push
```

### Task 6: Expose mission APIs and detail compatibility

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/ResearchRunDetailResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/FinScopeApiIntegrationTest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/response/ResearchRunDetailResponseTest.java`

- [ ] **Step 1: Write failing API assertions**

Add:

```java
mvc.perform(get("/api/research/tools"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.data[*].code").value(hasItem("public_news_search")));

mvc.perform(get("/api/research/runs/1/mission"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.data.tasks.length()").value(greaterThanOrEqualTo(6)));
```

Assert run detail contains `mission.mission.goal`.

- [ ] **Step 2: Verify RED**

Run the feature integration method and confirm 404/missing JSON.

- [ ] **Step 3: Add controller endpoints and response field**

Use Mission Service and Tool Registry; keep the existing response constructor overload so unrelated tests compile.

- [ ] **Step 4: Verify GREEN**

Run API and response tests. Expected: new runs expose mission data and old constructor behavior remains unchanged.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-web
git commit -m "feat: 开放研究任务图与工具接口"
git push
```

### Task 7: Build the Research Mission Map

**Files:**
- Create: `frontend/src/features/research/ResearchMissionMap.tsx`
- Create: `frontend/src/features/research/ResearchMissionMap.test.tsx`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing component tests**

Render a mission with one running counter-search task and assert:

```tsx
expect(screen.getByRole('region', { name: '研究作战图' })).toBeInTheDocument();
expect(screen.getByText('反方证据搜索')).toBeInTheDocument();
expect(screen.getByText('正在取证')).toBeInTheDocument();
expect(screen.getByText('缺少反向或风险证据')).toBeInTheDocument();
expect(screen.getByText('规则计划')).toBeInTheDocument();
```

Add tests for completed mission, old run without mission and mobile-safe semantic ordering.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd frontend
npx vitest run src/features/research/ResearchMissionMap.test.tsx
```

Expected: component/types missing.

- [ ] **Step 3: Implement component and types**

Group tasks into baseline/search/assess/synthesis lanes. Display status text in addition to tone. Use the technical design token palette, one animated evidence pulse for `RUNNING`, and a vertical layout below 820px.

- [ ] **Step 4: Integrate and verify GREEN**

Place Mission Map above the legacy progress panel. Run Mission Map and Research View tests.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: 增加研究过程可视化作战图"
git push
```

### Task 8: Documentation and full verification

**Files:**
- Modify: `README.md`
- Modify: `docs/路线图.md`

- [ ] **Step 1: Update documentation**

Document the adaptive planning boundary, registered tools, fallback semantics, APIs and Mission Map. Do not claim parallel SQLite execution.

- [ ] **Step 2: Run backend verification**

```bash
cd backend
JAVA_HOME=/Users/machengqian/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
  /opt/homebrew/bin/mvn test
```

Expected: all modules pass on Java 8.

- [ ] **Step 3: Run frontend verification**

```bash
cd frontend
npm test -- --run
npm run build
```

Expected: all tests and TypeScript/Vite build pass. The existing bundle-size warning is informational.

- [ ] **Step 4: Check the final diff**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only adaptive-research files are modified.

- [ ] **Step 5: Commit and push**

```bash
git add README.md docs
git commit -m "docs: 更新自适应研究智能体说明"
git push
```
