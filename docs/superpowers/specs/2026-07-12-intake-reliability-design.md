# Intake 可靠性与实时抓取设计

## 目标

把信息源抓取从同步 HTTP 请求改为可观察的后台批次任务，并保证候选状态、Promote 重试和信源生命周期不会产生不可恢复的数据状态。

## 范围

- `POST /api/sources/{id}/intake-fetch-async` 立即返回持久化任务，后台执行抓取；保留旧同步接口兼容已有调用方。
- 复用 `GET /api/tasks/{taskId}/stream` 推送抓取、筛选、Agent 评审、汇总和终态进度。
- Source 卡片实时显示阶段、当前进度、失败原因与重试动作。
- 人工状态由服务端状态机约束，终态可显式恢复为待处理；只有 Promote 可以写入 `PROMOTED`。
- Promote 保存工作流结果；工作包失败后可针对既有 Article 重试，不会重复入库。
- 有候选但全部重复的批次标记为 `PARTIAL_SUCCESS`，保留真实计数。
- 删除改为归档，归档信息源不再可抓取，但历史候选仍可 Promote。

## 非目标

不修改密钥配置，不实现 SSRF 限制、白名单或网络出口策略。

## 数据与状态

`fetch_batch` 新增 `phase`、`progress_message`、`processed_count`，作为任务状态源。批次由 `RUNNING` 进入 `COMPLETED`、`PARTIAL_SUCCESS` 或 `FAILED`。

候选人工状态只允许：`PENDING/SAVED_FOR_LATER -> SAVED_FOR_LATER/SKIPPED/REJECTED/PROMOTED`，`SKIPPED/REJECTED -> PENDING`。`PROMOTED` 只能由成功创建或复用 Article 的 Promote 流程写入。

候选保存 `promotion_event_id`、`workflow_status` 与 `workflow_error_message`。首次 Promote 仅创建一个 Article；后续 Promote 或 retry 只重跑研究工作包，并返回首次保存的事件结果。

## 执行模型

`IntakeFetchTaskService` 使用现有 ingest executor 和持久化 `async_task` 启动后台线程。它依次发出 `FETCHING`、`PARSING`、`LLM`、终态事件；既有 `TaskSseRegistry` 按 task ID 维护 `SseEmitter`，并在完成时关闭连接。

前端在点击抓取后立即订阅流。若 SSE 断开，则以短轮询批次详情兜底，刷新后仍能恢复运行中批次的可见状态。
