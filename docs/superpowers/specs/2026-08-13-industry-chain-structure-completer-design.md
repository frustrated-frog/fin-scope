# 产业图谱通用结构补全设计

## 背景与目标

当前产业图谱已经支持 `INDUSTRY_CHAIN_V3` 语义节点、研究画像和多图层展示，但生成任务仍由进程内线程池驱动，且旧 V2 图谱或语义节点偏少的 V3 图谱只能通过一次完整刷新被动升级。对于机器人等复杂产业，用户进入图谱后仍可能看到“环节有了、内容不够立体”的结果。

本次目标是建立一条适用于所有行业的结构补全链路：统一判断图谱的结构完整度，复用既有证据采集器和归纳 Agent，在 Kafka 中异步执行 V2→V3 或稀疏 V3 的丰富化，并在新版本完整通过校验后原子发布。任务期间继续展示上一版图谱。

本次不建设知识库，不为机器人等单一行业写规则，也不复制研究 Agent。

## 方案比较

### 方案 A：继续把刷新交给进程内线程池

改动最小，但应用重启后排队任务不可恢复，多实例会产生不一致的执行边界，也无法体现本次已经引入的 Kafka 基座。

### 方案 B：新增通用完整度评估与 Kafka 补全任务（采用）

后端根据结构而不是行业名称计算完整度；刷新时创建持久化 revision，再发布带事件 ID、版本、产业链 ID 和 revision ID 的 Kafka 消息。消费者幂等领取 revision，复用原有证据采集和归纳 Agent，并把旧图作为结构上下文而不是事实来源。Kafka 不可用时回退到现有本地执行器，保证个人本地工作台仍可用。

### 方案 C：只在前端投影更多信息

能够改善视觉密度，但无法补出不存在的材料、设备、部件、技术、应用和关系，也无法真正升级 V2 数据，因此只保留为展示层职责，不作为本次主方案。

## 领域模型

### 结构完整度

`IndustryChainStructureAssessment` 由已发布图谱即时计算，不新增数据库快照。评分维度为：

- V3 schema：20 分；
- 语义节点数量：20 分，9 个达到满分；
- 环节语义覆盖：20 分，每个 STAGE 至少挂载一个非公司语义节点；
- 语义类型多样性：15 分，材料、设备、部件、产品、技术、应用中 4 类达到满分；
- 节点研究画像覆盖：15 分；
- 语义关系密度：10 分。

状态分为：

- `BUILDING`：尚无已发布图谱；
- `UPGRADE_AVAILABLE`：schema 不是 V3；
- `ENRICHMENT_RECOMMENDED`：已是 V3，但结构覆盖或内容密度仍不足；
- `COMPLETE`：达到通用完整度门槛。

评分用于反馈和触发建议，不取代发布前的事实与结构校验。某些行业天然缺少个别语义类型时仍可通过其他维度获得高分，避免把制造业模板硬套到所有行业。

### Kafka 事件

`IndustryChainGenerationMessage` 是独立事件 DTO，包含：

- `eventId`：全局唯一事件 ID；
- `eventVersion`：当前为 1；
- `eventType`：`INDUSTRY_CHAIN_STRUCTURE_COMPLETION_REQUESTED`；
- `chainId`、`revisionId`：业务主键；
- `requestedAt`：发生时间。

Topic 为 `finscope.industry-chain.structure-completion.requested`，消息 key 使用 revision ID，确保同一修订稳定路由。消费者只处理仍处于 `RUNNING` 的 revision；重复消息安全返回。

## 执行流程

```text
创建 / 补全结构
  → 检查同一产业链是否存在活跃 revision
  → 创建 RUNNING / QUEUED revision（持久任务凭据）
  → 发布 Kafka 消息
      → 发布不可用时回退本地有界线程池
  → 消费者按 chainId + revisionId 幂等加载任务
  → COLLECTING_EVIDENCE：复用三路证据采集器
  → COMPLETING_STRUCTURE：复用 IndustryChainSynthesisAgent
       输入新证据 + 旧图结构上下文
       旧图只用于保留有效结构，所有事实仍须引用本轮证据
  → VALIDATING_STRUCTURE：既有 validator 校验
  → repository.publish() 原子写入并切换 current_revision_id
```

失败时 revision 进入 `FAILED`，上一版 `current_revision_id` 不变。Kafka 消费异常由监听器重新抛出以保留框架重试语义；业务执行器会把可判定的生成失败写回 revision。

## Agent 复用策略

不新增独立 LLM Agent。`IndustryChainSynthesisAgent` 增加可选的 `previousGraph` 输入：

- 初次创建：只使用冻结证据生成 V3；
- V2 升级：保留旧环节命名和有效结构作为候选，补齐语义节点、画像与关系；
- 稀疏 V3：优先填补未覆盖环节、语义类型与节点画像；
- 所有输出仍执行字段白名单、证据引用、企业供销关系清理和图谱 validator。

这使“研究”与“结构补全”共享同一事实归纳能力，但拥有不同的任务状态与 UI 反馈。

## API 与前端

工作台响应新增 `structure`：状态、分数、语义节点数、覆盖环节数、总环节数和待补全提示。现有刷新接口保持兼容，按钮根据结构状态显示为“补全结构”或“更新图谱”。

工具栏新增紧凑的结构仪表：圆形完成度、状态文案和最多两个待补维度。生成期间，仪表保持展示旧图评分，进度条按 `QUEUED / COLLECTING_EVIDENCE / COMPLETING_STRUCTURE / VALIDATING_STRUCTURE` 显示当前动作。视觉采用低饱和深色玻璃材质、青色进度弧和单一暖色提醒，不新增大侧栏，不压缩主图空间。

## 容错与可观测性

- Kafka 开关关闭或同步发布失败时，回退现有有界线程池；
- revision 是业务幂等键，消费者重复投递不会重复发布；
- 日志携带 eventId、chainId、revisionId、stage 和结果；
- 30 分钟未完成的 revision 沿用现有租约过期策略，允许用户重新补全；
- 不在日志、消息或文档中复制任何 API Key。

## 验收标准

- V2 和稀疏 V3 工作台均返回可解释的结构状态；
- Kafka 启用时刷新只发布消息，不进入本地线程池；禁用或发布失败时安全回退；
- 重复 Kafka 消息不会重复采集、生成或发布；
- 归纳 Agent 能看到上一版结构上下文，但输出事实仍只能引用本轮证据；
- 任务期间旧图持续可见，完成后轮询切换到新 revision；
- 前端桌面和移动端均展示清晰、克制的完整度与补全进度；
- 后端测试、前端测试和生产构建通过。

