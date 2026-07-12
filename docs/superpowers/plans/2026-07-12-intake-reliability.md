# Intake Reliability Implementation Plan

**Goal:** 交付非阻塞、可观察、可恢复的信息源摄入闭环。

1. 先为批次阶段、状态迁移、重复 promote 结果写回归测试。
2. 扩展批次/候选 schema 和 repository，加入进度与工作流持久化。
3. 把 Intake fetch 放入 executor，并通过 SSE 推送批次事件。
4. 约束 Source/Intake 服务的状态变更、归档与重试语义。
5. 更新 Sources/Intake 页面，展示进度、失败、重试、筛选与恢复操作。
6. 运行定向和全量验证，修复回归。
