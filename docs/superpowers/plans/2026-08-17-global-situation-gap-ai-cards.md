# Global Situation Gap and AI Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为全球预期事件增加确定性的预期—现实偏离状态，并让所有卡片拥有可降级、可缓存的 AI 快读内容。

**Architecture:** Radar Matcher 在完成事件匹配后统计关联信号的时间窗口和来源，Gap Analyzer 将预测强度与现实强度组合为五态结果。Rule Interpreter 立即生成可读文本，Enhancement Service 再按稳定物质指纹分批进行单次结构化 LLM 增强，前端以双轨强度卡片展示。

**Tech Stack:** Java 21、Spring Boot 2.7、SQLite、Redis、JUnit 5、React、TypeScript、Vitest、CSS

---

### Task 1: Reality 指标与 Gap 状态

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationRadarMatch.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationEventGroup.java`
- Create: `backend/finscope-common/src/main/java/com/finscope/common/enums/globalexpectations/ExpectationRealityState.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationGapAnalyzer.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationGapAnalyzerTest.java`

- [ ] 写入失败测试，覆盖预期先行、现实先行、双侧升温、平静和数据不足五种状态。
- [ ] 运行 `mvn -pl finscope-service -am test -Dtest=GlobalExpectationGapAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false`，确认因类型和分析器缺失而失败。
- [ ] 增加独立枚举、领域字段和纯规则分析器；预测阈值为 40，现实评分由 1h 数量、独立来源与相邻小时加速度组成。
- [ ] 重跑测试并确认五态矩阵通过。

### Task 2: Radar 时间窗口统计

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationRadarMatcher.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationRadarMatcherTest.java`

- [ ] 写入失败测试：匹配事件包含最近 1h、前 1h、24h、独立来源和最近出现时间；低匹配结果不进入现实统计。
- [ ] 运行 Matcher 测试，确认新字段断言失败。
- [ ] 使用 `RadarRepository.findSignalsByEventId()` 汇总有效时间和来源，并将匹配阈值提升为高置信门槛；查询失败时清空 Reality 指标而不阻塞预测侧。
- [ ] 重跑 Matcher 与 Gap 测试。

### Task 3: 全卡片规则解读与 AI 队列

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/globalexpectations/GlobalExpectationInterpretation.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationRuleInterpreter.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationEnhancementService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationInterpretationAgent.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationRuleInterpreterTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationEnhancementServiceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationInterpretationAgentTest.java`

- [ ] 写入失败测试，证明普通观察事件立即获得 `RULE_BASED` 五字段解读。
- [ ] 写入失败测试，证明一轮前五条缓存命中后队列会继续增强后续事件，并且小幅概率、成交量和排名变化不会改变指纹。
- [ ] 写入失败测试，要求 AI 返回 `uncertainty`，失败状态保留规则字段。
- [ ] 实现规则解读、稳定指纹、全事件优先队列和新的固定 JSON Prompt。
- [ ] 重跑三组测试并确认通过。

### Task 4: 刷新编排与 API 契约

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/globalexpectations/GlobalExpectationsService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/globalexpectations/GlobalExpectationsServiceTest.java`

- [ ] 写入失败测试，断言 refresh 顺序为聚合、Radar、Gap、规则解读、快照、异步增强，feed 能恢复旧缓存并补齐新字段。
- [ ] 运行 Service 测试确认失败。
- [ ] 注入并串联 Gap Analyzer 与 Rule Interpreter；对旧 Redis JSON 做空字段兼容。
- [ ] 重跑全球预期后端相关测试。

### Task 5: 双轨情报卡片 UI

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.tsx`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsView.test.tsx`
- Modify: `frontend/src/features/global-expectations/GlobalExpectationsResponsive.test.ts`
- Modify: `frontend/src/styles.css`

- [ ] 写入失败测试，断言状态筛选、预期/现实双强度、规则解读来源、AI 解读来源、新闻窗口和不确定性展开均可见。
- [ ] 运行目标 Vitest，确认 UI 契约因字段和组件缺失而失败。
- [ ] 扩展 TypeScript 类型；将卡片拆为状态头、双侧信号、预测选项、快读和 Radar 线索五个聚焦组件。
- [ ] 增加具有靛蓝/青绿双轨语义的样式、窄屏单列布局和 reduced-motion 规则。
- [ ] 重跑目标测试与 `npm run build`。

### Task 6: 全量验证与运行态检查

**Files:**
- Review: all files modified above

- [ ] 执行 `git diff --check` 和 Java 大括号、字段注入、模块落点自检。
- [ ] 使用 JDK 21 执行 `cd backend && mvn test`，确认 Reactor BUILD SUCCESS。
- [ ] 执行 `cd frontend && npm test -- --run && npm run build`，确认全部通过。
- [ ] 重启本地后端并调用 `/api/global-expectations/feed`，确认五态、规则/AI 解读和 Reality 统计字段存在。
- [ ] 在真实页面检查桌面与窄屏布局、展开交互和控制台错误。
- [ ] 使用 `feat: <中文描述>` 提交并推送 `codex/global-situation-gap-ai-cards`。
