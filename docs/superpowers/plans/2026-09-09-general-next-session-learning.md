# 通用次日预测强化 Implementation Plan

> **For agentic workers:** 使用 executing-plans 逐项实施；用户禁止子智能体与工作树，沿用当前分支。已获用户批准，无需再次询问执行方式。

**Goal:** 完善次日绝对涨跌、幅度、相对排名三个独立任务，通过通用市场特征、多时段候选比较、日期分组评价提高可泛化能力。

**Architecture:** 保留现有单股与联合模型入口。新增通用 direction_evaluation.py、market_state_features.py、adaptive_classifiers.py；联合训练先在独立于最终 calibration/test 的日期上比较候选，再拟合当前模型。单股及联合报告共享方向评价协议，历史与未来证据分开。

**Tech Stack:** Python / NumPy / LightGBM / sklearn；Java domain DTO / RPC；React 原预测页面。

## 冻结实验协议

- 绝对标签保持下一交易日收盘相对信号日收盘，零涨跌单独计数但归非上涨类别；不使用排名或五日收益代替。
- 通用候选：基础特征树、完整特征树、180 日窗口树、126 日半衰期加权树、标准化逻辑回归、树与逻辑回归等权组合。参数保持现有深度、叶子数、学习率；不根据已经看到的最终测试结果搜索参数。
- train/selection/calibration/test 保留连续日期分组及跨段标签清除。selection 划分三个连续区间报告稳定性；另外在 train 尾部做早一期模型训练/校准/验证，与 selection 共同比较，避免单一时期选择。最终 60 日不参与候选选择。
- 同日股票等权后，交易日等权。比较训练期先验、简单动量、现有逻辑回归；报告 accuracy、balanced accuracy、Brier、置信区间、覆盖率和分市场状态指标。区间按连续 5 个日期为块重采样，不能把同日股票视作独立日。
- 确认通过需概率与方向同时胜过基准，并满足最低独立日期。已见历史仅记 RETROSPECTIVE；新方法冻结日 2026-09-09 及之前的测试不可标前瞻成绩。
- 高频、新闻与复杂神经网络是否引入，由本批通用信息增益和数据覆盖决定；没有完整时点数据就记录暂缓原因，不伪造输入。

## Task 1：统一方向评测与市场通用特征

Files: 新建 `market-data-service/src/finscope_market_data/forecast/direction_evaluation.py`、`market_state_features.py` 与对应 tests；修改 `joint_dataset.py`。

- [x] 写失败测试：同日样本复制不改变日等权分数；全部看涨不能产生虚假的平衡准确率；原训练先验胜过模型时不得通过；改变未来日期不能改变历史市场特征。

```python
assert evaluate_direction(p, y, dates, {'PRIOR': baseline})['balancedAccuracy'] == .5
assert market_state_features({'a': row, 'b': row})['a'] == market_state_features({'a': row, 'b': row})['b']
```

- [x] 运行 `.venv/bin/pytest tests/test_direction_evaluation.py tests/test_market_state_features.py -q`，确认缺失模块/行为导致失败。
- [x] 实现 `evaluate_direction(probabilities, labels, dates, baselines, regimes=None)`：按日期权重、基准逐一配对、块重采样、全样本和高置信度覆盖，输出 JSON 可序列化字典。输入非有限或长度不一致直接拒绝。
- [x] 实现 `market_state_features(features_by_code)`，以当日可见完整横截面计算上涨广度、收益分散、等权收益、趋势广度、流动性分组差异、市场波动及个股与状态交互；不读取未来标签或今日行业归属回填。追加到原特征之后以保持既有索引。
- [x] 测试通过后提交推送。

## Task 2：多时期候选比较与适应模型

Files: 新建 `forecast/adaptive_classifiers.py`、`tests/test_adaptive_classifiers.py`；修改 `joint_training.py`、`tests/test_joint_training.py`。

- [x] 写失败测试：最新日期权重较高但同日总权重相同；候选选择不随最终测试标签变化；基础特征候选看不到新增市场特征。
- [x] 候选统一接口 `fit_candidates(rows, feature_codes, parameters)` 与 `.predict_proba(matrix)`。基础树按 MARKET_STATE_FEATURE_CODES 删除新增列；RECENT_TREE 按最近 180 个交易日；DECAY_TREE 的权重为 `2 ** (-age_days / 126) / same_day_count` 并归一化；其他候选维持同日等权。
- [x] 新增早期滚动比较，模型、校准及评价边界相互分离；以多个比较区间的日等权 Brier/方向基准表现和最差区间结果选出候选，测试区绝不参与选择。输出逐候选、逐时期成绩与入选原因。
- [x] 联合与单股使用统一方向评价，分类门槛与收益/排名门槛分开。生产重训选择同一候选配方，报告验证与当前拟合时间边界。
- [x] 相关 Python 回归通过后提交推送。

## Task 3：协议、页面、真实数据实验与后续复杂度决策

Files: `next_session_types.py`、Java `SingleStockForecast.java` / 新版协议测试、前端 `quantTypes.ts` / `NextSessionForecast.tsx`（以现有实际文件为准）、`scripts/evaluate_general_next_session.py`、`docs/quant/2026-09-09-general-next-session-review.md`。

- [x] 添加可选 `directionEvaluation`、`adaptationEvidence` 证据；旧报告可读，新报告在原页面展示平衡准确率、基准差值、置信区间、覆盖及候选选择摘要。
- [x] 只读现有日线和原快照，冻结新数据集并跑完整候选比较与最终测试；分别列出绝对涨跌、收益幅度、排序结果。记录缓存/存活股票池限制以及已见历史身份。
- [x] 通过消融比较决定保留市场信息、近期权重及组合；不能以排名提升代替方向提升。复杂模型和新闻/分时数据若缺可验证历史，本批输出明确不引入的决定及前提。
- [x] Python 全套、Java 相关协议/服务、前端测试构建；重启并验证真实单股和股票发现，两处都具备新方向证据且次日预测有效。每批独立改动测试后提交推送。

## 自检

不改用户配置与 pnpm-lock；Java 字段注入、完整大括号、领域 DTO 落 domain，外部协议留 RPC；不建立新 Tab，不以拒绝预测后的命中率冒充全样本成绩。

实施结果见 `docs/quant/2026-09-09-general-next-session-review.md`。计划的实现、测试、服务重启和真实页面验证已完成；实证未证明方向提升，因此未提升新联合模型的应用资格。
