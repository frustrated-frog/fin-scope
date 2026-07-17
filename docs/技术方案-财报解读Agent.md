# 技术方案：财报解读 Agent

> 文档状态：已实现；版本：V1.1；日期：2026-07-17；关联 PRD：`docs/产品需求-财报解读Agent.md`

## 一、目标与边界

本方案在现有公司财报分析工作台上增加受证据约束的财报解读能力。系统先生成不可变的结构化分析快照，再让单个解读 Agent 组织语言。模型既不读取数据库，也不重新计算财务指标。

本期技术边界：

- 仅支持 A 股非金融企业。
- 主要证据来自三张表、确定性指标、规则发现、同口径趋势和数据缺口。
- 复用现有 `LlmChatClient`、`AgentHarness`、`AgentTraceService`。
- 财报证据包、输出解析器、可信门禁和持久化模型独立实现，不依赖资金行为 Agent 的领域对象。
- 不把 PDF 文本、Strategy 命题和自由问答纳入本期运行链路。

本方案细化并取代《技术方案：公司财报分析工作台》中尚未落地的 `financial_research_snapshot` 与 `financial_interpretation` 草案；已经实现的报告、科目、指标、发现和文档表保持兼容。

## 二、关键设计决策

1. **单 Agent**：当前任务是受约束的综合解释，单 Agent 更容易统一口径、控制成本并执行门禁；暂不引入多 Agent 编排。
2. **快照优先**：生成前固化所有输入，模型结果必须绑定快照和输入指纹，保证可重放。
3. **引用由服务端分配**：模型只能选择证据 ID，不能创建证据。
4. **模型输出只是候选结果**：只有通过解析和门禁后才能成为成功解读。
5. **最多修复一次**：首次失败将精简后的校验错误反馈给模型；二次失败立即走确定性降级。
6. **异步生成**：创建接口快速返回任务记录，前端轮询状态，不让 HTTP 请求占用完整模型执行时间。
7. **旧成功不覆盖**：任务和结果采用追加式历史记录；新生成失败不影响上一份成功结果。
8. **PDF 非依赖**：没有 PDF 文本时仍可完整运行，避免把易变外部抓取引入核心闭环。

## 三、总体架构

```text
FinancialReport + 可比报告 + 三张表项目
             |
             v
FinancialMetricEngineV2
FinancialRuleEngineV2
FinancialTrendEngine
             |
             v
FinancialAnalysisSnapshotService
             |
             v
FinancialEvidencePacketAssembler
             |
             v
FinancialInterpretationFacade
             |
             v
AgentHarness -> FinancialInterpretationAgent -> LlmChatClient
                         |
                         v
        ResponseParser -> TrustGate -> Result/Fallback
                         |
                         v
 InterpretationRepository + AgentTraceService
                         |
                         v
                 REST API + React UI
```

### 3.1 分层职责

| 层 | 新增核心组件 | 职责 |
| --- | --- | --- |
| domain | `FinancialAnalysisSnapshot`、`FinancialInterpretation`、`FinancialEvidencePacket` | 不可变业务对象与枚举 |
| service | `FinancialMetricEngineV2`、`FinancialRuleEngineV2`、`FinancialTrendEngine` | 确定性计算、规则与趋势 |
| service | `FinancialAnalysisSnapshotService` | 组装、指纹计算、持久化快照 |
| service | `FinancialEvidencePacketAssembler` | 为快照分配稳定证据 ID |
| agent | `FinancialInterpretationAgent`、`FinancialInterpretationResponseParser`、`FinancialInterpretationGate` | 提示、解析与可信校验 |
| service | `FinancialInterpretationFacade`、`FinancialInterpretationFallbackBuilder` | 异步编排、幂等、修复、降级 |
| dao | `FinancialAnalysisSnapshotRepository`、`FinancialInterpretationRepository` | SQLite 持久化与查询 |
| web | `FinancialInterpretationController` | 创建、状态、历史与证据接口 |
| frontend | interpretation API、hooks、panel、evidence drawer | 生成状态和结果展示 |

## 四、确定性分析层

### 4.1 指标扩展

指标 ID 必须稳定，并记录值、单位、当前期、比较期、计算公式、输入科目引用和质量状态。

| 领域 | 指标 |
| --- | --- |
| 增长 | 营收同比、归母净利润同比、经营现金流同比 |
| 盈利 | 毛利率、净利率、期间费用率及同比变化 |
| 现金 | 经营现金流/净利润、资本开支、自由现金流 |
| 资产 | 应收增速差、存货增速差、合同负债变化 |
| 偿债 | 资产负债率、流动比率、速动比率、有息负债 |
| 一致性 | 资产恒等式差异、收入利润背离、利润现金背离 |

所有除法统一处理零分母、负分母和缺失值。无法可靠计算时返回 `UNAVAILABLE` 及原因，不返回伪造的 0。

自由现金流一期定义为：

```text
经营活动产生的现金流量净额 - 购建固定资产、无形资产和其他长期资产支付的现金
```

如资本开支科目缺失，指标不可用，不使用投资现金流净额替代。

### 4.2 规则发现

规则引擎只输出可解释规则，不承担最终叙事。建议规则包括：

- 营收增长而归母净利润下降。
- 归母净利润增长显著高于营收，但毛利率未改善。
- 经营现金流长期弱于净利润。
- 应收或存货增速显著快于收入。
- 毛利率改善但净利率恶化。
- 短期偿债能力连续下降。
- 有息负债上升同时自由现金流为负。
- 合同负债变化与收入趋势背离。
- 资产负债表恒等式超过容差。

每个发现包含严重度、事实描述、触发阈值、证据引用、反证条件与适用限制。阈值集中配置并纳入算法版本。

### 4.3 趋势语义

- 单季度趋势：最多 8 个单季度，必须先由累计数据可靠差分得到或来自可信单季源。
- 年度趋势：最多 5 个年度报告。
- 同一序列不得混合年度、累计季度和单季度。
- 资产负债表时点值可以按报告期末比较，但必须标注为时点数据。
- 若报告类型或口径不能可靠判定，则不生成该趋势。

## 五、不可变分析快照

### 5.1 快照内容

```json
{
  "report": {},
  "comparables": [],
  "metrics": [],
  "findings": [],
  "trends": [],
  "dataGaps": [],
  "quality": {},
  "algorithmVersion": "financial-analysis-v2"
}
```

快照中的数组按稳定键排序后进行规范化 JSON 序列化，并计算 SHA-256 `input_hash`。一旦持久化不允许修改；数据或算法变化必须生成新快照。

### 5.2 快照复用

如果报告 ID、源数据指纹和算法版本均未变化，复用现有快照。源数据指纹取规范化报告元数据和排序后的三张表科目计算结果，不依赖易变化的数据库行 ID 或更新时间。报告内容变化、人工修正或算法升级后生成新快照，并使旧解读显示为“基于旧快照”。

## 六、证据包

### 6.1 输入协议

证据包不暴露任意数据库字段，示例：

```json
{
  "report": {
    "stockCode": "600519",
    "reportType": "ANNUAL",
    "periodEnd": "2025-12-31",
    "scope": "CONSOLIDATED"
  },
  "qualityCeiling": "HIGH",
  "metrics": [
    {
      "id": "M_REVENUE_YOY",
      "label": "营业收入同比",
      "displayValue": "12.30%",
      "period": "2025-12-31",
      "sourceRefs": ["L_IS_REVENUE_2025", "L_IS_REVENUE_2024"]
    }
  ],
  "lineItems": [],
  "findings": [],
  "trends": [],
  "dataGaps": []
}
```

### 6.2 稳定引用规则

- 指标：`M_{METRIC_CODE}`。
- 科目：`L_{STATEMENT}_{ITEM_CODE}_{PERIOD_KEY}`。
- 发现：`F_{RULE_CODE}_{SEQUENCE}`。
- 趋势：`T_{METRIC_CODE}_{BASIS}`。
- 缺口：`G_{GAP_CODE}`。

证据包同时生成服务器端索引，门禁通过索引验证引用。证据 ID 不包含数据库自增 ID，避免环境迁移后失效。

## 七、Agent 输出协议

### 7.1 JSON Schema 形态

```json
{
  "operatingState": "IMPROVING | STABLE | UNDER_PRESSURE | INSUFFICIENT_EVIDENCE",
  "confidence": "HIGH | MEDIUM | LOW",
  "executiveSummary": [
    {
      "claim": "...",
      "claimType": "FACT | INFERENCE | WATCHPOINT",
      "refs": ["M_REVENUE_YOY"]
    }
  ],
  "periodChanges": [],
  "crossStatementInsights": [],
  "dimensions": [
    {
      "code": "GROWTH",
      "assessment": "POSITIVE | NEUTRAL | NEGATIVE | INSUFFICIENT_EVIDENCE",
      "summary": "...",
      "refs": [],
      "details": []
    }
  ],
  "positiveSignals": [],
  "risks": [],
  "turningPoints": [],
  "watchpoints": [],
  "limitations": [],
  "disclaimer": "..."
}
```

六个维度代码固定为：`GROWTH`、`PROFITABILITY`、`EARNINGS_QUALITY`、`CASH_QUALITY`、`ASSET_QUALITY`、`SOLVENCY_CAPITAL_DISCIPLINE`。

### 7.2 提示词约束

系统提示明确要求：

- 只能使用证据包。
- 不重新计算或外推精确数字。
- 事实与推断分开。
- 重要结论同时考虑反证。
- 数据不足时选择证据不足。
- 仅返回符合 Schema 的 JSON。
- 不给出投资建议、目标价或收益预测。

提示词版本写入解读记录，并与输入指纹共同参与生成键计算。

## 八、可信门禁

校验顺序：

1. JSON 可解析。
2. Schema、枚举、数量和长度合法。
3. 六维结果完整且不重复。
4. 必须引用的条目均有引用。
5. 所有引用存在于当前证据索引。
6. 精确数字通过白名单。
7. 置信度不超过 `qualityCeiling`。
8. 不含投资动作、目标价、收益承诺等禁止表达。

### 8.1 数字白名单

证据包在生成时提取所有允许出现的：

- 原始数值及统一格式化文本。
- 百分比和百分点展示值。
- 报告日期、年度与明确的时间跨度。

门禁从自然语言字段中提取数字 token 并规范化比较。章节序号、证据 ID 和允许的模糊数量词不参与比较。首期宁可拒绝不确定输出，也不放宽到模型自行运算。

### 8.2 修复与降级

```text
主请求（使用模型客户端统一超时）
  -> 解析和门禁成功：保存 SUCCESS
  -> 失败：带精简错误和精简证据进行一次修复请求
      -> 成功：保存 SUCCESS，并记录 repaired=true
      -> 失败：保存 FALLBACK，使用确定性模板结果
```

降级结果由已有指标、规则和数据缺口生成，同样使用统一输出模型与证据引用，但明确标记 `generation_mode=DETERMINISTIC_FALLBACK`。

## 九、持久化设计

财报模块当前占用自定义 SQLite `schema_migration` 版本 300、301。本期使用全局唯一版本 **302**，避免不同模块共享迁移表时发生版本冲突。

### 9.1 `financial_analysis_snapshot`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 快照 ID |
| report_id | INTEGER | 主报告 |
| algorithm_version | TEXT | 指标与规则版本 |
| source_hash | TEXT | 规范化报告元数据和科目 SHA-256 |
| input_hash | TEXT | 规范化输入 SHA-256 |
| payload_json | TEXT | 完整快照 |
| quality_level | TEXT | 质量上限 |
| created_at | TEXT | 创建时间 |

唯一约束：`(report_id, algorithm_version, input_hash)`；`source_hash` 单独保留用于判断旧快照和排查来源变化。

### 9.2 `financial_interpretation`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 解读/任务 ID |
| report_id | INTEGER | 主报告 |
| snapshot_id | INTEGER | 输入快照 |
| generation_key | TEXT | 快照、提示词和配置摘要 |
| prompt_version | TEXT | 提示词版本 |
| model_name | TEXT | 实际模型 |
| status | TEXT | QUEUED/RUNNING/VALIDATING/SUCCESS/FALLBACK/FAILED |
| generation_mode | TEXT | LLM/REPAIRED/DETERMINISTIC_FALLBACK |
| result_json | TEXT | 合规结果 |
| validation_errors_json | TEXT | 门禁错误摘要 |
| duration_ms | INTEGER | 总耗时 |
| failure_code/message | TEXT | 失败信息 |
| created_at/started_at/completed_at | TEXT | 生命周期时间 |

索引：

- `(report_id, id DESC)`：历史列表。
- `(snapshot_id, status, id DESC)`：当前快照最新结果。
- `(generation_key, status, id DESC)`：幂等查询。

不对 `generation_key` 建唯一约束，因为显式“重新生成”需要保留多个版本。普通生成请求在服务层复用同键的进行中任务或最新成功结果。

Agent Trace 不通过外键列关联。复用现有通用主题机制，写入 `subject_type=FINANCIAL_INTERPRETATION`、`subject_id=financial_interpretation.id`；主请求和修复请求都以该主题记录为不同尝试。

## 十、接口设计

### 10.1 创建解读

`POST /api/financials/reports/{reportId}/interpretations`

请求：

```json
{ "force": false }
```

返回 `202 Accepted`：

```json
{
  "interpretationId": 123,
  "status": "QUEUED",
  "reused": false
}
```

- `force=false`：复用相同生成键的进行中任务或最新成功结果。
- `force=true`：创建新历史版本，但仍拒绝同一报告同时存在多个运行任务。

### 10.2 查询接口

- `GET /api/financials/reports/{reportId}/interpretations/latest`
- `GET /api/financials/reports/{reportId}/interpretations?limit=20`
- `GET /api/financials/interpretations/{id}`
- `GET /api/financials/interpretations/{id}/evidence`

详情接口返回解读状态、结果、快照是否过旧、模型元信息和可读错误。证据接口按结果所用引用返回最小证据集合，不默认传输整个快照。

## 十一、执行与并发

- 使用现有受控异步执行设施；若项目无统一任务执行器，则为财报解读配置有界线程池和队列。
- 通过事务内状态检查和单进程键锁避免重复运行；数据库查询作为最终幂等保障。
- 应用启动时将长时间停留在 `QUEUED/RUNNING/VALIDATING` 的记录标记为 `FAILED`，错误码为 `INTERRUPTED`，允许重新生成。
- 状态更新和结果写入使用短事务，不在数据库事务中等待模型响应。
- Agent Trace 从主请求开始记录；主请求与修复请求使用相同的财报解读主题，并以不同 attempt 保存。

## 十二、前端设计

新增：

- `financialInterpretationApi`：创建、轮询、历史与证据请求。
- `useFinancialInterpretation`：聚合最新结果、运行状态和快照过旧状态。
- `FinancialInterpretationPanel`：摘要、六维分析、信号、风险、拐点和观察项。
- `FinancialEvidenceDrawer`：按引用类型展示指标、科目、趋势、发现和缺口。
- `FinancialInterpretationHistory`：切换历史版本并显示生成方式和时间。

前端不解析模型原始文本，只渲染服务端通过门禁后的类型化 DTO。轮询只在任务处于非终态时开启，采用 2 秒起步、最大 5 秒的退避，并在页面卸载时取消。

## 十三、错误码

| 错误码 | 含义 | 客户端行为 |
| --- | --- | --- |
| FINANCIAL_REPORT_NOT_FOUND | 报告不存在 | 返回报告选择页 |
| FINANCIAL_DATA_INSUFFICIENT | 无法形成最小快照 | 展示缺口和刷新入口 |
| INTERPRETATION_ALREADY_RUNNING | 已有运行任务 | 跳转到现有任务 |
| INTERPRETATION_LLM_UNAVAILABLE | 模型不可用 | 展示降级结果 |
| INTERPRETATION_VALIDATION_FAILED | 两次输出均不合规 | 展示降级结果和说明 |
| INTERPRETATION_INTERRUPTED | 服务重启中断 | 允许重试，保留旧成功结果 |
| INTERPRETATION_INTERNAL_ERROR | 未知内部错误 | 保留旧结果并提供重试 |

## 十四、安全与隐私

- 模型输入只包含分析所需财务字段，不包含数据库连接信息、用户凭据或上传文件路径。
- 上传 PDF 原文不进入本期提示词。
- 日志默认不记录完整证据包和模型原文；Trace 中敏感载荷按现有策略截断。
- 输出在服务端完成禁止表达检查，前端不提供绕过门禁查看“原始回答”的入口。

## 十五、测试策略

### 15.1 后端单元测试

- 指标公式、零分母、负数、缺失科目和单位归一化。
- 年度、累计和单季度趋势隔离。
- 快照排序、指纹稳定性和源数据变更检测。
- 证据 ID 稳定性与引用索引。
- Parser 对 Markdown 包裹、非法 JSON、未知字段和长度越界的处理。
- Gate 对非法引用、凭空数字、过高置信度和投资建议的拒绝。
- 一次修复和确定性降级。

### 15.2 后端集成与契约测试

- 自定义 SQLite 迁移 302 可从空库和现有库升级，并能安全重复执行。
- 创建、轮询、历史、强制重生成和证据接口。
- 并发点击只产生一个运行任务。
- 服务重启后的中断任务恢复。
- LLM 超时、不可用、非法输出时的完整链路。
- `AgentTrace` 与解读记录关联。

### 15.3 前端测试

- 空态、运行态、成功、降级、失败和旧快照提示。
- 六维卡片与证据标签展示。
- 证据抽屉按引用类型正确渲染。
- 历史版本切换不会误覆盖最新状态。
- 轮询终止、取消和错误重试。
- TypeScript 类型检查与生产构建。

## 十六、实施分段

1. 指标、规则、趋势与快照模型。
2. 快照仓储、迁移和证据包。
3. 输出协议、Parser、Gate 与确定性降级。
4. Agent、Facade、异步状态机和 Trace。
5. REST API 与契约测试。
6. 前端解读区域、证据抽屉与历史。
7. 全量回归、真实样本走查和文档更新。

每段遵循测试先行，先锁定行为再实现。完成后用至少一个数据完整样本和一个缺失比较期样本验证可信降级。
