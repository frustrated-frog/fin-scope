# Single-stock Forecast Statistical Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the single-stock forecast from a point estimate to a calibrated, locked-test-qualified and uncertainty-aware research result, with an auditable high-fidelity UI.

**Architecture:** Python owns time-series splitting, calibration, qualification metrics and bootstrap intervals. Java validates and persists the versioned report without duplicating statistics. React renders v3 evidence while remaining compatible with v2 history.

**Tech Stack:** Python 3.11+, NumPy, Pydantic, pytest; Java 8, Spring Boot 2.7, Jackson, JUnit 5; React 18, strict TypeScript, Vitest, CSS.

---

## File map

- Create `market-data-service/src/finscope_market_data/forecast/qualification.py`: split audit, locked qualification pipeline and metrics.
- Create `market-data-service/src/finscope_market_data/forecast/calibration.py`: Platt fitting, fallback and calibrated probability.
- Create `market-data-service/src/finscope_market_data/forecast/bootstrap.py`: deterministic moving-block confidence intervals.
- Modify `market-data-service/src/finscope_market_data/forecast/walk_forward.py`: expose reusable metric primitives without changing matured-label behavior.
- Modify `market-data-service/src/finscope_market_data/forecast/schemas.py`: v3 qualification, calibration, reliability and interval contract.
- Modify `market-data-service/src/finscope_market_data/forecast/service.py`: fixed qualification pipeline, production refit and v3 assembly.
- Add focused Python tests beside existing forecast tests.
- Modify `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`: v3 DTOs.
- Modify `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonSingleStockForecastClient.java`: fail-fast v3 contract validation.
- Modify the RPC/domain tests for valid and invalid v3 payloads.
- Modify `frontend/src/features/strategy/quantTypes.ts`: optional v3 types for v2 compatibility.
- Modify `frontend/src/features/strategy/SingleStockForecastPanel.tsx`: qualification dashboard and calibration chart.
- Modify `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`: v3 rendering and fallback states.
- Modify `frontend/src/styles.css`: restrained research-instrument layout and responsive states.

### Task 1: Time split and Platt calibration

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/calibration.py`
- Create: `market-data-service/src/finscope_market_data/forecast/qualification.py`
- Test: `market-data-service/tests/test_forecast_calibration.py`
- Test: `market-data-service/tests/test_forecast_qualification.py`

- [ ] **Step 1: Write failing split isolation tests**

Add tests that build dated `ForecastSample` objects and assert the 60/20/20 boundaries are ordered, each prediction only trains on samples with `exit_date < signal_date`, and locked labels are absent from both fit and calibration inputs.

- [ ] **Step 2: Verify the tests fail for missing APIs**

Run: `cd market-data-service && uv run pytest -q tests/test_forecast_qualification.py`  
Expected: collection failure because `qualification` and its public types do not exist.

- [ ] **Step 3: Implement split audit and qualification observations**

Create immutable dataclasses `SplitSlice`, `SplitAudit`, `QualificationObservation`, and `QualificationInput`. Use chronological integer boundaries, explicit label-maturity filtering and every-20th-signal independent anchors. Raise `ValueError` for unordered or overlapping boundaries rather than silently reshaping input.

- [ ] **Step 4: Write failing Platt calibration tests**

Cover miscalibrated synthetic probabilities, deterministic output, too few observations, single-class input, and finite probability bounds.

- [ ] **Step 5: Verify calibration tests fail**

Run: `cd market-data-service && uv run pytest -q tests/test_forecast_calibration.py`  
Expected: collection failure for missing `PlattCalibrator`.

- [ ] **Step 6: Implement the minimal calibrator**

Implement `PlattCalibrator.fit(raw_probabilities, labels)` with clipped logits, two parameters, L2 regularization and deterministic gradient descent. Return a typed `CalibrationResult` with `FITTED` or `NOT_FITTED`, parameters, counts and a Chinese fallback reason. Reject fitted calibration when calibration Log Loss degrades beyond the fixed tolerance.

- [ ] **Step 7: Run focused and existing forecast tests**

Run: `cd market-data-service && uv run pytest -q tests/test_forecast_calibration.py tests/test_forecast_qualification.py tests/test_forecast_service.py`  
Expected: all pass.

- [ ] **Step 8: Commit and push**

Commit: `feat: 增加单股预测锁定切分与概率校准`

### Task 2: Qualification metrics, bootstrap and v3 Python report

**Files:**
- Create: `market-data-service/src/finscope_market_data/forecast/bootstrap.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/qualification.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/schemas.py`
- Modify: `market-data-service/src/finscope_market_data/forecast/service.py`
- Test: `market-data-service/tests/test_forecast_bootstrap.py`
- Test: `market-data-service/tests/test_forecast_qualification.py`
- Test: `market-data-service/tests/test_forecast_service.py`
- Test: `market-data-service/tests/test_api.py`

- [ ] **Step 1: Write failing metric and reliability-bin tests**

Assert Accuracy, Brier, baseline Brier, Brier Skill, Log Loss and fixed five-bin ECE against hand-calculated data. Assert empty bins have count zero and null observed rate.

- [ ] **Step 2: Verify red state**

Run: `cd market-data-service && uv run pytest -q tests/test_forecast_qualification.py`  
Expected: failure for missing qualification metrics.

- [ ] **Step 3: Implement metrics and locked qualification**

Fit the base model on development data only, obtain raw probabilities for calibration and locked areas, fit calibration only on calibration anchors, evaluate raw/calibrated/baseline metrics only on locked anchors, and return explicit insufficiency reasons when minimum counts or class balance fail.

- [ ] **Step 4: Write failing deterministic bootstrap tests**

Assert same seed yields byte-equivalent intervals, different seed can differ, paired series preserve alignment, interval ordering is valid, and invalid samples produce `UNAVAILABLE` instead of zero intervals.

- [ ] **Step 5: Verify bootstrap tests fail**

Run: `cd market-data-service && uv run pytest -q tests/test_forecast_bootstrap.py`  
Expected: collection failure for missing bootstrap API.

- [ ] **Step 6: Implement moving-block bootstrap**

Use `numpy.random.default_rng(seed)`, circular block starts, exact requested output length and percentile intervals. Provide separate helpers for metric observations, paired daily strategy/benchmark returns and calibration-refit current probability.

- [ ] **Step 7: Write failing v3 service contract tests**

Assert `single-stock-research-v3`, `logistic-platt-qualified-v3`, stable `trialId`, raw/calibrated probability, split audit totals, calibration state, locked metrics, reliability bins, confidence intervals and finite JSON. Retain the structured insufficient-data behavior.

- [ ] **Step 8: Implement v3 schemas and service assembly**

Add Pydantic models with camelCase aliases and nullable unavailable values. Derive trial ID and seeds from SHA-256 of model/config/data identity. Fit the production model on all mature labels only after qualification, apply the frozen calibration mapping, retain factor explanations on raw logit, and include explicit warnings about interval scope.

- [ ] **Step 9: Integrate intervals into conclusion policy**

Centralize fixed qualification thresholds. Make locked calibrated Brier Skill and its interval primary statistical evidence, then combine economic excess interval, trade count and existing parameter stability without tuning thresholds from the locked result.

- [ ] **Step 10: Run Python suite and a timing probe**

Run: `cd market-data-service && uv run pytest -q`  
Expected: all tests pass.  
Run a fixed 1,600-bar local probe and record total forecast duration; expected added qualification work under two seconds on the current machine.

- [ ] **Step 11: Commit and push**

Commit: `feat: 输出单股预测资格检验与置信区间`

### Task 3: Java v3 contract and immutable history compatibility

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/quant/forecast/SingleStockForecast.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonSingleStockForecastClient.java`
- Create: `backend/finscope-domain/src/test/java/com/finscope/domain/quant/forecast/SingleStockForecastContractTest.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonSingleStockForecastClientTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/quant/forecast/SingleStockForecastServiceTest.java`

- [ ] **Step 1: Write failing valid-v3 deserialization test**

Create a complete fixture with nested trial, split audit, calibration, locked metrics, bins and intervals. Assert every nested property is retained and old v2 JSON without new fields still deserializes.

- [ ] **Step 2: Verify red state**

Run targeted domain test with Maven and JDK 8. Expected: compilation failure for missing v3 accessors.

- [ ] **Step 3: Add Lombok DTOs matching the Python contract**

Keep new fields optional on the top-level object for v2 history. Use nested `@Data` classes consistent with the existing domain style; do not implement statistics in Java.

- [ ] **Step 4: Write failing RPC rejection tests**

Cover out-of-range probability, reversed interval, invalid trial ID, reliability-bin count mismatch, unknown qualification/calibration status and non-finite values.

- [ ] **Step 5: Verify expected failures, then implement validation**

Validate v3 only when the schema version is v3. Preserve v2 response behavior and existing `ProviderContractException` mapping.

- [ ] **Step 6: Verify relevant Java modules**

Run targeted domain, RPC and service tests with JDK 8. Expected: all targeted tests pass.

- [ ] **Step 7: Commit and push**

Commit: `feat: 接入单股预测资格检验契约`

### Task 4: High-fidelity qualification UI

**Files:**
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.tsx`
- Modify: `frontend/src/features/strategy/SingleStockForecastPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing v3 rendering tests**

Extend the report fixture and assert visible calibrated probability, raw probability, 95% interval, qualification state, locked date range, purge count, Brier Skill, Log Loss, ECE, reliability-bin labels and interval limitations.

- [ ] **Step 2: Write failing fallback tests**

Assert `NOT_FITTED`, `UNAVAILABLE`, insufficient qualification and v2 history produce accurate explanatory copy without empty charts or misleading “calibrated” labels.

- [ ] **Step 3: Verify red state**

Run: `cd frontend && npm test -- src/features/strategy/SingleStockForecastPanel.test.tsx`  
Expected: assertions fail because v3 evidence is not rendered.

- [ ] **Step 4: Add strict TypeScript v3 types**

Model trial identity, slice audit, metric set, reliability bin, calibration and intervals. Keep `rawProbability`, `probabilityInterval` and `qualification` optional for v2 compatibility.

- [ ] **Step 5: Build the calibration instrument**

Add a focused `CalibrationChart` SVG with a square viewBox, ideal diagonal, fixed probability grid, raw/calibrated observed points, keyboard-neutral accessible labeling, and an accompanying exact-value table. Never infer missing observed rates.

- [ ] **Step 6: Build the qualification dashboard**

Place the calibrated probability and interval in the hero, raw probability as secondary evidence, and add a dedicated section containing qualification stamp, locked metrics, uncertainty intervals, split audit and methodology disclosures. Keep the existing performance, factor and history sections intact.

- [ ] **Step 7: Apply the visual system**

Use existing forecast tokens plus deep indigo `#34446f`, instrument cyan `#2ba8bd`, graphite `#26313a`, quiet blue-gray `#eef3f6`, amber `#c98a35` and oxide `#b76158`. The signature is the calibration coordinate-paper chart; typography remains the established Iowan/Palatino research display, system body, and monospace data face. Add responsive single-column behavior, focus states and reduced-motion handling.

- [ ] **Step 8: Verify focused test, full frontend tests and build**

Run focused Vitest, then `npm test`, then `npm run build`. Expected: all tests pass and TypeScript build succeeds.

- [ ] **Step 9: Run visual QA**

Start existing services only if needed, open the strategy page, generate or load a v3 fixture/report, inspect desktop and mobile widths, and correct overflow, hierarchy, contrast or empty-state problems.

- [ ] **Step 10: Commit and push**

Commit: `feat: 展示单股预测校准与可信度证据`

### Task 5: Full verification and handoff

**Files:**
- Modify only files required by failures introduced by this feature.

- [ ] **Step 1: Review the implementation against all 15 design sections**

Check scope, boundaries, purge semantics, four-stage evidence chain, calibration fallback, locked metrics, bootstrap, classification, trial identity, API compatibility, UI, failure behavior, performance and all test gates.

- [ ] **Step 2: Run full Python verification**

Run: `cd market-data-service && uv run pytest -q`.

- [ ] **Step 3: Run Java verification with JDK 8**

Run targeted tests first and then `cd backend && mvn test`. Report any pre-existing unrelated failure separately with the exact failing test.

- [ ] **Step 4: Run frontend verification**

Run: `cd frontend && npm test && npm run build`.

- [ ] **Step 5: Inspect git scope and generated files**

Run `git diff --check`, inspect `git status`, and ensure no database, cache, build output, credential or unrelated user file is included.

- [ ] **Step 6: Commit any final verified correction and push**

Use the narrowest Conventional Commit with a Chinese subject.

- [ ] **Step 7: Complete branch handoff**

Summarize statistical behavior, UI changes, test evidence, performance result, known unrelated failures and branch/commit state without claiming guaranteed investment returns.
