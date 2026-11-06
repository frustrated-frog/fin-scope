import { useState } from 'react';
import { selectionProtocol, type SameDaySelection, type SelectionForwardGroup, type SelectionArm } from './overnightSelectionTypes';
import './OvernightSelectionProgress.css';

const arms: Array<[SelectionArm, string]> = [['INDEX', '个股 + 指数'], ['STOCK_ONLY', '仅看个股'], ['INDEX_ONLY', '仅看指数']];
const percent = (value: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function OvernightSelectionProgress({ cutoff, historical, forward }: {
  cutoff: string; historical?: SameDaySelection; forward?: SelectionForwardGroup;
}) {
  const [history, setHistory] = useState(false);
  const past = historical?.protocol === selectionProtocol ? historical : undefined;
  const live = forward?.sameDaySelection.protocol === selectionProtocol ? forward.sameDaySelection : undefined;
  const showHistory = history && !!past;
  const result = showHistory ? past : live;
  return <section className="overnight-selection" aria-label={`${cutoff} 同日选股对照`}>
    <header><h5>选出的股票，是否更容易上涨</h5><span>同日选股对照</span></header>
    <div className="overnight-selection-switch" role="group" aria-label={`${cutoff} 对照来源`}>
      <button type="button" aria-pressed={!showHistory} onClick={() => setHistory(false)}>真实次日结果</button>
      <button type="button" aria-pressed={showHistory} disabled={!past} onClick={() => setHistory(true)}>历史研究</button>
    </div>
    {showHistory ? <p>历史研究用于诊断，不代表真实预测准确率，也不会自动替换主判断。</p>
      : <p>只核验当时自动发现并完整留档的名单。已配对 {forward?.pairedCount ?? 0} / {forward?.scheduledCount ?? 0} 条，完整日期 {live?.dayCount ?? 0} / {forward?.scheduledDays ?? 0} 日。</p>}
    {result?.dayCount ? <>
      <div className="overnight-selection-table">
        <table>
          <caption>同一天、同一名单、相同名额 · 次日实际上涨占比</caption>
          <thead><tr><th scope="col">对照方案</th><th scope="col">固定前三名</th><th scope="col">相同候选名额</th></tr></thead>
          <tbody>{arms.map(([key, label]) => <tr key={key}>
            <th scope="row">{label}{key === 'INDEX_ONLY' && <small>同日不区分个股</small>}</th>
            <td>{percent(result.forcedTopK.precision[key])}</td><td>{percent(result.matchedCandidates.precision[key])}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <p className="overnight-selection-counts">{result.dayCount} 个完整日期 · 候选覆盖 {result.matchedCandidates.selectedDays} 日 · 无看涨候选 {result.noCandidateDays} 日</p>
      <p>各日期等权，并列按份额计数。固定前三名包含无看涨候选的日期，仅用于比较排序。无候选不计作猜对；“—”表示没有可统计的候选。</p>
    </> : <p className="overnight-selection-empty">尚无完整日期可统计。后台将自动补齐次日结果，缺失记录仍保留在分母中。</p>}
  </section>;
}
