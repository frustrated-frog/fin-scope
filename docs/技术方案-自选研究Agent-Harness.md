# 技术方案：自选研究 Agent Harness

## 1. 架构原则

采用“粗粒度 Plan-and-Execute + 轨道内受限查询调整 + 代码验证门”。控制面只在 `finscope-service`，外部检索在 `finscope-rpc`，持久化在 `finscope-dao`，Web 层仅转换 DTO 与推送状态。

```text
AttributionController
  -> AttributionService（创建运行、恢复、SSE）
  -> AttributionHarness（计划校验、调度、预算、节点状态）
  -> ResearchTrackExecutor（公司/行业/宏观/市场/反证）
  -> WebSearchClient + 本地新闻查询
  -> EvidenceNormalizer / EvidenceScorer / CausalReportGenerator
  -> AttributionRepository
```

Harness 不直接访问 HTTP、SQL 或 Controller；模型输出不直接写库。

## 2. 领域模型

### 2.1 AttributionResearchRun

一次归因的运行控制面，与 `AttributionReport` 一对一关联。字段包括：`reportId`、`planJson`、`status`、`budgetJson`、`currentStep`、`terminationReason`、`createdAt`、`updatedAt`。

状态为：`PENDING`、`RUNNING`、`COMPLETED`、`PARTIAL`、`FAILED`、`CANCELLED`。

### 2.2 AttributionResearchStep

运行的可恢复步骤。字段包括：`runId`、`stepId`、`track`、`status`、`inputSummary`、`outputSummary`、`attempt`、`maxAttempts`、`startedAt`、`endedAt`、`errorMessage`。

步骤状态为：`PENDING`、`RUNNING`、`COMPLETED`、`SKIPPED`、`FAILED`。

### 2.3 AttributionEvidence 扩展

增加：`eventType`、`stance`、`directness`、`publishedAt`、`eventKey`、`isHistoricalContext`。其中 `stance` 为 `SUPPORT`、`COUNTER`、`BACKGROUND`，`directness` 为 `DIRECT`、`INDIRECT`、`BACKGROUND`。

### 2.4 AttributionDriver 扩展

增加：`facts`、`transmissionPath`、`counterEvidence`、`observationWindow`、`evidenceUrls`。`confidence` 由代码计算的证据质量上限约束，模型不能自行越级。

## 3. 运行合同

每次运行最多五条轨道，每轨最多两次 Tavily 查询，总查询预算默认八次；每次查询最多四条结果。满足以下任一条件即停止补查：

1. 已有四个以上有效驱动且每个轨道都有证据或明确无证据结论。
2. 查询预算耗尽。
3. 运行时长超过 90 秒。
4. 连续两次查询没有新增规范化证据。

搜索失败只影响对应轨道；全轨失败时运行标记 `PARTIAL`，报告继续使用本地新闻和行情背景生成。

## 4. 执行步骤

1. `market-diagnosis`：校验标的、行情、异动方向，写入概览。
2. `research-plan`：依据类型创建固定轨道和查询计划，执行计划校验。
3. `parallel-research`：按并发上限执行独立轨道搜索，并写入步骤状态。
4. `local-recall`：按实体、别名和近期窗口检索本地文章。
5. `evidence-normalize`：URL 归一化、事件键提取、来源分类、立场识别、去重。
6. `evidence-gate`：计算时效、来源、直接性、独立性；决定是否补查或降级。
7. `causal-synthesis`：仅将经过门控的证据注入模型，生成丰富驱动。
8. `report-verify`：校验驱动数量、证据引用、置信度上限、反证和免责声明。

## 5. 记忆策略

不引入向量库。使用 SQLite 的报告、驱动与证据作为事实源：

- 工作记忆：`AttributionResearchRun` 与步骤表，用于恢复本次运行。
- 事件记忆：近期已完成报告的驱动和证据，用于同标的背景。
- 领域记忆：现有 `Instrument` 的别名、板块、产业链标签。

历史内容被标记为 `BACKGROUND`，仅用于生成“与历史相比”的描述，不能提高当日结论置信度。

## 6. Prompt 与验证

模型输入分为行情概览、当日证据、历史背景、输出 JSON 契约。最多注入 16 条当日证据和 4 条历史背景，按评分排序。

报告 JSON 必须满足：

```json
{
  "summary": "一句话结论",
  "primaryDriver": {"claim":"", "facts":[""], "transmissionPath":"", "evidenceUrls":[""], "confidence":"MID"},
  "drivers": [],
  "uncertainties": [""],
  "observationWindow": [""]
}
```

验证器拒绝空事实、找不到引用 URL 的高置信度、超过六个驱动、以及未声明不确定性的报告；拒绝后走确定性丰富兜底报告。

## 7. 可观测性与恢复

每个步骤写入运行表和 `agent_run`。SSE 仅展示阶段、轨道和摘要；报告详情可查询计划、步骤和预算。进程启动时将长期处于 `RUNNING` 的运行标为 `FAILED` 并写明“服务重启前未完成”，避免伪完成。

## 8. 测试策略

测试覆盖：计划生成与 lint、预算停止、证据去重、来源独立性、置信度上限、历史背景隔离、报告验证与丰富兜底、运行恢复、SSE 状态展示。外部 Tavily 使用 fake client，不依赖网络额度。
