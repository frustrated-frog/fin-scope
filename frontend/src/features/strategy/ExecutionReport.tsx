import {useState} from 'react';
import './PlanStages.css';

export interface ExecutionCosts {
  commission: number;
  stampTax: number;
  slippage: number;
  notional: number;
}
export interface RebalancePlan {
  at: string;
  updated: string;
  expires: string;
  status: string;
  reason?: string;
  fills: number;
  fees: number;
  source?:string;
  legs: Record<string, {weight:number; target:number; originalTarget?:number; actual?:number; remaining?:number; reason?:string; lastOutcome?:string; riskReason?:string}>;
}
export interface Performance {
  days:number; closeDays?:number; asOf?:string; returnDays:number; netReturn:number; excess:number; maxDrawdown:number|null; feeRate:number;
  dailyVol:number | null; sharpe:number | null; turnover?:number | null;
}
const money = (v:number) => '¥ ' + v.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
const percent = (v:number) => (v*100).toFixed(2)+'%';
const stamp = (at:string) => at.slice(0,16).replace('T',' ');

export function PerformanceReport({value:p, costs}:{value:Performance; costs?:ExecutionCosts}) {
  return <details className="execution-report"><summary>绩效与交易成本 <small>{p.days} 个交易日</small></summary>
    <dl className="execution-metrics">
      <div><dt>扣费收益</dt><dd>{percent(p.netReturn)}</dd></div>
      <div><dt>相对基准</dt><dd>{(p.excess*100).toFixed(2)} 个百分点</dd></div>
      <div><dt>日末最大回撤</dt><dd>{p.maxDrawdown==null?'—':percent(p.maxDrawdown)}</dd></div>
      <div><dt>费用 / 初始本金</dt><dd>{percent(p.feeRate)}</dd></div>
      {p.turnover != null && <div><dt>区间双边换手率</dt><dd>{percent(p.turnover)}</dd></div>}
      {p.dailyVol != null && <div><dt>日收益波动率</dt><dd>{percent(p.dailyVol)}</dd></div>}
      {p.sharpe != null && <div><dt>年化夏普（无风险利率为零）</dt><dd>{p.sharpe.toFixed(2)}</dd></div>}
      {costs && <><div><dt>佣金</dt><dd>{money(costs.commission)}</dd></div><div><dt>印花税</dt><dd>{money(costs.stampTax)}</dd></div>
        <div><dt>成交价内的滑点</dt><dd>{money(costs.slippage)}</dd></div><div><dt>双边成交额</dt><dd>{money(costs.notional)}</dd></div></>}
    </dl>
    <p>{p.asOf && '收益截至 '+stamp(p.asOf)+'。'}收盘风险使用 15:00 净值{p.closeDays!=null?'，已覆盖 '+p.closeDays+' 个收盘日':''}。双边换手率取买卖成交额之和除以每日最后观测的平均权益。波动率取相邻交易日的净收益，满 60 个有效日收益后显示夏普。</p>
  </details>;
}

type PlanLeg = RebalancePlan['legs'][string];

function initialTarget(plan:RebalancePlan, leg:PlanLeg) {
  if (plan.source === '盘中风控') {
    return undefined;
  }
  return leg.originalTarget ?? (leg.riskReason ? undefined : leg.target);
}

function PlanStages({plan, name}:{plan:RebalancePlan; name:(code:string)=>string}) {
  const [chosen,choose]=useState('');
  const codes=Object.keys(plan.legs).sort((a,b)=>{
    const first=plan.legs[a], second=plan.legs[b];
    return Math.abs(second.remaining??0)-Math.abs(first.remaining??0)
      || Number(Boolean(second.riskReason))-Number(Boolean(first.riskReason))
      || Math.max(second.target,second.actual??0)-Math.max(first.target,first.actual??0);
  });
  const code=codes.includes(chosen)?chosen:codes[0];
  if (!code) {
    return null;
  }
  const leg=plan.legs[code];
  const stages=[{label:'策略目标',shares:initialTarget(plan,leg)}, {label:'风控后目标',shares:leg.target}, {label:'实际持仓',shares:leg.actual}];
  const scale=Math.max(1,...stages.map(s=>s.shares??0));
  return <section className="plan-stages" aria-label="调仓股数对照"><header><div><h5>从目标到持仓</h5>
    <small>{stamp(plan.updated)} · 按股数比较</small></div><select aria-label="查看调仓股票" value={code} onChange={e=>choose(e.target.value)}>
    {codes.map(c=><option key={c} value={c}>{name(c)}</option>)}</select></header>
    <dl>{stages.map(({label,shares})=><div key={label}><dt>{label}</dt><dd>
      <span className="plan-scale" aria-hidden="true">{shares!=null && <i style={{width:(100*shares/scale)+'%'}}/>}</span>
      <b>{shares!=null ? shares.toLocaleString()+' 股' : '未记录'}</b></dd></div>)}</dl>
    <p>{[leg.riskReason,leg.reason,leg.lastOutcome && leg.lastOutcome!=='成交' ? leg.lastOutcome : undefined].filter(Boolean).join(' · ')}</p>
  </section>;
}

export function PlanReport({plan:p, name, visual=false}:{plan:RebalancePlan; name:(c:string)=>string; visual?:boolean}) {
  return <>{visual && <PlanStages plan={p} name={name}/>}<details className="execution-report"><summary>{p.source??'调仓执行'} <strong>{p.status}</strong> <small>{p.fills} 笔成交</small></summary>
    <p>目标生成于 {stamp(p.at)}，有效至 {stamp(p.expires)}。截至 {stamp(p.updated)}，已付费用 {money(p.fees)}。{p.reason}</p>
    <div className="paper-coverage-scroll"><table className="paper-position-table"><thead><tr><th>股票</th><th>盘中风控前目标</th><th>风控后目标</th><th>实际股数</th><th>剩余调整</th><th>执行情况</th></tr></thead>
      <tbody>{Object.entries(p.legs).map(([code,l])=><tr key={code}><th>{name(code)}</th><td>{initialTarget(p,l) ?? '—'}</td><td>{l.target}</td><td>{l.actual??'—'}</td>
        <td>{l.remaining ? (l.remaining>0?'买 ':'卖 ')+Math.abs(l.remaining)+' 股' : '—'}</td>
        <td>{l.riskReason && l.riskReason+' · '}{l.reason}{l.lastOutcome && l.lastOutcome!=='成交' && ' · '+l.lastOutcome}</td></tr>)}</tbody></table></div>
  </details></>;
}
