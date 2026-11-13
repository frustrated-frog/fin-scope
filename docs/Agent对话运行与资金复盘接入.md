# Agent 对话运行与资金复盘接入

本次实现用户确认的三项：资金复盘读取、执行进度与停止、历史对话恢复。不包含统一全文检索或固定业务问题集。

## 启动与交互

先在 IDEA 重新启动 FinScope 后端和 Weavria Sidecar（共享配置 `FinScope with Weavria`），再刷新前端。
默认前端连接 8766；8765 可能由其他本地服务占用。两个仓库需配套更新。

Agent 页的“历史对话”从 Sidecar 恢复消息，“新建对话”创建独立上下文。浏览器按 Sidecar 地址隔离当前会话。
页面关闭只停止轮询，已接受的服务端任务继续执行；再次进入会自动恢复进度。“停止运行”会请求 Sidecar
取消对应 Task，并等待模型/工具和持久化清理。读取状态失败时可“重新连接”，不要把断网误认为任务已停止。

页面已移除证据快照卡片及查看弹窗。Weavria 内部仍保留工具结果和来源记录，用于执行追溯。

旧版保留的浏览器 session ID 可恢复该会话并纳入新列表。列表最多 100 个会话，框架历史每页 30 个轮次。
应用索引不扫描 Weavria 的内部数据库表来发现已经丢失 ID 的旧会话。

## 资金复盘契约

FinScope `GET /api/market-intel/instruments/{id}/capital-replay?refresh=false` 只读已有分钟事实和已保存复盘
快照，不调用在线行情网关、不写快照。省略 refresh 时保留原有在线读取行为。

Weavria 使用已有 `read_finscope_resource` 工具，新增 `kind=market_capital_replay`，identifier 为标的 ID，
secondary_identifier 为交易日，from_time/to_time 为交易分钟范围。返回分钟证据、来源、覆盖计数、区间净额、关键转折、
净流向变号和区间结束后的 5/15/30 个交易分钟收益。午休不计入交易分钟，不以跨日价格填充当日后续表现。
缺少连续价格时收益为空；资金缺少任一样本时完整区间净额为空，部分统计保留覆盖计数。变号不是机构意图识别。

## 验证

- 后端：CapitalReplayServiceTest 验证 refresh=false 不调用在线网关或 retain 写入。
- Weavria：`uv run pytest tests/finscope_app`；`uv run ruff check apps/finscope tests/finscope_app`；
  `uv run pyright apps/finscope tests/finscope_app`。
- 前端：`VITE_WEAVRIA_AGENT_URL='' npm test`（隔离本机 Agent 地址对未配置场景测试的影响）；`npm run build`。
- Playwright 使用隔离浏览器与明确标记的联调样例检查历史对话界面，不将样例称为真实模型效果验证。
