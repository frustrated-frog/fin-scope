# Research Agent Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Observation-driven research Agent loop with dynamic tool selection, persisted working memory, bounded local replanning, finish verification, process visualization and trajectory evaluation.

**Architecture:** Keep the existing Research Mission and Runtime as the deterministic outer loop. Add a persisted Agent Core inside the thesis-research collection phase: context builder -> validated decision -> typed tool dispatcher -> structured observation -> state reducer -> local replan or finish verifier. Expose the append-only trace through the existing run-detail API and render it in the Research tab.

**Tech Stack:** Java 8, Spring Boot 2.7, SQLite/JdbcTemplate, Jackson, JUnit 5/Mockito, React 18, TypeScript, Vitest, CSS.

---

### Task 1: Persist Agent state, decisions and observations

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/agent/ResearchAgentState.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/agent/ResearchAgentDecision.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/agent/ResearchToolObservation.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/agent/ResearchAgentTraceView.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/research/agent/ResearchAgentRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/research/agent/ResearchAgentRepositoryTest.java`

- [x] Write a failing repository test covering initialization, optimistic state updates, append-only decisions and one observation per decision.
- [x] Run `cd backend && mvn -q -pl finscope-dao -am -Dtest=ResearchAgentRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` and verify RED.
- [x] Add the three SQLite tables and domain/repository mappings from the technical design.
- [x] Verify state versions reject stale updates and decision iterations are unique per run.
- [x] Run the focused DAO test and verify GREEN.
- [x] Commit and push: `feat: 增加研究智能体状态与轨迹持久化`.

### Task 2: Define the decision protocol and strict validation

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionDraft.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionContext.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionValidator.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchAgentContextBuilder.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchDecisionValidatorTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchAgentContextBuilderTest.java`

- [x] Write failing tests for legal `TOOL_CALL`, illegal tool, unknown argument, invalid confidence, malformed `FINISH`, repeated fingerprint and context size bounding.
- [x] Run the two focused tests and verify RED.
- [x] Implement strict field/tool/argument policy validation and stable action fingerprints.
- [x] Build context from mission, state, latest gap, recent trace, attempted actions, tool contracts and finish rejection.
- [x] Verify old trace compaction retains the latest four Decision/Observation pairs.
- [x] Run focused tests and verify GREEN.
- [x] Commit and push: `feat: 建立研究智能体决策协议与上下文`.

### Task 3: Implement model decisions and deterministic fallback

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/DeterministicResearchPolicy.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionResult.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchDecisionAgentTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/DeterministicResearchPolicyTest.java`

- [x] Write failing tests for a valid model decision, strict JSON rejection, timeout, disabled model, gap-directed fallback and exhausted-action abort.
- [x] Run focused tests and verify RED.
- [x] Implement a bounded prompt, strict Jackson parser, 8-second timeout and 1,200-token output cap.
- [x] Implement deterministic priority: counter -> support -> primary -> assess -> finish -> abort, excluding attempted fingerprints.
- [x] Persist explicit `MODEL` or `DETERMINISTIC` decision mode and safe fallback reason.
- [x] Run focused tests and verify GREEN.
- [x] Commit and push: `feat: 实现观察驱动的研究决策Agent`.

### Task 4: Add executable tools and structured observations

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchAgentTool.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchAgentToolContext.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchAgentToolRegistry.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchToolDispatcher.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/PublicNewsSearchTool.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/EvidenceAssessTool.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/tool/ResearchToolDispatcherTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/tool/PublicNewsSearchToolTest.java`

- [x] Write failing tests for dynamic lookup, arguments, success deltas, no-progress classification, retryable errors and unknown tool rejection.
- [x] Run focused tests and verify RED.
- [x] Implement the typed registry and dispatcher without reflection.
- [x] Reuse `ResearchSearchSourceFactory`, `FetchService` and run-scoped output counts in the news tool.
- [x] Convert `ResearchMissionService.assess` results into an evidence observation.
- [x] Run focused tests and verify GREEN.
- [x] Commit and push: `feat: 增加可执行研究工具与Observation`.

### Task 5: Implement state reduction, local replan and finish verification

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchAgentStateReducer.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchFinishVerifier.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchFinishVerdict.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/research/mission/ResearchMissionRepository.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchAgentStateReducerTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchFinishVerifierTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchMissionServiceTest.java`

- [x] Write failing tests for memory updates, no-progress counters, fallback counts, accepted/rejected finish and allowed/forbidden plan patches.
- [x] Run focused tests and verify RED.
- [x] Implement compact working-memory reduction and optimistic state updates.
- [x] Add `applyPatch` that only changes pending/failed/interrupted adaptive tasks and increments plan version.
- [x] Implement an independent finish gate using current evidence sufficiency and runtime consistency.
- [x] Run focused tests and verify GREEN.
- [x] Commit and push: `feat: 实现工作记忆局部重规划与完成校验`.

### Task 6: Integrate the Agent loop with the research runtime

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchAgentLoopService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchStartupRecoveryService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchAgentLoopServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchServiceHarnessTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/ResearchStartupRecoveryServiceTest.java`

- [x] Write an integration-style failing test proving the second decision context contains the first Observation and may select another tool.
- [x] Add failing tests for repeated-action rejection, finish rejection continuation, action budget exhaustion and resume without duplicate execution.
- [x] Run focused tests and verify RED.
- [x] Implement one-decision-per-iteration orchestration and Runtime event/checkpoint integration.
- [x] Replace only the thesis run's hard-coded public-search loop; preserve non-thesis legacy execution.
- [x] Verify report synthesis occurs only after Finish Verifier acceptance.
- [x] Run focused service tests and verify GREEN.
- [x] Commit and push: `feat: 接入观察驱动研究智能体循环`.

### Task 7: Expose Agent Core and trajectory evaluation

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/agent/ResearchAgentTrajectoryMetrics.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchTrajectoryEvaluator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/evaluation/ResearchEvaluationScorer.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/ResearchRunDetailResponse.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchTrajectoryEvaluatorTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/response/ResearchRunDetailResponseTest.java`

- [x] Write failing metric tests for validity, follow-up, duplicate, no-progress, replan, finish and fallback rates.
- [x] Write a failing response test for populated and legacy-null Agent Core traces.
- [x] Implement deterministic trajectory scoring and append metrics to the existing evaluation.
- [x] Aggregate state, decisions, observations and metrics into run detail without N+1 queries.
- [x] Run focused service/web tests and verify GREEN.
- [x] Commit and push: `feat: 开放研究智能体轨迹与过程评测`.

### Task 8: Build the Agent decision-flow UI

**Files:**
- Create: `frontend/src/features/research/ResearchAgentDecisionFlow.tsx`
- Create: `frontend/src/features/research/ResearchAgentDecisionFlow.test.tsx`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/features/research/ResearchView.test.tsx`

- [x] Write failing component tests for current subgoal, remaining actions, Decision/Observation pairing, plan patch, fallback and finish rejection.
- [x] Run `cd frontend && npm test -- ResearchAgentDecisionFlow.test.tsx ResearchView.test.tsx` and verify RED.
- [x] Implement the server-driven decision flow between Mission Map and legacy diagnostics.
- [x] Add restrained state transitions and a complete reduced-motion override.
- [x] Verify keyboard and screen-reader structure with region labels and semantic timeline markup.
- [x] Run focused frontend tests and `npm run build`.
- [x] Commit and push: `feat: 增加研究智能体决策过程可视化`.

### Task 9: Regression, documentation status and final delivery

**Files:**
- Modify: `README.md`
- Modify: `docs/产品需求-研究智能体决策内核.md`
- Modify: `docs/技术方案-研究智能体决策内核.md`
- Modify: `docs/superpowers/plans/2026-07-27-research-agent-core.md`

- [x] Run `cd backend && mvn test`.
- [x] Run `cd frontend && npm test`.
- [x] Run `cd frontend && npm run build`.
- [x] Inspect `git diff --check`, `git status --short` and confirm no database, private documents or credentials were added.
- [x] Mark implemented requirements and plan checkboxes accurately; update README architecture and feature descriptions.
- [x] Commit and push: `docs: 完善研究智能体决策内核说明`.
- [x] Confirm `origin/codex/research-agent-core` points to the final commit.
