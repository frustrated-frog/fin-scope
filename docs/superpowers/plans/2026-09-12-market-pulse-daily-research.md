# Market Pulse Daily Research Implementation Plan

> 按已批准范围，在当前分支使用 subagent-driven-development 执行独立数据任务；主任务负责 Java 接入与前端。共享工作区不建立 worktree。每批验证后提交并推送，仅提交本次文件。

**Goal:** 在今日雷达提供可复算的行业筛选、人工主题地图和股票组日频表现。
**Architecture:** Python SnapshotStore计算 → Java RPC验证/Service编排/Web响应 → React独立加载；行业筛选为纯函数，主题维护为浏览器本地配置。
**Tech Stack:** 现有 Python/Pydantic/FastAPI、Java21/Spring、React/TypeScript/Vitest；不新增依赖。

## Task 1 本地样本计算与Python接口
- [x] tests/test_daily_research.py 先覆盖昨日入组后今日下跌/缺失、窗口缺失、复权混合、指数排除、未来数据、少样本、交易日边界。
- [x] 执行 `.venv/bin/python -m pytest tests/test_daily_research.py` 确认缺少模块失败。
- [x] src/finscope_market_data/daily_research.py 实现设计中的确定性函数与Pydantic契约，app.py新增路由，复用SnapshotStore与交易日历。
- [x] 添加API契约测试并运行相关Python测试；审查后提交推送。

## Task 2 Java边界接入
- [x] 新增RPC测试，固定schema/date和数字、成员计数校验；确认缺少类失败。
- [x] domain/marketpulse新增内部研究快照/股票/组DTO，common/enums/marketpulse集中组枚举；rpc/marketpulse新增PythonDailyResearchSource。
- [x] service/marketpulse新增日频研究服务；web新增独立controller/response，沿用字段注入与统一响应。
- [x] 运行 `mvn -pl finscope-web -am test -Dtest='*DailyResearch*' -Dsurefire.failIfNoSpecifiedTests=false` 并审查提交推送。

## Task 3 日频研究界面
- [x] marketResearch.test.ts先覆盖过滤缺失值、阈值、主题去重、生效日期及覆盖率。
- [x] marketResearch.ts/types实现纯函数与主题持久化；SectorResearchPanel、ThemeResearchPanel、CohortResearchPanel拆分职责；DailyResearchPanel独立请求与重试。
- [x] MarketPulseView今日雷达接入；App透传已有产业链研究导航；自选复用POST /api/watchlist。
- [x] 交互测试覆盖筛选/比较/研究跳转、主题保存与错误、股票组成员和收益分布、日期请求竞争与失败重试。
- [x] `npm test -- src/features/market-pulse`、`npm run build`；浏览器检查桌面/窄屏。审查提交推送。

## Task 4 集成与交付
- [x] 全量前端测试、相关后端/Python回归；核对开发规范，独立审查需求与代码质量。
- [x] 补充使用说明及验证记录，确认原有application.yml和pnpm-lock.yaml改动完整保留。

## 实施核验

分批提交：设计、Python数据计算、Java接口、前端界面与文档。依赖注入/大括号/模块依赖方向逐项检查；仅保留必要改动，无新依赖。独立审查发现的无效日K问题已修复并回归，行业上下文来源文案已区分。测试与真实样本说明见 docs/market-pulse-daily-research.md。
