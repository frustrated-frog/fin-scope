# 每日收盘复盘与市场历史实现计划

> **执行要求：** 按 TDD 顺序逐项完成；每一批通过对应测试后独立提交并推送当前分支。

**Goal:** 为 Market Pulse 增加同花顺全行业 60 日历史、证据驱动的每日收盘复盘和历史演变视图。

**Architecture:** Python sidecar 采集并标准化行业历史；Java RPC 校验契约，service 计算行业轮动与复盘，DAO 冻结快照；React 在同一主页面提供三个下钻视图。

**Tech Stack:** Python 3.13、FastAPI、Pydantic、pytest、Java 21、Spring Boot 2.7、SQLite、JUnit 5、React、TypeScript、Vitest。

---

## Task 1: 同花顺全行业历史契约

**Files:**
- Create: `market-data-service/src/finscope_market_data/sector_history.py`
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/README.md`
- Create: `market-data-service/tests/test_sector_history.py`
- Modify: `market-data-service/tests/test_api.py`

1. 先写失败测试，覆盖截止业务日期、1/5/20 日收益、5 日上涨天数、单行业失败 warning 和 API schema。
2. 运行 `cd market-data-service && .venv/bin/pytest tests/test_sector_history.py tests/test_api.py -q`，确认因实现/路由不存在而失败。
3. 实现 Pydantic 契约、同花顺历史加载、有界并发和 FastAPI 路由。
4. 再运行定向测试与 `cd market-data-service && .venv/bin/pytest -q`。
5. 提交并推送：`feat: 增加同花顺全行业历史契约`。

## Task 2: Java 行业历史 RPC 与轮动计算

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/SectorHistorySnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/SectorHistoryItem.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/SectorHistorySource.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/PythonSectorHistorySource.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketpulse/PythonSectorHistorySourceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseSectorService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseSectorServiceTest.java`

1. 先写 RPC 契约和行业轮动失败测试，要求全行业条目拥有真实 5/20 日收益且不依赖已有 workspace。
2. 运行对应 Maven 测试，确认新增类型/行为缺失导致失败。
3. 实现 RPC 解析、数据校验、历史优先/本地快照回退和全行业评分。
4. 运行 `cd backend && mvn -pl finscope-rpc,finscope-service -am test -Dtest=PythonSectorHistorySourceTest,MarketPulseSectorServiceTest -Dsurefire.failIfNoSpecifiedTests=false`。
5. 提交并推送：`feat: 使用行业历史增强轮动计算`。

## Task 3: 每日复盘领域模型、规则和持久化

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/DailyMarketReview.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketPulseHistoryPoint.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/DailyMarketReviewService.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/DailyMarketReviewServiceTest.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketPulseWorkspace.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseServiceTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseSchemaMigrator.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseRepository.java`
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/marketpulse/MarketPulseRepositoryTest.java`

1. 先写规则测试：扩散上涨、缩量修复、宽度背离、主线与退潮、风险和观察清单。
2. 写 Repository 失败测试，要求复盘单独快照且同业务日期幂等覆盖，历史点按日期倒序。
3. 实现领域 DTO、确定性规则服务、表结构和编排接入。
4. 运行 service/dao 定向测试，再运行 `cd backend && mvn test`。
5. 提交并推送：`feat: 生成可回溯的每日市场复盘`。

## Task 4: Web 契约与三视图页面

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/MarketPulseWorkspaceResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/MarketPulseControllerTest.java`
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseResponsive.test.ts`
- Modify: `frontend/src/styles.css`

1. 先写 Web/前端失败测试，覆盖每日复盘字段、默认视图、三个视图切换、历史点和缺失降级。
2. 运行定向测试确认失败。
3. 实现 ISO 响应映射、今日复盘组件、结构视图复用、历史演变列表/图形和响应式样式。
4. 运行 `cd frontend && npm test -- --run`、`npm run build` 和 Web 模块测试。
5. 提交并推送：`feat: 增加每日复盘与历史演变视图`。

## Task 5: 文档、全量验证与评审

**Files:**
- Modify: `README.md`
- Modify: `market-data-service/README.md`
- Modify: `docs/superpowers/plans/2026-08-23-daily-market-review-implementation.md`

1. 更新功能清单、数据来源、降级边界和启动说明，不记录任何密钥。
2. 对照《项目开发规范与代码评审清单.md》检查注入方式、Java if/for 大括号、模块落点与依赖方向。
3. 运行全量验证：
   - `cd market-data-service && .venv/bin/pytest -q`
   - `cd backend && mvn test`
   - `cd frontend && npm test -- --run`
   - `cd frontend && npm run build`
4. 执行代码评审，修复重要问题后重新运行受影响测试。
5. 提交并推送：`docs: 补充每日市场复盘使用说明`。
