import { useState } from 'react';
import type { MarketBreadth } from './marketPulseTypes';
import { finite } from './panoramaModel';
import { breadthChanges, deltaText, difference, highLowRows, numberText, shareText, trendRows, validRatio } from './breadthAnalysis';

type Props = { breadth?: MarketBreadth; previous?: MarketBreadth };
export function BreadthTrend({ breadth, previous }: Props) {
  const rows = trendRows(breadth);
  const priors = trendRows(previous);
  const available = rows.filter(row => validRatio(row.ratio));
  const majority = available.filter(row => row.ratio! > .5).length;
  return <section className="mbw-panel" aria-label="多周期趋势参与">
    <header><div><span>短期 → 长期</span><h3>趋势宽度</h3></div><span className="mbw-unit">站上均线的比例</span></header>
    <div className="mbw-diagnosis"><strong>{available.length ? `${available.length} 个有效周期中，${majority} 个参与过半` : '等待趋势样本'}</strong><span>MA20 − MA250 <b>{deltaText(difference(rows[0].ratio, rows[3].ratio))}</b></span></div>
    <div className="mbw-trend-rows">{rows.map((row, i) => <div key={row.window}>
      <div><strong>MA{row.window}</strong><span>{numberText(row.count, 0)} 只样本</span><b>{shareText(row.ratio)}</b></div>
      <div className="mbw-share-track"><i className="mbw-midpoint" />{validRatio(row.ratio) && <b style={{ width: `${row.ratio * 100}%` }} />}{validRatio(priors[i].ratio) && <i className="mbw-prior-tick" style={{ left: `${priors[i].ratio! * 100}%` }} title={`对照 ${shareText(priors[i].ratio)}`} />}</div>
      {previous && <small>较对照 {deltaText(difference(row.ratio, priors[i].ratio))}</small>}
    </div>)}</div>
    <footer>虚线为50%{previous ? '，竖线为对照日' : ''}。各周期按自身有效样本计算。</footer>
  </section>;
}

export function BreadthExtremes({ breadth, previous }: Props) {
  const rows = highLowRows(breadth);
  const priors = highLowRows(previous);
  const maximum = Math.max(.1, ...rows.flatMap(row => [row.highRatio, row.lowRatio].filter(finite)));
  return <section className="mbw-panel" aria-label="强弱两端分析">
    <header><div><span>突破与破位</span><h3>新高 / 新低</h3></div><span className="mbw-unit">同周期有效样本</span></header>
    <div className="mbw-extreme-legend"><span data-tone="up">新高占比 ←</span><span>→ 新低占比</span></div>
    <div className="mbw-extremes">{rows.map((row, i) => <div key={row.window}>
      <div className="mbw-extreme-heading"><strong>{row.window} 日</strong><span>{numberText(row.count, 0)} 只样本</span><small>{!finite(row.balance) ? '等待样本' : row.balance > 0 ? '新高更多' : row.balance < 0 ? '新低更多' : '两端持平'}</small></div>
      <div className="mbw-extreme-bars"><span data-tone="up">{numberText(row.high, 0)} 家 <b>{shareText(row.highRatio)}</b></span><div><i />{finite(row.highRatio) && <b data-tone="up" style={{ right: '50%', width: `${row.highRatio / maximum * 48}%` }} />}{finite(row.lowRatio) && <b data-tone="down" style={{ left: '50%', width: `${row.lowRatio / maximum * 48}%` }} />}</div><span data-tone="down">{numberText(row.low, 0)} 家 <b>{shareText(row.lowRatio)}</b></span></div>
      <p>新高 − 新低 <strong>{finite(row.balance) ? `${(row.balance * 100).toFixed(1)} 个百分点` : '—'}</strong>{previous && <span>较对照 {deltaText(difference(row.balance, priors[i].balance))}</span>}</p>
    </div>)}</div>
    <footer>柱长在三个周期使用相同比例尺，平衡值为新高占比减新低占比。</footer>
  </section>;
}

export function BreadthChanges({ breadth, previous }: Props) {
  const [filter, setFilter] = useState('all');
  const changes = breadthChanges(breadth, previous);
  const rows = changes.filter(row => filter === 'all' || (filter === 'up' ? row.change > 0 : row.change < 0));
  const maximum = Math.max(.01, ...changes.map(row => Math.abs(row.change)));
  return <section className="mbw-panel" aria-label="结构变化排序">
    <header><div><span>两日截面对照</span><h3>结构变化排序</h3></div><select aria-label="结构变化筛选" value={filter} onChange={event => setFilter(event.target.value)}><option value="all">全部变化</option><option value="up">比例上升</option><option value="down">比例下降</option></select></header>
    {rows.length ? <ol className="mbw-changes">{rows.map(row => <li key={row.label}><div><strong>{row.label}</strong><span>{shareText(row.previous)} → {shareText(row.current)}</span></div><div className="mbw-change-track"><i style={{ width: `${Math.abs(row.change) / maximum * 100}%` }} data-direction={row.change >= 0 ? 'up' : 'down'} /></div><b>{deltaText(row.change, 'pp')}</b></li>)}</ol> : <p className="mbw-empty">{previous ? '当前筛选下没有可比较的变化。' : '选择并加载对照日后，查看哪些结构指标变化最大。'}</p>}
    <footer>按变化绝对值排序 · pp为百分点 · 各日样本可能变化，非同一股票群体迁移。</footer>
  </section>;
}
