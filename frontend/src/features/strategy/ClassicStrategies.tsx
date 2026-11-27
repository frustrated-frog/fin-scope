import { useState } from 'react';
import { PlanReport, type ExecutionCosts, type RebalancePlan, type Performance } from './ExecutionReport';
import './ClassicStrategies.css';

const families = {
  TREND: ['双均线趋势跟踪', '短均线高于长均线时，按两者距离选股。', '短均线 / 长均线 − 1'],
  MOMENTUM: ['截面动量', '在同一股票池中，优先选择窗口涨幅领先的股票。', '收盘价 / 窗口起点价 − 1'],
  REVERSION: ['均值回归', '价格低于均值时，寻找向下偏离程度较大的股票。', '−Z-Score（收盘价）'],
  MULTI: ['动量与低波多因子', '分别对涨幅与低波动率排名，再合成选股得分。', '½ 动量排名 ＋ ½ 低波排名'],
} as const;
export type Family = keyof typeof families;
const bases = { COHORT: '登记研究池', DISCOVERY: '股票发现历史候选池', GEM: '登记研究池 · 创业板', SH_MAIN: '登记研究池 · 沪市主板' };
const intervals = { '5m': '5 分钟', '15m': '15 分钟', '1d': '日线' };
const allocations = { EQUAL: '等权', SCORE: '得分排名加权', INVERSE_VOL: '逆波动率', MIN_VARIANCE:'最小方差', RISK_PARITY:'风险平价' };
const frequencies = { DAILY: '每个交易日', WEEKLY: '每周', MONTHLY: '每月' };
export function classicExperimentType(recipe:ClassicRecipe) {
  return `${frequencies[recipe.rebalance]}调仓 · ${allocations[recipe.allocation]}`;
}
export interface ClassicRecipe {
  family: Family; base: keyof typeof bases; period: keyof typeof intervals; allocation: keyof typeof allocations;
  rebalance: keyof typeof frequencies; window: number; holdings: number; poolSize: number;
  maxWeight: number; exposure: number; minTurnover: number; maxFeeBps?:number;
  riskWindow?:number; targetVol?:number; stopLoss?:number; drawdownStop?:number;
}
export const defaultRecipe: ClassicRecipe = { family: 'TREND', base: 'COHORT', period: '15m', window: 6,
  holdings: 3, poolSize: 12, allocation: 'EQUAL', rebalance: 'DAILY', exposure: .95, maxWeight: .4, minTurnover: 0 };
export interface Selection {at: string; knownAt: string; base: string[]; candidates: string[]; targets: Record<string, number>; skipped?: string;
  risk?:{days?:number; through?:string; annualVol?:number; scale?:number; contributions?:Record<string,number>; strongestPair?:{left:string;right:string;correlation:number}}}
export interface ClassicTrade {at: string; available: string; code: string; action: string; side: string; quantity?: number; price?: number; fee?: number; reason: string}
export interface ClassicRecord {decisions?: Selection[]; trades?: ClassicTrade[]; firstDay?: string; lastDay?: string;
  requestedStart?:string; requestedEnd?:string; plans?:RebalancePlan[]; costs?:ExecutionCosts; performance?:Performance;
  classicSnapshots?: string[]; coverageError?: string; classicCoverage?: {symbols:number; measured:number; bars:number; missingActions:number; unknownTerms:number};
  fees: number; fills: number; validationReturn?: number; validationBenchmark?: number; comparison?: {version: number; netReturn: number; fills: number; fees: number;
    executionVersion?:number; frequency?:keyof typeof frequencies; performance?:Performance}}
const percent = (v: number) => (v*100).toFixed(2)+'%';

export function ClassicLibrary({onChoose}: {onChoose: (family: Family) => void}) {
  return <section className="quant-readout classic-library"><h4>经典股票策略</h4>
    <div className="classic-cards">{(Object.keys(families) as Family[]).map(f =>
      <button type="button" key={f} data-family={f} onClick={() => onChoose(f)}>
        <strong>{families[f][0]}</strong><code>{families[f][2]}</code><span>{families[f][1]}</span><b>配置实验 ↗</b>
      </button>)}</div>
    <details className="classic-operators"><summary>这些策略用到哪些金融算子？</summary>
      <dl>
        <div><dt>SMA · EMA</dt><dd>平滑价格。双均线策略比较短期与长期的简单均价，EMA 则给近期价格更高的权重。</dd></div>
        <div><dt>ROC · RSI</dt><dd>ROC 衡量窗口首尾涨幅。这里的 RSI 用窗口内涨幅占全部绝对变动的比例衡量强弱。</dd></div>
        <div><dt>波动率 · ATR</dt><dd>波动率取逐根收益的标准差。ATR 取真实波幅的窗口均值，包含相对上一根收盘价的跳空。</dd></div>
        <div><dt>Z-Score · Rank</dt><dd>前者衡量价格偏离自身均值多少个标准差，后者比较同一时刻不同股票的相对名次，并列得分相同。</dd></div>
      </dl>
    </details>
  </section>;
}
function options(labels: Record<string,string>) {
  return Object.entries(labels).map(([id,label]) => <option key={id} value={id}>{label}</option>);
}
export function ClassicEditor({recipe:r, onChange, fixedBase}: {recipe: ClassicRecipe; onChange: (r: ClassicRecipe) => void; fixedBase: boolean}) {
  function number(key: keyof ClassicRecipe, label: string, min: number, max: number, multiplier=1, step=1) {
    return <label>{label}<input type="number" required min={min} max={max} step={step}
      value={Number.isFinite(r[key]) ? Number((Number(r[key])*multiplier).toFixed(5)) : ''}
      onChange={e => onChange({...r, [key]:e.target.value === '' ? NaN : Number(e.target.value)/multiplier})}/></label>;
  }
  const [activeModule, setActiveModule] = useState(1);
  const modules = [
    {title:'研究股票池', summary:bases[r.base], fields:<><label>基础股票池<select value={r.base} disabled={fixedBase} onChange={e=>onChange({...r,base:e.target.value as ClassicRecipe['base']})}>{options(bases)}</select></label>
      {number('poolSize','流动性候选数',r.holdings || 1,24)}
      {number('minTurnover','最低平均成交额估算（万元 / 根）',0,1e8,.0001,.01)}</>},
    {title:'信号与因子', summary:families[r.family][0], fields:<><label>策略类型<select value={r.family} onChange={e=>onChange({...r,family:e.target.value as Family})}>
        {(Object.keys(families) as Family[]).map(f=><option key={f} value={f}>{families[f][0]}</option>)}</select></label>
      <label>信号行情<select value={r.period} onChange={e=>onChange({...r,period:e.target.value as ClassicRecipe['period'],window:Math.min(r.window,e.target.value==='15m'?12:20)})}>{options(intervals)}</select></label>
      {number('window','回看窗口（根）',3,r.period==='15m'?12:20)}
      <div className="classic-formula"><code>{families[r.family][2]}</code><span>{families[r.family][1]}</span></div></>},
    {title:'组合仓位', summary:allocations[r.allocation], fields:<><label>权重分配<select value={r.allocation} onChange={e=>onChange({...r,allocation:e.target.value as ClassicRecipe['allocation']})}>{options(allocations)}</select></label>
      {number('holdings','最多持股数',1,Math.min(r.poolSize || 10,10))}
      {number('exposure','股票目标总仓位（%）',.1,100,100,.1)}
      {number('maxWeight','单股仓位上限（%）',.1,100,100,.1)}</>},
    {title:'风险设置', summary:r.stopLoss || r.drawdownStop || r.targetVol ? '已配置风险阈值' : '未设主动阈值', fields:<>{number('riskWindow','协方差窗口（交易日收益）',5,20)}
      {number('targetVol','年化波动率目标（%）',0,200,100,.1)}
      {number('stopLoss','个股成本止损（%）',0,99.9,100,.1)}
      {number('drawdownStop','组合回撤清仓线（%）',0,99.9,100,.1)}</>},
    {title:'再平衡', summary:frequencies[r.rebalance], fields:<><label>调仓频率<select value={r.rebalance} onChange={e=>onChange({...r,rebalance:e.target.value as ClassicRecipe['rebalance']})}>{options(frequencies)}</select></label>
      <p>周期内首个可用交易日 14:45 选股；未完成的股数目标持续到下一次调仓。</p></>},
    {title:'交易执行', summary:'五分钟撮合 · T+1', fields:<>{number('maxFeeBps','单笔费用上限（基点，0 表示不限）',0,10000,1,.1)}
      <p>先卖后买，成交受可卖股数、现金和实际成交量限制。费用上限按佣金及印花税计算。</p></>}
  ];
  return <section className="classic-editor qh-builder"><h4>策略构建器</h4><div className="qh-builder-layout">
    <nav aria-label="策略组成模块">{modules.map((module,index)=><button type="button" key={module.title}
      aria-pressed={activeModule===index} onClick={()=>setActiveModule(index)}><b>{module.title}</b><small>{module.summary}</small></button>)}</nav>
    <div>{modules.map((module,index)=><fieldset key={module.title} hidden={index!==activeModule}
      onInvalidCapture={event=>{setActiveModule(index);const input=event.target as HTMLInputElement;requestAnimationFrame(()=>input.focus());}}>
      <legend>{module.title}配置</legend><div className="classic-fields">{module.fields}</div></fieldset>)}</div>
  </div></section>;
}
export function ClassicSelection({value, name}: {value: Selection; name: (c:string)=>string}) {
  return <div className="classic-selection"><p>名单可用时间 {value.knownAt.slice(0,16).replace('T',' ')} · 基础 {value.base.length} 只 · 合格候选 {value.candidates.length} 只</p>
    <details><summary>当时的股票名单</summary><p>基础名单 · {value.base.map(name).join('、') || '空'}</p>
      <p>流动性筛选后 · {value.candidates.map(name).join('、') || '空'}</p></details>
    {value.skipped ? <p>{value.skipped}</p> : <div className="classic-targets">{Object.entries(value.targets).map(([code,w])=><span key={code}>{name(code)} <b>{percent(w)}</b></span>)}
      {!Object.keys(value.targets).length && <span>目标为空仓</span>}</div>}
    {value.risk?.annualVol != null && <details><summary>目标组合的风险估计</summary>
      <p>使用截至 {value.risk.through} 的 {value.risk.days} 个共同日收益，估算年化波动率 {percent(value.risk.annualVol)}。协方差向对角阵收缩 10%。</p>
      <p>风险贡献占比 · {Object.entries(value.risk.contributions??{}).map(([c,v])=>name(c)+' '+percent(v)).join('、')}</p>
      {value.risk.strongestPair && <p>相关性绝对值最高 · {name(value.risk.strongestPair.left)} 与 {name(value.risk.strongestPair.right)}，收缩后相关系数 {value.risk.strongestPair.correlation.toFixed(2)}。</p>}</details>}
  </div>;
}
export function ClassicEvidence({history:h,recipe:r,name}: {history: ClassicRecord;recipe:ClassicRecipe;name:(c:string)=>string}) {
  const days = Array.from(new Set([...(h.decisions??[]),...(h.trades??[]),...(h.plans??[])].map(e=>e.at.slice(0,10)))).sort().reverse();
  const [selected,select]=useState('');
  const day = days.includes(selected) ? selected : days[0];
  const decision = h.decisions?.find(e=>e.at.startsWith(day));
  const plans = h.plans?.filter(p=>p.at.startsWith(day)) ?? [];
  const trades = h.trades?.filter(e=>e.at.startsWith(day)) ?? [];
  return <div className="classic-report"><p>{h.firstDay} 至 {h.lastDay} · {intervals[r.period]} × {r.window} 根 · {frequencies[r.rebalance]}调仓 · {allocations[r.allocation]}</p>
    {h.requestedStart && <p>指定区间 {h.requestedStart} 至 {h.requestedEnd}。区间前的行情用于预热指标，收益从首个可用交易日开始记账。</p>}
    <div className="paper-validation"><div><small>区间扣费收益</small><b>{percent(h.validationReturn??0)}</b></div>
      <div><small>期初股票池等权基准</small><b>{percent(h.validationBenchmark??0)}</b></div>
      <div><small>调仓观察次数</small><b>{h.decisions?.length??0}</b></div></div>
    {h.classicCoverage && <p>区间快照共 {h.classicCoverage.symbols} 只，实测成交量覆盖 {h.classicCoverage.measured.toLocaleString()} / {h.classicCoverage.bars.toLocaleString()} 根。
      {h.classicCoverage.missingActions > 0 && `其中 ${h.classicCoverage.missingActions} 只缺少完整除权核验，跨日价格变化可能影响收益。`}
      {h.classicCoverage.unknownTerms > 0 && `区间内有 ${h.classicCoverage.unknownTerms} 次除权只有日期，权益估值可能不完整。`}</p>}
    {h.coverageError && <p>{h.coverageError}</p>}
    <details open><summary>历史调仓与逐笔交易</summary><label>查看日期 <select value={day??''} onChange={e=>select(e.target.value)}>{days.map(d=><option key={d}>{d}</option>)}</select></label>
      {decision && <ClassicSelection value={decision} name={name}/>}
      {plans.map(plan=><PlanReport key={plan.at} plan={plan} name={name}/>)}
      {trades.map((e,i)=><p key={i}><time>{e.at.slice(11,16)}</time> · {e.side==='BUY'?'买入':'卖出'} {name(e.code)} · {e.action} · {e.quantity ? e.quantity+' 股 × ¥ '+e.price+'，费用 ¥ '+e.fee : e.reason}</p>)}
      {!trades.length && <p>此日没有委托或成交。</p>}
    </details>
  </div>;
}
