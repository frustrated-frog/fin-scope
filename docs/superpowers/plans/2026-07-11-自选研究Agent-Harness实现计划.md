# 自选研究 Agent Harness 实现计划

> **面向 Agent 执行者：** 必须逐任务执行；每个任务先写失败测试，再写最小实现，再运行验证。任务通过复选框跟踪。

**目标：** 将自选深度归因升级为可计划、可恢复、证据受控且能输出丰富因果解释的金融研究 Harness。

**架构：** 保持模块化单体。`finscope-domain` 定义运行、计划、步骤、证据和报告对象；`finscope-dao` 负责 SQLite 增量表与查询；`finscope-service` 实现 Harness、计划校验、证据门和报告验证；`finscope-rpc` 只提供受控搜索；Web 与 React 只暴露运行状态和报告。

**技术栈：** Java 8、Spring Boot 2.7、SQLite、Jackson、React、TypeScript、JUnit、Vitest。

---

### 任务 1：建立归因运行与步骤状态模型

**文件：**
- 新建：`backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionResearchRun.java`
- 新建：`backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionResearchStep.java`
- 新建：`backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionResearchRunRepository.java`
- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- 测试：`backend/finscope-dao/src/test/java/com/finscope/dao/attribution/AttributionResearchRunRepositoryTest.java`

- [ ] 编写失败测试：创建运行后可按报告 ID 查询；保存步骤后返回稳定顺序；运行状态可从 RUNNING 更新为 PARTIAL。
- [ ] 运行 `mvn -o -pl finscope-dao -am -Dtest=AttributionResearchRunRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认测试因类不存在或行为缺失失败。
- [ ] 新增运行/步骤领域对象和 SQLite 表：`attribution_research_run`、`attribution_research_step`；添加 reportId 唯一索引和 runId/stepId 唯一索引。
- [ ] 实现 Repository 的 `createRun`、`findByReportId`、`updateRun`、`saveStep`、`findStepsByRunId`。
- [ ] 重跑同一测试，确认通过。

### 任务 2：建立研究计划、预算和计划校验

**文件：**
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionResearchPlan.java`
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionResearchPlanFactory.java`
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionPlanValidator.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionResearchPlanFactoryTest.java`

- [ ] 编写失败测试：股票计划生成 COMPANY/INDUSTRY/MACRO/MARKET/COUNTER 五轨；基金计划包含 FUND_EXPOSURE；每轨有最大查询数、成功条件和查询文本。
- [ ] 运行 `mvn -o -pl finscope-service -am -Dtest=AttributionResearchPlanFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败。
- [ ] 实现计划、轨道、查询与预算对象；默认总预算 8 次、单轨最多 2 次、总时长 90 秒。
- [ ] 实现 Validator：轨道/查询不能为空、查询总数不超预算、必须有 COUNTER、每个轨道有成功条件。
- [ ] 重跑测试，确认通过。

### 任务 3：标准化证据并实现质量门

**文件：**
- 修改：`backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionEvidence.java`
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionEvidenceNormalizer.java`
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionEvidenceGate.java`
- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionRepository.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionEvidenceGateTest.java`

- [ ] 编写失败测试：带不同 UTM 参数的同 URL 只保留一条；同标题无 URL 的重复证据被移除；单一 T3 证据不能形成 HIGH 置信度；T1 直接证据可达到 MID。
- [ ] 运行 `mvn -o -pl finscope-service -am -Dtest=AttributionEvidenceGateTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败。
- [ ] 扩展证据持久化字段：事件类型、立场、直接性、发布时间、事件键和历史上下文标记。
- [ ] 实现 URL 规范化、来源独立性、直接性和时效评分；实现 `capConfidence` 限制模型置信度。
- [ ] 重跑测试，确认通过。

### 任务 4：实现受控归因 Harness 与丰富兜底报告

**文件：**
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionHarness.java`
- 修改：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`
- 修改：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionService.java`
- 修改：`backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionDriver.java`
- 修改：`backend/finscope-domain/src/main/java/com/finscope/domain/attribution/AttributionReport.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionHarnessTest.java`

- [ ] 编写失败测试：Harness 创建计划与五个步骤；搜索失败只使对应轨道失败且报告为 PARTIAL；无 LLM 时根据四条证据生成至少四个驱动；高置信度驱动没有 URL 时被降级。
- [ ] 运行 `mvn -o -pl finscope-service -am -Dtest=AttributionHarnessTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败。
- [ ] 实现 Harness：计划校验、步骤落库、按轨道受限搜索、本地召回、证据门、报告合成和报告验证。
- [ ] 让 AttributionService 调用 Harness；保留旧 Agent 的公开接口作为兼容委托，逐步将检索/合成职责迁入 Harness。
- [ ] 扩展报告/驱动字段：主因、事实、传导链、反证、观察窗口和不确定性；将历史背景与当日证据分开处理。
- [ ] 重跑测试，确认通过。

### 任务 5：接入历史事件记忆与运行恢复

**文件：**
- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/attribution/AttributionRepository.java`
- 修改：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionHarness.java`
- 新建：`backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionStartupRecoveryService.java`
- 修改：`backend/finscope-web/src/main/java/com/finscope/web/FinScopeApplication.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionStartupRecoveryServiceTest.java`

- [ ] 编写失败测试：近期已完成同标的报告作为 BACKGROUND 返回；背景证据不会提高当日证据置信度；遗留 RUNNING 运行启动后被标为 FAILED 并写入终止原因。
- [ ] 运行 `mvn -o -pl finscope-service -am -Dtest=AttributionStartupRecoveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败。
- [ ] 实现近期历史报告/证据查询，最多注入四条背景；实现应用启动恢复服务，仅处理超过五分钟的 RUNNING 运行。
- [ ] 重跑测试，确认通过。

### 任务 6：暴露运行详情并丰富前端报告

**文件：**
- 修改：`backend/finscope-web/src/main/java/com/finscope/web/controller/AttributionController.java`
- 新建：`backend/finscope-web/src/main/java/com/finscope/web/response/AttributionResearchRunResponse.java`
- 修改：`frontend/src/shared/types/index.ts`
- 修改：`frontend/src/features/watchlist/AttributionReaderView.tsx`
- 修改：`frontend/src/styles.css`
- 测试：`frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [ ] 编写失败测试：完成报告展示“最可能主因”和至少一个“反证/不确定性”；运行详情 API 返回步骤状态，不暴露原始 Prompt。
- [ ] 运行 `npm test -- --run src/features/watchlist/AttributionReaderView.test.tsx`，确认失败。
- [ ] 新增 `GET /api/attribution/reports/{reportId}/run`；前端加载运行详情并显示轨道状态、主因、传导链、反证和观察窗口。
- [ ] 重跑 focused 测试、`npm test`、`npm run build`，确认通过。

### 任务 7：回归验证

**文件：**
- 修改：`docs/PRD-自选研究Agent-Harness.md`
- 修改：`docs/技术方案-自选研究Agent-Harness.md`

- [ ] 运行 `git diff --check`，确认没有空白错误。
- [ ] 运行离线 Maven 目标模块测试；若公司仓库依赖缺失，记录跳过原因并继续前端验证。
- [ ] 运行 `npm test && npm run build`。
- [ ] 将 PRD 与技术方案中的状态更新为“已实现”，并记录实际测试结果。
