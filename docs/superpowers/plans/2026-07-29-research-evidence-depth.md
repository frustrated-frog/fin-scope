# Research Evidence Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 FinScope 读取原始金融材料、审计 Claim 引用支持度、用 Benchmark 量化研究质量，并提供官方来源优先和 QUICK/DEEP 有界研究模式。

**Architecture:** 保留现有 Java/SQLite Research Runtime 作为唯一控制面。Tavily 只发现 URL，RPC 读取 HTML/PDF，Service 分块召回后写入运行级证据；报告生成后执行确定性 Claim 审计和最多一次修复。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite、Jsoup、PDFBox、JUnit 5、Mockito、React、TypeScript、Vite。

---

### Task 1: Full-text evidence acquisition

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchSourceDocument.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/ResearchSourceReader.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/DefaultResearchSourceReader.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/evidence/ResearchEvidenceChunker.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/evidence/ResearchEvidenceRanker.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/evidence/ResearchEvidenceAcquisitionService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/PublicNewsSearchTool.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchSearchEvidence.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/research/ResearchSearchEvidenceRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: corresponding `src/test/java` classes in rpc, service, and dao modules

- [ ] Write tests proving HTML/PDF text is read, private URLs are rejected, relevant chunks outrank boilerplate, and failures retain a marked search-snippet fallback.
- [ ] Run targeted tests and confirm they fail because the reader, chunker, metadata fields, and orchestration do not exist.
- [ ] Implement the minimal reader, chunker, ranker, schema migration, repository mapping, and `PublicNewsSearchTool` integration.
- [ ] Run targeted tests and the affected modules until all pass.
- [ ] Commit with `feat: deepen research evidence acquisition` and push both configured remotes.

### Task 2: Claim-level citation audit

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaim.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaimAudit.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaimExtractor.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaimAuditor.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportRepairAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportGenerator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportQualityValidator.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchClaimAuditorTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportRepairAgentTest.java`

- [ ] Write failing tests for supported numbers, missing dates, partial support, invalid citations, conflicting figures, one-call repair, and citation-whitelist preservation.
- [ ] Run the tests and verify the expected audit/repair behavior is absent.
- [ ] Implement extraction, deterministic audit, bounded repair, re-audit, and quality-gate integration.
- [ ] Run report tests and service module tests until green.
- [ ] Commit with `feat: audit research claims against citations` and push.

### Task 3: Financial research benchmark

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/evaluation/ResearchGroundingMetrics.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/evaluation/FinancialResearchBenchmark.java`
- Create: `backend/finscope-service/src/test/resources/research-benchmark/grounding-cases.json`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/evaluation/ResearchEvaluationScorer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/evaluation/FinancialResearchBenchmarkTest.java`

- [ ] Write failing golden-case tests for citation coverage, support rate, key-fact coverage, primary-source ratio, counter coverage, and deterministic serialization.
- [ ] Run tests and confirm metrics are not yet available.
- [ ] Implement the offline runner and add grounding metrics to the existing evaluation score without removing reliability metrics.
- [ ] Run evaluation tests and inspect the frozen output.
- [ ] Commit with `feat: add financial research grounding benchmark` and push.

### Task 4: Official financial source lane

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/source/OfficialFinancialSourceRegistry.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/source/FinancialSourceQueryPolicy.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/PublicNewsSearchTool.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchEvidenceSelector.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/source/OfficialFinancialSourceRegistryTest.java`

- [ ] Write failing tests that PRIMARY searches use official query lanes and official domains receive T1 consistently.
- [ ] Run tests and confirm current generic query and scattered tier inference fail them.
- [ ] Implement the registry and query policy, then project their result into search evidence and dossiers.
- [ ] Run source, search, selector, and report tests.
- [ ] Commit with `feat: prioritize official financial sources` and push.

### Task 5: QUICK/DEEP bounded orchestration

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchMode.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchOrchestrator.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/BoundedResearchOrchestrator.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/research/ResearchRun.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/ResearchRuntimePolicy.java`
- Modify: REST request/response DTOs and frontend research-run controls
- Test: mode, budget, concurrency, branch-failure isolation, API, and frontend interaction tests

- [ ] Write failing tests for default DEEP compatibility, QUICK/DEEP budgets, three complementary branches, concurrency limits, and sequential SQLite commits.
- [ ] Run backend and frontend tests and confirm mode-aware behavior is absent.
- [ ] Implement the mode model, schema migration, policy projection, orchestrator seam, bounded read-only execution, and UI selector.
- [ ] Run all affected tests and production build.
- [ ] Commit with `feat: add bounded research modes` and push.

### Task 6: Full verification and end-to-end acceptance

**Files:**
- Modify only files required by failures found during verification.

- [ ] Run `cd backend && mvn test` and require all seven modules to succeed.
- [ ] Run `cd frontend && npm test -- --run` and require zero failed files/tests.
- [ ] Run `cd frontend && npm run build` and require TypeScript and Vite success.
- [ ] Start the final backend, create a new DEEP research run, and inspect database evidence metadata, Claim audit, report citations, run convergence, and article-table isolation.
- [ ] Run `git status --short`, compare local/remote refs, and push any final verified fix commit.

