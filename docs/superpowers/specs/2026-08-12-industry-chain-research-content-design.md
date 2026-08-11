# 产业链研究内容丰富化设计

## 目标

在不改变现有产业链图谱结构和证据模型的前提下，增加可直接用于产业研究的内容层，让用户能快速理解产业处于什么阶段、当前景气如何、核心变量是什么、哪些环节有瓶颈，以及公司在产业链中的定位。

本期聚焦四类内容：

- 产业总览：生命周期、景气度、供需状态、趋势标签。
- 关键变量：需求驱动、供给驱动、行业跟踪指标。
- 环节画像：产业作用、商业模式、成本结构、利润驱动、壁垒、风险和瓶颈。
- 竞争格局：代表公司的行业位置、核心产品、下游市场、竞争优势和关键观察项。

## 方案取舍

### 采用：修订级研究内容聚合

在 `IndustryChainGraph` 中增加 `researchContent`，内含一个产业总览、多个环节画像和多个公司画像。整个聚合序列化到 `industry_chain_revision.research_content_json`，与图谱修订一起发布。

优点：

- 研究内容与图谱版本完全一致，刷新失败不会污染已发布内容。
- 避免为节点表增加大量稀疏列。
- 环节与公司画像通过稳定 `nodeKey` 关联，不复制图谱节点。
- 后续可独立增加技术路线、景气时序和跨链内容。

### 不采用：节点表宽表化

直接向 `industry_chain_node` 添加商业模式、成本结构、景气等十余个字段，会产生大量空列，且每次扩展都需要数据库迁移。

### 不采用：前端根据现有描述自动拼装

该方案不需要后端改动，但无法稳定产出竞争格局、瓶颈和核心指标，也无法随修订管理内容。

## 数据契约

### `IndustryChainResearchContent`

- `overview`：产业总览。
- `stageProfiles`：按 `stageOrder` 排列的环节画像。
- `companyProfiles`：公司竞争格局。

### `overview`

- `lifecycle`：`EMERGING` / `GROWTH` / `MATURE` / `CONSOLIDATING` / `DECLINING`。
- `prosperity`：`RISING` / `STABLE` / `COOLING` / `MIXED`。
- `supplyDemand`：`TIGHT` / `BALANCED` / `LOOSE` / `STRUCTURAL`。
- `cycleType`：产业周期属性的简短中文描述。
- `demandDrivers`、`supplyDrivers`、`keyVariables`、`bottlenecks`、`overcapacityRisks`、`trendTags`：短语列表。

### `stageProfiles[]`

- `nodeKey`：必须引用已有 `STAGE` 节点。
- `roleSummary`、`businessModel`、`costStructure`、`valueCapture`、`bottleneck`：简短研究结论。
- `prosperity`、`supplyDemand`、`lifecycle`：状态枚举。
- `profitDrivers`、`barriers`、`coreMetrics`、`risks`、`keyVariables`、`trendTags`：短语列表。

### `companyProfiles[]`

- `nodeKey`：必须引用已有 `COMPANY` 节点。
- `industryPosition`：公司在该产业链的定位。
- `coreProducts`、`downstreamMarkets`、`competitiveAdvantages`、`keyVariables`：短语列表。

字符串和列表都设置上限，避免模型输出失控。无法判断的内容使用空列表或“待观察”，不增加额外占位节点。

## 生成与发布

1. 现有证据采集流程保持不变。
2. `IndustryChainSynthesisAgent` 一次输出图谱和 `researchContent`。
3. 解析器校验根字段、枚举、列表上限以及 `nodeKey` 引用。
4. `IndustryChainGraphValidator` 校验环节画像和公司画像不重复、类型匹配。
5. Repository 在发布修订时将聚合保存为 JSON，与节点和边同一事务提交。
6. 旧修订没有 `research_content_json` 时返回空聚合，不影响现有图谱。

图谱 schema 升级为 `INDUSTRY_CHAIN_V2`。

## 前端信息架构

顶部视图增加“研究面板”，与“产业全景 / 链上动态”并列。

### 研究面板

- 顶部状态带：生命周期、景气、供需和周期类型。
- 关键驱动：需求驱动、供给驱动和核心变量。
- 瓶颈雷达：突出展示当前产业瓶颈和过剩风险。
- 环节画像：按产业顺序显示商业模式、核心指标、壁垒和风险。
- 公司矩阵：紧凑表格展示产业位置、核心产品、下游和竞争优势。

### 产业全景右侧面板

选中环节或公司时，在原节点描述下方展示对应画像摘要。保留原证据列表，但不将证据作为本期视觉主角。

## 视觉方向

主题是“个人产业研究编辑台”，不做通用数据大屏。

- 底色：石墨灰蓝 `#081118`。
- 主文字：冷白 `#E9F1F4`。
- 结构色：青碧 `#4ED7D1`。
- 景气色：上行 `#5CC9A5`、平稳 `#7FA7B4`、降温 `#D18478`、分化 `#D9B56D`。
- 风险色：低饱和琥珀 `#D9B56D`。
- 标题使用 `Noto Sans SC`，数据与状态使用 `IBM Plex Mono`，正文使用 `IBM Plex Sans`。

标志性元素是一条“产业状态带”：它不是装饰性大数字，而是用四个紧凑刻度直接编码产业阶段、景气、供需和周期。其他区域保持安静，不堆叠渐变、玻璃卡片和无意义动画。

## 响应式与可访问性

- 桌面端使用宽内容区与双列环节画像。
- 平板收缩为单列，公司矩阵可水平滚动。
- 手机端状态带改为两列，内容卡改为单列。
- 所有切换、卡片和行项均有明确焦点态。
- 颜色不是状态的唯一载体，同时显示中文标签。
- 遵循 `prefers-reduced-motion`，状态带仅使用轻量入场过渡。

## 空状态与兼容

- V1 图谱没有研究内容：研究面板解释“刷新图谱后生成”，不伪造示例数据。
- 某个环节或公司缺少画像：仅隐藏对应模块，保留节点基础描述。
- 列表字段为空：不渲染空标题或占位符。

## 测试

- Synthesis：研究内容解析、枚举和节点引用校验。
- DAO：新修订内容往返序列化，旧数据库自动增列。
- Frontend：研究视图切换、状态翻译、环节画像、公司矩阵和空状态。
- Build：后端全量测试与前端测试/生产构建。

## 非目标

- 不接入实时价格、库存、产能或财务时序数据。
- 不增加原材料、设备、地理和物流等新节点类型。
- 不建设技术路线对比和跨产业链关联。
- 不生成股价预测、目标价或买卖建议。
