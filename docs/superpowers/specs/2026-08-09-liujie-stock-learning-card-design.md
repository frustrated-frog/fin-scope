# 刘杰框架股票学习卡设计

> 实现更新（2026-08-09）：本文记录第一版原始方案，其中“复用完整 Research Runtime”的编排已经被[股票学习卡独立 Agent 设计](./2026-08-09-stock-learning-card-agent-design.md)替代。当前实现只复用搜索、正文读取和 LLM 等基础能力，不创建研究主题、命题、通用研究运行或通用报告；状态、错误和六维局部失败均由学习卡领域独立管理。

## 1. 目标与边界

为个人学习场景新增“刘杰框架股票学习卡”。用户只选择一只股票并点击生成；系统随后通过受控 Research Runtime 收集公开材料、补足支持与反方证据，并自动生成结构化学习卡。用户不需要填写投资逻辑、买入条件、估值或复盘内容。

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
  -> 复用运行报告受控投影为六维学习卡
  -> 交易语言检查通过后持久化为最新版本
  -> 用户阅读结论、未知项与下一观察点
```

用户在整个流程中不填写研究内容，也不需要确认 Agent 草稿。页面仅提供重新生成操作；重新生成创建新的不可变学习卡运行，旧运行保留在数据库中供后续历史阅读入口使用。

## 4. Agent 编排与安全边界

新增 `StockLearningCardService`，它是面向学习卡的编排层，不允许 Controller 直接调用 Repository。

1. 服务验证股票代码并通过现有 `StrategyInstrumentResolver` 解析 `STOCK` 类型标的。
2. 服务按固定框架构造公司研究问题，并以 `DEEP` 模式启动现有 Research Runtime。
3. 现有 Research Runtime 继续负责任务 DAG、工具白名单、证据缺口、反方检索、运行恢复和 Agent 决策轨迹。学习卡复用该运行的报告，自己不拥有 SQL、Shell、任意 HTTP 或自定义工具权限。
4. 第一版不另起第二个模型合成节点：当研究报告终态可读时，服务将其受控投影到固定六维卡片，并为每个维度保留反方检查、未知项和低置信度标记；`research_run_id` 可追溯完整 Agent 轨迹。
5. 报告缺失或含有买卖、仓位、目标价等交易语言时，运行标记为降级；服务不展示原文而是生成保守卡片和明确告警。研究流程不因卡片投影失败而丢失材料。

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

第一版不复制研究证据表；每张卡保存 `research_run_id`，可回到既有 Research Runtime 的报告、证据和 Agent 轨迹。下一版再增加逐维证据链接。

### 5.5 `stock_learning_card_watch_item`

保存每次运行的学习清单：`id`、`run_id`、`metric`、`baseline`、`frequency`、`upgrade_condition`、`downgrade_condition`、`next_review_at`、`sort_order`。无当前基线时保存明确文本“当前公开证据未覆盖”，不能伪造数值。

## 6. API

- `GET /api/stock-learning-cards/{code}`：读取该股票的卡片身份、最新运行、六维结论和学习清单；不存在时创建空卡片状态。
- `POST /api/stock-learning-cards/{code}/runs`：为选定股票创建新的学习卡运行并异步启动研究；已有运行中的同股票请求返回稳定业务错误，避免重复模型调用。
所有异步进度复用既有研究运行轮询。Controller 只转换 HTTP 请求与响应；研究、Agent、持久化和状态迁移均由 Service 层负责。

## 7. 前端

在 Strategy 下新增“股票学习卡”入口，保留现有“股票孵化”用于用户未来自行维护的策略研究卡，两者语义不混合。

学习卡页面包含：

1. 股票选择器与“按刘杰框架生成”按钮；
2. 当前运行进度、模型降级说明和研究材料状态；
3. 顶部学习结论与方法来源，固定提示“学习材料，不构成投资建议”；
4. 六个框架维度区块，每块显示判断、依据、反方与未知项；
5. “下一步学习”清单，按 `next_review_at` 和重要性排序；
6. 第一版只显示最新运行；不可变旧版本已经持久化，历史阅读入口留待下一版接入。

无任何需要用户撰写的字段。页面不显示“建议买入”“建议仓位”“目标价”等视觉或文字暗示。

## 8. 错误处理与可靠性

- 标的不存在或不是股票：拒绝创建，返回可理解的业务错误。
- 资料源局部失败：保留已得到的材料，状态为 `DEGRADED`，并在卡片中明确缺失维度。
- 研究运行中断：遵循现有 Runtime 的 `INTERRUPTED`/恢复机制，不能创建半成品 READY 卡片。
- 报告缺失或含交易语言：保存 `CONTROLLED` 保守卡片，不展示原报告中的交易内容。
- 再次生成：旧版本不可变；仅在新版本已完整持久化后更新 `latest_run_id`。

## 9. 验收标准

1. 用户只选一只 A 股并点击一次，即可完成从研究运行到学习卡生成；没有必填文本输入。
2. 正常运行的卡片固定展示六个框架维度；每个维度都能显示支持材料、反方材料或“证据不足”。
3. 报告缺失或包含交易语言时，服务拒绝展示原文并持久化保守学习卡。
4. 模型不可用时仍保留可读的保守学习卡及资料缺口，且可由 `research_run_id` 查到既有 Agent 轨迹。
5. 卡片不写入 `strategy_holding` 或 `strategy_stock_thesis`，也不包含交易指令、目标价或仓位建议。
6. 同股票重新生成保留旧版本；第一版只读取最新版本。
7. DAO、Service、Web、前端测试覆盖状态迁移、交易语言防护、模型降级和无人工输入生成流程。

## 10. 后续演进

第二期可在有新财报、公告或高相关雷达事件时创建“待复核”提醒；用户点击后才运行案例复核 Agent。第三期再允许用户将一张学习卡显式“转为个人投资案例”，届时才进入估值、配置纪律和版本化决策台账。
