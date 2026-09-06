# 次日联合预测与横截面排序实施计划

> 使用 executing-plans 在本任务内独立执行；用户明确禁止子智能体和工作树。

**Goal:** 提升现有次日预测和选股的建模能力，训练并比较联合 LightGBM 与独立 LambdaRank；保留无法证实提升的真实结果。

**Architecture:** Python 构建按日期分组的次日收盘标签和增强因子，训练概率、收益、排序模型，冻结独立测试证据并发布同日联合预测快照。股票发现用有证据的排序选择深度候选；单股报告复用同日快照。Java 透传独立领域 DTO，React 在原卡片中展示联合模型及排序证据，无新 Tab。

**Tech Stack:** 现有 Python/scikit-learn + LightGBM；Spring/SQLite、React/TypeScript。

## 方法与边界

- 参考 Qlib Alpha158 的量价特征思路，复用已有 20 个特征及截面特征；新增 K 线形态、RSV、量价相关与成交量波动。并非完整复现 Alpha158。
- 参考 https://arxiv.org/abs/2012.07149 的同日横截面排序目标；使用 LightGBM 官方 LGBMRanker（LambdaRank、按日 group、同日收益分位等级 label）。排名得分不转换为概率。
- 概率模型为 LightGBM 二分类，收益为 LightGBM 回归；联合逻辑回归及历史上涨频率为对照。MASTER 是后续候选，本期不引入 Transformer。
- 标签固定为下一市场交易日 close / 当前 close - 1，必须验证目标股票在该日有日线，避免将停牌后收益错当作次日收益。只使用截至当时可观察的特征。
- 共同截止日截断原始行情；当前预测只有在快照日期、目标股票有效行情指纹一致时使用。同日快照包含其截面位置，单股入口不伪造其他股票的特征。
- 日期分为开发、选择、校准、最后 60 日测试。开发/选择/校准均清除跨越下一分段的未成熟标签。固定超参数，不根据最终测试结果调参。
- 分类和排序分别评价；分类比较 Brier、准确率，排序比较逐日 Rank IC、Top 5 平均收益、同日候选池和动量排序。模型选择只看选择段，测试作为最终验收。
- 发布时使用固定配置按最新成熟数据重训，测试成绩仍来自历史时点模型，不能用重训后的拟合成绩替换。
- 当前股票池由已有行情覆盖决定，历史评估属于此股票池内条件性比较，不能声称消除了幸存者偏差或覆盖全部 A 股。

## 任务

- [ ] 数据与特征：新增 `forecast/joint_dataset.py`；测试 `tests/test_joint_dataset.py` 覆盖未来数据不变性、次日停牌排除、截面同日分组、短历史和特征有限性。先运行失败测试，再实现。
- [ ] 训练与对照：新增 `forecast/joint_training.py`，包括固定时序切分、LightGBM classifier/regressor/ranker、概率校准及独立测试指标；测试 `tests/test_joint_training.py` 覆盖标签边界、排名分组、固定测试不参与选择、可学习合成信号、不可推广的弱模型。
- [ ] 快照与应用：新增 `forecast/joint_snapshot.py`，存储 JSON 预测快照及验证证据（不反序列化不可信 pickle）；`app.py`、`forecast/service.py`、`discovery/service.py`、`discovery/ranking.py` 接入。校验日期和行情指纹，缺少有效联合模型仍保留单股结果。专项测试验证实际接入与回退。
- [ ] 页面与契约：Python `next_session_types.py`、Java `domain/quant/forecast` 独立联合证据 DTO、React `NextSessionForecast.tsx` / `quantTypes.ts`。测试确认概率与排序指标分开，旧报告兼容。
- [ ] 真实实验：在本地真实日线上训练，保存 JSON 对照结果；针对代表性股票补充同日期原单股模型对照。结果不佳不得反复窥探最终测试再调参。
- [ ] 验证与交付：相应 Python/Java/前端测试与构建；重启原服务，真实 API、浏览器检验两部分功能；更新实验结论，分批提交推送。

## 执行命令与验收

`market-data-service/.venv/bin/pytest market-data-service/tests/test_joint_dataset.py -q`

`market-data-service/.venv/bin/pytest market-data-service/tests/test_joint_training.py market-data-service/tests/test_joint_snapshot.py -q`

`cd frontend && npm test -- --run`；`npm run build`。

Java 使用本地 Java 21，逐次执行 Maven，避免并行编译破坏运行中的测试类路径。任何修改不得覆盖用户 `application.yml` 改动或输出密钥。

每批通过后 `git add` 精确文件，`git commit -m 'feat: 中文主题'`，`git push -u origin HEAD`。最终保留新分支供用户 review，不自动合并。
