# TRD: FinScope Research Agent Workflow

## 1. Document Information

- Project: FinScope
- Document type: Technical Requirements Document
- Date: 2026-07-09
- Status: Approved for phased implementation by owner delegation
- Primary surface: Research tab
- Related existing documents:
  - `docs/trd-agent-harness-hardening.md`
  - `docs/trd-event-research-system.md`
  - `docs/superpowers/specs/2026-06-27-plan-a-research-hardening-design.md`
- Local reference material:
  - `/Users/machengqian.1/Documents/研究/self-summary/Harness Engineering 总结.md`
  - `/Users/machengqian.1/Documents/研究/self-summary/Claude Code：12 个可复用的 Agentic Harness 设计模式.md`
  - `/Users/machengqian.1/Documents/面经/开源项目实现调研/Agent任务规划机制深度调研.md`
  - `/Users/machengqian.1/Documents/面经/开源项目实现调研/Agent循环最大次数与防死循环机制深度解析.md`
  - `/Users/machengqian.1/Documents/面经/开源项目实现调研/DeerFlow 的工具调用去重设计.md`
  - `/Users/machengqian.1/Documents/面经/开源项目实现调研/查询改写与用户澄清设计.md`

## 2. Executive Summary

Research tab must evolve from a manual "run research" page into a governed agent workflow console.

The target is not a free-form chatbot. FinScope's advantage is a local-first, evidence-backed, deterministic investment research workbench. The agent layer should make the workflow more capable, but it must not gain unchecked write authority or hide decisions behind natural language.

The recommended architecture is a hybrid agent workflow:

```text
Natural-language mission
  -> Query rewrite and slot extraction
  -> Clarification if required
  -> Structured research plan
  -> Deterministic execution steps
  -> Controlled LLM nodes
  -> Evidence, event, learning, content artifacts
  -> Trace, budget, warnings, fallback, resumability
```

The Research tab should expose this lifecycle as an operational workbench:

1. Mission entry: structured controls remain, natural-language request is added.
2. Plan state: every research run has explicit steps with status, dependencies, attempts, timings, and outcomes.
3. Execution trace: agent nodes show fingerprint, fallback, error type, termination reason, budget snapshot, and progress delta.
4. Recovery controls: failed or skipped steps can later be retried, skipped, or resumed from the last meaningful checkpoint.
5. Clarification path: ambiguous missions pause before execution and ask focused questions instead of guessing.

The first implementation phase must build the plan-state backbone and Research detail UI. Natural-language mission and recovery depend on that backbone and should be layered on top.

## 3. Product Positioning

FinScope is a personal investment intelligence system for learning, research, content preparation, and interview/project demonstration. The Research tab should communicate three product qualities:

1. It can run a complete research workflow, not just fetch feeds.
2. It can explain what happened during a run, including partial failures.
3. It can progressively become more agentic without sacrificing auditability.

The most advanced version is not "an autonomous model decides everything." The stronger design is:

> The model understands fuzzy research goals; the system owns planning, execution eligibility, budget, evidence grounding, persistence, and recovery.

That positioning maps well to the local interview material. Production-grade agent systems are judged by plan schema, loop control, tool governance, traceability, fallback, and state recovery, not by how many model calls they make.

## 4. Goals

### 4.1 User Goals

1. Run daily or ad-hoc investment research by theme, date, and source strategy.
2. Understand exactly what a run did and where it failed.
3. See research artifacts connected to the run: events, evidence, learning tasks, content ideas, and brief.
4. Start with a natural-language research objective when structured controls are too slow.
5. Receive clarification when the objective is underspecified or conflicts with available sources.
6. Resume useful work after partial failures without losing completed steps.

### 4.2 Engineering Goals

1. Keep the modular monolith architecture.
2. Keep deterministic services as the execution backbone.
3. Add explicit plan state for every research run.
4. Make agent traces machine-readable and UI-readable.
5. Add loop guards: max nodes, max LLM calls, repeated action detection, progress delta, termination reasons.
6. Add query rewrite and clarification as controlled nodes, not a chat subsystem.
7. Make every new behavior testable through service or API tests.

### 4.3 Demonstration Goals

The feature should help explain agent engineering in interviews:

1. Plan-and-execute workflow with `plan + past_steps + response`.
2. State-machine driven recovery.
3. Stable action fingerprint and warning/hard thresholds.
4. LLM output schema validation and deterministic fallback.
5. Clarification as an interruptible workflow state.
6. Trace-first observability.

## 5. Non-Goals

1. Do not build a general-purpose chat assistant.
2. Do not allow the model to directly write database rows.
3. Do not introduce LangGraph, CrewAI, AutoGen, MQ, Redis, vector DB, or a workflow engine.
4. Do not implement autonomous multi-agent delegation in the first phases.
5. Do not expose raw prompts in the default UI.
6. Do not make investment recommendations or trading advice.
7. Do not scrape private or credentialed data.
8. Do not couple runtime behavior to Codex or any development tool.

## 6. Current State

### 6.1 Existing Capabilities

The codebase already has:

1. `ResearchService.createRun(...)`.
2. `ThemeProfileService`, `SourcePlanner`, `SourceProfile`, and `ResearchRun`.
3. `research_run` and `research_run_source` tables.
4. `AgentHarness`, `AgentRunContext`, `AgentBudgetPolicy`, `AgentNodeResult`.
5. `AgentTraceService` and enriched `agent_run` columns.
6. `ResearchView` with run controls, run list, planned sources, and trace rows.
7. `AgentRunsView` for latest traces.
8. Event, evidence, learning, content idea, and brief services.

### 6.2 Current Gaps

1. There is no persistent `research_run_plan` table.
2. `ResearchRunPlan` only contains the run and planned sources, not steps.
3. Step status is implicit in trace rows, so the UI cannot answer "what is pending, failed, skipped, or retryable?"
4. `ResearchService.execute(...)` is a loop over sources plus brief generation, but the workflow is not represented as a plan.
5. `AgentHarness` checks repeated action fingerprints, but does not own step-state transitions.
6. Research detail returns planned sources and agent runs, but no plan state.
7. Natural-language research requests are not supported.
8. No clarification state exists for empty source plans, ambiguous themes, or underspecified goals.
9. No retry/resume API exists.

## 7. Design Principles

### 7.1 Deterministic Mainline, Agentic Edges

Data fetching, article persistence, dedupe, event clustering, evidence storage, and brief writing remain deterministic service responsibilities. LLM nodes may help with:

1. Mission rewrite and slot extraction.
2. Evidence candidate extraction.
3. Article/event interpretation.
4. Learning question generation.
5. Content angle generation.
6. Human-readable synthesis from grounded artifacts.

### 7.2 Plan Is a Runtime Object

A plan is not prose. It must be persisted, queryable, and recoverable. Each plan step must have:

1. Stable `step_id`.
2. Human-readable title.
3. Type and executor.
4. Status.
5. Dependencies.
6. Input summary and output summary.
7. Attempts and max attempts.
8. Timing.
9. Error type and error message.
10. Fallback and termination reason.

### 7.3 Trace Is Product Surface

Trace is not only debugging. It is part of the Research tab's value. Users should see why a run is partial, why a node fell back, whether budget was hit, and what evidence was produced.

### 7.4 Clarification Is a State, Not a Chat

When a mission is ambiguous, the run should move to `NEEDS_CLARIFICATION` with a structured question and options. User response should update the mission or plan and then continue. The first version can return clarification without multi-turn chat.

### 7.5 Runtime Guards Beat Prompt Rules

Prompts can instruct the model, but runtime code must enforce:

1. Max nodes.
2. Max LLM calls.
3. Max retries.
4. Same-action warning and hard thresholds.
5. No-progress limit.
6. Error classification.
7. Output schema validation.

### 7.6 Advanced but Local-First

The system should show agent capability through clear engineering, not external dependency sprawl. SQLite, Java services, React UI, and local Markdown remain the center.

## 8. Target User Experience

### 8.1 Research Tab Layout

Research tab evolves into three zones:

1. Mission/Run Launcher
   - Structured fields: date, themes, max sources, include disabled.
   - Natural-language mission input.
   - "Preview plan" and "Run research" actions in later phases.

2. Runs and Plan
   - Run list remains dense and operational.
   - Selecting a run opens a detail panel with plan steps.
   - Each step shows status, duration, attempt, progress, error/fallback badge.

3. Trace and Artifacts
   - Planned sources.
   - Agent trace timeline.
   - Warning/fallback panel.
   - Links to generated brief, events, evidence, learning tasks, and content ideas.

### 8.2 Status Model

Run statuses:

```text
RUNNING
COMPLETED
PARTIAL_SUCCESS
FAILED
NEEDS_CLARIFICATION
CANCELLED
```

Plan step statuses:

```text
PENDING
RUNNING
COMPLETED
FAILED
SKIPPED
BLOCKED
```

Step status interpretation:

1. `PENDING`: dependencies not complete or not started.
2. `RUNNING`: currently executing.
3. `COMPLETED`: success criteria satisfied.
4. `FAILED`: no fallback succeeded and the step is not safely skippable.
5. `SKIPPED`: intentionally not executed because fallback or guard decided it was unnecessary or unsafe.
6. `BLOCKED`: waiting for user clarification or external configuration.

## 9. Workflow Architecture

### 9.1 High-Level Flow

```text
ResearchController
  -> ResearchMissionService
       -> QueryRewriteAgent or deterministic parser
       -> ClarificationPolicy
  -> ResearchService
       -> ThemeProfileService
       -> SourcePlanner
       -> ResearchRunRepository
       -> ResearchRunPlanService
       -> ResearchExecutionService
            -> AgentHarness
            -> FetchService
            -> Event/Evidence/Learning/Content services
            -> BriefService
       -> AgentTraceService
```

Phase 1 can keep execution inside `ResearchService`, but it must introduce `ResearchRunPlanService` and persist plan steps. Later phases may extract `ResearchExecutionService` to keep `ResearchService` small.

### 9.2 Plan Template

The default research plan is:

```text
plan_sources
  -> fetch_sources
  -> classify_events
  -> extract_evidence
  -> compose_brief
  -> summarize_run
```

Phase 1 implementation may model source fetching as one aggregate step plus individual trace rows. Later phases can add child steps for each source if needed.

### 9.3 Mission Flow

Structured request:

```json
{
  "runDate": "2026-07-09",
  "themeCodes": ["china_macro", "ai_startup"],
  "maxSourcesPerTheme": 3,
  "includeDisabled": false
}
```

Natural-language request:

```json
{
  "missionText": "今天跟一下 AI 创业融资、中国宏观和公司 IPO，有禁用来源就先不跑"
}
```

Rewrite output:

```json
{
  "runDate": "2026-07-09",
  "themeCodes": ["ai_startup", "china_macro", "company_ipo"],
  "maxSourcesPerTheme": 3,
  "includeDisabled": false,
  "confidence": 0.86,
  "missingSlots": [],
  "rewriteSummary": "Run daily research for AI startup, China macro, and company IPO themes."
}
```

Clarification output:

```json
{
  "status": "NEEDS_CLARIFICATION",
  "question": "AI 创业主题当前没有启用来源，是否包含停用来源？",
  "options": [
    {"label": "包含停用来源", "value": "include_disabled"},
    {"label": "跳过该主题", "value": "skip_theme"}
  ]
}
```

## 10. Domain Model

### 10.1 ResearchRunPlanStep

Package: `com.finscope.domain.research`

Fields:

```text
id: Long
researchRunId: Long
stepId: String
title: String
stepType: String
executor: String
status: String
dependencies: List<String>
inputSummary: String
outputSummary: String
errorType: String
errorMessage: String
fallbackUsed: boolean
fallbackReason: String
terminationReason: String
attempt: int
maxAttempts: int
progressDelta: int
startedAt: LocalDateTime
endedAt: LocalDateTime
createdAt: LocalDateTime
updatedAt: LocalDateTime
metadataJson: String
```

### 10.2 ResearchMission

Later phase domain object:

```text
id: Long
researchRunId: Long
missionText: String
rewriteJson: String
clarificationQuestion: String
clarificationOptionsJson: String
clarificationAnswerJson: String
status: String
createdAt: LocalDateTime
updatedAt: LocalDateTime
```

This may be deferred until natural-language input is implemented. Phase 1 can store no mission row.

### 10.3 ResearchRun Extensions

Add later when useful:

```text
objective: String
terminationReason: String
warningCount: Integer
llmCallCount: Integer
fallbackCount: Integer
```

Phase 1 can avoid changing `research_run` unless needed by UI.

## 11. Persistence

### 11.1 research_run_plan

```sql
CREATE TABLE IF NOT EXISTS research_run_plan (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  research_run_id INTEGER NOT NULL,
  step_id TEXT NOT NULL,
  title TEXT NOT NULL,
  step_type TEXT NOT NULL,
  executor TEXT NOT NULL,
  status TEXT NOT NULL,
  dependencies TEXT,
  input_summary TEXT,
  output_summary TEXT,
  error_type TEXT,
  error_message TEXT,
  fallback_used INTEGER NOT NULL DEFAULT 0,
  fallback_reason TEXT,
  termination_reason TEXT,
  attempt INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 1,
  progress_delta INTEGER NOT NULL DEFAULT 0,
  started_at TEXT,
  ended_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  metadata_json TEXT,
  UNIQUE(research_run_id, step_id)
);

CREATE INDEX IF NOT EXISTS idx_research_run_plan_run
  ON research_run_plan(research_run_id);

CREATE INDEX IF NOT EXISTS idx_research_run_plan_status
  ON research_run_plan(status);
```

Dependencies should be stored as comma-separated step IDs in Phase 1 to match existing simple storage style. JSON can be introduced later if plan graph complexity grows.

### 11.2 research_mission

Deferred until natural-language mission:

```sql
CREATE TABLE IF NOT EXISTS research_mission (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  research_run_id INTEGER,
  mission_text TEXT NOT NULL,
  rewrite_json TEXT,
  clarification_question TEXT,
  clarification_options_json TEXT,
  clarification_answer_json TEXT,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

## 12. Service Design

### 12.1 ResearchRunPlanService

Responsibilities:

1. Create default plan steps for a run.
2. Mark a step as running.
3. Complete a step with output summary and progress delta.
4. Fail a step with error type and message.
5. Skip or block a step with termination reason.
6. List plan steps for run detail.
7. Keep repository-level operations small and business-free.

Required public methods:

```java
List<ResearchRunPlanStep> initializeDefaultPlan(Long researchRunId, int sourceCount);
ResearchRunPlanStep start(Long researchRunId, String stepId);
ResearchRunPlanStep complete(Long researchRunId, String stepId, String outputSummary, int progressDelta);
ResearchRunPlanStep fail(Long researchRunId, String stepId, String errorType, String errorMessage);
ResearchRunPlanStep skip(Long researchRunId, String stepId, String reason);
List<ResearchRunPlanStep> findByRunId(Long researchRunId);
```

### 12.2 ResearchService Integration

On create run:

1. Resolve themes.
2. Plan sources.
3. Save `research_run`.
4. Save planned sources.
5. Initialize default plan.
6. Execute plan.

During execution:

1. `plan_sources` completes after sources are persisted.
2. `fetch_sources` runs around the per-source loop.
3. `compose_brief` runs around `briefService.generate(...)`.
4. `summarize_run` completes after counts and status are updated.
5. On exception, current step fails and run becomes `FAILED`.

Phase 1 can mark `classify_events` and `extract_evidence` as completed with aggregate counts after fetch because existing ingestion side effects already run under fetch/article pipeline. Later phases can split these into explicit service calls if needed.

### 12.3 AgentHarness Evolution

Current `AgentHarness.runNode(...)` should remain small. Later phases can add:

1. Node budget pre-check.
2. LLM budget pre-check.
3. No-progress tracking.
4. Retry helper.
5. Error classifier.
6. Hook to update plan steps directly or via event callback.

Phase 1 should not overfit. It should connect plan state from `ResearchService` rather than rebuilding the harness.

## 13. API Contract

### 13.1 Existing Research Detail

Current:

```text
GET /api/research/runs/{id}
```

Current response:

```json
{
  "run": {},
  "plannedSources": [],
  "agentRuns": []
}
```

Target response:

```json
{
  "run": {},
  "plannedSources": [],
  "planSteps": [],
  "agentRuns": []
}
```

### 13.2 Future Mission APIs

```text
POST /api/research/missions/preview
POST /api/research/missions
POST /api/research/missions/{id}/clarify
POST /api/research/runs/{id}/resume
POST /api/research/runs/{id}/steps/{stepId}/retry
POST /api/research/runs/{id}/steps/{stepId}/skip
```

These are not Phase 1 requirements.

## 14. UI Requirements

### 14.1 Phase 1 Research Detail

Research detail panel must show:

1. Run status and summary.
2. Plan step list before or beside source list.
3. Step status badge.
4. Attempt count.
5. Duration when available.
6. Output summary.
7. Error/fallback reason when present.
8. Agent trace rows with fallback, error type, and termination reason.

The UI should remain dense and operational. Avoid marketing copy, hero sections, or chat-like presentation.

### 14.2 Later Mission UI

Add a compact mission input:

```text
[自然语言研究目标 input] [Preview plan] [Run]
```

If clarification is needed:

1. Show a small blocked state panel.
2. Show the exact question.
3. Show 2-3 option buttons.
4. Let the user choose and continue.

## 15. Error Handling

### 15.1 Error Types

Use stable strings:

```text
UNKNOWN
VALIDATION_FAILED
NO_SOURCES
SOURCE_FETCH_FAILED
LLM_UNCONFIGURED
LLM_TIMEOUT
LLM_INVALID_JSON
UNGROUNDED_OUTPUT
BUDGET_EXCEEDED
REPEATED_ACTION
NO_PROGRESS
NEEDS_CLARIFICATION
```

### 15.2 Run Outcome Rules

1. All required steps completed: `COMPLETED`.
2. Non-critical source failures but brief generated: `PARTIAL_SUCCESS`.
3. Critical persistence or plan initialization failure: `FAILED`.
4. Missing user decision before execution: `NEEDS_CLARIFICATION`.
5. Repeated action hard stop with no fallback: `FAILED` or `PARTIAL_SUCCESS` depending on completed artifacts.

## 16. Testing Strategy

### 16.1 Backend Unit Tests

Add or extend:

1. `ResearchRunPlanServiceTest`
   - Initializes default steps in order.
   - Starts and completes a step.
   - Fails a step with error type.
   - Dependencies round-trip correctly.

2. `ResearchServiceHarnessTest`
   - Creating a run initializes plan steps.
   - Partial source failure marks fetch step completed with error or marks run partial.
   - Brief generation failure marks compose step failed.

3. Repository tests if existing integration pattern supports local SQLite.

### 16.2 Backend Integration Tests

Extend `FinScopeApiIntegrationTest`:

1. `GET /api/research/runs/{id}` returns `planSteps`.
2. Plan steps are ordered and have expected statuses.

### 16.3 Frontend Tests

Extend `App.test.tsx` or add feature test:

1. Research detail renders plan steps.
2. Trace rows show fallback or error metadata when present.

### 16.4 Verification Commands

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
```

If full backend tests are slow, run targeted tests first, then full tests before completion.

## 17. Phased Implementation

### Phase 1: Plan State Backbone

Deliver:

1. `ResearchRunPlanStep` domain object.
2. `research_run_plan` table and indexes.
3. `ResearchRunPlanRepository`.
4. `ResearchRunPlanService`.
5. `ResearchRunPlan` includes `planSteps`.
6. `ResearchRunDetailResponse` includes `planSteps`.
7. `ResearchService` initializes and updates plan steps.
8. Research tab displays plan steps and richer trace fields.

Acceptance:

1. Creating a research run creates visible plan steps.
2. Detail API returns run, planned sources, plan steps, and agent runs.
3. Failed steps include error information.
4. Existing run list still works.
5. Backend targeted tests and frontend build pass.

### Phase 2: Trace and Guard Polish

Deliver:

1. Agent Runs filters by research run and status.
2. Trace row expansion for input/output metadata.
3. Budget snapshot rendering.
4. Warning/fallback summary panel.
5. More explicit harness result statuses for repeated actions and budget exceeded.

Acceptance:

1. User can identify fallback and termination reasons from UI.
2. User can identify repeated action and budget issues from UI.

### Phase 3: Mission Rewrite and Clarification

Deliver:

1. Mission text input.
2. Deterministic parser for common theme names and dates.
3. Optional LLM rewrite node with JSON schema validation.
4. Clarification response for no sources or ambiguous themes.
5. Preview plan action.

Acceptance:

1. "今天跟一下 AI 创业和中国宏观" maps to a structured request.
2. Missing source case returns `NEEDS_CLARIFICATION`.
3. Rewrite failure falls back to manual controls.

### Phase 4: Recovery Controls

Deliver:

1. Retry failed step.
2. Skip optional step.
3. Resume run from first non-completed step.
4. Persist attempt count and recovery trace.

Acceptance:

1. A failed compose step can be retried without refetching sources.
2. Optional failed steps can be skipped with explicit termination reason.

### Phase 5: Research Mission Workspace

Deliver:

1. Mission history.
2. Links from mission to runs and artifacts.
3. Follow-up tasks generated from unfinished questions.
4. Knowledge-wiki style accumulation into local vault.

Acceptance:

1. Research tab becomes a durable workspace rather than only a run log.
2. Longitudinal research questions survive across daily runs.

## 18. Implementation Constraints

1. New Spring beans use constructor injection.
2. Touching legacy core-path beans should migrate them to constructor injection when reasonable.
3. Repository methods only perform SQL and row mapping.
4. Service methods own state transitions.
5. Web layer only adapts request/response.
6. Domain objects do not depend on Spring or Jackson.
7. Prompt input/output must be summarized or length-limited before persistence.
8. No real credentials or private data in trace.
9. Do not edit user-created unrelated changes.

## 19. Open Design Decisions Already Resolved

1. B + C direction: implement governed pipeline first, then mission workspace.
2. First phase: plan state and Research detail UI.
3. No free-form chatbot in the first implementation.
4. No external workflow framework.
5. Use local Documents research as design inspiration, not as runtime dependency.

## 20. Self-Review

Placeholder scan: no unresolved placeholder markers remain.

Internal consistency: mission, plan, trace, and recovery are separated by phase. Phase 1 does not depend on natural-language mission.

Scope check: the full vision spans multiple phases, but Phase 1 is a single implementable slice with clear acceptance criteria.

Ambiguity check: run statuses, step statuses, table shape, service methods, API response, UI behavior, and verification commands are explicit.
