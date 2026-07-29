# Research Source Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reuse FinScope's provider reliability infrastructure to add structured research materials, broaden research planning, and remove user-controlled source counts.

**Architecture:** Extract provider metadata and capability-neutral guard entry points while preserving domain-specific gateways. Add a `ResearchMaterialGateway` and one strict Agent tool that normalizes provider results into run-scoped evidence. Upgrade planning to select structured materials only for supported A-share theses.

**Tech Stack:** Java 8, Spring Boot 2.7, Jackson, SQLite/JdbcTemplate, React, TypeScript, Vitest, JUnit 5, Mockito.

---

### Task 1: Shared external-provider runtime

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/provider/ExternalDataProvider.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketdata/MarketDataProvider.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketintel/ProviderRequestGuard.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketdata/ProviderRoutePolicy.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketintel/ProviderRequestGuardTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/ProviderRoutePolicyTest.java`

- [ ] Add failing tests proving a non-market provider shares interval, retry and circuit behavior and can be dynamically ordered.
- [ ] Run `mvn -pl finscope-rpc,finscope-service -am -Dtest=ProviderRequestGuardTest,ProviderRoutePolicyTest test` and confirm the new API is missing.
- [ ] Add `ExternalDataProvider`, make `MarketDataProvider` extend it, and add capability-code overloads without changing existing market call sites.
- [ ] Run the focused tests and existing market-data contract tests.
- [ ] Commit with `refactor: 抽取外部数据源可靠性运行时`.

### Task 2: Reusable POST acquisition

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionRequest.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/JdkAcquisitionRuntime.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/acquisition/JdkAcquisitionRuntimeTest.java`

- [ ] Add a failing local-server test proving form and JSON POST bodies are transmitted with bounded response handling.
- [ ] Run the focused acquisition test and confirm POST construction is unavailable.
- [ ] Add immutable request-body support and write the body only for POST while preserving GET behavior.
- [ ] Run acquisition and RPC module tests.
- [ ] Commit with `feat: 增加可复用受控请求能力`.

### Task 3: Research material providers and gateway

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/material/ResearchMaterial.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/material/ResearchMaterialType.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/ResearchMaterialProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/ResearchMaterialRequest.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/CninfoResearchMaterialProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/ClsNewsResearchMaterialProvider.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/material/BrokerReportResearchMaterialProvider.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/material/ResearchMaterialGateway.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/research/material/CninfoResearchMaterialProviderTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/research/material/ClsNewsResearchMaterialProviderTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/material/BrokerReportResearchMaterialProviderTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/material/ResearchMaterialGatewayTest.java`

- [ ] Add failing contract tests for announcement orgId resolution, interaction replies, CLS signature/parsing, broker-report reuse and partial-provider failure.
- [ ] Run focused tests and confirm provider classes are missing.
- [ ] Implement normalized material types, strict host checks, response validation, shared guard execution and gateway aggregation/deduplication.
- [ ] Run provider/gateway tests and module regression tests.
- [ ] Commit with `feat: 接入结构化研究资料来源`.

### Task 4: Research Agent material tool

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchMaterialSearchTool.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchDecisionValidator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchToolRegistry.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchAgentLoopService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/tool/ResearchMaterialSearchToolTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchDecisionValidatorTest.java`

- [ ] Add failing tests for exact arguments, unsupported codes/types, evidence conversion, URL/external-ID deduplication and partial success.
- [ ] Run focused tests and confirm the tool is not registered.
- [ ] Implement the tool and whitelist it as an external action with a stable action fingerprint.
- [ ] Run Agent tool, validator and loop tests.
- [ ] Commit with `feat: 为研究Agent增加资料检索工具`.

### Task 5: Serenity-style planning

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningInput.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/DeterministicResearchPlanner.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanValidator.java`
- Test: planning and validator tests in `backend/finscope-service/src/test/java/com/finscope/service/research/mission/`.

- [ ] Add failing tests for research-map, primary-material, cross-check and counter tasks; assert non-A-share theses never receive the structured tool.
- [ ] Run focused planning tests and confirm the old fixed plan fails the new contract.
- [ ] Add subject code to planning input and implement the eight-step bounded workflow with strict tool/type contracts.
- [ ] Run all mission and Agent planning tests.
- [ ] Commit with `feat: 升级研究任务图与证据规划`.

### Task 6: Automatic source breadth and simplified UI

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/SourcePlanner.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/request/CreateResearchRunRequest.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/ResearchController.java`
- Modify: `frontend/src/features/research/ResearchView.tsx`
- Modify: `frontend/src/App.tsx`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/SourcePlannerTest.java`
- Test: `frontend/src/features/research/ResearchView.test.tsx`

- [ ] Add failing backend and frontend tests asserting all eligible enabled sources are selected and run requests omit both removed fields.
- [ ] Run the focused tests and confirm the old controls/limits fail expectations.
- [ ] Remove request/UI parameters and make `SourcePlanner` select all eligible enabled sources in deterministic quality order.
- [ ] Run service, web and frontend research tests.
- [ ] Commit with `refactor: 改为系统自动规划研究来源`.

### Task 7: Full verification

**Files:**
- Modify only files required by failures attributable to this feature.

- [ ] Run `cd backend && mvn test` and require exit code 0.
- [ ] Run `cd frontend && npm test -- --run` and require all tests pass.
- [ ] Run `cd frontend && npm run build` and require exit code 0.
- [ ] Run live smoke requests for at least one announcement, interaction and CLS query; record provider, count, URL and timestamps without printing sensitive configuration.
- [ ] Run `git diff --check`, inspect `git status`, and commit remaining verified changes with `test: 完善研究资料扩展验证` if needed.
