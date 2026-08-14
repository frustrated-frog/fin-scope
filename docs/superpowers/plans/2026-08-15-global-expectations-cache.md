# Global Expectations Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为精选 Polymarket 观察命题建立可控的内存快照、自动刷新和价格变化详情。

**Architecture:** RPC 仍只访问 Polymarket Gamma 公共接口。Service 层新增目录与快照缓存，定时刷新后向 REST 层提供当前观察视图；React 页面消费变化窗口和详情数据，不将高频价格写入 SQLite。

**Tech Stack:** Java 21、Spring Boot Scheduler、JUnit 5、React、TypeScript、Vitest。

---

### Task 1: 观察命题目录与内存快照

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsCatalog.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationSnapshotCache.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationSnapshotCacheTest.java`

- [ ] **Step 1: Write failing snapshot test**

```java
assertEquals(4.0D, cache.changeSince("oil", now, 31, Duration.ofMinutes(5)));
```

- [ ] **Step 2: Run test and confirm missing cache failure**

Run: `mvn -pl finscope-service -am test -Dtest=GlobalExpectationSnapshotCacheTest`

- [ ] **Step 3: Implement catalog and bounded snapshot cache**

```java
cache.record("oil", now, 27);
cache.record("oil", now.plusMinutes(5), 31);
```

- [ ] **Step 4: Run focused test**

Run: `mvn -pl finscope-service -am test -Dtest=GlobalExpectationSnapshotCacheTest`

### Task 2: 刷新编排和 REST 契约

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsService.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationItem.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/GlobalExpectationsController.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationsServiceTest.java`

- [ ] **Step 1: Write failing service test for retained cache and changes**

```java
assertEquals(4.0D, refreshed.get(0).getChange5m());
```

- [ ] **Step 2: Implement scheduled/manual refresh and retained last success**

```java
@Scheduled(fixedDelayString = "${finscope.global-expectations.refresh-interval-ms:60000}")
public void refreshScheduled() { refresh(); }
```

- [ ] **Step 3: Add `POST /api/global-expectations/refresh` and run focused test**

Run: `mvn -pl finscope-web -am test -Dtest=GlobalExpectationsServiceTest`

### Task 3: 变化窗口与详情界面

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.tsx`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Write failing UI test**

```tsx
expect(await screen.findByText('5m')).toBeInTheDocument();
expect(screen.getByRole('button', { name: /查看变化详情/ })).toBeInTheDocument();
```

- [ ] **Step 2: Implement multi-window movement presentation and accessible detail dialog**

```tsx
<button type="button" onClick={() => setSelected(item)}>查看变化详情</button>
```

- [ ] **Step 3: Run frontend tests and build**

Run: `npm test -- --run src/features/global-expectations/GlobalExpectationsView.test.tsx && npm run build`

### Task 4: 回归验证与提交

**Files:**
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] **Step 1: Add explicit refresh interval configuration**

```yaml
global-expectations:
  refresh-interval-ms: 60000
```

- [ ] **Step 2: Run backend focused cache and service tests with JDK 21**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -pl finscope-web -am test -Dtest=GlobalExpectationSnapshotCacheTest,GlobalExpectationsServiceTest -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 3: Run complete frontend test suite and production build, then commit and push**

Run: `npm test -- --run && npm run build`
