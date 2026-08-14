# Stock Discovery and Radar Fault Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复股票发现与热点雷达各自的业务失败，并在共享 Kafka 上建立可验证的双向故障隔离。

**Architecture:** 保留一个 Kafka 集群但为两个领域配置独立 topic、消费组、监听容器、重试和 DLT。热点雷达在持久化前归并重复事件身份；Python 股票发现对热门板块源有界重试并保留分源诊断，失败批次由 Java 恢复调度重放。

**Tech Stack:** Java 21、Spring Boot 2.7、Spring Kafka 2.8、SQLite、Python 3.13、FastAPI、pytest、React/TypeScript。

---

### Task 1: 合并热点雷达的重复事件身份

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`

- [x] 写一个失败测试：两个排名聚类使用同一 `eventKey` 时，流水线应合并关系并只持久化一个事件。
- [x] 运行 `mvn -pl finscope-service -Dtest=RadarHotspotProductionPipelineTest test`，确认当前因“重复事件身份”失败。
- [x] 在 `persist` 之前按事件键归并事件关系，并按信号身份去重；不吞掉真正的持久化异常。
- [x] 重新运行聚焦测试并提交 `fix: 合并热点雷达重复事件身份`。

### Task 2: 增强热门板块数据源恢复与诊断

**Files:**
- Modify: `market-data-service/src/finscope_market_data/discovery/service.py`
- Modify: `market-data-service/src/finscope_market_data/discovery/providers.py`
- Test: `market-data-service/tests/test_discovery_service.py`
- Test: `market-data-service/tests/test_discovery_providers.py`

- [x] 写失败测试：provider 首次异常、第二次成功时返回在线数据；全部失败且无快照时异常包含每个来源和尝试次数。
- [x] 运行 `uv run pytest tests/test_discovery_service.py tests/test_discovery_providers.py -q` 并确认新测试失败。
- [x] 实现每源两次有界尝试、短退避和分源错误聚合；继续严格拒绝超过四天的快照。
- [x] 增加独立于东方财富资金流接口的行业榜备用 provider，并保持来源审计字段真实。
- [x] 运行聚焦 Python 测试和完整 Python 测试，提交 `fix: 增强股票发现数据源恢复能力`。

### Task 3: 允许失败股票批次自动重试

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/quant/discovery/StockDiscoveryScheduler.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/quant/discovery/StockDiscoveryServiceTest.java`

- [x] 写失败测试：相同业务日的 `FAILED` 批次在恢复调度时再次发布，已成功批次保持幂等。
- [x] 运行聚焦测试，确认恢复语义尚未满足。
- [x] 保持唯一 `runKey`，重新发布失败批次；执行仍由 repository CAS 和 attempt token 防重。
- [x] 运行聚焦测试并提交 `fix: 恢复失败的股票发现批次`。

### Task 4: 配置领域独立的 Kafka 重试与死信

**Files:**
- Create: `backend/finscope-web/src/main/java/com/finscope/web/config/DomainKafkaListenerConfig.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/messaging/StockDiscoveryListener.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/messaging/RadarInterpretationBatchListener.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Create: `backend/finscope-web/src/test/java/com/finscope/web/config/DomainKafkaListenerConfigTest.java`
- Create: `backend/finscope-web/src/test/java/com/finscope/web/messaging/StockDiscoveryListenerTest.java`

- [x] 写失败测试：两个 Listener 必须引用不同的命名 factory，两个 recoverer 必须路由到不同 DLT。
- [x] 运行 `mvn -pl finscope-web -am -Dtest=DomainKafkaListenerConfigTest,StockDiscoveryListenerTest,RadarInterpretationBatchListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` 并确认失败。
- [x] 使用项目字段注入规范创建两个 factory；分别配置 `DefaultErrorHandler` 和 `DeadLetterPublishingRecoverer`。
- [x] 在 Listener 注解中显式指定 factory，配置独立 DLT 名称、重试间隔和次数。
- [x] 运行聚焦测试并提交 `fix: 隔离股票发现与热点消息失败`。

### Task 5: 展示业务失败与基础设施状态

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/StockDiscoveryController.java`
- Modify: `frontend/src/features/strategy/StockDiscoveryPanel.tsx`
- Modify: `frontend/src/features/strategy/quantTypes.ts`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/StockDiscoveryControllerTest.java`
- Test: `frontend/src/features/strategy/StockDiscoveryPanel.test.tsx`

- [x] 写失败测试：失败状态展示业务错误、自动重试提示和下一次调度时间，不将其描述为 Kafka 故障。
- [x] 运行 Java Controller 与前端聚焦测试并确认失败。
- [x] 扩展状态 DTO 并实现分层状态卡片，保持现有视觉语言和响应式布局。
- [x] 运行聚焦测试、前端完整测试和构建，提交 `feat: 展示股票发现链路诊断状态`。

### Task 6: 完整隔离验证与文档收口

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-14-stock-discovery-radar-fault-isolation-design.md`

- [x] 运行 Python 完整测试：`cd market-data-service && uv run pytest -q`。
- [x] 运行 Java 相关模块测试：`cd backend && mvn -pl finscope-web -am test`。
- [x] 运行前端测试和构建：`cd frontend && npm test -- --run && npm run build`。
- [x] 启动依赖后检查两个消费组 lag 与两个 DLT 路由，记录实际验证结论。
- [x] 对照《项目开发规范与代码评审清单》检查字段注入、大括号、模块落点、幂等、重试和错误映射。
- [x] 更新 README 的 topic 与故障语义，提交并推送最终分支。

**实际验证：** Python 134 项和前端 405 项测试通过；Java 全 reactor 通过，其中 Web 模块 156 项、0 失败、0 错误、1 跳过。前端生产构建通过。运行中 Kafka 检查见设计文档的验证结论；DLT 为失败耗尽时按需创建，路由通过单元测试锁定。
