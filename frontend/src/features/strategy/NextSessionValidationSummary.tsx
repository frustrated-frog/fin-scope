import type { NextSessionPredictionRecord } from './quantTypes';
import { summarizeNextSession } from './nextSessionValidation';

const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function NextSessionValidationSummary({ records }: { records: NextSessionPredictionRecord[] }) {
  return <div className="next-validation" aria-label="真实预测效果验收">
    <h4>真实预测效果验收</h4>
    <p>当前加载最近 {records.length} 条记录（最多 100 条），不是全量历史。按版本分组，同股同目标日保留本窗口内最早生成的一条；先按日内股票平均，再按交易日等权。</p>
    <details><summary>三类次日研究的统计口径</summary>
      <p>收盘方向：目标日收盘相对信号日收盘的涨跌，不扣交易费用。本区只评估这一口径。</p>
      <p>强势观察：该股历史类似事件的结果频率，不直接等于当前获利概率。</p>
      <p>尾盘与盘后：各自按入场价格代理或持仓收盘参考价，计算四个退出时点的净收益，单独复盘。</p>
    </details>
    {summarizeNextSession(records).map(group => <article key={group.version} className="next-validation-version">
      <h5>{group.version}</h5>
      <dl><div><dt>到期有效记录 / 交易日</dt><dd>{group.count} / {group.days}</dd></div><div><dt>按日等权方向命中</dt><dd>{percent(group.accuracy)}</dd></div><div><dt>概率误差 Brier</dt><dd>{group.brier?.toFixed(4) ?? '—'}</dd></div></dl>
      <p>待到期 {group.pending} 条 · 无法验证 {group.unavailable} 条 · 重复记录排除 {group.duplicates} 条。{group.days < 60 ? '独立预测日不足 60 天，仅供早期观察。' : '样本数量不代表已证明优势。'}</p>
      <details><summary>概率是否说得准：分段对照</summary><div className="quant-table-wrap"><table><thead><tr><th>预测概率段</th><th>记录 / 日期</th><th>平均预测概率</th><th>实际上涨比例</th></tr></thead><tbody>{group.bins.map(bin => <tr key={bin.lower}><td>{percent(bin.lower)}–{percent(bin.upper)}{bin.upper === 1 ? '（含上界）' : '（不含上界）'}</td><td>{bin.count} / {bin.days}</td><td>{percent(bin.forecast)}</td><td>{percent(bin.actual)}</td></tr>)}</tbody></table></div></details>
    </article>)}
    <p>当前冻结账本未保存逐条基线预测，不能用训练期 Brier 代替真实前瞻基线，因此暂不判定“优于基线”。方向命中不代表扣费后盈利。</p>
  </div>;
}
