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
  legs: Record<string, {weight:number; target:number; actual?:number; remaining?:number; reason?:string; lastOutcome?:string; riskReason?:string}>;
}
export interface Performance {
  days:number; returnDays:number; netReturn:number; excess:number; maxDrawdown:number; feeRate:number;
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
      <div><dt>日末最大回撤</dt><dd>{percent(p.maxDrawdown)}</dd></div>
      <div><dt>费用 / 初始本金</dt><dd>{percent(p.feeRate)}</dd></div>
      {p.turnover != null && <div><dt>区间双边换手率</dt><dd>{percent(p.turnover)}</dd></div>}
      {p.dailyVol != null && <div><dt>日收益波动率</dt><dd>{percent(p.dailyVol)}</dd></div>}
      {p.sharpe != null && <div><dt>年化夏普（无风险利率为零）</dt><dd>{p.sharpe.toFixed(2)}</dd></div>}
      {costs && <><div><dt>佣金</dt><dd>{money(costs.commission)}</dd></div><div><dt>印花税</dt><dd>{money(costs.stampTax)}</dd></div>
        <div><dt>成交价内的滑点</dt><dd>{money(costs.slippage)}</dd></div><div><dt>双边成交额</dt><dd>{money(costs.notional)}</dd></div></>}
    </dl>
    <p>回撤使用每个交易日最后可用净值。双边换手率取买卖成交额之和除以日末平均权益。波动率取相邻交易日的净收益，满 60 个有效日收益后显示夏普。</p>
  </details>;
}

export function PlanReport({plan:p, name}:{plan:RebalancePlan; name:(c:string)=>string}) {
  return <details className="execution-report"><summary>{p.source??'调仓执行'} <strong>{p.status}</strong> <small>{p.fills} 笔成交</small></summary>
    <p>目标生成于 {stamp(p.at)}，有效至 {stamp(p.expires)}。截至 {stamp(p.updated)}，已付费用 {money(p.fees)}。{p.reason}</p>
    <div className="paper-coverage-scroll"><table className="paper-position-table"><thead><tr><th>股票</th><th>目标股数</th><th>实际股数</th><th>剩余调整</th><th>执行情况</th></tr></thead>
      <tbody>{Object.entries(p.legs).map(([code,l])=><tr key={code}><th>{name(code)}</th><td>{l.target}</td><td>{l.actual??'—'}</td>
        <td>{l.remaining ? (l.remaining>0?'买 ':'卖 ')+Math.abs(l.remaining)+' 股' : '—'}</td>
        <td>{l.riskReason && l.riskReason+' · '}{l.reason}{l.lastOutcome && l.lastOutcome!=='成交' && ' · '+l.lastOutcome}</td></tr>)}</tbody></table></div>
  </details>;
}
