# Intake 工作流阶段代码走查与整改记录

日期：2026-07-09

## 走查结论

本轮走查对照 `docs/superpowers/specs/2026-07-09-intake-workflow-design.md` 和当前项目流程，重点检查 Sources、Intake、Article 三段链路。

核心原则仍然是：

1. Source 手动或定时抓取结果必须先进入 Intake 候选池。
2. 第一阶段不能自动进入 Article。
3. Agent 或 fallback 只做预审和打分，人工状态才是最终决策。
4. 只有人工 Promote 才能调用 Article ingest 链路生成 Article 和 Insight Card。

## 问题与整改优先级

### 1. 旧 `/api/sources/{id}/fetch` 绕过 Intake

问题：

旧接口仍调用 `FetchService.fetch`，会直接调用 `ArticleIngestCoordinator.ingest`，导致抓取结果直接进入 Article。

整改：

保留旧路径以兼容调用方，但控制器内部改为走 `IntakeService.intakeFetch`。以后无论前端调用 `/fetch` 还是 `/intake-fetch`，Source 抓取都只进入 Intake 候选池。

验收：

1. 调用 `/api/sources/{id}/fetch` 创建 `fetch_batch` 和 `intake_candidate`。
2. 调用 `/api/sources/{id}/fetch` 后 Article 列表不新增文章。
3. 前端 Sources 页面继续调用 `/intake-fetch`。

### 2. 抓取失败或 0 候选时 UI 误报成功

问题：

`IntakeService` 捕获异常后返回 `FAILED` batch，但 HTTP 仍为 200；前端只要请求成功就提示“已抓取到候选池”。另外过滤后 0 条候选也会被标记为 `COMPLETED`。

整改：

1. 后端在 0 候选时将 batch 标记为 `FAILED`，写入明确错误信息。
2. 前端检查返回的 batch status。只有 `COMPLETED` 或 `PARTIAL_SUCCESS` 才提示成功，否则提示失败原因。

验收：

1. 0 候选 batch status 为 `FAILED`。
2. 前端收到 `FAILED` batch 时展示错误 toast。

### 3. LLM 成功预审被错误保存为 FALLBACK

问题：

`CandidateReviewAgent` 有 LLM 成功路径，但 `IntakeService` 保存候选时固定写入 `agentStatus=FALLBACK` 和 `agentModel=fallback`。

整改：

让 `CandidateReviewAgent` 返回 review 内容和运行状态。`IntakeService` 根据运行状态写入 `SUCCESS` 或 `FALLBACK`。

验收：

1. LLM 可用且返回合法 JSON 时，候选 `agentStatus=SUCCESS`。
2. LLM 不可用或失败时，候选 `agentStatus=FALLBACK`。

### 4. Intake UI 缺少“稍后看”

问题：

后端已支持 `SAVED_FOR_LATER`，但前端没有状态筛选，也没有操作按钮。

整改：

Intake tab 增加“稍后看”状态筛选和候选操作按钮。用户可以把候选从 Pending 收纳到 Later，并且 Later 状态仍可 Promote。

验收：

1. Intake 状态筛选包含“稍后看”。
2. 候选卡片包含“稍后看”按钮。
3. 点击后调用 `/api/intake/candidates/{id}/status`，body 为 `{"humanStatus":"SAVED_FOR_LATER"}`。

### 5. WEB_LIST 没有按 `maxItemsPerRun` 控制实际抓取数量

问题：

`WebListSourceAdapter` 先抓取最多 20 篇文章正文，再由 `IntakeService` 截断候选数量。用户配置 3 条时，实际仍可能发起 20 次文章请求。

整改：

`WebListSourceAdapter` 读取 `source.maxItemsPerRun`，在列表链接阶段就限制实际抓取数量，同时保留上限保护。

验收：

1. `WEB_LIST` source 配置 `maxItemsPerRun=1` 时，只抓 1 篇文章正文。
2. 默认仍最多抓 20 篇，避免列表页无限扩散。

## 验证计划

整改完成后运行：

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
```

## 本轮整改结果

已完成：

1. 旧 `/api/sources/{id}/fetch` 已转入 Intake，不再直接创建 Article。
2. 0 候选抓取会返回 `FAILED` batch，并在前端展示失败原因。
3. LLM 预审成功时，候选项会保存 `agentStatus=SUCCESS` 和真实模型名；LLM 不可用或失败仍为 `FALLBACK`。
4. Intake 页面已补齐“稍后看”筛选和候选操作按钮，状态写入 `SAVED_FOR_LATER`。
5. `WEB_LIST` 已在链接阶段按 `maxItemsPerRun` 限制实际抓取正文数量。

已补充测试：

1. `/api/sources/{id}/fetch` 只进入 Intake、不新增 Article。
2. 0 候选抓取返回 `FAILED`。
3. LLM 成功预审保存 `SUCCESS` 和模型名。
4. Intake 前端“稍后看”筛选与状态更新。
5. Sources 前端展示失败 batch 错误。
6. `WEB_LIST` 在 `maxItemsPerRun=1` 时不访问第二篇详情页。
7. 旧主题/日报流程显式 Promote 后再使用 Article。

最终验证：

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run build
```

结果：全部通过。

## 备注

`notes/` 当前为未跟踪目录，本轮整改不触碰、不纳入提交范围。
