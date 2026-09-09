# 次日方向校准与滚动学习实施计划

> 按 executing-plans 在当前分支逐项执行；用户明确禁止子智能体和隔离树。

**目标：** 实现《量化增强-9.9.md》第一阶段 E0，分离校准损耗与模型陈旧的影响，给后续条件融合提供可复用的真实时序样本外概率。

**架构：** 复用 JointDataset、日期等权、既有树参数、日期分块配对评价和只读行情加载。新校准器独立于旧 Platt，保留旧行为作为实验对照；测试期结果不参与方法选择。现有页面增加方向分数诊断，无新 tab。

**技术：** Python / NumPy / SciPy / LightGBM / pytest；React / TypeScript。

## 实验规则（查看本轮结果前固定）

- 固定缓存 SHA256 顺序 240 股和原 60 日测试区，同池同日；全程标记 RETROSPECTIVE，不声称历史全 A 股或前瞻胜率。
- 候选族为 BASE_TREE、FULL_TREE、POOLED_LOGISTIC；现有参数固定。校准候选为 RAW、INTERCEPT、单调 PLATT（恒等收缩强度 0.001/0.01/0.1）。旧 Platt 独立对照。
- 每 5 个信号日期重训，训练最多 505 个日期，标签 exit_date 严格早于本批首日；这是保守的盘后可得性处理。校准最多使用此前 60 个日期的成熟 OOF 预测，同一模型族、同一更新规则，不混合模型族。
- 原 selection 区前预热 60 日 OOF，selection 区选模型与校准，calibration 区用于后续滚动积累，最后 test 区按预先固定规则更新参数但不重新选方法。
- 选择：日期等权 Brier 不劣于训练先验 0.001，BA > 0.5，总体 AUC > 0.5，三个连续时期中至少两个准确率优于先验；合格者按准确率、BA、Brier 排序。无合格者回退 BASE_TREE + RAW，明确未通过选择门槛。
- 2×2：同一预先选定模型族，固定训练/滚动训练 × 旧校准/所选新校准；另存两种训练的原始概率。固定训练沿用旧 train 区，固定校准用独立 calibration 区。报告校准数据机制差异，不将交互解释为单一原因。
- 保存逐股票日 raw/calibrated 概率、标签、模型批次、训练及校准截止、参数、年龄；同时报告上涨预测比例、概率分位数、准确率、BA、总体和同日 AUC、Brier、Log Loss。
- 历史实验不自动替换线上概率，不篡改已冻结预测。先修正证据可见性，后续模型接入必须明确影子和前瞻验证口径。

## 批次 1：校准和诊断

- [ ] 新增 `tests/test_direction_calibration.py`：47%/51% 已校准分桶保持决策；权重复制不变；单调性；无效/不足样本回退。
- [ ] 执行 `.venv/bin/pytest tests/test_direction_calibration.py -q`，确认因缺少实现失败。
- [ ] 新增 `forecast/direction_calibration.py`，复用 CalibrationResult，正则向 (1,0) 收缩，斜率非负、只截距模式、日期权重，无正则化损失退化容忍。
- [ ] 在 `direction_evaluation.py` 增加 `probability_diagnostics` 并集成，测试总体与截面 AUC 的区别和单类日期分母。
- [ ] 通过相关测试、自检后提交推送。

## 批次 2：滚动实验

- [ ] 新增 `tests/test_rolling_direction.py`，验证训练/校准无未来标签、每五日新模型、未来标签变化不影响之前预测及预先选择。
- [ ] 新增 `forecast/rolling_direction.py`，生成同族 OOF、预先选择和四组配对比较；复用 fit_candidates 并支持只拟合指定族，避免无关重训。
- [ ] 新增 `scripts/evaluate_rolling_direction.py`，复用只读行情加载，保存压缩逐条预测、输入及代码指纹、参数和汇总。
- [ ] 固定 240 股实跑，记录所有结果，不能以测试期最佳候选冒充预先选择结果。
- [ ] 验证后提交推送。

## 批次 3：现有入口与运行验证

- [ ] 联合训练证据记录所选模型校准前后诊断及生产训练截止；现有股票发现/单股预测展开区展示预测上涨比例、AUC和校准影响。
- [ ] 定向 Python/前端测试、生产构建，完整 Python 回归，按项目规范自检，提交推送。
- [ ] 重启受影响服务，通过实际 API 和浏览器验证新证据、次日日期及影子状态。
- [ ] 在 `docs/quant/2026-09-09-calibration-rolling-review.md` 记录实测、限制和下一阶段依据，更新清单，提交推送。

后续 E1 行业历史、分钟数据、条件残差融合分别形成实验，不在 E0 内凭空生成缺失历史或承诺 +2 个百分点。
