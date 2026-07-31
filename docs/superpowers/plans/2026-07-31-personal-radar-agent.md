# Personal Radar Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为个人研究雷达增加受控的灰区语义聚类、规范标题、多源证据补全和可审计 Agent 轨迹，同时保留无 LLM 时的确定性回退。

**Architecture:** `RadarClusteringService` 保持唯一聚类接口，在内部组合规则判定、持久化新闻对缓存、严格 JSON Agent 和 BFS 连通分量。事件保存后由固定预算的证据编排模块复用现有研究工具 Seam，所有节点写入 `agent_run`；前端只展示简化轨迹和证据结论，不增加策略配置。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、现有 `LlmChatClient`、JUnit 5、Mockito、React、TypeScript、Vitest。

---

### Task 1: 灰区判定缓存

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarPairDecision.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarPairDecisionRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/radar/RadarPairDecisionRepositoryTest.java`

- [ ] 写测试：排序后的两个语义指纹构成对称键，保存后可读取判定、置信度和原因。
- [ ] 运行 `cd backend && mvn -pl finscope-dao -Dtest=RadarPairDecisionRepositoryTest test`，确认因表或类型不存在而失败。
- [ ] 增加 `radar_pair_decision` 表、领域对象和 Repository，使用 `ON CONFLICT(pair_key)` 幂等更新。
- [ ] 再次运行测试并确认通过。

### Task 2: 灰区事件判断 Agent

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEventMatchAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarAgentTraceRecorder.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEventMatchAgentTest.java`

- [ ] 写测试：合法 JSON 被解析；未知字段、越界置信度、非输入事实和模型异常触发保守结果。
- [ ] 运行定向测试，确认类缺失导致 RED。
- [ ] 使用 `LlmChatClient` 和严格 Jackson 映射实现；限制输入正文和输出长度。
- [ ] 将成功、失败和回退写入 `AgentRunRepository`，`subject_type=RADAR_CLUSTER`。
- [ ] 再次运行测试并确认通过。

### Task 3: 缓存增强的图聚类

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarClusteringServiceTest.java`

- [ ] 写测试：灰区 Agent 可合并、缓存避免重复调用、主体冲突不调用 Agent、A-B/B-C 形成一个连通分量。
- [ ] 运行定向测试，确认当前保守拆分和代表信号算法导致 RED。
- [ ] 将规则判定与最终判定分离，灰区按语义指纹查询缓存后调用 Agent。
- [ ] 构建无向邻接表并使用 BFS 生成聚类，关系记录最终判断来源。
- [ ] 模型和缓存异常均回退 `AMBIGUOUS` 保守拆分。
- [ ] 运行聚类测试并确认通过。

### Task 4: 规范事件标题

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarCanonicalTitleAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarClusteringService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarCanonicalTitleAgentTest.java`

- [ ] 写测试：多信号事件使用合法标题；空值、超长、Markdown 和模型失败使用代表标题。
- [ ] 运行定向测试并确认 RED。
- [ ] 实现严格 JSON 标题生成和 `agent_run` 留痕。
- [ ] 聚类完成时仅对多信号事件调用标题 Agent。
- [ ] 运行 Radar 服务测试并确认通过。

### Task 5: 第一批验证、提交与推送

**Files:**
- Modify: `docs/superpowers/specs/2026-07-31-personal-radar-agent-design.md`
- Modify: `docs/superpowers/plans/2026-07-31-personal-radar-agent.md`

- [ ] 运行 `cd backend && mvn -pl finscope-dao,finscope-service -am test`。
- [ ] 检查 `git diff --check` 和 `git status --short`，确认未包含用户的资金流文件。
- [ ] 提交 `feat: 增加雷达灰区事件判断Agent` 并推送当前分支。

### Task 6: 固定预算的雷达证据编排

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvidencePlan.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEvidencePlanAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/radar/RadarEvidenceOrchestrator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/radar/RadarEvidenceOrchestratorTest.java`

- [ ] 写测试：高优先级事件最多执行两个白名单工具；无股票代码不调用结构化股票资料；低优先级不自动检索。
- [ ] 运行定向测试并确认 RED。
- [ ] 实现严格计划输出和工具白名单，复用现有 `ResearchAgentToolRegistry` 的 Adapter。
- [ ] 将 Observation 摘要、数据引用和降级原因绑定到雷达事件。
- [ ] 运行服务层测试并确认通过。

### Task 7: 雷达证据结论与详情轨迹

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/radar/RadarEvent.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarView.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/radar/ResearchRadarService.java`
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Modify: `frontend/src/features/news/researchRadarTypes.ts`
- Test: `backend/finscope-web/src/test/java/com/finscope/web/controller/ResearchRadarApiIntegrationTest.java`
- Test: `frontend/src/features/news/NewsWorkbench.test.tsx`

- [ ] 写后端测试：详情返回证据摘要、数据引用和按事件查询的 Agent 轨迹。
- [ ] 写前端测试：默认显示结论，展开后显示节点、状态、耗时、工具和回退原因，不显示完整 Prompt。
- [ ] 分别运行定向测试并确认 RED。
- [ ] 扩展持久化、详情模型和 UI 折叠轨迹。
- [ ] 运行后端、前端全量测试和生产构建。
- [ ] 提交 `feat: 增加雷达多源证据与Agent轨迹` 并推送当前分支。
