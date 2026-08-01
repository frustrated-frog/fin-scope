# Agent 原生投研方法体系设计

## 目标

FinScope 通过研究优秀开源 Skill 和投研项目，吸收其中的数据获取方式、财报分析框架、公司判断框架、产业链分析方法、事件研究方法和证据校验思想，再将这些思想实现为 FinScope 自己可执行、可测试、可追溯的 Agent 能力。

外部 Skill 是开发阶段的参考资料，不是 FinScope 的运行时依赖。生产运行时不下载、不加载、不调用外部 Skill，也不让外部 Prompt 直接控制系统工具。

用户只需要提出研究问题。Agent 负责识别研究意图、选择和组合投研方法、调用数据工具、检查证据缺口、重新规划并生成结论。用户不需要先选择研究模板。

## 核心边界

1. **Agent-first**：研究入口始终是 Agent，不把方法选择责任交给用户。
2. **借鉴思想，不接入运行时**：外部 Skill 只用于提炼分析步骤、指标、数据源和校验规则。
3. **方法不是纯 Prompt**：每种方法由 Agent 指导、确定性工具和证据门禁共同组成。
4. **事实与判断分离**：Java 服务负责数据、计算、规则和证据约束；模型负责规划、解释、追问和综合。
5. **方法必须可评测**：每种方法都有输入条件、必查问题、证据要求、失败状态和验收样例。
6. **不输出交易指令**：产出研究判断、风险、观察指标和失效条件，不给出买卖建议、目标价或收益承诺。

## 方案选择

### 方案一：只扩展 Agent Prompt

把开源 Skill 中的分析方法直接复制或改写进现有 Prompt。实现成本最低，但方法不可独立测试，容易随 Prompt 变化发生遗漏，也无法可靠判断是否执行了关键计算和反证检查。

### 方案二：每种方法编写固定工作流

把财报、公司、产业链等分析分别写成固定步骤。结果稳定，但对问题差异适应不足，容易产生大量相似而孤立的工作流。

### 方案三：Agent 编排方法能力与确定性工具

Agent 根据研究问题自动选择多个方法能力；方法能力声明问题、数据需求、工具、反证和完成条件；Java 工具执行数据获取和计算；证据门禁限制结论与置信度。

本设计采用方案三。它既保留 Agent 的自主规划能力，又让关键数据、计算和结论约束保持确定性。

## Agent 归属

### 主 Agent：通用深度研究 Agent

投研方法的选择与组合应进入现有通用研究链路：

- `ResearchPlanningAgent`：识别研究意图，选择方法并生成研究蓝图。
- `ResearchAgentLoopService`：根据最新证据缺口决定下一动作、调用工具或重新规划。
- `ResearchFinishVerifier`：检查方法完成条件和证据门槛。
- `ResearchReportSynthesisAgent`：仅使用通过门禁的证据和方法结果生成报告。

通用深度研究 Agent 是方法体系唯一的总编排入口。它适合承载“分析一家公司”“判断最新财报是否改善”“研究一个产业链”“解释一个事件的影响”等开放式研究问题。

### 领域 Agent：作为专业执行器

现有领域 Agent 不承担全局方法选择，而作为通用研究 Agent 可调用的专业能力：

| 领域 Agent | 适合执行的方法 | 在体系中的职责 |
| --- | --- | --- |
| `FinancialInterpretationAgent` | 财报质量、盈利质量、现金流验证、资产质量、偿债与资本纪律 | 基于财报证据包完成结构化财务解读 |
| `CapitalInterpretationAgent` | 资金行为、量价关系、流向连续性、反方资金信号 | 基于资金因子和指标生成受门禁约束的解释 |
| `AttributionAgent` | 标的异动归因、产业链传导、支持与替代解释 | 搜索和整理事件归因证据 |
| `RadarEvidencePlanAgent`、`RadarEvidenceSynthesisAgent` | 近期事件发现和浅层验证 | 负责触发和初查，不承担完整公司研究 |
| `InvestmentRecognitionAgentService` | 将已验证变化转化为待跟踪命题 | 消费研究结果，形成验证指标和失效条件 |

专业 Agent 可以独立服务各自页面，但从“深度研究”入口运行时，应通过类型化 Research Tool 被通用 Research Agent 编排。

## 总体架构

```text
用户研究问题
  -> ResearchPlanningAgent
       -> 识别研究对象与研究意图
       -> 从 ResearchMethodRegistry 选择并组合方法
       -> 生成 ResearchBlueprint
  -> ResearchAgentLoopService
       -> 根据蓝图与证据缺口选择下一动作
       -> 调用类型化数据工具、计算工具或领域 Agent
       -> 写入 Observation 和方法执行状态
       -> 证据不足时补查或重新规划
  -> ResearchFinishVerifier
       -> 校验方法完成条件、来源、正反证据与数据完整性
  -> ResearchReportSynthesisAgent
       -> 生成事实、判断、风险、观察指标和局限性
  -> Vault / Research Run / Investment Recognition
       -> 保存研究过程、方法使用记录与可持续验证命题
```

## 核心模型

### ResearchMethod

每种方法是 Agent 可发现的类型化能力，而不是一段孤立 Prompt：

```java
public interface ResearchMethod {
    String code();

    boolean supports(ResearchContext context);

    MethodPlan plan(ResearchContext context);

    MethodAssessment assess(
            ResearchContext context,
            List<ResearchEvidence> evidence,
            List<MethodObservation> observations);
}
```

每个方法声明：

- 适用对象和研究意图；
- 必须回答的子问题；
- 所需数据及来源等级；
- 可调用的工具；
- 必须执行的确定性计算；
- 支持证据与反方证据要求；
- 数据不足和证据冲突时的处理；
- 完成条件、置信度上限和输出契约。

### ResearchMethodRegistry

注册项目原生方法，并向 `ResearchPlanningAgent` 暴露精简描述。规划 Agent 只能选择已注册的方法编码，服务端必须校验：

- 方法是否支持当前对象；
- 方法要求的工具是否已注册；
- 动作数量是否在预算内；
- 是否包含必要反证步骤；
- 方法间依赖是否无环；
- 是否存在重复或互相冲突的任务。

### ResearchBlueprint

规划结果在现有 Research Mission 基础上增加方法维度：

```json
{
  "researchType": "COMPANY_FINANCIAL",
  "methodCodes": [
    "FINANCIAL_STATEMENT_QUALITY",
    "CASH_FLOW_VERIFICATION",
    "DUPONT_ANALYSIS",
    "PEER_COMPARISON"
  ],
  "questions": [],
  "requiredEvidence": [],
  "requiredCalculations": [],
  "counterChecks": [],
  "completionCriteria": []
}
```

蓝图必须持久化在 Research Run 中，以便解释 Agent 为什么选择这些方法，以及后续是否完整执行。

### MethodObservation

领域 Agent 和确定性工具统一返回方法观察，而不是直接返回最终投资结论：

- `methodCode`：产生观察的方法；
- `observationType`：事实、计算、推断、风险、反证或数据缺口；
- `statement`：受长度限制的结构化陈述；
- `evidenceRefs`：原始证据引用；
- `calculationRefs`：计算过程引用；
- `timeRange`：观察窗口；
- `confidenceCap`：由数据和规则决定的置信度上限；
- `limitations`：当前观察的限制。

## 首批投研方法

### 财报质量分析

必须覆盖：

- 收入、利润和扣非利润的增长质量；
- 毛利率、费用率和净利率趋势；
- 经营现金流与净利润匹配；
- 应收、存货和合同负债与收入变化；
- 非经常性损益、资产减值和审计意见；
- 杜邦拆解和资本结构；
- 同比、环比、连续趋势及同行比较。

确定性规则示例：

- 缺少现金流量表时，盈利质量不得给出高置信度；
- 经营现金流恶化而利润增长时，必须生成反方观察；
- 非经常性损益占比较高时，下调盈利质量判断；
- 只有单期数据时，不得声称趋势已经形成；
- 计算值必须由服务端生成，模型不得自行创造财务比率。

### 公司质量判断

必须覆盖：

- 商业模式和收入来源；
- 客户、供应商和产品集中度；
- 竞争壁垒、替代风险和行业位置；
- 盈利能力、成长持续性和周期性；
- 管理层治理与资本配置；
- 经营杠杆、财务风险和潜在失效条件；
- 竞争对手及同行对照。

公司质量方法不能只消费模型摘要。关键判断必须引用财务数据、公告、监管披露、公司 IR 或其他可追溯证据。

### 后续方法

- `INDUSTRY_CHAIN_ANALYSIS`：价值链、利润分配、供需瓶颈、价格传导和议价权。
- `EVENT_DRIVEN_ANALYSIS`：事件确认、影响对象、传导路径、预期差、持续时间和证伪条件。
- `CAPITAL_BEHAVIOR_ANALYSIS`：资金流向、量价关系、连续性、扩散度和多来源印证。
- `VALUATION_SCENARIO_ANALYSIS`：估值口径、假设、情景敏感性和安全边界，不生成目标价承诺。

## 数据与工具接入

方法体系通过现有 `ResearchAgentToolRegistry` 使用类型化工具，不直接在 Prompt 中请求任意网络或数据库操作。建议逐步增加：

- `financial_quality_assess`：组装财报证据包并调用财报领域 Agent；
- `company_quality_assess`：收集公司、治理、竞争和资本配置证据；
- `capital_behavior_assess`：组装资金因子并调用资金领域 Agent；
- `event_attribution_research`：调用归因研究能力；
- `industry_chain_map`：生成带来源的产业链实体与传导关系；
- `peer_comparison_calculate`：确定性计算同行指标；
- `method_evidence_assess`：按方法完成条件评估证据缺口。

外部数据访问继续遵守 `web -> service -> dao/rpc` 依赖方向，所有外部 API 通过 `finscope-rpc` 实现。方法层只声明数据需求，不直接实现 HTTP 调用。

## 运行数据流示例

用户问题：“宁德时代最新财报是否说明盈利质量改善？”

1. `ResearchPlanningAgent` 识别为公司财报研究。
2. Agent 选择财报质量、现金流验证、杜邦分析和同行比较。
3. 蓝图要求最近多个报告期财务数据、现金流量表、附注、审计意见及同行指标。
4. 财报工具执行服务端计算，`FinancialInterpretationAgent` 生成结构化观察。
5. Agent 发现缺少同行和反方证据，调用专业资料搜索与同行比较工具。
6. 方法评估器检查现金流、非经常性损益、应收存货和同行覆盖。
7. 未达到完成条件时继续补查；达到预算上限则生成带局限声明的低置信度报告。
8. 报告保存方法选择、事实、判断、风险、观察指标、失效条件和全部证据引用。

## 失败与降级

- 模型规划失败：使用确定性规划器，根据对象类型和问题关键词选择最小方法集合。
- 方法不支持对象：服务端拒绝该方法并要求 Agent 重新规划。
- 领域数据缺失：保留已有观察，记录 `DATA_GAP`，限制置信度，不伪造完整分析。
- 外部来源部分失败：保留已经取得的证据，并在方法评估中记录来源缺口。
- 领域 Agent 输出无效：使用确定性计算和规则摘要降级，不阻断 Research Run。
- 方法执行无新增证据：触发现有无进展保护，禁止重复调用相同动作。
- 正反证据冲突：必须在报告中保留冲突，不允许用模型文本消除冲突。
- 达到预算仍不充分：生成局限明确的部分报告，不把“没有证据”写成否定结论。

## 学习记录闭环

每次运行除保存报告外，还应保存：

- Agent 选择的方法及理由；
- 每种方法提出的问题和完成状态；
- 使用的数据源、工具、计算和证据；
- 未完成步骤与数据缺口；
- 支持判断、反方判断和失效条件；
- 后续需要跟踪的指标和时间窗口；
- 后续事实对历史判断的验证或证伪结果。

这些记录用于评估方法本身，而不是只评估模型文本。后续可以统计方法问题覆盖率、证据支持率、反证覆盖率、数据源可靠性和历史判断验证率。

## 测试与评测

### 单元测试

- 方法支持条件和方法选择校验；
- 财务与估值计算正确性；
- 数据不足时的置信度上限；
- 必须反证条件是否触发；
- 未注册方法和未注册工具是否被拒绝；
- 方法完成条件是否正确计算。

### 集成测试

- 从研究问题到蓝图、工具调用、方法评估和报告生成的完整链路；
- 领域 Agent 失败时的确定性降级；
- 部分数据源失败后已有证据仍被保留；
- Agent 根据新证据缺口重新规划；
- 报告中的实质判断均引用有效证据或计算结果。

### 冻结案例评测

使用历史财报和公开材料建立不访问实时网络的案例集，检查：

- 必查问题覆盖率；
- 财务计算正确率；
- 关键风险和反证召回率；
- 原始来源和一手证据比例；
- Claim 证据支持率；
- 不同模型或 Prompt 版本下的方法执行稳定性。

## 分阶段实施

### 第一阶段落地状态（2026-08-02）

第一阶段已经实现以下受测试保护的能力：

- 通用深度研究 Agent 可从白名单注册表自动选择 `FINANCIAL_STATEMENT_QUALITY` 和 `COMPANY_QUALITY`；
- 模型只能选择方法编码，证据、计算、反证和完成条件由服务端注册表重新生成；
- 模型不可用或计划无效时，确定性规划器根据研究对象和问题自动选择方法；
- 方法蓝图随 Research Mission 持久化，旧 SQLite 数据库启动时自动增加兼容字段；
- 规划 Agent 能看到方法的必查问题、证据、计算、反证、完成条件与必需意图，缺少必需意图的计划会被服务端拒绝；
- 确定性规划器会把财报质量与公司质量要求写入一手披露、专业资料、交叉核对和反方检索任务；
- 每个 Agent 工具决策必须携带服务端校验过的 `missionTaskKey`，工具、意图、查询参数和依赖必须与该任务完全一致，Observation 只回写这一条 Mission Task；
- 每轮外部取证后都会重新评估证据缺口；达到证据门槛时，剩余检索任务按 `SUFFICIENT_EVIDENCE` 跳过并自动完成评估任务，避免 Agent 提前结束后遗留悬空任务；
- Agent 决策上下文包含方法蓝图，方法所需意图未完成时 Finish Verifier 拒绝提前结束；只有因证据充分而跳过的任务可以满足方法门禁；
- 财报质量方法和公司质量方法已有方法选择、严格校验、持久化、精确任务回写及完成门禁测试。

本阶段尚未把 `FinancialInterpretationAgent`、`CapitalInterpretationAgent` 和 `AttributionAgent` 注册成通用 Research Tool，也尚未增加产业链、事件驱动、资金行为和估值方法；这些仍属于第二、三阶段范围。

### 第一阶段：方法编排骨架

- 建立 `ResearchMethod`、`ResearchMethodRegistry`、`ResearchBlueprint` 和方法执行状态。
- 扩展 `ResearchPlanningAgent`，让 Agent 自动选择已注册方法。
- 扩展 Research Mission 和 Finish Verifier，使完成条件包含方法完成度。
- 接入财报质量分析和公司质量判断两个方法。

### 第二阶段：领域 Agent 工具化

- 将财报、资金、归因能力注册为 Research Tool。
- 补充同行比较、产业链映射和公司质量证据工具。
- 统一领域 Agent 输出为 `MethodObservation`。

### 第三阶段：方法评测与长期学习

- 建立冻结历史案例和方法质量指标。
- 保存方法版本、选择理由和完成情况。
- 将研究结论的验证指标与 Radar、投资认识和后续研究关联。
- 根据评测结果持续吸收新的开源投研方法，而不是累积不可控 Prompt。

## 验收标准

- 用户无需手工选择方法，Agent 能根据研究问题生成合法的方法组合。
- 外部 Skill 不出现在运行时依赖、工具调用或生产配置中。
- 财报和公司研究至少各有一个可独立测试的方法实现。
- 方法中声明的确定性计算由服务端完成，模型不能创造计算结果。
- 每项实质判断可追溯到证据或计算引用。
- 缺少关键数据、反方证据或一手来源时，系统能识别缺口并限制置信度。
- 专业领域 Agent 由通用研究 Agent 编排，同时保持各自页面的独立使用能力。
- Research Run 能展示 Agent 选择了什么方法、为什么选择、执行到哪一步以及哪些条件尚未满足。
