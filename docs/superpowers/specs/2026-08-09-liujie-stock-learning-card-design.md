# 刘杰框架股票学习卡设计

## 1. 目标与边界

为个人学习场景新增“刘杰框架股票学习卡”。用户只选择一只股票并点击生成；系统随后通过受控 Research Runtime 收集公开材料、补足支持与反方证据，并自动生成可追溯的结构化学习卡。用户不需要填写投资逻辑、买入条件、估值或复盘内容。

学习卡是研究材料，不是投资建议，也不进入 `strategy_holding`、不改变真实持仓、不输出买卖或调仓指令。它与现有 `strategy_stock_thesis` 分离，避免 Agent 结论被误解为用户已经采纳的投资决策。

第一版只支持用户主动选择的一只 A 股股票。每次生成均显式发起，不扫描整张自选表、不定时批量调用模型。自动更新、真实仓位同步、目标价、组合优化和交易执行都不在本期范围内。

## 2. 固定投研框架

框架代码为 `LIUJIE_BUYSIDE_RESEARCH_V1`，来源为用户提供的《重新认识基金经理｜持仓人生》转录稿。框架由服务端固定，不将整篇播客稿交给模型临时解释。

每次研究必须覆盖以下六个维度：

| 维度代码 | 学习问题 | 完成要求 |
| --- | --- | --- |
| `SPACE` | 行业与公司可达空间多大？ | 给出可验证的市场、产品、客户或产能线索；无可靠材料时标记未知。 |
| `PROFIT_MODEL` | 公司靠什么赚钱，利润和现金流能否持续？ | 使用经营、财报或公告材料解释收入与利润来源，并给出利润质量反方检查。 |
| `COMPETITION` | 为什么公司能赚钱、竞争优势能否持续？ | 至少说明竞争位置、替代或对手风险中的一项，并给出反方证据或缺口。 |
| `GOVERNANCE` | 管理层、治理与资本配置是否值得持续观察？ | 仅依据公开治理、公告、股权或资本配置材料；不能由模型补造管理层事实。 |
| `VALUATION` | 当前市场可能在交易什么预期，哪些变量还需验证？ | 说明价格判断所依赖的公开材料、未知项和观察变量；材料不足时拒绝给出精确价值区间。 |
| `COUNTER_CASE` | 市场为什么可能不选择它，什么会推翻当前认识？ | 汇总最强反方解释、关键风险与需要补证的事项。 |

系统可以生成“值得继续学习”“证据不足”“关键风险待验证”等研究状态；不得生成买入、卖出、加仓、减仓、目标价或收益承诺。

## 3. 用户流程

```text
Strategy / 股票学习卡
  -> 选择一只 A 股股票
  -> 点击“按刘杰框架生成”
  -> 创建学习卡运行并进入 Research Runtime
  -> 受控计划收集公告、财报、研报、新闻与反方材料
  -> 结构化合成 Agent 生成六维学习卡
  -> 证据与字段校验通过后持久化为最新版本
  -> 用户阅读结论、证据、未知项与下一观察点
```

用户在整个流程中不填写研究内容，也不需要确认 Agent 草稿。页面仅提供“重新生成”与“查看历史版本”操作。重新生成创建新的不可变学习卡运行，旧运行仍可查看。

## 4. Agent 编排与安全边界

新增 `StockLearningCardService`，它是面向学习卡的编排层，不允许 Controller 直接调用 Repository。

1. 服务验证股票代码并通过现有 `StrategyInstrumentResolver` 解析 `STOCK` 类型标的。
2. 服务按固定框架构造研究问题和 `ResearchPlanningInput`，强制选择 `COMPANY_QUALITY`；当问题涉及财务材料时同时选择 `FINANCIAL_STATEMENT_QUALITY`。
3. 现有 Research Runtime 继续负责任务 DAG、工具白名单、证据缺口、反方检索、运行恢复和 Agent 决策轨迹。学习卡 Agent 不拥有 SQL、Shell、任意 HTTP 或自定义工具权限。
4. 研究运行结束后，`StockLearningCardSynthesisAgent` 仅使用本次运行已持久化的证据清单和报告上下文生成严格 JSON。每个维度必须给出结论、证据引用、反方引用、未知项和置信度。
5. `StockLearningCardDraftValidator` 验证所有证据引用都属于本次 Research Run，维度完整且不重复，置信度仅为 `LOW`、`MEDIUM`、`HIGH`，并拒绝交易指令、目标价和无证据事实。
6. 模型不可用、输出格式非法或引用不合法时，运行标记为降级；服务按证据覆盖率生成保守卡片，所有未覆盖维度为“证据不足”，并记录降级原因。研究流程不因模型失败而丢失材料。
7. Agent 输入、原始输出摘要、模型状态、耗时、降级原因与输入指纹写入既有 `agent_run`，节点名使用 `stock-learning-card-synthesis`。

现有 `InvestmentRecognitionAgentService` 不能直接承担本功能：它以短期价格变化为触发、输入仅有行情快照，且明确不消费文章正文或研究证据。学习卡必须建立在独立的公司研究运行之上。

## 5. 持久化模型

所有表由 `DatabaseInitializer` 以幂等迁移创建，并遵循现有 SQLite、外键、时间文本和 Repository 模式。

### 5.1 `stock_learning_card`

每只股票一行，保存学习对象的稳定身份与最新状态：

- `id`、`instrument_id`（唯一外键）、`framework_code`；
- `latest_run_id`（指向最新学习卡运行，可为空）；
- `status`：`IDLE`、`RUNNING`、`READY`、`DEGRADED`、`FAILED`；
- `created_at`、`updated_at`、`revision`。

### 5.2 `stock_learning_card_run`

每次生成一行、永不覆盖，保存某一时点的卡片总览：

- `id`、`card_id`、`research_run_id`、`framework_code`；
- `status`、`conclusion_status`（`CONTINUE_LEARNING`、`INSUFFICIENT_EVIDENCE`、`KEY_RISK_TO_VERIFY`）；
- `summary`、`evidence_completeness`、`warning_message`；
- `source_fingerprint`、`generation_mode`（`MODEL_ASSISTED` 或 `CONTROLLED`）；
- `created_at`、`completed_at`。

`research_run_id` 记录用于生成的研究运行，保证学习卡可回到任务图、证据与原始报告。

### 5.3 `stock_learning_card_claim`

每个运行恰有六个维度行，字段为 `id`、`run_id`、`dimension_code`、`judgment`、`rationale`、`counterargument`、`unknowns`、`confidence`、`sort_order`。对 `(run_id, dimension_code)` 建唯一约束。

### 5.4 `stock_learning_card_evidence_link`

将学习卡维度与本次研究证据显式关联：`id`、`claim_id`、`evidence_id`、`stance`（`SUPPORT`、`COUNTER`、`CONTEXT`）、`excerpt_snapshot`、`sort_order`。`evidence_id` 必须属于 `research_run_id` 的证据集合，服务层和数据库外键共同保护该关联。

### 5.5 `stock_learning_card_watch_item`

保存每次运行的学习清单：`id`、`run_id`、`metric`、`baseline`、`frequency`、`upgrade_condition`、`downgrade_condition`、`next_review_at`、`sort_order`。无当前基线时保存明确文本“当前公开证据未覆盖”，不能伪造数值。

## 6. API

- `GET /api/stock-learning-cards/{code}`：读取该股票的卡片身份、最新运行、六维结论、证据链接、学习清单和历史运行摘要；不存在时返回空卡片状态。
- `POST /api/stock-learning-cards/{code}/runs`：为选定股票创建新的学习卡运行并异步启动研究；已有运行中的同股票请求返回稳定业务错误，避免重复模型调用。
- `GET /api/stock-learning-cards/{code}/runs/{runId}`：读取某一不可变历史运行。

所有异步进度复用既有研究运行轮询或 SSE 能力。Controller 只转换 HTTP 请求与响应；研究、Agent、持久化和状态迁移均由 Service 层负责。

## 7. 前端

在 Strategy 下新增“股票学习卡”入口，保留现有“股票孵化”用于用户未来自行维护的策略研究卡，两者语义不混合。

学习卡页面包含：

1. 股票选择器与“按刘杰框架生成”按钮；
2. 当前运行进度、模型降级说明和研究材料状态；
3. 顶部学习结论与方法来源，固定提示“学习材料，不构成投资建议”；
4. 六个框架维度区块，每块显示判断、依据、反方、未知项与证据跳转；
5. “下一步学习”清单，按 `next_review_at` 和重要性排序；
6. 历史版本轨道，打开旧版本时仅读取保存的结果，不重新发起模型或外部搜索。

无任何需要用户撰写的字段。页面不显示“建议买入”“建议仓位”“目标价”等视觉或文字暗示。

## 8. 错误处理与可靠性

- 标的不存在或不是股票：拒绝创建，返回可理解的业务错误。
- 资料源局部失败：保留已得到的材料，状态为 `DEGRADED`，并在卡片中明确缺失维度。
- 研究运行中断：遵循现有 Runtime 的 `INTERRUPTED`/恢复机制，不能创建半成品 READY 卡片。
- 合成 Agent 失败：保存 `CONTROLLED` 保守卡片或标记失败；不修改上一次 READY 卡片。
- 再次生成：旧版本不可变；仅在新版本已完整持久化后更新 `latest_run_id`。

## 9. 验收标准

1. 用户只选一只 A 股并点击一次，即可完成从研究运行到学习卡生成；没有必填文本输入。
2. 正常运行的卡片固定展示六个框架维度；每个维度都能显示支持材料、反方材料或“证据不足”。
3. 任一 Agent 结论引用不存在或不属于本次研究运行的证据时，服务拒绝持久化该模型输出。
4. 模型不可用时仍保留可读的保守学习卡及资料缺口，且 `agent_run` 可查到降级原因。
5. 卡片不写入 `strategy_holding` 或 `strategy_stock_thesis`，也不包含交易指令、目标价或仓位建议。
6. 同股票重新生成保留旧版本；历史版本打开时不重新访问外部数据或调用模型。
7. DAO、Service、Web、前端测试覆盖状态迁移、证据引用校验、模型降级、历史只读和无人工输入生成流程。

## 10. 后续演进

第二期可在有新财报、公告或高相关雷达事件时创建“待复核”提醒；用户点击后才运行案例复核 Agent。第三期再允许用户将一张学习卡显式“转为个人投资案例”，届时才进入估值、配置纪律和版本化决策台账。
