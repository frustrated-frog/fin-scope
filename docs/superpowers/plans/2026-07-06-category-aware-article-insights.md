# Category-Aware Article Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI article interpretation use category-specific Chinese analysis sections, with special handling for English originals.

**Architecture:** Add a small `InsightSection` value object and persist `analysis_sections` JSON on `InsightCard`. The backend prompt asks the LLM for a stable `analysisSections` array chosen from the article category, while the frontend renders that array before falling back to legacy fixed deep fields.

**Tech Stack:** Java/Spring/JdbcTemplate/Jackson, SQLite, React/TypeScript/Vitest.

---

### Task 1: Backend Dynamic Sections

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/insight/InsightCard.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/agent/ArticleInterpretation.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/agent/ArticleInterpretationAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/insight/InsightCardGenerator.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/insight/InsightCardGeneratorTest.java`

- [x] **Step 1: Write failing tests**

Add tests that verify:
- 市场 category produces sections including `政策/事件脉络`, `市场反应`, and `下一观察窗口`.
- 前沿技术 category can carry a `中文译文摘要` section from LLM output.

- [x] **Step 2: Run tests to verify failure**

Run: `mvn -f backend/pom.xml -pl finscope-service test -Dtest=InsightCardGeneratorTest`

Expected: FAIL because `analysisSections` does not exist yet.

- [x] **Step 3: Implement minimal backend support**

Add `InsightSection { title, content }`, parse `analysisSections` from LLM JSON, generate fallback category sections, render them into Markdown, and keep legacy fields intact for old cards.

- [x] **Step 4: Run backend tests**

Run: `mvn -f backend/pom.xml -pl finscope-service -am test -Dtest=InsightCardGeneratorTest -DfailIfNoTests=false`

Expected: PASS.

### Task 2: Persistence

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/insight/InsightCardRepository.java`

- [x] **Step 1: Write failing repository coverage if needed**

Use existing integration coverage unless a focused DAO test is necessary.

- [x] **Step 2: Implement storage**

Add `analysis_sections TEXT`, save/load JSON through `InsightCard.analysisSections`, and ensure existing databases migrate via `ensureColumn`.

- [x] **Step 3: Run backend service/web tests**

Run: `mvn -f backend/pom.xml test -DfailIfNoTests=false`

Expected: PASS.

### Task 3: Frontend Rendering

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/articles/InsightCardPreview.tsx`
- Test: `frontend/src/App.test.tsx`

- [x] **Step 1: Write failing frontend test**

Add an article fixture with `analysisSections` and verify the UI renders dynamic headings, including the market policy/event framing.

- [x] **Step 2: Run test to verify failure**

Run: `npm test -- App.test.tsx -t "article insight renders category-aware sections"`

Expected: FAIL because the frontend does not know `analysisSections`.

- [x] **Step 3: Implement rendering**

Add `analysisSections?: { title: string; content: string }[]` to the type and render it in `InsightCardPreview`; use legacy deep fields only when no dynamic sections exist.

- [x] **Step 4: Run frontend tests and build**

Run: `npm test`

Run: `npm run build`

Expected: PASS.
