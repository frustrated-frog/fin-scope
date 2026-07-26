# Research Agent Core Design

## Problem

The existing adaptive research mission is a safe plan executor, but its next action is predetermined by Java iteration. It does not pass a tool Observation back into a model-driven decision step, cannot dynamically select between tools, and cannot patch only the affected part of a plan.

## Approved direction

Build a single-agent, hierarchical loop:

- outer Mission/Runtime loop owns lifecycle, safety, budget, idempotency and report synthesis;
- inner decision loop owns subgoal selection, bounded tool choice, Observation consumption and local replanning;
- persisted working memory makes the loop resumable;
- an independent Finish Verifier prevents the model from declaring success early;
- the UI visualizes audit summaries and state deltas without exposing hidden chain-of-thought.

The full product contract is in `docs/产品需求-研究智能体决策内核.md`. The code-level architecture, schemas, decision protocol, fallback rules and test strategy are in `docs/技术方案-研究智能体决策内核.md`.

## Scope decisions

1. Keep the existing Java 8/Spring Boot/SQLite stack and do not add an agent framework.
2. Reuse `ResearchRuntimeService`, `ResearchMissionService`, `FetchService`, `ResearchReportService` and the existing evidence gate.
3. Expose only `public_news_search` and `evidence_assess` to the inner dispatcher in V1.
4. Keep `source_scan` and `report_synthesis` under outer-loop control.
5. Persist state, decisions and observations in append-oriented SQLite tables.
6. Add bounded working memory and prior-run episodic summaries without a vector database.
7. Implement local plan patches with strict limits and preserve completed history.
8. Extend the existing run-detail response and Research tab rather than create a separate tab.
9. Extend deterministic evaluation with trajectory metrics.
10. Preserve the deterministic fallback so research remains usable when the model fails.

## Success signal

The implementation is complete when an integration test proves that the second decision receives the first tool Observation and may choose a different action, while runtime guards, restart recovery, UI trace rendering and full regression verification remain green.
