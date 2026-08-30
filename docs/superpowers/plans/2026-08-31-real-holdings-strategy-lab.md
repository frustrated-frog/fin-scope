# Real Holdings Strategy Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a manually maintained A-share transaction ledger, reproducible position accounting, holding-aware shadow decisions, and a polished validation workspace shared by single-stock forecasting and stock discovery.

**Architecture:** Immutable ledger events in SQLite are the source of truth; Java owns validation, transactions, persistence, quote/forecast orchestration, and API mapping. Python owns the independent holding-policy calculation and never feeds personal cost or P&L into the forecasting model. The existing `strategy_holding` table remains the compatibility projection for portfolio metadata.

**Tech Stack:** Java 21, Spring Boot 2.7, Spring JDBC, SQLite, Python 3.12, FastAPI/Pydantic, React 18, TypeScript, Vitest.

---

### Task 1: Immutable ledger schema and accounting domain

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/holding/StockTransactionType.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/holding/StockTransaction.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/holding/StockPosition.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/holding/StockAccountSnapshot.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/strategy/StockTransactionRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/strategy/StockTransactionRepositoryTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/config/DatabaseInitializerStrategySchemaTest.java`

- [x] **Step 1: Write failing schema and repository tests**

Assert that `stock_transaction` exists, `client_request_id` is unique, events are returned in `trade_date,id` order, and `findByClientRequestId` returns the original event.

- [x] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=DatabaseInitializerStrategySchemaTest,StockTransactionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because ledger types, schema, and repository do not exist.

- [x] **Step 3: Implement the schema and repository**

Create `stock_transaction` with event data, an idempotency unique constraint, a reversal foreign key, stable indexes, and `CHECK` constraints for non-negative monetary fields. Use Lombok `@Data` for domain carriers and `@Resource` field injection in the repository.

- [x] **Step 4: Run DAO tests**

Run the command from Step 2. Expected: PASS.

- [x] **Step 5: Commit and push**

```bash
git add backend/finscope-domain backend/finscope-dao
git commit -m "feat: 增加真实股票交易账本"
git push
```

### Task 2: Reproducible stock accounting and APIs

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/strategy/holding/StockPositionAccountingService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/strategy/holding/StockTransactionService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/strategy/holding/StockAccountService.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/strategy/CreateStockTransactionRequest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/strategy/ReverseStockTransactionRequest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/response/strategy/StockAccountResponse.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockHoldingController.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/strategy/StrategyHoldingRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/strategy/StrategyHoldingService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/strategy/holding/StockPositionAccountingServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/strategy/holding/StockTransactionServiceTest.java`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockHoldingControllerTest.java`

- [x] **Step 1: Write failing accounting tests**

Cover opening balance, repeated buys, partial sell, full sell, dividend, bonus shares, reversal, deposit/withdrawal, oversell rejection, 100-share buy lots, and duplicate idempotency requests.

- [x] **Step 2: Verify failures**

Run: `cd backend && mvn -pl finscope-service,finscope-web -am -Dtest=StockPositionAccountingServiceTest,StockTransactionServiceTest,StockHoldingControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the services and endpoints do not exist.

- [x] **Step 3: Implement deterministic event replay**

Use `BigDecimal` for quantity and money. Exclude reversed events first, then replay active events. For BUY use `(oldCost + quantity*price + fees) / newQuantity`; for SELL realize `quantity*(price-oldAverageCost)-fees`; for BONUS_SHARE retain total cost. Reject invalid event-field combinations before persistence.

- [x] **Step 4: Implement API and compatibility projection**

Expose list/create/reverse/account endpoints. When a stock event changes a position, update only the matching `strategy_holding.quantity` and `average_cost` projection; portfolio role and target weight remain independently editable.

- [x] **Step 5: Run targeted and module tests**

Run: `cd backend && mvn -pl finscope-web -am test`

Expected: PASS.

- [x] **Step 6: Commit and push**

```bash
git add backend
git commit -m "feat: 实现真实持仓核算与接口"
git push
```

### Task 3: Python holding policy

**Files:**
- Create: `market-data-service/src/finscope_market_data/holding_policy.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Test: `market-data-service/tests/test_holding_policy.py`

- [x] **Step 1: Write failing policy tests**

Cover stale data -> `ABSTAIN`, unhealthy model -> `ABSTAIN`, concentration breach -> `REDUCE_CONCENTRATION`, strong net edge plus an affordable lot -> `ALLOW_ADD`, material evidence deterioration -> `EXIT_TRIGGERED`, and no affordable lot -> `HOLD`/`ABSTAIN` with an explicit blocker.

- [x] **Step 2: Verify failure**

Run: `cd market-data-service && python -m pytest tests/test_holding_policy.py -q`

Expected: FAIL because the policy module and route do not exist.

- [x] **Step 3: Implement policy request/response models and pure evaluator**

The request accepts market price, quantity, cash, total equity, probability and return quantiles, model status, quote age, costs, limits, and forecast identifiers. Cost basis and unrealized return may be echoed for explanation but must not contribute to the decision score. Return an action, executable lot quantity, risk amounts, evidence list, blockers, and policy version.

- [x] **Step 4: Expose `/v1/quant/holding-strategies/evaluate`**

Map validation failures to HTTP 422 and keep provider/runtime failures isolated from daily-bar and discovery routes.

- [x] **Step 5: Run Python tests**

Run: `cd market-data-service && python -m pytest tests/test_holding_policy.py tests/test_api.py -q`

Expected: PASS.

- [x] **Step 6: Commit and push**

```bash
git add market-data-service
git commit -m "feat: 增加真实持仓影子策略引擎"
git push
```

### Task 4: Frozen decisions and prospective validation

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/strategy/holding/HoldingStrategyDecision.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/strategy/HoldingStrategyDecisionRepository.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quant/PythonHoldingStrategyClient.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/strategy/holding/HoldingStrategyDecisionService.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/quant/SingleStockForecastRunRepository.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockHoldingController.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/strategy/holding/HoldingStrategyDecisionServiceTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/quant/PythonHoldingStrategyClientTest.java`

- [x] **Step 1: Write failing orchestration tests**

Assert per-stock failure isolation, quote/forecast absence creates a transparent abstention, same stock/date/policy version is idempotent, and personal cost is never placed in the forecast request.

- [x] **Step 2: Implement persistence and latest-forecast lookup**

Add `holding_strategy_decision` with a unique `(instrument_code,decision_date,policy_version)` key and frozen JSON payloads. Add a repository method for the latest successful forecast by instrument and horizon.

- [x] **Step 3: Implement RPC adapter and service**

Use the configured market-data base URL and existing HTTP conventions. Refresh each held stock independently, freeze successful output, and return existing same-day decisions on retries.

- [x] **Step 4: Run backend tests**

Run: `cd backend && mvn -pl finscope-web -am test`

Expected: PASS.

- [x] **Step 5: Commit and push**

```bash
git add backend
git commit -m "feat: 冻结持仓策略建议与验证证据"
git push
```

### Task 5: Real holdings workspace UI

**Files:**
- Create: `frontend/src/features/strategy/RealHoldingsLab.tsx`
- Create: `frontend/src/features/strategy/RealHoldingsLab.css`
- Create: `frontend/src/features/strategy/RealHoldingsLab.test.tsx`
- Modify: `frontend/src/features/strategy/LongTermStrategyView.tsx`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`

- [x] **Step 1: Write failing UI tests**

Assert account summary, transaction form field switching, position diagnostics, decision evidence, same-stock hold benchmark wording, explicit stale/insufficient states, and a visible held-stock badge in forecast/discovery contexts.

- [x] **Step 2: Implement the workspace**

Add five local tabs: 实盘总览、交易流水、持仓诊断、影子策略、验证账本. Keep monetary and risk figures at least 13px, use responsive grids/tables, and show source date/quality beside every valuation.

- [x] **Step 3: Integrate existing pages without changing ranking or prediction inputs**

Single-stock forecasting continues to freeze its existing real-position snapshot. Stock discovery marks held candidates after ranking; it does not add holding status to the rank score.

- [x] **Step 4: Run tests and build**

Run: `cd frontend && npm test -- --run RealHoldingsLab.test.tsx LongTermStrategyView.test.tsx SingleStockForecastPanel.test.tsx StockDiscoveryPanel.test.tsx && npm run build`

Expected: tests PASS and Vite build succeeds.

- [x] **Step 5: Commit and push**

```bash
git add frontend
git commit -m "feat: 增加真实持仓策略实验室界面"
git push
```

### Task 6: Documentation and full verification

**Files:**
- Modify: `README.md`
- Create: `docs/真实持仓策略实验室使用说明.md`
- Modify: `docs/superpowers/plans/2026-08-31-real-holdings-strategy-lab.md`

- [x] **Step 1: Document user workflow and boundaries**

Document opening-position migration, transaction entry, reversal, raw-price valuation, QFQ forecast separation, shadow-decision meanings, same-stock hold benchmark, sample-size limitations, and recovery from Python/quote failures.

- [x] **Step 2: Run complete verification**

```bash
cd backend && mvn test
cd ../market-data-service && python -m pytest -q
cd ../frontend && npm test -- --run && npm run build
```

Expected: all tests pass and production build succeeds.

- [x] **Step 3: Run repository-rule audit**

Search changed Java files for constructor-injected Spring beans and one-line `if`/`for`; inspect new types for correct module placement; verify no API keys or local data were staged.

- [x] **Step 4: Commit and push**

```bash
git add README.md docs
git commit -m "docs: 补充真实持仓策略实验室使用说明"
git push
```

## Verification record

2026-08-31 在 JDK 21、本地 Python 虚拟环境和 Node/Vite 环境完成最终验收：

- Java：`mvn -q test`，1671 tests，0 failures，0 errors，2 skipped；
- Python：`.venv/bin/python -m pytest -q`，240 passed，1 个 Starlette 依赖弃用提示；
- Frontend：`npm test -- --run`，87 个测试文件、456 个测试通过；
- Frontend build：`npm run build` 成功，保留项目已有的大 chunk 提示；
- `git diff --check` 通过；新增 Spring Bean 使用字段注入，新增/修改 Java 条件与循环均保留完整大括号，未改动运行时 API Key 或本地数据文件。
