import type { ReactNode } from 'react';
import type { ClassicRecord } from './ClassicStrategies';
import type { PaperVersion } from './PaperLab';
import type { Performance } from './ExecutionReport';
import './QuantAnalytics.css';

type Observation = {at: string; equity: number};
const pct = (v: number) => (100 * v).toFixed(2) + '%';
const palette = ['#73ccdb','#a797db','#699eda','#75bba9','#cfaa7c'];

function RiskTrace({label, series, stepped = false}: {
  label: string; series: {at: string; value: number}[]; stepped?: boolean;
}) {
  if (!series.length) {
    return null;
  }
  const values = series.map(p => p.value);
  const min = Math.min(0, ...values);
  const max = Math.max(stepped ? 1 : 0, ...values);
  const y = (v: number) => 8 + 56 * (max-v)/(max-min || 1);
  const start = Date.parse(series[0].at);
  const duration = Date.parse(series[series.length - 1].at)-start;
  const x = (at: string) => duration ? 300*(Date.parse(at)-start)/duration : 0;
  const line = series.map(({at,value}, i) => i === 0 ? 'M0 '+y(value) : stepped ? 'H'+x(at)+'V'+y(value) : 'L'+x(at)+' '+y(value)).join(' ');
  return <figure className="qa-mini" data-step={stepped}><figcaption>{label}<b>{pct(values[values.length-1])}</b></figcaption>
    <svg viewBox="0 0 300 72" preserveAspectRatio="none" role="img" aria-label={label}>
      <path d={line+' L'+x(series[series.length-1].at)+' '+y(0)+' H0 Z'} fill="currentColor" opacity=".13"/>
      <path d={line} fill="none" stroke="currentColor" strokeWidth="2" vectorEffect="non-scaling-stroke"/>
    </svg><footer>{series[0].at.slice(5,10)}<span>{series[series.length-1].at.slice(5,10)}</span></footer></figure>;
}

export function BacktestOverview({history, capital, children}: {
  history: ClassicRecord & {curve: Observation[]}; capital: number; children: ReactNode;
}) {
  const p = history.performance;
  const closes = new Map<string,Observation>();
  for (const point of history.curve) {
    closes.set(point.at.slice(0,10), point);
  }
  let peak = capital;
  const drawdowns = Array.from(closes.values(), point => {
    peak = Math.max(point.equity,peak);
    return {at:point.at, value:point.equity/peak-1};
  });
  const targets = (history.decisions ?? []).filter(d => !d.skipped && d.targets).map(d => ({
    at:d.at, value:Object.values(d.targets).reduce((total,v) => total+v,0)
  }));
  const metrics = [
    ['累计扣费收益', p ? pct(p.netReturn) : history.validationReturn != null ? pct(history.validationReturn) : '—'],
    ['日末最大回撤', p ? pct(-p.maxDrawdown) : '—'],
    ['年化夏普', p?.sharpe?.toFixed(2) ?? '—'],
    ['超额收益', p ? (p.excess*100).toFixed(2)+' 个百分点' : '—'],
    ['年化波动率', p?.dailyVol != null ? pct(p.dailyVol*Math.sqrt(252)) : '—'],
    ['累计交易费用', '¥ '+history.fees.toFixed(2)]
  ];
  return <div className="qa-analysis"><dl className="qa-metrics">{metrics.map(([label,value]) =>
    <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    {p && p.sharpe == null && <small>夏普需至少 60 个有效日收益且波动率大于零；当前 {p.returnDays} 个。</small>}
    <section className="qa-trace"><h5>净值曲线</h5>{children}</section>
    <div className="qa-pair"><RiskTrace label="调仓目标股票仓位" series={targets} stepped/>
      <RiskTrace label="日末回撤" series={drawdowns}/></div></div>;
}

export function PortfolioOverview({book, performance, daily, capital, name}: {
  book: NonNullable<PaperVersion['book']>; performance?: Performance; daily: Observation[]; capital: number; name:(code:string)=>string;
}) {
  if (!(book.equity > 0)) {
    return null;
  }
  const stocks = Object.entries(book.positions).map(([code,pos]) => ({
    label:name(code), value:pos.price*pos.lots.reduce((sum,[,quantity]) => sum+quantity,0)/book.equity
  })).filter(s => s.value > 0).sort((a,b) => b.value-a.value);
  const exposure = stocks.reduce((sum,s) => sum+s.value,0);
  const slices = [...stocks.map((s,i) => ({...s,color:palette[i%palette.length]})),
    {label:'现金',value:book.cash/book.equity,color:'#607187'}];
  const receivable = (book.dividendReceivable ?? 0)/book.equity;
  if (receivable > 0) {
    slices.push({label:'应收股息',value:receivable,color:'#bd9a70'});
  }
  let end = 0;
  const stops = slices.map(s => {
    const start = end;
    end += 100*s.value;
    return s.color+' '+start+'% '+end+'%';
  });
  const peak = daily.reduce((high,p) => Math.max(high,p.equity),Math.max(capital,book.equity));
  const risk = [
    {label:'最大单股仓位',value:stocks[0]?.value ?? 0},
    {label:'组合年化波动率',value:performance?.dailyVol != null ? performance.dailyVol*Math.sqrt(252) : null},
    {label:'相对历史日末高点回撤',value:1-book.equity/peak}
  ];
  return <div className="qa-pair qa-portfolio"><section><h5>当前投资组合</h5>
    <div className="qa-allocation"><div className="qa-ring" style={{background:'conic-gradient('+stops.join(',')+')'}}>
      <div><b>{(100*exposure).toFixed(1)}%</b><small>股票仓位</small></div></div>
      <ul>{slices.map((s,i) => <li key={i}><i style={{background:s.color}}/>{s.label}<b>{pct(s.value)}</b></li>)}</ul></div></section>
    <section><h5>风险监控</h5>{risk.map(r => <div className="qa-risk" key={r.label}><span>{r.label}</span>
      <b>{r.value != null ? pct(r.value) : '样本不足'}</b><meter min="0" max="1" value={r.value ?? 0}/></div>)}</section></div>;
}
