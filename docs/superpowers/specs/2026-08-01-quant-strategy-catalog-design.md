# 量化策略素材库设计

## 1. 设计结论

将 `paperswithbacktest/awesome-systematic-trading` 接入为 FinScope Quant 的策略发现源，而不是执行引擎或代码依赖。系统同步其股票策略目录，保留论文与实现链接，对候选策略进行本地能力匹配；用户选择候选和数据集后，仍由现有 `QuantStrategyAgent` 生成受限 DSL，并经过人工确认、版本锁定和确定性回测。

第一期只同步 README 中文版“股票”表，不复制、不解释和不执行上游 Python 文件。上游 Sharpe、波动率等数字始终标记为来源记录，不进入本地实验指标。

## 2. 产品范围

### 2.1 本期交付

- 手动同步固定 GitHub 仓库的股票策略目录。
- 以仓库、分支、commit SHA 和同步时间记录来源快照。
- 保存候选标题、来源指标、调仓频率、论文链接和实现链接。
- 按现有因子与 DSL 边界计算 `ADAPTABLE`、`NEEDS_FACTOR`、`UNSUPPORTED` 三种兼容状态。
- 支持按兼容性、关键词筛选候选，并查看适配说明与缺失能力。
- 从可适配候选生成策略草案，草案和最终策略版本保留候选来源关系。
- 上游消失的条目归档，不删除既有来源和实验链。

### 2.2 明确不做

- 不执行 QuantConnect Python 文件。
- 不引入 Python 回测子系统或第二套回测指标。
- 不同步库、书籍、课程、博客、期货、期权、外汇和加密策略。
- 不自动确认草案或自动启动实验。
- 不把上游报告指标表达为收益承诺或本地验证结果。

## 3. 领域边界

### 3.1 RPC

`QuantStrategyCatalogProvider` 负责外部目录读取。`GithubAwesomeTradingCatalogProvider` 只访问固定的 GitHub API/raw 地址，获取主分支 commit SHA 与 `README_zh.md`，由纯解析器 `AwesomeTradingMarkdownParser` 提取股票表。网络失败统一转换为可理解的外部数据异常，不改变旧快照。

### 3.2 Service

`QuantStrategyCatalogService` 编排同步、列表、详情与候选草案生成。`QuantStrategyCompatibilityService` 使用显式规则评估候选，不让 LLM 决定系统是否支持某策略。候选转草案时，服务组装包含来源事实、适配约束、缺失语义和禁止事项的提示词，再委托现有 `QuantStrategyService.generateDraft()`。

### 3.3 DAO

- `quant_catalog_source`：固定源身份、分支、commit SHA、状态、同步时间和错误摘要。
- `quant_strategy_candidate`：外部键、标题、资产类别、来源指标、调仓频率、实现/论文链接、兼容状态、适配说明、所需与缺失因子、归档状态和时间戳。
- `quant_strategy_candidate_origin`：候选与草案/版本的来源关系；允许草案生成失败后仍追踪来源。

同步事务按外部键 upsert 本次条目，再归档本次未出现的旧条目。失败时事务回滚，保留上一次成功目录。

## 4. 兼容性规则

规则以标题关键词和明确映射为入口，输出状态、可用因子、缺失因子和说明：

| 策略族 | 当前映射 | 状态 |
|---|---|---|
| 账面价值/Book-to-Market | `BP` | `ADAPTABLE` |
| 短期反转 | `REVERSAL_5D` | `ADAPTABLE` |
| 低波动 | `VOLATILITY_20D` | `ADAPTABLE`，必须提示周期近似 |
| 股票动量 | `MOMENTUM_60D` | `ADAPTABLE`，必须提示不是 12-1 动量 |
| ROA、资产增长、应计、52 周高等 | 对应新因子编码 | `NEEDS_FACTOR` |
| 多空、杠杆、配对、期权、日内、跨资产 | 无 | `UNSUPPORTED` |
| 未识别策略 | 无 | `NEEDS_FACTOR`，进入人工研究 |

`ADAPTABLE` 表示可以形成 FinScope 版本，不表示忠实复现。候选详情必须同时展示“原始来源口径”和“本地适配口径”。

## 5. API

- `POST /api/quant/catalog/sync`：手动同步固定目录，返回来源状态与新增、更新、归档计数。
- `GET /api/quant/catalog/candidates?compatibility=&query=`：查询未归档候选。
- `GET /api/quant/catalog/candidates/{id}`：查询候选详情。
- `POST /api/quant/catalog/candidates/{id}/drafts`：请求体仅含 `datasetId`，拒绝 `UNSUPPORTED` 候选，返回现有 `QuantStrategyDraft`。

控制器只做参数校验与响应转换。同步不接受任意 URL，避免 SSRF 和目录源漂移。

## 6. 前端布局

Quant 页签增加“策略素材库”。视觉延续现有深墨、潮汐青、琥珀与等宽数据标签，不引入新的全局字体和主题。

```text
┌ 来源刻度 / 最近同步 / 同步按钮 ─────────────────────────┐
│ [全部] [可适配] [缺因子] [暂不支持]    搜索策略         │
├───────────────┬──────────────────────┬──────────────────┤
│ 策略族与计数   │ 候选卡片流            │ 适配证据抽屉       │
│ compatibility │ 标题/来源指标/频率     │ 原始口径           │
│ filters       │ 本地状态/所需因子      │ 本地映射与差异      │
│               │                       │ 选择数据集→生成草案 │
└───────────────┴──────────────────────┴──────────────────┘
```

标志性元素是“来源路径刻度”：`GitHub → commit → catalog snapshot → candidate → local draft`，编码真实溯源关系。仅在同步成功时进行一次轻微进度扫过动画，并遵守 `prefers-reduced-motion`。

空状态说明先同步目录；同步失败显示旧快照仍可用及重试动作；无匹配结果提示清除筛选。键盘焦点、按钮禁用态和移动端单列布局必须完整。

## 7. 数据流与错误处理

1. 用户手动同步。
2. Provider 获取 commit 和 Markdown；解析器只接受“股票”章节及六列表格。
3. 兼容性服务为每条候选生成确定性评估。
4. Repository 在事务中 upsert 并归档缺失条目，完成后更新源快照。
5. 用户筛选、查看详情并选择 READY 数据集。
6. Service 生成带来源约束的 prompt，调用现有草案链。
7. 保存候选与草案关系；确认版本时补齐候选与版本关系。

解析为空、表头变化、网络超时和数据库失败均不覆盖旧目录。接口返回中文业务错误；日志只记录 source code、commit、数量、耗时和错误类型，不记录配置或凭据。

## 8. 测试与验收

- RPC 单元测试：中文表解析、N/A 指标、相对链接展开、章节边界、空表失败。
- Service 单元测试：兼容规则、同步统计、失败保留、不可支持候选拒绝、提示词来源约束。
- DAO 集成测试：upsert 幂等、归档、来源关系和迁移幂等。
- Web 测试：同步、筛选、详情、生成草案和非法请求。
- 前端测试：空状态、同步、筛选、选中详情、兼容性提示、生成草案和失败恢复。
- 验证：后端全量测试、前端全量测试、生产构建和桌面/移动截图走查。

## 9. 设计自审

- 无待定字段或自动执行路径。
- 外部采集位于 RPC，Controller 不访问 Repository。
- 上游代码和指标与本地执行结果严格隔离。
- 第一阶段只覆盖股票目录，范围可在一个实施计划中完成。
- 现有实验链、数据指纹、人工确认和回退边界保持不变。
