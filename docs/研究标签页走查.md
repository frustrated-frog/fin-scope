结论很明确：Research 页面目前更像“后台任务调试台”，不是用户可用的研究工作台。它的核心问题不是样式，而是研究结果、运行步骤和用户目标三者没有真正闭环。

## P0，先解决真实性

1. 运行结果没有归属到这次研究，统计会串任务。

[ResearchService.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java:215) 用全库 `countAll()` 前后差值计算文章、事件、证据等数量。并发抓取、后台入库或其他研究任务都会被算到当前运行里。

更严重的是，`ResearchRun` 本身没有保存“本次运行产出的事件、证据、学习任务、选题”的 ID 集合或关联表。页面只能显示数字，不能证明“这 12 条证据到底是哪 12 条”。

2. “归并事件、抽取证据”是展示性步骤，不是实际编排步骤。

Research 流程在抓源后直接把 `classify_events`、`extract_evidence` 标记完成，[ResearchService.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java:202)。

但实际的事件归并和证据抽取发生在每篇文章入库时：[ArticleIngestCoordinator.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/article/ArticleIngestCoordinator.java:77)，其中 `attachArticle` 会直接触发证据、学习任务与选题生成。

这导致运行详情展示的是“计划顺序”，不是“实际执行顺序”。用户看到“正在归并事件”时，真实工作可能早已在抓取阶段完成了。

3. 进程重启会直接丢弃在途任务，且无法恢复。

当前任务靠应用内 `Executor` 执行，[ResearchService.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java:139)。服务启动后会把所有 `RUNNING` 任务标成失败：[ResearchStartupRecoveryService.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/ResearchStartupRecoveryService.java:29)。

实际数据中，运行记录 `4` 到 `12` 多次因为进程关闭失败，没有自动重试、从断点续跑或一键重跑入口。这会让“研究”在本地开发环境中非常不可靠。

## P1，产品体验目前太薄

1. 用户不能定义研究问题。

页面只提供日期、三种固定主题、来源数和“包含停用来源”：[ResearchView.tsx](/Users/machengqian.1/code/MyProject/fin-scope/frontend/src/features/research/ResearchView.tsx:6)。没有研究目标、假设、关注公司/行业、时间窗口、排除条件或预期交付物。

所以它实际做的是“抓一批源，生成简报”，不是“发起一次研究”。

2. 主题和来源匹配承诺不准确。

界面写的是“每主题来源数”，但来源在主题间全局去重，[SourcePlanner.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/SourcePlanner.java:45)，最终只显示总来源数，不显示每个主题实际分到了哪些来源、是否覆盖不足。

同时，来源即使没有主题标签，只要来源层级命中某主题的“偏好层级”也会被选中：[SourcePlanner.java](/Users/machengqian.1/code/MyProject/fin-scope/backend/finscope-service/src/main/java/com/finscope/service/research/SourcePlanner.java:62)。例如 `MEDIA` 同时是多个主题的偏好层级，泛财经来源很容易被误分配。

3. 完成后没有可消费的研究成果。

页面的主内容是运行表格和 trace。用户只能“打开简报”，不能从该运行直接进入：

- 本次新增事件
- 本次证据链
- 本次生成的学习任务
- 本次生成的选题
- 被过滤/失败的来源及原因

这也是它显得薄弱的根本原因，页面主要在展示系统做了什么，而不是研究得出了什么。

4. 详情接口会返回完整 Agent 输入和输出，既重又不适合 UI。

`ResearchRunDetailResponse` 返回全部 `agentRuns`，没有分页、摘要或脱敏；前端直接渲染 `run.output`：[ResearchView.tsx](/Users/machengqian.1/code/MyProject/fin-scope/frontend/src/features/research/ResearchView.tsx:173)。

实际运行记录里已经能看到完整模型提示词和完整模型回答。这会导致详情接口越来越大、页面卡顿，也把只适合调试的内容暴露给普通研究用户。

## P2，技术实现还需要收口

- 运行中每 750ms 请求一次详情和一次完整运行列表：[App.tsx](/Users/machengqian.1/code/MyProject/fin-scope/frontend/src/App.tsx:158)。请求慢时可能重叠；切出页面后仍可能继续轮询已选任务。
- 没有取消、重试、幂等键或并发运行约束。同日期、同主题可以重复启动，结果也无法比较或合并。
- 状态、步骤名、来源层级、执行器名称直接展示英文技术词，用户很难判断它对研究质量意味着什么。
- ResearchView 没有独立组件测试，现有覆盖主要停留在 App 的“能启动并显示 trace”的 happy path。

## 我建议的重构方向

不要继续在当前表格上加字段。先把它重定义为“研究任务闭环”：

```text
研究目标
  → 来源覆盖预览
  → 可恢复的执行任务
  → 本次产出的事件/证据/结论
  → 用户确认、收藏、继续追问
```

实施优先级：

1. 建立 `research_run_output` 或给事件、证据、学习任务、选题增加 `research_run_id`，消除全库差值统计。
2. 让事件归并、证据抽取成为真实可追踪步骤，或明确把它们合并进“文章入库处理”，不要伪造阶段。
3. 将任务执行改为可恢复的持久化队列语义，至少支持失败重试和从未完成来源继续。
4. 页面改为“结果优先”：顶部是研究目标、覆盖率、关键结论和风险；下方才是技术 trace。
5. trace 改成摘要、折叠和分页，默认只展示错误、耗时、降级原因。
6. 加入研究问题、来源预览、每主题覆盖缺口、结果直达链接。

这页现在值得做，但需要按“研究产物可追溯”重建主线，而不是继续扩充运行记录面板。本次仅走查，未修改代码。