# Market Pulse Auto Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在每个 A 股交易日收盘后 30 分钟自动生成 Market Pulse 快照，并在应用错过定时点时自动补跑最近一个尚未生成快照的有效交易日。

**Architecture:** 调度入口放在 `finscope-service` 的 `marketpulse` 业务包中，只负责时间触发、日志和异常边界；刷新、交易日识别、幂等检查与并发防重仍由 `MarketPulseService` 统一编排。15:30 主任务只接受当天已经成为最新有效交易日的行情，小时级恢复任务只在目标交易日缺少冻结快照时补跑；人工补刷新与两个自动入口共享同一把进程内执行锁。

**Tech Stack:** Java 21、Spring Boot `@Scheduled`、JUnit 5、Mockito、React、TypeScript、Vitest

---

### Task 1: 固化 Market Pulse 自动刷新用例

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseServiceTest.java`

- [ ] **Step 1: 写失败测试**

新增测试，验证人工刷新与自动刷新共享防重、15:30 主任务不会把前一交易日误当成当天快照、恢复任务只补齐缺失快照、已有快照时不重复抓取。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=MarketPulseServiceTest test`

Expected: FAIL，原因是自动刷新和恢复方法尚未定义。

- [ ] **Step 3: 实现最小用例逻辑**

在 `MarketPulseService` 中增加进程内 `AtomicBoolean` 防重，并提供：

```java
public Optional<MarketPulseRefreshResult> refreshScheduled(LocalDate expectedBusinessDate)
public Optional<MarketPulseRefreshResult> recoverMissing()
```

人工 `refresh()`、定时刷新和补偿刷新复用同一执行骨架；恢复任务先查询 `MarketPulseRepository.findWorkspace()`，已有快照返回 `Optional.empty()`，保存仍沿用按交易日 UPSERT 的数据库幂等约束。

- [ ] **Step 4: 运行服务测试确认通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=MarketPulseServiceTest test`

Expected: PASS。

### Task 2: 增加 15:30 定时任务与小时级补偿

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseRefreshScheduler.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseRefreshSchedulerTest.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] **Step 1: 写失败测试**

新增调度器测试，使用固定上海时区时钟验证主任务传入当天日期、恢复任务调用缺失快照补偿、业务异常由 Job 边界记录且不向 Spring 调度线程传播。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=MarketPulseRefreshSchedulerTest test`

Expected: FAIL，原因是调度器尚未定义。

- [ ] **Step 3: 实现定时入口和配置**

新增两个调度入口：

```java
@Scheduled(cron = "${finscope.market-pulse.refresh-cron:0 30 15 * * MON-FRI}", zone = "Asia/Shanghai")
public void refreshAfterClose()

@Scheduled(initialDelayString = "${finscope.market-pulse.recovery-initial-delay-ms:30000}",
        fixedDelayString = "${finscope.market-pulse.recovery-interval-ms:3600000}")
public void recoverMissedRefresh()
```

任务日志记录触发日期、结果、业务日期和耗时；异常携带完整堆栈，等待下一周期恢复。

- [ ] **Step 4: 运行调度器测试确认通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=MarketPulseRefreshSchedulerTest test`

Expected: PASS。

- [ ] **Step 5: 提交并推送后端改动**

```bash
git add backend/finscope-service backend/finscope-web/src/main/resources/application.yml docs/superpowers/plans/2026-08-30-market-pulse-auto-refresh.md
git commit -m "feat: 增加市场状态收盘自动刷新"
git push
```

### Task 3: 调整页面刷新语义

**Files:**
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/styles/market-pulse.css`（以仓库实际样式文件为准）

- [ ] **Step 1: 写失败测试**

更新组件测试，验证页面展示“每日 15:30 自动更新”，人工按钮改为“立即补刷新”，页面加载时不会因快照不可用自动发起写请求。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- --run src/features/market-pulse/MarketPulseView.test.tsx`

Expected: FAIL，原因是页面仍展示旧按钮并执行页面侧自动修复。

- [ ] **Step 3: 实现页面语义**

移除页面加载后的隐式写操作；在控制区展示自动更新时间，保留“立即补刷新”作为行情源失败后的人工补救，并继续展示快照生成时间。

- [ ] **Step 4: 运行组件测试和生产构建**

Run: `cd frontend && npm test -- --run src/features/market-pulse/MarketPulseView.test.tsx && npm run build`

Expected: PASS。

- [ ] **Step 5: 提交并推送前端改动**

```bash
git add frontend/src/features/market-pulse frontend/src/styles
git commit -m "feat: 调整市场状态自动刷新提示"
git push
```

### Task 4: 全量验证与规范自检

**Files:**
- Verify only

- [ ] **Step 1: 运行后端相关模块测试**

Run: `cd backend && mvn -pl finscope-web -am test`

Expected: 所有 Maven 模块测试通过。

- [ ] **Step 2: 运行前端全量测试与构建**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 所有 Vitest 测试和 TypeScript/Vite 构建通过。

- [ ] **Step 3: 对照项目规范检查**

确认新增 Spring Bean 采用字段注入，所有 `if`/`for` 完整使用大括号，调度入口没有复制业务实现，配置不包含新增密钥或地址，工作树只包含本需求文件。

- [ ] **Step 4: 确认分支同步状态**

Run: `git status --short --branch && git log -3 --oneline`

Expected: 当前分支已推送，工作树干净。
