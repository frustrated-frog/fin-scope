# 多周期单股预测设计

## 目标

把现有固定 20 日的单股概率研究升级为 1/5/20 日三个相互独立、允许弃权、可保存和可到期验证的研究周期。默认选择 5 日，不把概率包装成确定性交易指令。

## 数据流

前端提交股票代码与周期；Java 校验周期并调用 Python；Python 从服务端缓存路由读取 5000 根 QFQ 日线，构造 T+1 可执行标签，完成前向验证、校准、锁定测试与回测；Java 保存不可变报告及周期维度；前端按周期带展示结果和历史。

## Python 契约 v4

请求新增 `horizonDays`，只允许 1、5、20，默认 5。响应升级为 `single-stock-research-v4`，新增：

- `decision`: `UP / DOWN / ABSTAIN`
- `decisionReason`: 当前方向或弃权理由
- `selectiveValidation`: 上下阈值、覆盖样本数、coverage、covered accuracy、abstain rate
- 分周期 `labelHorizonDays` 与 `independentStrideDays`

标签使用 T+1 开盘买入、T+H+1 开盘卖出，并扣除固定往返成本。报告中的策略说明、指纹和 trial identity 均包含周期。

## 资格与决策

资格切分保持 60/20/20，训练标签退出日必须早于预测日。独立锚点步长为周期天数。资格样本门槛按独立锚点计算；生产概率只有在资格非 `INSUFFICIENT_DATA` 时出现。

方向决策先使用固定阈值 0.60/0.40。资格为 `FAILED`、样本不足或概率落在中间区间时输出 `ABSTAIN`。页面同时展示 coverage 和 covered accuracy，防止只看高命中率而忽视极低覆盖。

## Java 与存储

Web request 增加 `horizonDays`，Bean Validation 限定取值。RPC client 把周期传给 Python 并校验返回周期。运行表新增 `horizon_days`、`maturity_status`；重复数据判断按股票与周期隔离。历史接口可选周期过滤，旧数据迁移默认 20 日。

第一阶段到期状态为 `PENDING / MATURED / UNAVAILABLE`，不修改原始报告。实际收益结算需要 Python 使用同一标签规则，作为后续增量服务接入；本批先把存储生命周期和 UI 状态完整打通。

## 前端设计

延续现有研究账本风格，新增一条 1D/5D/20D “期限结构带”：每格显示周期用途，当前周期用靛青描边和青色状态点，支持键盘选择。主结论增加 `UP / DOWN / 暂不判断` 印章；可信度区域增加覆盖率、覆盖后命中率和弃权率。历史项显示周期与到期状态，窄屏折行为三列卡片。

不增加装饰性大渐变，不引入新 UI 库；复用现有字体、色板、边框和 reduced-motion 约束。

## 验证

- Python：标签时点、非法周期、分周期锚点、选择性指标、v4 报告、区间有限性。
- Java：请求校验、RPC 请求/响应周期、DAO 迁移与按周期去重、Service 保存。
- 前端：默认 5 日、切换周期、POST 参数、方向/弃权、历史周期状态、生产构建。
