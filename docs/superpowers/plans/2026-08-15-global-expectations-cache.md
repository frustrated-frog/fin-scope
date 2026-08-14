# Global Expectations Official Changes and Redis Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Polymarket 官方变化字段与 CLOB 历史替换 JVM 内存采样，并使用项目现有 Redis 保存可跨重启恢复的观察快照。

**Architecture:** RPC 层分别封装 Gamma 市场发现和 CLOB 批量历史读取；DAO 层以 `StringRedisTemplate` 保存历史及页面 JSON；Service 层筛选最多 20 个市场、合并官方变化、计算 5 分钟变化并执行分级降级。REST 契约保持不变，前端只修正数据来源文案。

**Tech Stack:** Java 21、Spring Boot 2.7、Spring Data Redis、Jackson、JUnit 5/Mockito、React、TypeScript、Vitest。

---

### Task 1: Gamma 官方变化与 CLOB 历史协议

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicMarket.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicClient.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPricePoint.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/polymarket/PolymarketPublicClientTest.java`

- [ ] Write failing parser tests for `clobTokenIds`, `oneHourPriceChange`, `oneDayPriceChange`, and batch history.
- [ ] Run the focused RPC test with JDK 21 and confirm failures are caused by missing fields/history method.
- [ ] Implement Gamma field parsing plus `POST /batch-prices-history` with `interval=1d`, `fidelity=1`, and at most 20 token IDs.
- [ ] Run the focused RPC tests and commit `feat: 接入官方概率变化与历史价格`.

### Task 2: Redis global-expectations repository

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationHistorySnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationsViewSnapshot.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/GlobalExpectationsCacheRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/cache/RedisGlobalExpectationsCacheRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/cache/RedisGlobalExpectationsCacheRepositoryTest.java`

- [ ] Write failing Redis repository tests covering JSON round-trip, the two key prefixes, 26-hour TTL, and malformed/unavailable Redis reads.
- [ ] Run the focused DAO test and confirm the repository is missing.
- [ ] Implement field-injected Redis repository methods for page and token history snapshots; catch Redis/serialization failures and return `Optional.empty()`.
- [ ] Run the focused DAO tests and commit `feat: 增加全球预期Redis快照`.

### Task 3: Service merge, five-minute calculation, and degradation

**Files:**
- Delete: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationSnapshotCache.java`
- Delete: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationSnapshotCacheTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationsServiceTest.java`

- [ ] Write failing service tests proving 1h/24h use Gamma fields, 5m uses the CLOB baseline immediately, CLOB failure uses Redis history as `PARTIAL`, and Gamma failure uses Redis view as `STALE`.
- [ ] Run the focused service tests and confirm the old in-memory implementation fails the new contract.
- [ ] Refactor Service to select top 20 before history fetch, merge official data, persist successful Redis snapshots, and remove all mutable in-process history/view state.
- [ ] Run focused service tests and commit `fix: 改用官方变化与Redis缓存`.

### Task 4: UI wording and regression verification

**Files:**
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.tsx`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.test.tsx`

- [ ] Write a failing UI assertion that the detail chart is labelled `官方价格轨迹` and an unavailable 5m value reads `暂无历史`.
- [ ] Update only the affected copy while retaining the existing card, modal, filters, and refresh behavior.
- [ ] Run the focused UI test and commit `fix: 校正全球预期数据来源文案`.
- [ ] Run JDK 21 backend focused tests, the complete frontend test suite, and the production build.
- [ ] Check braces, field injection, module placement, git diff, and secrets; then push every new commit to the current branch.
