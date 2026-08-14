# Global Expectations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only Global Expectations workspace that persists and presents relevant Polymarket probability changes as research leads.

**Architecture:** A public RPC client discovers configured-topic markets and reads public price/liquidity data. Service orchestration snapshots successful reads, calculates local changes and exposes a view; the React view is a separate navigation destination with only Dashboard, Brief and Timeline summary links.

**Tech Stack:** Java 21, Spring Boot, SQLite/JdbcTemplate, Jackson, React 18, TypeScript, Vitest, CSS.

---

### Task 1: Domain, schema and public client

**Files:**
- Create domain prediction-market models and DAO migration/repositories.
- Create `finscope-rpc/.../polymarket/PolymarketPublicClient.java`.
- Test RPC mapping and repository uniqueness.

- [ ] Write failing tests for mapping a Gamma market and CLOB price/book data to a typed public market snapshot.
- [ ] Run the focused Maven test and verify failure because the client/model does not exist.
- [ ] Implement minimal typed mapping, request timeouts, response validation and SQLite market/snapshot identity constraints.
- [ ] Run focused tests and verify pass.
- [ ] Commit `feat: 增加预测市场公开数据适配`.

### Task 2: Refresh, views and REST API

**Files:**
- Create service refresh, anomaly calculation, scheduler and read view.
- Create web controller and Timeline archive request handling.
- Test normal refresh, insufficient baseline and external failure fallback.

- [ ] Write failing service tests for significant liquid 24-hour changes and for retained stale data after a failed fetch.
- [ ] Run the focused Maven test and verify expected failure.
- [ ] Implement configuration-backed watch rules, snapshot refresh, local signal calculation and read-only REST endpoints.
- [ ] Run focused tests and verify pass.
- [ ] Commit `feat: 增加全球预期监控服务`.

### Task 3: Global Expectations UI and integrations

**Files:**
- Create `frontend/src/features/global-expectations/GlobalExpectationsView.tsx` and tests.
- Modify navigation, app state/types, Dashboard, Brief rendering and Timeline origin display.
- Modify `frontend/src/styles.css` with responsive, reduced-motion-safe component styles.

- [ ] Write a failing component test proving filtering, signal presentation and archiving action.
- [ ] Run the focused Vitest test and verify expected failure.
- [ ] Implement the independent data view, restrained dashboard/brief summaries and Timeline archive interaction.
- [ ] Run component tests and production build; inspect a local rendered screen.
- [ ] Commit `feat: 增加全球预期监控工作台`.

### Task 4: Full verification and review

- [ ] Run backend module tests and frontend test/build suites.
- [ ] Review code against module boundaries, injection style, brace rules, failure handling and the design acceptance list.
- [ ] Commit any review fixes and push the feature branch.
