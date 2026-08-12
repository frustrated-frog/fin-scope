# Radar Hotspot V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建不依赖 ES、向量和 Agent 判榜的可解释热点 V2，提高转载识别、聚类、时序热度、可信度和事件稳定性。

**Architecture:** 在 service 模块内建立一次提取的信号特征、来源独立性、候选图聚类、评分和生命周期边界。SQLite 保存事件事实与版本化评分快照，现有 Redis revision 发布继续提供页面读取；旧接口保持兼容。

**Tech Stack:** Java 21、Spring Boot 2.7、Spring JDBC、SQLite、JUnit 5、Mockito、Redis snapshot cache。

---

### Task 1: 信号特征与来源独立性

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSignalFeatures.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarSourceIndependenceService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarTextAnalyzer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarSourceIndependenceServiceTest.java`

- [ ] 写失败测试，证明同来源和转载只产生一个有效独立来源，而事实不同的第二来源可以形成独立确认。
- [ ] 使用 JDK 21 运行指定测试，确认失败原因是新类型或行为尚不存在。
- [ ] 实现不可变信号特征、集中事实槽位和来源/转载分析，输入为空时返回空结果，不抛出异常。
- [ ] 运行来源独立性与文本分析测试，确认通过。
- [ ] 提交 `feat: 增加雷达信号特征与转载识别` 并推送。

### Task 2: 候选图聚类与稳定事件键

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventIdentityService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarTextAnalyzer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringEvaluationTest.java`
- Modify: `backend/finscope-service/src/test/resources/radar/clustering-cases.json`

- [ ] 写失败测试，证明聚类与输入顺序无关、传递边能合并、硬冲突不会链式误合并、标题改写保持事件键。
- [ ] 运行测试并确认现有贪心算法不能满足新契约。
- [ ] 实现候选分桶、缓存特征、强边图和合并前簇间冲突校验；事件键优先使用证券代码/主体、动作、变量和事实日期。
- [ ] 运行聚类测试和评测集，要求误合并率不高于 2%、正样本召回不低于 85%。
- [ ] 提交 `feat: 升级雷达事件图聚类与稳定身份` 并推送。

### Task 3: 时序热度、可信度与生命周期

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarLifecycleService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotScoreService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotScoreServiceTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarLifecycleServiceTest.java`

- [ ] 写失败测试，覆盖转载不增加 Burst/确认分、排名上升得分、指数衰减、低可信热点和带滞回生命周期。
- [ ] 运行指定测试，确认 V1 数量差和阈值状态无法满足测试。
- [ ] 实现 `Hotness V2` 六维特征、`Confidence`、结构化解释和生命周期决策；所有分数限制在 0 到 100。
- [ ] 运行热点评分、来源质量和生命周期测试，确认通过。
- [ ] 提交 `feat: 增加时序热点评分与可信度模型` 并推送。

### Task 4: 研究相关性与多目标排序

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarPriorityService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarPriorityServiceTest.java`

- [ ] 写失败测试，覆盖证券代码、标准名、别名精确匹配和短名称子串不误判。
- [ ] 运行测试并确认旧字符串包含逻辑失败。
- [ ] 将研究优先级改为 45% 相关性、35% 热度、20% 可信度，保留旧调用的兼容入口。
- [ ] 运行优先级和生产管线测试，确认首页热度与雷达优先级仍为不同排序。
- [ ] 提交 `feat: 优化雷达研究相关性与多目标排序` 并推送。

### Task 5: SQLite 快照与生产管线接入

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvent.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEventSnapshot.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarEventSnapshotRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarHotspotProductionPipeline.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/config/DatabaseInitializerTest.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarEventSnapshotRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotProductionPipelineTest.java`

- [ ] 写失败测试，证明旧库可增量增加 V2 字段，事件和快照能完整回读，生产结果使用 V2 分数与版本。
- [ ] 运行 DAO/service 指定测试并确认新字段尚未持久化。
- [ ] 增加兼容列、映射、版本化快照和管线编排，不改变事务及 Redis revision 发布顺序。
- [ ] 运行 DAO、service、web 雷达测试，确认现有 API 兼容。
- [ ] 提交 `feat: 接入热点V2持久化与生产管线` 并推送。

### Task 6: 回放评测与全量验证

**Files:**
- Create: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarHotspotReplayEvaluationTest.java`
- Create: `backend/finscope-service/src/test/resources/radar/hotspot-replay-cases.json`
- Modify: `docs/superpowers/specs/2026-08-13-radar-hotspot-v2-design.md`

- [ ] 建立固定时钟的回放样本，覆盖官方公告、多源独立确认、集中转载、排名升温、陈旧事件和事实冲突。
- [ ] 断言转载误计数、重复卡片、事件键稳定、Top K 独立确认和生命周期抖动门槛。
- [ ] 使用 JDK 21 运行 `mvn test` 和前端生产构建，记录实际验证结果。
- [ ] 检查 git diff 仅包含热点 V2 和文档，扫描是否意外打印或移动配置密钥。
- [ ] 提交 `test: 增加热点V2历史回放评测`，推送 `codex/radar-hotspot-v2`。
