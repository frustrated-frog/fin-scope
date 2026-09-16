import { useState } from 'react';
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
  const [query, setQuery] = useState('');
  const [source, setSource] = useState('ALL');
  const [page, setPage] = useState(0);
  if (!report.strength_watchlist) {
    return null;
  }
  const audit = report.discovery_audit;
  const items = report.strength_watchlist;
  const filtered = items.filter(item => (source === 'ALL' || item.sources.includes(source))
    && `${item.name} ${item.code}`.toLowerCase().includes(query.trim().toLowerCase()));
  const pageCount = Math.max(1, Math.ceil(filtered.length / 8));
  const currentPage = Math.min(page, pageCount - 1);
  const visible = filtered.slice(currentPage * 8, currentPage * 8 + 8);
  const sources = [...new Set(items.flatMap(item => item.sources))];
  return <section className="strength-discovery" aria-label="短期强势股研究">
    <header className="strength-heading"><div><span className="strength-eyebrow">事件观察 · 次日研究</span><h3>短期强势观察池</h3></div><time>{report.as_of_date}</time></header>
    <p className="strength-intro">先看已经发生的强势事件，再查看次日历史统计。观察名单不代表买入建议。</p>
    <div className="strength-overview">
      <div><span>观察股票</span><strong>{items.length}<small>只</small></strong></div>
      <div><span>完成深度分析</span><strong>{audit?.event_deep_count ?? '—'}<small>只</small></strong></div>
      <div className="strength-scan"><span>来源覆盖</span><b>{audit ? label(audit.scan.status) : '暂无记录'}</b></div>
      <details className="strength-scan-detail"><summary>扫描详情</summary>
        <p>强势事件 {audit?.event_count ?? '—'} · 扫描名额截断 {audit?.scan.truncated_count ?? 0}</p>
        <p>深度名额：{Object.entries(audit?.sector_seats ?? {}).map(([name, count]) => `${name} ${count}`).join('、') || '暂无'}</p>
        {audit?.scan.warnings?.map(warning => <p key={warning}>{warning}</p>)}
      </details>
    </div>
    <div className="strength-toolbar">
      <label className="strength-search"><span>查找股票</span><input type="search" placeholder="股票名称或代码" value={query} onChange={event => { setQuery(event.target.value); setPage(0); }} /></label>
      <label><span>事件来源</span><select value={source} onChange={event => { setSource(event.target.value); setPage(0); }}><option value="ALL">全部来源</option>{sources.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
      <span className="strength-result-count" role="status">找到 {filtered.length} 只</span>
    </div>
    <div className="strength-list">{visible.map(item => {
      const evidence = item.assessment;
      return <article className="strength-stock" key={item.code}>
        <div className="strength-stock-main">
          <div className="strength-identity">
            {onOpenResearch ? <button className="strength-stock-link" type="button" aria-label={`${item.name} ${item.code}`} onClick={() => onOpenResearch(item.code)}>{item.name}<span>{item.code} ↗</span></button>
              : <strong>{item.name}<span>{item.code}</span></strong>}
            <div className="strength-tags">{item.sources.map(value => <span key={value}>{label(value)}</span>)}</div>
          </div>
          <div className="strength-metric"><span>近 5 日涨跌</span><strong>{pct(evidence.features?.return_5)}</strong><small>成交额比 {evidence.features?.amount_ratio_20?.toFixed(1) ?? '—'}</small></div>
          <div className="strength-metric"><span>历史次日上涨比例</span><strong>{pct(evidence.up_probability)}</strong><small>同类样本 {evidence.sample_count} 例</small></div>
          <div className="strength-state"><span className="strength-status" data-limited={evidence.status !== 'WATCH'}>{label(evidence.status)}</span><small>{item.rejection_reasons.length ? item.rejection_reasons.map(label).join('、') : '基础准入通过，仍需门禁验证'}</small></div>
        </div>
        <details className="strength-stock-detail"><summary>展开风险与验证<span>涨幅分布 · 失败样本 · 概率误差</span></summary>
          <div className="strength-detail-grid">
            <section><h4>上涨与延续</h4><dl><div><dt>涨幅至少 3%</dt><dd>{pct(evidence.continuation_probability)}</dd></div><div><dt>上涨比例 95% 区间</dt><dd>{evidence.up_interval?.map(pct).join(' ～ ') ?? '—'}</dd></div><div><dt>平均次日涨跌</dt><dd>{pct(evidence.mean_return)}</dd></div></dl></section>
            <section><h4>失败与回落</h4><dl><div><dt>冲高回落率</dt><dd>{pct(evidence.fade_probability)}</dd></div><div><dt>亏损率</dt><dd>{pct(evidence.loss_rate)}</dd></div><div><dt>历史疑似一字涨停</dt><dd>{pct(evidence.one_price_limit_rate)}</dd></div></dl><small>可成交性未验证</small></section>
            <section><h4>验证可靠性</h4><dl><div><dt>滚动验证</dt><dd>{evidence.validation_count ?? 0} 例</dd></div><div><dt>模型 Brier</dt><dd>{evidence.brier_score?.toFixed(3) ?? '—'}</dd></div><div><dt>基线 Brier</dt><dd>{evidence.baseline_brier_score?.toFixed(3) ?? '—'}</dd></div></dl><small>概率误差越低越好，尚未证明稳定优于基线。</small></section>
          </div>
          <p className="strength-detail-note">疑似连续涨停 {evidence.features?.limit_like_streak ?? '—'} 日 · 统计包含失败样本，历史比例不等同于明日获利概率。</p>
        </details>
      </article>;
    })}</div>
    {!filtered.length && <div className="strength-empty"><strong>{items.length ? '没有匹配的股票' : '本批次未检出强势事件'}</strong><p>{items.length ? '试试其他名称、代码，或切换事件来源。' : '请结合来源覆盖状态判断，不能等同于全市场没有机会。'}</p>{!!items.length && <button type="button" onClick={() => { setQuery(''); setSource('ALL'); setPage(0); }}>清除筛选</button>}</div>}
    {!!filtered.length && <nav className="strength-pagination" aria-label="强势观察池分页"><span>第 {currentPage + 1} / {pageCount} 页 · 每页最多 8 只</span><div><button type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>上一页</button><button type="button" disabled={currentPage + 1 >= pageCount} onClick={() => setPage(currentPage + 1)}>下一页</button></div></nav>}
    <div className="strength-audit">
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
    </div>
  </section>;
}
