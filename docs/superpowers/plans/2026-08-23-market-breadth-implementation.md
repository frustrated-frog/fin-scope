# Market Pulse Market Breadth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 为 Market Pulse 增加由 Python 行情服务统一采集、可降级且与交易日对齐的 A 股核心市场宽度。

**Architecture:** Python 行情侧车新增市场级宽度 Provider、快照和 HTTP 契约；Java RPC 只消费标准化结果，Service 将宽度、五大指数和行业上涨比例冻结进每日 Market Pulse；React 在现有页面展示宽度和数据质量。所有规则采用确定性实现并保持部分失败可用。

**Tech Stack:** Python 3.11+/FastAPI/Pydantic/AkShare/SQLite、Java 21/Spring Boot 2.7/SQLite/Jackson、React 18/TypeScript/Vitest

---

### Task 1: Python 市场宽度领域契约与 Provider

**Files:**
- Create: `market-data-service/src/finscope_market_data/breadth.py`
- Modify: `market-data-service/src/finscope_market_data/models.py`
- Test: `market-data-service/tests/test_breadth.py`

- [x] **Step 1: 写失败测试**

覆盖东方财富与新浪字段映射、无效行过滤、上涨比例、成交额、中位数、涨跌停部分失败和主源失败备用源成功。

- [x] **Step 2: 运行测试确认失败**

Run: `cd market-data-service && .venv/bin/pytest tests/test_breadth.py -q`

Expected: FAIL，原因是 `breadth` 模块和模型尚不存在。

- [x] **Step 3: 实现最小 Provider**

实现 `MarketBreadthSnapshot`、`MarketBreadthProvider`、东方财富和新浪加载函数。统一输出 `business_date/source/quality/counts/ratio/amount/median/warnings`，涨跌停池失败时保留主体并降为 `PARTIAL_FRESH`。

- [x] **Step 4: 运行测试确认通过**

Run: `cd market-data-service && .venv/bin/pytest tests/test_breadth.py -q`

Expected: PASS。

- [x] **Step 5: 提交**

```bash
git add market-data-service/src/finscope_market_data/breadth.py market-data-service/src/finscope_market_data/models.py market-data-service/tests/test_breadth.py
git commit -m "feat: 增加A股市场宽度采集"
git push
```

### Task 2: Python API、快照与行业宽度

**Files:**
- Modify: `market-data-service/src/finscope_market_data/app.py`
- Modify: `market-data-service/src/finscope_market_data/sectors.py`
- Modify: `market-data-service/src/finscope_market_data/snapshot_store.py`
- Modify: `market-data-service/tests/test_api.py`
- Modify: `market-data-service/tests/test_sectors.py`

- [x] **Step 1: 写失败测试**

验证 `GET /v1/markets/CN-A/breadth` 返回 `market-breadth-v1`，同花顺行业条目返回上涨、下跌、平盘和上涨比例，并验证缓存回退质量。

- [x] **Step 2: 运行测试确认失败**

Run: `cd market-data-service && .venv/bin/pytest tests/test_api.py tests/test_sectors.py -q`

Expected: FAIL，新接口和字段不存在。

- [x] **Step 3: 实现接口和快照**

在应用生命周期创建市场宽度服务；快照使用固定市场键持久化；扩展行业映射但保持旧字段兼容。

- [x] **Step 4: 运行测试确认通过**

Run: `cd market-data-service && .venv/bin/pytest tests/test_breadth.py tests/test_api.py tests/test_sectors.py -q`

Expected: PASS。

- [x] **Step 5: 提交**

```bash
git add market-data-service/src/finscope_market_data/app.py market-data-service/src/finscope_market_data/sectors.py market-data-service/src/finscope_market_data/snapshot_store.py market-data-service/tests/test_api.py market-data-service/tests/test_sectors.py
git commit -m "feat: 提供市场宽度与行业广度接口"
git push
```

### Task 3: Java RPC 与领域模型

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketBreadthSnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketIndexPerformance.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/MarketBreadthSource.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSource.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/instrument/SectorMarketEntry.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/PythonTonghuashunSectorMarketProvider.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/marketpulse/PythonMarketBreadthSourceTest.java`

- [x] **Step 1: 写失败测试**

构造完整、部分和不可用 Python 响应，验证日期、来源、计数、比例、警告和行业宽度字段映射。

- [x] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=PythonMarketBreadthSourceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新类型不存在。

- [x] **Step 3: 实现领域类型和 RPC**

RPC 访问 `/v1/markets/CN-A/breadth`，严格校验 schema、日期、非负计数、0 到 1 的比例和质量状态；异常转换为 `ProviderContractException`。

- [x] **Step 4: 运行测试确认通过**

运行 Step 2 命令，Expected: PASS。

- [x] **Step 5: 提交**

```bash
git add backend/finscope-domain backend/finscope-rpc
git commit -m "feat: 接入标准化市场宽度数据"
git push
```

### Task 4: Market Pulse 宽度编排、持久化和日期边界

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketBreadthService.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketPulseWorkspace.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/marketpulse/MarketRegimeFeatures.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseFeatureService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketRegimeClassifier.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/marketpulse/MarketPulseSectorService.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseSchemaMigrator.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/marketpulse/MarketPulseRepository.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketBreadthServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketRegimeClassifierTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketpulse/MarketPulseServiceTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/marketpulse/MarketPulseRepositoryTest.java`

- [x] **Step 1: 写失败测试**

验证五大指数收益、业务日期不一致拒绝混合、上涨比例进入风险偏好、完整宽度升级为 `READY`、未来错误快照不再成为 latest、行业宽度进入轮动评分。

- [x] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -pl finscope-dao,finscope-service -am -Dtest='MarketBreadthServiceTest,MarketRegimeClassifierTest,MarketPulseServiceTest,MarketPulseRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，编排和字段尚不存在。

- [x] **Step 3: 实现编排与快照**

读取宽度后再计算市场状态；将上涨比例写入 `MarketRegimeFeatures`；指数或涨跌停局部失败只降低质量；查询 latest 时传入有效交易日上限；保存冻结宽度 JSON。

- [x] **Step 4: 运行测试确认通过**

运行 Step 2 命令，Expected: PASS。

- [x] **Step 5: 提交**

```bash
git add backend/finscope-domain backend/finscope-dao backend/finscope-service
git commit -m "feat: 使用市场宽度校准市场状态"
git push
```

### Task 5: Web 契约与 Market Pulse 页面

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/response/MarketPulseWorkspaceResponse.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/controller/MarketPulseControllerTest.java`
- Modify: `frontend/src/features/market-pulse/marketPulseTypes.ts`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.tsx`
- Modify: `frontend/src/features/market-pulse/MarketPulseView.test.tsx`
- Modify: `frontend/src/styles.css`

- [x] **Step 1: 写失败测试**

验证响应包含宽度，页面显示五大指数、涨跌比例条、成交额、涨跌停、市场中位数、来源与降级警告。

- [x] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx`

Expected: FAIL，市场宽度区域不存在。

- [x] **Step 3: 实现响应和页面**

增加紧凑的宽度区，不新增 Tab；空字段显示 `—`；A 股颜色保持红涨绿跌；窄屏下指数卡改为两列和单列。

- [x] **Step 4: 运行专项测试**

Run: `cd frontend && npm test -- MarketPulseView.test.tsx`

Expected: PASS。

- [x] **Step 5: 提交**

```bash
git add backend/finscope-web frontend/src
git commit -m "feat: 展示市场宽度与指数共振"
git push
```

### Task 6: 文档、回归和最终核验

**Files:**
- Modify: `market-data-service/README.md`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-23-market-breadth-design.md`

- [x] **Step 1: 更新数据契约说明**

记录市场宽度端点、主备来源、部分失败语义、Market Pulse 判断边界与不包含的新高/新低、炸板率。

- [x] **Step 2: 运行 Python 回归**

Run: `cd market-data-service && .venv/bin/pytest -q`

Expected: PASS。

- [x] **Step 3: 运行 Market Pulse 后端回归**

Run: `cd backend && mvn -pl finscope-web -am -Dtest='*MarketPulse*,*MarketBreadth*,*SectorRotation*' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [x] **Step 4: 运行前端回归和构建**

Run: `cd frontend && npm test && npm run build`

Expected: 全部测试和生产构建通过。

- [x] **Step 5: 检查规范并提交**

Run: `git diff --check && git status --short`

```bash
git add README.md market-data-service/README.md docs/superpowers/specs/2026-08-23-market-breadth-design.md docs/superpowers/plans/2026-08-23-market-breadth-implementation.md
git commit -m "docs: 补充市场宽度数据边界"
git push
```

