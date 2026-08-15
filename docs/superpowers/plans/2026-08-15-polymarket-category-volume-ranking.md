# Polymarket Category Volume Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Polymarket 官方五分类标签，为每个分类抓取按 24 小时成交量排序的前 10 个活跃市场。

**Architecture:** Catalog 提供分类定义，RPC 负责 tag 解析、分类市场请求和历史批处理，Service 负责组合五个分类榜单与缓存，前端只按服务端分类筛选并展示榜单口径。

**Tech Stack:** Java 21、Spring Boot、Jackson、JUnit 5、React、TypeScript、Vitest

---

### Task 1: 分类市场 RPC

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicClient.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicMarket.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/polymarket/PolymarketPublicClientTest.java`

- [ ] 新增失败测试：调用分类 slug 时先读取 `/tags/slug/{slug}`，再请求 `markets?active=true&closed=false&tag_id=...&order=volume24hr&ascending=false&limit=10&locale=zh`。
- [ ] 新增失败测试：解析 `volume24hr` 字段。
- [ ] 新增失败测试：超过 20 个 token 时拆分历史请求并合并三批结果。
- [ ] 实现 tag ID 进程内缓存、分类市场查询、24 小时成交量解析和历史批处理。
- [ ] 运行 `cd backend && mvn -pl finscope-rpc test -Dtest=PolymarketPublicClientTest`，确认通过。

### Task 2: 五分类榜单编排

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsCatalog.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsService.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationItem.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationsCatalogTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationsServiceTest.java`

- [ ] 新增失败测试：Catalog 顺序返回政治、财务、地缘冲突、科技、经济及对应官方 slug。
- [ ] 新增失败测试：Service 对每个分类独立取 10 条并保留 `volume24h`，中文标题无需关键词匹配。
- [ ] 将 Catalog 改为稳定分类定义，将 Service 改为逐分类请求且不做全局 Top 20 截断。
- [ ] 运行 `cd backend && mvn -pl finscope-service test -Dtest=GlobalExpectationsCatalogTest,GlobalExpectationsServiceTest`，确认通过。

### Task 3: 前端筛选和成交量口径

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.tsx`
- Test: `frontend/src/features/global-expectations/GlobalExpectationsView.test.tsx`

- [ ] 新增失败测试：页面包含五个分类筛选，点击分类只显示对应市场。
- [ ] 新增失败测试：卡片显示“24h 成交”及 `volume24h`。
- [ ] 增加 `volume24h` 类型字段，替换筛选列表与成交量展示文案。
- [ ] 运行 `cd frontend && npm test -- GlobalExpectationsView.test.tsx`，确认通过。

### Task 4: 回归验证与交付

**Files:**
- Modify if generated: `frontend/tsconfig.tsbuildinfo`

- [ ] 运行 `cd backend && mvn test`。
- [ ] 运行 `cd frontend && npm test`。
- [ ] 运行 `cd frontend && npm run build`。
- [ ] 启动或复用本地服务，在真实页面验证五个筛选和分类数量。
- [ ] 运行 `git diff --check` 并对照《项目开发规范与代码评审清单.md》自检。
- [ ] 使用 `feat: 增加 Polymarket 五分类成交榜` 提交并推送当前分支。

