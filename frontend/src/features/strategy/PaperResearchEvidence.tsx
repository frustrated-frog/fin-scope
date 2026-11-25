import { useState } from 'react';

interface Horizon {
  observations: number; pairs: number; ic: number | null; rankIC: number | null;
  positiveFraction: number | null; dailyRankICIR: number | null; recent20DayRankIC: number | null;
  upMarketRankIC?: number | null; downMarketRankIC?: number | null; partialRankIC?: Record<string,number | null>;
  groups: (number | null)[]; spread: number | null;
}
export interface Trial {
  candidate: number; name: string; hypothesis: string; factor: string;
  decision?: { action: 'FREEZE' | 'REVISE' | 'STOP'; candidate: number; reason: string; direction: string };
  metrics: { netReturn: number; benchmarkReturn: number; maxDrawdown: number; fills: number; feeRate: number;
    tradedNotionalRate: number; unfilled: Record<string, number>;
    factors: Record<string, { horizons: Record<string, Horizon>; correlations: Record<string, number | null> }> };
}
export interface SymbolCoverage {
  bars: number; days: number; poolDays: number; completeDays: number; measuredVolumeBars: number;
  actionsVerified?: boolean; exDates?: string[]; unsupportedActions?: number;
}
const number = (value: number | null, percent = false) => value == null ? '—' :
  percent ? (value * 100).toFixed(2) + '%' : value.toFixed(3);
const horizonNames: Record<string,string> = { '5m':'5 分钟','30m':'30 分钟','1d':'1 日','3d':'3 日','5d':'5 日' };
const decisions = { FREEZE:'冻结', REVISE:'修订', STOP:'结束研究' };

function FactorStudy({ name, horizons, correlations }: {
  name: string; horizons: Record<string,Horizon>; correlations: Record<string,number | null>;
}) {
  const [horizon,setHorizon] = useState('1d');
  const h = horizons[horizon];
  if (!h) { return null; }
  return <section className="paper-factor-study" aria-label={name + '的预测表现'}>
    <header><strong>{name}</strong><label>预测周期 <select aria-label={name + '的预测周期'} value={horizon} onChange={e => setHorizon(e.target.value)}>
      {Object.keys(horizons).map(k => <option key={k} value={k}>{horizonNames[k] ?? k}</option>)}</select></label></header>
    <dl className="paper-evidence-metrics">
      {Object.entries({ '有效截面': h.observations, 'IC':number(h.ic), 'Rank IC':number(h.rankIC),
        '正 Rank IC 占比':number(h.positiveFraction,true), '日 Rank ICIR':number(h.dailyRankICIR),
        '近 20 日 Rank IC':number(h.recent20DayRankIC) }).map(([k,v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
    </dl>
    <div className="paper-factor-groups">{h.groups.map((v,i) => <span key={i}>第 {i+1} 组 <b>{number(v,true)}</b></span>)}</div>
    <small>从低分到高分分组 · 最高组减最低组 {number(h.spread,true)} · {h.pairs} 个标的观测</small>
    {h.upMarketRankIC !== undefined && <p>股票池过去一个交易日上涨时的 Rank IC 为 {number(h.upMarketRankIC)}，下跌时为 {number(h.downMarketRankIC ?? null)}。</p>}
    {h.partialRankIC && Object.keys(h.partialRankIC).length > 0 && <p>控制另一因子后的偏秩相关 · {Object.entries(h.partialRankIC).map(([k,v]) => k+' '+number(v)).join(' / ')}</p>}
    {Object.keys(correlations).length > 0 && <p>与其他因子的截面相关性 · {Object.entries(correlations).map(([k,v]) => k+' '+number(v)).join(' / ')}</p>}
  </section>;
}

export function PaperResearchEvidence({ trials }: { trials: Trial[] }) {
  return <div className="paper-trials">{trials.map(t => <details key={t.candidate} className="paper-trial">
    <summary><b>候选 {t.candidate} · {t.name}</b><small>{t.decision ? decisions[t.decision.action] + ' · 候选 ' + t.decision.candidate : '待评议'}</small></summary>
    <p>{t.hypothesis}</p><p>{t.factor}</p>
    <dl className="paper-evidence-metrics">{Object.entries({
      '开发段净收益':number(t.metrics.netReturn,true), '等权基准':number(t.metrics.benchmarkReturn,true),
      '最大回撤':number(t.metrics.maxDrawdown,true), '成交笔数':t.metrics.fills,
      '费用 / 本金':number(t.metrics.feeRate,true), '成交额 / 本金':number(t.metrics.tradedNotionalRate,true),
    }).map(([k,v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
    {Object.entries(t.metrics.unfilled).map(([reason,count]) => <small key={reason}>{reason} · {count} 笔　</small>)}
    {Object.entries(t.metrics.factors).map(([name,report]) => <FactorStudy key={name} name={name} {...report}/>)}
    {t.decision && <p><strong>评议依据　</strong>{t.decision.reason}{t.decision.action === 'REVISE' && ' '+t.decision.direction}</p>}
  </details>)}</div>;
}
