import type { StockDiscoveryReport } from './quantTypes';
import './StrengthDiscoveryPanel.css';

const labels: Record<string, string> = {
  LIMIT_UP: '涨停池', BROKEN_LIMIT: '炸板池', SPOT: '全市场异动', HOT_SECTOR: '热门行业',
  OVER_BUDGET: '超出一手预算', INSUFFICIENT_QFQ_HISTORY: '完整模型历史不足',
  STALE_OR_SUSPENDED_MARKET_DATA: '行情未覆盖研究日期', STALE_MARKET_DATA: '行情过旧',
  MARKET_DATA_UNAVAILABLE: '行情不可用', LOW_LIQUIDITY: '成交不足', SPECIAL_TREATMENT: '特殊处理股票',
  SEAT_LIMIT_OR_SECTOR_CAP: '名额或行业上限', DEEP_ANALYSIS_FAILED: '深度分析失败',
  OUTSIDE_CANDIDATE_POOL: '未进入候选池', NOT_DEEP_REVIEWED: '未进入深度分析',
  COMPLETE: '来源获取完成', PARTIAL: '来源覆盖不完整', UNAVAILABLE: '来源不可用',
  BEFORE_CLOSE: '等待收盘', WATCH: '观察', INSUFFICIENT_DATA: '同类样本不足', STALE_DATA: '行情日期不符',
};
const label = (value: string) => labels[value] ?? value;
const pct = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function StrengthDiscoveryPanel({ report, onOpenResearch }: {
  report: StockDiscoveryReport; onOpenResearch?: (code: string) => void;
}) {
  if (!report.strength_watchlist) {
    return null;
  }
  const audit = report.discovery_audit;
  return <section className="strength-discovery" aria-label="短期强势股研究">
    <header><h3>短期强势观察池</h3><span>{report.as_of_date} · {report.strength_watchlist.length} 只</span></header>
    <p>已发生的强势事件与次日预测分开展示。次日数值来自该股历史同类事件，包含失败样本；尚未证明能稳定优于基线。</p>
    {audit && <div className="strength-coverage">
      <p>扫描状态：{label(audit.scan.status)} · 强势事件 {audit.event_count} · 完成深度分析 {audit.event_deep_count} · 扫描名额截断 {audit.scan.truncated_count ?? 0}</p>
      <p>深度名额：{Object.entries(audit.sector_seats).map(([name, count]) => `${name} ${count}`).join('、') || '暂无'}</p>
      {audit.scan.warnings?.map(warning => <p key={warning}>{warning}</p>)}
    </div>}
    <div className="strength-table-scroll"><table><thead><tr>
      <th>股票 / 来源</th><th>次日上涨统计</th><th>冲高回落 / 亏损率</th><th>评测与准入</th>
    </tr></thead><tbody>{report.strength_watchlist.map(item => {
      const evidence = item.assessment;
      return <tr key={item.code}>
        <td><button type="button" onClick={() => onOpenResearch?.(item.code)}>{item.name} {item.code}</button><small>{item.sources.map(label).join('、')}</small>
          {evidence.features && <small>近 5 日 {pct(evidence.features.return_5)} · 成交额比 {evidence.features.amount_ratio_20?.toFixed(1) ?? '—'} · 疑似连续涨停 {evidence.features.limit_like_streak ?? 0} 日</small>}</td>
        <td>{pct(evidence.up_probability)}<small>同类样本 {evidence.sample_count} · {label(evidence.status)}</small>
          <small>涨幅至少 3%：{pct(evidence.continuation_probability)}</small>
          {evidence.up_interval && <small>95% 区间 {evidence.up_interval.map(pct).join(' ～ ')}</small>}</td>
        <td>{pct(evidence.fade_probability)} / {pct(evidence.loss_rate)}<small>平均次日涨跌 {pct(evidence.mean_return)}</small><small>历史疑似一字涨停 {pct(evidence.one_price_limit_rate)}</small><small>可成交性未验证</small></td>
        <td><small>滚动验证 {evidence.validation_count ?? 0} 例</small><small>Brier {evidence.brier_score?.toFixed(3) ?? '—'} / 基线 {evidence.baseline_brier_score?.toFixed(3) ?? '—'}</small>
          <small>{item.rejection_reasons.length ? item.rejection_reasons.map(label).join('、') : '基础准入通过，仍需门禁验证'}</small></td>
      </tr>;
    })}</tbody></table></div>
    {!report.strength_watchlist.length && <p>本批次未检出强势事件；请结合来源覆盖状态判断，不能等同于全市场没有机会。</p>}
    {!!audit?.misses.length && <details><summary>查看 {audit.misses.length} 只未完成深度分析的强势股</summary>
      <ul>{audit.misses.map(item => <li key={item.code}>{item.name} {item.code}：{item.reasons.map(label).join('、')}</li>)}</ul>
    </details>}
    <details><summary>冻结名单的次日漏选与失败评测</summary>
      <p>以缓存中同时覆盖信号日、次日的股票为评测范围，并非完整历史全市场；“强势上涨”统一按次日收盘涨幅至少 5% 统计。</p>
      {!report.recall_evaluations?.length && <p>等待首批冻结名单到期；不使用事后重排结果补成绩。</p>}
      {report.recall_evaluations?.map(item => <article key={item.signal_date}>
        <strong>{item.signal_date} → {item.target_date ?? '等待次日'} · {item.evidence_kind === 'FORWARD' ? '提前冻结' : '历史回顾'}</strong>
        <p>覆盖 {item.covered_count ?? 0} 只 · 强势上涨 {item.winner_count ?? 0} 只 · 观察池召回 {pct(item.event_recall)} · 深度召回 {pct(item.deep_recall)}</p>
        <p>强势事件亏损率 {pct(item.event_loss_rate)} · 冲高回落率 {pct(item.event_fade_rate)}</p>
        <ul>{item.missed_winners?.map(miss => <li key={miss.code}>{miss.code}：次日 {pct(miss.actual_return)}，{miss.reason.split(',').map(label).join('、')}</li>)}</ul>
      </article>)}
    </details>
  </section>;
}
