# FinScope Research Agent Workflow Design

This design is captured as a full TRD in `docs/trd-research-agent-workflow.md`.

Approved direction:

1. Build the governed research pipeline first.
2. Layer the research mission workspace on top gradually.
3. Keep deterministic FinScope services as the execution backbone.
4. Use agent capability for mission understanding, controlled LLM nodes, trace, budget, fallback, and recovery.

First implementation slice:

1. Add persistent research run plan state.
2. Return plan steps from research run detail.
3. Display plan steps and richer trace metadata in the Research tab.
4. Keep natural-language mission, clarification, and resume controls for later phases.

Self-review:

1. No placeholders.
2. No contradiction with the full TRD.
3. Scope is intentionally narrowed to Phase 1 for immediate implementation.
