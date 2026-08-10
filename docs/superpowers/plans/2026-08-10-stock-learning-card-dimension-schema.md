# Stock Learning Card Dimension Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stock learning card's shared why/counterargument/unknowns template with six dimension-specific section schemas while preserving the fixed six-dimension research chain.

**Architecture:** Add a server-owned schema registry that defines required and optional sections per dimension. Persist normalized section rows under each claim, validate LLM JSON against the registry, and synthesize the counter-case after the first five claims so it can challenge their combined logic. Render the resulting generic section list in React.

**Tech Stack:** Java 8, Spring Boot 2.7, Jackson, JdbcTemplate, SQLite, JUnit 5, Mockito, React, TypeScript, Vitest.

---

### Task 1: Define and persist dimension-specific claims

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardSection.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/learningcard/StockLearningCardClaim.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/learningcard/StockLearningCardRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/learningcard/StockLearningCardRepositoryTest.java`

- [ ] Write a repository test that saves and reloads `headline`, dimension rating, ordered sections, evidence references and verification status.
- [ ] Run `cd backend && mvn -pl finscope-dao -am -Dtest=StockLearningCardRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test` and confirm the new assertions fail because the contract is absent.
- [ ] Add the domain fields, additive SQLite migration and transactional section persistence needed by the test.
- [ ] Re-run the targeted DAO test and confirm it passes.
- [ ] Commit with `feat: 增加学习卡维度栏目持久化` and push the current branch.

### Task 2: Add the schema registry and strict synthesis contract

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningDimensionSchema.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningFramework.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardSynthesisAgent.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningFrameworkTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardSynthesisAgentTest.java`

- [ ] Write tests for the six exact required/optional section sets and for rejecting unknown, duplicate, missing, reordered or invalid-evidence sections.
- [ ] Run the two targeted service tests and confirm they fail against the old five-field output.
- [ ] Implement the immutable schema registry, dimension-specific prompt payload, strict JSON parser and conservative insufficient-evidence sections.
- [ ] Re-run the targeted tests and confirm they pass.
- [ ] Commit with `feat: 增加六维专属学习卡契约` and push the current branch.

### Task 3: Sequence counter-case synthesis after the first five dimensions

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/learningcard/StockLearningCardAgentExecutor.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/learningcard/StockLearningCardAgentExecutorTest.java`

- [ ] Write an executor test proving `COUNTER_CASE` receives the first five claims while the other dimensions do not.
- [ ] Run the targeted executor test and confirm it fails with the old synthesis signature/order.
- [ ] Split primary-dimension and counter-case execution while preserving evidence collection, partial-failure behavior and six total results.
- [ ] Re-run the executor tests and confirm they pass.
- [ ] Commit with `feat: 串联学习卡反方验证链路` and push the current branch.

### Task 4: Render specialized sections in the web UI

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/strategy/StockLearningCardDetail.tsx`
- Modify: `frontend/src/features/strategy/StockLearningCardPanel.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] Update the component test fixture to use `headline`, rating and section arrays; assert specialized titles and verification labels are visible and the old three labels are absent.
- [ ] Run `cd frontend && npm test -- --run src/features/strategy/StockLearningCardPanel.test.tsx` and confirm it fails against the old types/rendering.
- [ ] Update TypeScript contracts and render dimension ratings, ordered sections, per-section evidence links and verification status.
- [ ] Re-run the targeted frontend test and confirm it passes.
- [ ] Commit with `feat: 展示股票学习卡专属维度栏目` and push the current branch.

### Task 5: Verify the complete change

**Files:**
- Modify: `README.md`
- Modify: `docs/模型服务接入与配置说明.md`

- [ ] Update product and model documentation to describe dimension-specific schemas and second-stage counter-case synthesis without copying any API keys.
- [ ] Run `cd backend && mvn test` and confirm zero failures.
- [ ] Run `cd frontend && npm test -- --run` and confirm zero failures.
- [ ] Run `cd frontend && npm run build` and confirm the strict TypeScript production build succeeds.
- [ ] Inspect `git diff --check`, `git status --short` and the final diff for unrelated or sensitive changes.
- [ ] Commit with `docs: 补充股票学习卡维度契约说明` and push the current branch.
