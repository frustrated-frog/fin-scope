# Deep Research Runtime + Eval Harness 技术设计

## 1. 设计结论

采用“确定性主图 + 受约束动态循环 + 持久化事件/checkpoint + 离线规则评测”的 Java 原生方案。

现有 `ResearchService` 不再独自持有整段运行控制流，而是把业务动作交给 runtime 节点执行。Runtime 负责状态转换与安全约束，业务节点负责来源抓取、证据判断、查询扩展和报告合成。Eval Harness 只读取持久化结果和 trace，不修改研究数据。

## 2. 方案比较

### 方案 A：Java 原生领域 Runtime（采用）

- 优点：复用 Spring/SQLite/现有 Repository；类型、事务和部署边界一致；能展示对状态机、幂等、预算、trace、eval 的真实工程理解。
- 缺点：需要自行实现小型调度内核。
- 适用性：FinScope 的节点和数据边界明确，动态性主要集中在证据不足后的查询扩展，不需要完整通用图框架。

### 方案 B：引入 LangGraph Python 服务

- 优点：checkpoint、graph 和 interrupt 能力成熟。
- 缺点：新增 Python 服务、跨进程协议和双运行时运维；现有 Java 业务服务仍需远程适配；对个人本地项目成本过高。

### 方案 C：继续扩展 `ResearchService` 固定流程

- 优点：改动最少。
- 缺点：恢复、版本控制、事件一致性和评测会继续散落在业务代码中，无法形成稳定的 runtime 边界。

## 3. 运行图

```text
PLAN
  -> COLLECT_SOURCES
  -> ASSESS_EVIDENCE
       -> EXPAND_QUERY -> COLLECT_DYNAMIC -> ASSESS_EVIDENCE  (最多 3 轮)
       -> SYNTHESIZE_REPORT                                (证据充分或预算终止)
  -> VERIFY_OUTPUT
  -> COMPLETE
```

主图是规则定义的，查询内容和是否继续扩展由领域服务决定。所有节点都必须声明：

- `nodeId`：稳定节点标识。
- `actionFingerprint`：动作去重键。
- `expectedProgress`：本节点应带来的状态增量。
- `retryable`：失败是否允许安全重试。
- `terminalPolicy`：失败、部分成功或预算耗尽时如何收敛。

## 4. Runtime 状态

### 4.1 `research_runtime_checkpoint`

每个研究运行只有一个当前 checkpoint：

- `research_run_id`：主键。
- `state_version`：每次状态转换递增，用于乐观更新。
- `phase` / `current_node` / `status`：当前执行位置。
- `iteration` / `consumed_actions` / `max_actions`：循环和动作预算。
- `no_progress_count`：连续无进展次数。
- `last_state_hash`：基于证据数、来源数、报告状态生成的状态指纹。
- `resume_count`：成功领取恢复执行权的次数。
- `termination_reason`：完成或停止原因。
- `last_error` / 时间字段。

`state_version` 是并发控制基础。更新语句必须携带旧版本；影响行数为 0 表示已有其他执行者推进状态，当前执行者停止。

### 4.2 `research_runtime_event`

事件表只追加不修改：

- `sequence_no` 在单个 run 内唯一且递增。
- `event_type` 包括 `RUN_CREATED`、`NODE_STARTED`、`NODE_COMPLETED`、`NODE_FAILED`、`GUARD_TRIGGERED`、`RUNTIME_INTERRUPTED`、`RESUMED`、`TERMINATED`。
- 保存节点、动作指纹、状态哈希、进度增量、输入/输出摘要和结构化错误类型。

Runtime 页面和 Eval Harness 都以该事件流为事实来源，应用日志仅作为补充诊断。

## 5. 执行与恢复语义

### 5.1 正常执行

1. 创建研究运行和默认 plan。
2. 创建 `READY` checkpoint 与 `RUN_CREATED` 事件。
3. 后台执行器通过 compare-and-set 将 checkpoint 置为 `RUNNING`。
4. 每个节点执行前写 `NODE_STARTED`，执行后计算状态哈希和 progress delta。
5. 同一事务内保存 checkpoint 并追加完成/失败事件。
6. 到达终态后同步更新 `research_run` 状态。

### 5.2 恢复

- 仅 `FAILED`、`INTERRUPTED`、`PARTIAL_SUCCESS` 且未越过硬预算的 checkpoint 可恢复。
- resume 先执行乐观版本更新；只有一个请求可以获得执行权。
- 已有 `NODE_COMPLETED` 事件的幂等节点不重复执行，从 `current_node` 继续。
- 应用启动时不直接重跑任务，只把遗留 `RUNNING` checkpoint 标记为 `INTERRUPTED`，等待显式 resume，避免启动风暴。

### 5.3 防循环与预算

- 相同 `actionFingerprint` 最多执行两次；第三次触发 `REPEATED_ACTION`。
- 状态哈希连续两次不变化触发 `NO_PROGRESS`。
- 动作数达到 `maxActions` 触发 `BUDGET_EXHAUSTED`。
- 以上终止均允许进入报告合成节点，用已有证据生成明确标记的部分报告；不能伪装为完全成功。

## 6. Eval Harness

### 6.1 评测输入

评测器读取：

- `ResearchRun` 与 `ResearchReport`。
- runtime checkpoint 与完整事件流。
- run-scoped article/event/evidence outputs。
- 评测器版本 `deep-research-rules-v2`；证据数和独立来源数以 run-scoped 真实产物为准，并校验报告自报数量与轨迹状态机。

### 6.2 评测流程

```text
Load immutable snapshot
  -> validate invariants
  -> calculate six deterministic metrics
  -> apply critical gates
  -> persist eval run + metric details
  -> return report
```

所有分数都来自明确公式，不调用 LLM。相同输入快照通过 SHA-256 得到 `input_fingerprint`，同一评测器版本和指纹重复执行时返回已有结果。

### 6.3 持久化

`research_evaluation` 保存 run、evaluator version、input fingerprint、score、gate status、summary 和时间。

`research_evaluation_metric` 以 `(evaluation_id, metric_code)` 为主键，保存 score、max score、status、evidence 和 recommendation。

## 7. API

- `GET /api/research/runs/{id}/runtime`：checkpoint 与事件列表。
- `POST /api/research/runs/{id}/resume`：从 checkpoint 恢复。
- `POST /api/research/runs/{id}/evaluations`：执行或复用离线评测。
- `GET /api/research/runs/{id}/evaluations/latest`：获取最新评测。

运行详情响应同时携带 runtime 摘要和最新评测，减少前端首次展示的请求瀑布。

## 8. 错误处理

- 乐观锁冲突返回业务冲突，不覆盖新 checkpoint。
- 不可恢复状态返回明确原因与当前状态。
- Eval 输入不完整时仍生成失败报告，缺失项进入 metric evidence；只有研究运行不存在才返回 404。
- 数据库写入失败不得将节点标记为成功。
- 对外错误不暴露 prompt、密钥、完整模型响应或原始异常栈。

## 9. 测试策略

- Runtime 单元测试：合法状态转换、预算耗尽、重复动作、无进展、乐观锁冲突、断点恢复。
- DAO 测试：checkpoint CAS、事件序号唯一、eval 幂等键和指标读写。
- Service 测试：真实 run 快照六项评分、严重门禁、重复评测复用。
- Web 集成测试：runtime 查询、resume 冲突、触发评测并读取结果。
- Frontend 测试：runtime 状态、事件、评测指标、失败不清空旧结果。
- 全量 Maven、Vitest 和 Vite build 作为完成门禁。

## 10. 自审结果

- 无占位内容；本期范围聚焦一个可独立交付的研究 Runtime 与配套 Eval Harness。
- Runtime 的状态、恢复、预算与 Eval 指标均有唯一数据来源。
- 不引入与现有 Java 8 / Spring Boot 2.7 / SQLite 架构冲突的组件。
- UI 只展示新增能力，不重做 Research 页信息架构。
