# 尾盘参考价预测与数据连续性修复

## 目标和版本

用户关注的是 14:30 之后买入、次日价格能否上涨。新版训练标签改为“次一交易日收盘价 / 判断时可见参考价 - 1”，不扣费用，不用当日稍后的收盘价充当尾盘买入起点。

- 尾盘：14:30、14:45 分别训练、冻结和核验。
- 盘后持仓：参考时点仍为 15:00，保留独立场景。
- 方向协议：`overnight-reference-direction-v2`；环境增强协议：`overnight-context-reference-v3`。
- 归档版本：`overnight-v6-decision-direction`；目标：`NEXT_SESSION_CLOSE_VS_DECISION_PRICE`。
- 参考价随预测冻结。即便缓存行情之后修订，到期核验仍使用冻结价格。
- 原始价格观察不能证明用户实际成交；当前目标仍是未复权价格方向，普通分红除权覆盖不完整。

旧模型、旧校准门槛和旧前瞻成绩不继承为新版资格。旧预测按原来的收盘对收盘口径核验；后台训练任务键包含方向协议，新版本会自动训练并重新积累前瞻记录。不同模型版本的预测不相互覆盖。

## 采集与自动运行

东方财富五分钟源连接失败时，自动使用新浪五分钟源。备用源必须提供真实成交额、完整已结束窗口及有效 OHLC；缺额、过期、缺柱、冲突数据均不能伪装成功，不以价格乘成交量代替真实成交额。

14:20—14:30、14:40—14:45 按分钟预采集候选行情，每次最多三个并行请求。预采集只写缓存，不提前产生预测；正式窗口仍冻结当时结果。有限返回条数造成的最早半天数据不会覆盖完整历史日期。

自动任务区分“任务执行完成”和“形成有效前瞻涨跌概率”。只有真实前瞻方向输出才计入生成数量；全部缺失时返回 `NO_PREDICTION`，不会把有档案 ID 或 `WATCH` 状态视为预测成功。

## 本地开发对照结果

使用截至 2026-11-04 00:00 的本地分钟缓存，按股票代码 SHA-256 排序固定取前 40 只，不按收益选股。形成 7,479 个有效标签、192 个信号日；14:30 模型最近 20 个逐日检验日共 609 例，按日期等权统计。

| 方案 | 方向命中 | 涨/不涨平均识别 | Brier（越低越好） |
| --- | ---: | ---: | ---: |
| 原有 Brier 选择方法，使用新版目标 | 58.24% | 51.39% | 0.244935 |
| 平衡方向选择方法，使用相同标签 | 58.96% | 50.00% | 0.244844 |
| 预测时可获得的历史比例基准 | 58.96% | 50.00% | 0.244266 |

平衡方案的 20 折全部退回历史比例。原方案相对基准命中率差异的 95% 日期分块区间为 -5.18 至 +3.13 个百分点。此次未证明模型改法提高了准确性，因此不据此替换正式选择方法。上述结果是开发诊断，不是实盘或真实前瞻成绩；股票池有来源覆盖和幸存偏差。

输入标签指纹：`d3733a276a6cf7bf015c2ec18c7542e1aec4b408798c7e267942308dda746e97`。

现有冻结输入评估脚本增加同样本选择方法对照，可复现而不发布模型：

```bash
cd market-data-service
PYTHONPATH=src .venv/bin/python scripts/evaluate_overnight_direction.py \
  --input-dir /path/to/frozen-minute-inputs --output-dir /path/to/new-audit \
  --cutoffs 14:30 --compare-selection
```

输入沿用现有 `protocol.json`（codes、through）与每股 `<code>.json.gz` 分钟数组。

## 档案连续性与恢复

本地 `market-data-service/data/overnight-research.db` 与 Docker `/app/data/overnight-research.db` 是不同数据库。前者有 210 份较早档案，后者仍在持续运行。新增工具只迁移预测及结果，保留原始 ID、请求、生成时间、输入和所有结果版本；不会拷贝模型、配置或分钟缓存。

```bash
cd market-data-service
# 只读导出两张证据表，拒绝覆盖已有文件
PYTHONPATH=src .venv/bin/python -m finscope_market_data.overnight.archive_transfer \
  --source data/overnight-research.db --export /tmp/legacy-archive.db
# 默认只预检；加 --apply 后先用 SQLite backup 备份目标库再合并
PYTHONPATH=src .venv/bin/python -m finscope_market_data.overnight.archive_transfer \
  --source /tmp/legacy-archive.db --target /path/to/current/overnight-research.db
```

同 ID 或同请求冲突时整体停止，结果冲突或缺少原始预测也拒绝导入。相同记录幂等跳过。备份位于目标库旁，后缀为 UTC 时间和 `.bak`。`--apply` 会重新预检，不能以先前预检结果绕过冲突。历史记录数与可用涨跌预测数分别展示，恢复数量不代表预测成绩。

## 页面和生效方式

今日列表只展示股票、明日判断、上涨概率和依据；历史比例回退显示“证据不足”，不冒充个股模型预测。详情及复盘围绕参考价到次日收盘展开，旧口径显式标注；费用、净收益与多个退出时点不再占据日常预测主流程。

Vite 开发模式可热更新前端。Python 当前在 Docker 中，源码未挂载；更新运行版本需要从项目根目录执行 `docker compose up -d --build --no-deps market-data`。该命令保留命名数据卷，禁止删除数据卷。无需改动或重新编译 Java DTO。本轮没有将事后生成的结果补记成当时预测。

## 提交自检

复用分钟 Provider、Store、自动任务、方向训练、前瞻验证和现有页面组件。没有新增依赖、Java Bean 或跨层引用；SQL 数据参数化，动态表名仅来自固定白名单。覆盖了备用源失败、半日数据、冻结参考价、旧口径隔离、档案冲突回滚与重复导入，以及前端缺失概率和历史比例展示。
