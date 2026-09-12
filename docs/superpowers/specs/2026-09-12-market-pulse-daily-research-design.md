# Market Pulse 日频研究增强

用户已批准上一轮评审提出的下一版范围：行业机会筛选、小范围人工主题地图、基础赚钱效应。沿用今日雷达入口与现有视觉，不新增顶层页面，不接盘中数据。

## 数据与边界

- 行业筛选读取当前 workspace 的行业快照；支持全部、初步转强、中期强势回落、弱势修复；定义公开展示：初步转强为 5日超额收益>0、1日收益>0；回落为20日收益>0、1日收益<0；修复为20日收益<0、5日收益>0。附加最低上涨比例、最高5日涨幅、搜索；缺失所需指标的行业排除。选择最多三个行业比较，进入股票发现时携带选中的行业和日期。
- Python 新增 GET /v1/markets/CN-A/daily-research?business_date=YYYY-MM-DD，仅读取已有 SnapshotStore 本地日K。使用已维护交易日历确定前一交易日和连续窗口，未知日期失效关闭。不主动抓全市场。股票范围排除指数、基金和其他非A股代码。
- 输出 schema_version=daily-research-v1，business_date、selection_date、source_code=LOCAL_DAILY_BAR_PANEL、quality_status=PARTIAL或UNAVAILABLE、sample_count、stocks、groups、warnings。百分比字段统一百分点；代码统一600519.SH。
- stocks 字段：instrument_code、return_1d/5d/20d（可空）、amount（可空）、group_codes。仅以同日有效行情计算结果，缺失当日仍保留已入组成员。
- groups 固定 STRONG/TREND/BREAKOUT：昨日单日收益>=3%；昨日收盘>昨日MA20且昨日5日收益>0；昨日收盘>此前20日收盘最高值。成员只用 selection_date 及之前的数据确定，今日涨跌不参与选组。groups 字段 code、label、definition、eligible_count（可判断规则的数量）、member_count、valid_count、advance_ratio、median_return、members（代码列表）。今日无数据仍计入member_count，不计入valid_count。收益分布由前端根据成员收益呈现。少于5个有效成员不做统计结论。
- Java 通过既有 FinanceHttpClient 的 RPC 适配器校验版本、日期、代码、有限数值与计数，再经 Service/Web Response 对外提供 GET /api/market-pulse/research/{businessDate}。独立加载失败只影响新模块，不影响原工作台。
- 主题在当前浏览器本地保存（明确提示位置），默认三个空白模板：算力建设、机器人、半导体。用户维护名称和成员，每行“代码,名称,产业环节,归属依据”，不预填未经核实的公司映射。每个主题独立去重，最多5个主题、每主题50只。编辑后重新确定生效日期；历史截面早于生效日期不计算主题表现。等权平均成员区间收益；按环节展示广度、成交额和覆盖率，覆盖不足80%或有效成员少于3不输出主题聚合结论。成员可加入已有自选、打开已有产业链研究。
- 所有主题收益为当前篮子区间表现，明确不代表历史成分策略收益。日K快照可能修订，不声称不可变历史回测；不引入胜率或收益预测。

## 验收

筛选条件可复算、缺失值不冒充0、三个候选可比较并传递研究上下文。股票组跨日不使用未来数据、跨假日正确、停牌缺失不抹除成员、未复权与混合复权不生成多日趋势结论。主题可编辑、重载保留、重复/非法成员拒绝、历史生效边界明确。请求切换日期丢弃旧响应，失败可重试。桌面与窄屏可用，已有市场视图回归通过。
