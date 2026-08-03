# Deep Research Report Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the detailed evidence-driven report contract and make model-enhanced report generation succeed across OpenAI-compatible models without depending on one giant nested JSON response.

**Architecture:** Java owns a deterministic blueprint, complete narrative baseline, evidence references, and the fourteen-section Markdown contract. LLM calls may enhance only named text slots through a tolerant marker protocol; malformed or missing slots are repaired or filled locally without discarding valid model output or changing the report structure.

**Tech Stack:** Java 8, Spring Boot 2.7, Jackson, JUnit 5, Mockito, Maven, SQLite, React/TypeScript/Vite.

---

### Task 1: Lock the detailed Markdown contract with failing tests

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/StructuredResearchReportPipelineTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportGeneratorTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportQualityValidatorTest.java`

- [ ] Add assertions for `关键认识`, `执行摘要`, `研究范围与口径`, a Markdown evidence table, `核心证据链`, `最终认识与未知项`, `跟踪清单与失效条件`, and `证据附录`.
- [ ] Run `cd backend && mvn -pl finscope-service -Dtest=StructuredResearchReportPipelineTest,ResearchReportGeneratorTest,ResearchReportQualityValidatorTest test` and verify failures identify the removed rich sections and legacy-section rejection.
- [ ] Update `StructuredResearchReportAssembler` to render the fourteen-section contract with escaped table cells and stable evidence anchors.
- [ ] Update `ResearchReportQualityValidator` required sections and table/chain/monitoring checks; remove the rule that rejects execution summary and scope sections.
- [ ] Run the same targeted tests and verify they pass.
- [ ] Commit with `fix: 恢复详细研究报告结构` and push the branch.

### Task 2: Add an always-valid evidence blueprint and narrative baseline

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/DeterministicReportBlueprintBuilder.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/DeterministicReportNarrativeBuilder.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/report/DeterministicReportBlueprintBuilderTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/report/DeterministicReportNarrativeBuilderTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportGenerator.java`

- [ ] Write tests proving the baseline has 3–6 key insights and subquestions, at least two argument chains, three scenarios, 3–8 watch items, valid references, and mandatory use of available counter evidence.
- [ ] Run the new tests and verify they fail because the builders do not exist.
- [ ] Implement the two builders using thesis type/question signals, evidence stance, source tier, relevance, and sufficiency without inventing numeric baselines.
- [ ] Refactor `ResearchReportGenerator` to build dossier → blueprint baseline → narrative baseline → canonical assembler instead of maintaining a separate compact template.
- [ ] Run builder, generator, assembler, and validator tests and verify green.
- [ ] Commit with `feat: 增加证据驱动报告基线` and push.

### Task 3: Replace brittle blueprint JSON with server-owned slot enhancement

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportSectionParser.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportSectionParserTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportBlueprint.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportBlueprintAgent.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportBlueprintAgentTest.java`

- [ ] Write parser tests for exact markers, missing end markers, unknown markers, surrounding prose, and duplicate markers.
- [ ] Run parser tests and verify RED.
- [ ] Implement a bounded parser for `<<<NAME>>>...<<<END>>>` slots that ignores unknown names and limits each slot length.
- [ ] Write blueprint-agent tests showing model text can replace only known text slots while keys, references, list sizes, direction, and confidence remain server-owned.
- [ ] Add tests for empty content, JSON-shaped incompatible content, and partial markers; these must return the valid baseline plus diagnostics rather than throw away the report.
- [ ] Implement marker-based blueprint enhancement and a targeted retry containing only missing critical slots.
- [ ] Run blueprint and parser tests and verify green.
- [ ] Commit with `fix: 消除报告蓝图巨型JSON依赖` and push.

### Task 4: Make narrative generation tolerant and locally repairable

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportNarrative.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportNarrativeAgent.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/StructuredResearchReportPipelineTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportNarrativeAgentTest.java`

- [ ] Write tests for complete marker output, partial output, malformed JSON-like model output, missing array equivalents, and targeted completion of only missing narrative slots.
- [ ] Run tests and verify failures reproduce current `FIELD_COVERAGE`, `JsonMappingException`, and array/object drift at behavior level.
- [ ] Change the narrative prompt to named text slots and merge output into `DeterministicReportNarrativeBuilder` output.
- [ ] Add coverage metadata to distinguish complete model output, normalized output, and deterministic local completion.
- [ ] Keep the model prompt evidence-only and forbid new numbers, dates, URLs, and evidence identifiers.
- [ ] Run narrative, pipeline, and assembler tests and verify green.
- [ ] Commit with `fix: 支持报告正文局部生成与修复` and push.

### Task 5: Prevent whole-report fallback after one model-stage failure

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportSynthesisAgent.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportSynthesisAgentTest.java`
- Modify: `frontend/src/features/research/ResearchReportReader.tsx`
- Modify: `frontend/src/features/research/ResearchReportReader.test.tsx`

- [ ] Write service tests proving blueprint enhancement failure still attempts narrative enhancement, partial narrative output yields `MODEL_REPAIRED`, and model-complete output yields `MODEL_STRUCTURED`.
- [ ] Run the service tests and verify current code returns `EVIDENCE_STRUCTURED_FALLBACK` too early.
- [ ] Refactor synthesis into independent baseline, blueprint enhancement, narrative enhancement, assembly, claim audit, and quality stages.
- [ ] Preserve stage diagnostics without model output bodies; use fallback mode only when no model text passes the minimum coverage threshold.
- [ ] Update reader labels and tests for repaired/model-enhanced diagnostics without changing old report compatibility.
- [ ] Run backend service and frontend reader tests and verify green.
- [ ] Commit with `fix: 避免模型局部失败拖垮整篇报告` and push.

### Task 6: Correct claim auditing and evidence admission

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaimExtractor.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchClaimAuditorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchEvidenceSelector.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchEvidenceSelectorTest.java`

- [ ] Write claim tests proving numeric/date facts and explicit fact rows are audited, while nonnumeric `AI 解读` inference is not rejected solely because lexical overlap is low.
- [ ] Write selector tests proving a high provider score cannot admit a company-irrelevant result and that subject-name or controlled-alias matches remain eligible.
- [ ] Run both test classes and verify RED.
- [ ] Implement fact/inference-aware extraction and a minimum local subject-signal gate for search evidence.
- [ ] Run claim, selector, dossier, report, and benchmark tests and verify green.
- [ ] Commit with `fix: 提升报告事实审计与证据相关性` and push.

### Task 7: Verify model compatibility and update documentation

**Files:**
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/llm/OpenAiCompatibleLlmClientTest.java`
- Modify: `docs/模型服务接入与配置说明.md`
- Modify: `docs/技术方案-结构化深度研究报告.md`
- Modify: `README.md`

- [ ] Add protocol fixtures for empty final content, reasoning-only first response, text-part arrays, and marker-based long-form content.
- [ ] Run `cd backend && mvn -pl finscope-rpc test` and verify green.
- [ ] Update model documentation to describe the provider-neutral marker protocol and the actual current GLM-5 configuration without copying credentials.
- [ ] Run all backend tests with `cd backend && mvn test`.
- [ ] Run frontend tests and build with `cd frontend && npm test -- --run && npm run build`.
- [ ] Use the configured model against a frozen in-memory dossier to produce a report without writing the production database; verify a model generation mode, all fourteen sections, the evidence table, valid references, and report quality.
- [ ] Commit with `docs: 更新深度报告模型兼容说明` and push.

### Task 8: Final review and branch handoff

**Files:**
- Review all files changed on `codex/restore-deep-research-reports`.

- [ ] Inspect `git diff origin/main...HEAD` for unrelated changes, credentials, full model output, and temporary files.
- [ ] Run `git status --short`, targeted report tests, full backend tests, frontend tests, and production build one final time.
- [ ] Confirm the generated smoke report meets the design acceptance criteria and record only non-sensitive metrics.
- [ ] Push the final branch and report commit IDs, tests, model mode, character count, evidence count, and remaining limitations.
