import { useId, useState } from 'react';

export const methodName: Record<string, string> = {
  EQUAL: '等权',
  INVERSE_VOL: '逆波动率',
  RISK_PARITY: '风险平价',
};
const colors = ['#65c8cc', '#e1a86e', '#ab99e4'];
export interface Material {
  tool: 'ALLOCATION' | 'CORRELATION' | 'EXECUTION';
  source: string;
  title: string;
  version: number;
  first: string;
  last: string;
  observations: number;
}
interface Member {
  code: string;
  weight: number;
  risk: number;
}
interface Leg {
  method: string;
  net: number;
  drawdown: number;
  fees: number;
  fills: number;
  turnover: number | null;
  annualVol?: number | null;
  sharpe?: number | null;
  samples: { at: string; knownThrough: string; volatility: number; members: Member[] }[];
  curve: { day: string; nav: number }[];
}
interface Common {
  formula: string;
  design: string;
  limitation: string;
  snapshots?: string[];
}
interface Allocation extends Common {
  legs: Leg[];
  decisions: number;
  window: number;
  capital: number;
  skipped: string[];
}
interface Group {
  state: string;
  days: number;
  mean: number;
  pairs: { left: string; right: string; rho: number }[];
}
interface Correlation extends Common {
  groups: Group[];
  stocks: string[];
  proxySize: number;
  observations: number;
  flat: number;
}
interface Execution extends Common {
  net: number;
  fees: number;
  beforeFees: number;
  fills: number;
  observations: number;
  events: { code: string; action: string; side: string; reason: string; count: number; shares: number }[];
}
export interface Evidence {
  source: Material;
  metrics: Allocation | Correlation | Execution;
  names: Record<string, string>;
}
const pct = (v: number) => (100 * v).toFixed(2) + '%';
const money = (v: number) =>
  v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
function Chart({ legs }: { legs: Leg[] }) {
  const id = useId();
  const dates = [...new Set(legs.flatMap((l) => l.curve.map((p) => p.day)))].sort();
  if (!dates.length) {
    return null;
  }
  const values = legs.flatMap((l) => l.curve.map((p) => p.nav));
  const lo = Math.min(1, ...values),
    hi = Math.max(1, ...values),
    pad = (hi - lo || 0.04) * 0.12;
  const x = (i: number) => 56 + (650 * i) / Math.max(1, dates.length - 1);
  const y = (v: number) => 17 + (135 * (hi + pad - v)) / (hi - lo + 2 * pad);
  return (
    <figure className="qs-chart">
      <figcaption>
        <b>扣费后净值</b>
        <span>初始净值 1.000</span>
      </figcaption>
      <svg viewBox="0 0 728 181" role="img" aria-labelledby={id}>
        <title id={id}>三种配置方法的每日收盘净值</title>
        {[lo, (lo + hi) / 2, hi].map((n, i) => (
          <g key={i}>
            <path className="qs-gridline" d={`M56 ${y(n)}H706`} />
            <text x="46" y={y(n) + 4} textAnchor="end">
              {n.toFixed(3)}
            </text>
          </g>
        ))}
        {legs.map((l, i) => (
          <path
            key={l.method}
            style={{ stroke: colors[i] }}
            className="qs-trace"
            d={l.curve.map((p, j) => (j ? 'L' : 'M') + x(dates.indexOf(p.day)) + ' ' + y(p.nav)).join(' ')}
          />
        ))}
        <text x={56} y={176}>
          {dates[0]}
        </text>
        <text x={706} y={176} textAnchor="end">
          {dates[dates.length - 1]}
        </text>
      </svg>
      <div className="qs-legend">
        {legs.map((l, i) => (
          <span key={l.method}>
            <i style={{ background: colors[i] }} />
            {methodName[l.method]}
          </span>
        ))}
      </div>
    </figure>
  );
}
function Allocations({ m, names }: { m: Allocation; names: Evidence['names'] }) {
  const [method, choose] = useState(0);
  const leg = m.legs[method];
  const [day, setDay] = useState(
    () => m.legs[0]?.samples.find((s) => s.members.length > 1)?.at ?? m.legs[0]?.samples[0]?.at,
  );
  const sample = leg?.samples.find((s) => s.at === day);
  return (
    <>
      <Chart legs={m.legs} />
      <div className="qs-parameters">
        共同本金 ¥ {money(m.capital)} <span>·</span> {m.decisions} 个决策时点 <span>·</span> {m.window}{' '}
        日风险窗口 <span>·</span> {m.skipped.length} 次共同跳过
      </div>
      <table className="qs-table">
        <thead>
          <tr>
            {['资金配置', '扣费收益', '最大收盘回撤', '年化实现波动', '夏普', '成交费用（元）', '成交笔数'].map((s) => (
              <th key={s}>{s}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {m.legs.map((l) => (
            <tr key={l.method}>
              <th>{methodName[l.method]}</th>
              <td data-sign={Math.sign(l.net)}>{pct(l.net)}</td>
              <td>{pct(l.drawdown)}</td>
              <td>{l.annualVol == null ? '—' : pct(l.annualVol)}</td>
              <td title="夏普至少需要 60 个有效日收益">{l.sharpe == null ? '—' : l.sharpe.toFixed(2)}</td>
              <td>{money(l.fees)}</td>
              <td>{l.fills}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <details className="qs-disclosure">
        <summary>查看每只股票分到了多少资金和风险</summary>
        <div className="qs-controls">
          <label>
            配置方法
            <select value={method} onChange={(e) => choose(Number(e.target.value))}>
              {m.legs.map((l, i) => (
                <option key={l.method} value={i}>
                  {methodName[l.method]}
                </option>
              ))}
            </select>
          </label>
          <label>
            历史决策
            <select value={day} onChange={(e) => setDay(e.target.value)}>
              {leg.samples.map((s) => (
                <option key={s.at} value={s.at}>
                  {s.at.slice(0, 10)}
                </option>
              ))}
            </select>
          </label>
        </div>
        {sample && (
          <>
            <p>
              风险估计截至 {sample.knownThrough}，目标组合年化波动率 {pct(sample.volatility)}。
            </p>
            <table className="qs-table">
              <thead>
                <tr>
                  <th>选中股票</th>
                  <th>目标资金权重</th>
                  <th>组合风险贡献</th>
                </tr>
              </thead>
              <tbody>
                {sample.members.map((s, i) => (
                  <tr key={s.code}>
                    <th>{names[s.code] || '样本股票 ' + (i + 1)}</th>
                    <td>{pct(s.weight)}</td>
                    <td>{pct(s.risk)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </details>
    </>
  );
}
function Correlations({ m, names }: { m: Correlation; names: Evidence['names'] }) {
  const [index, setIndex] = useState(0);
  const g = m.groups[index];
  const stock = (code: string) => names[code] || '样本股票 ' + (m.stocks.indexOf(code) + 1);
  return (
    <>
      <div className="qs-group-summary">
        {m.groups.map((r, i) => (
          <button aria-pressed={i === index} key={r.state} onClick={() => setIndex(i)}>
            <span>代理组合{r.state === 'UP' ? '上涨' : '下跌'}日</span>
            <b>{r.mean.toFixed(3)}</b>
            <small>平均相关系数 · {r.days} 日</small>
          </button>
        ))}
      </div>
      <table className="qs-table qs-matrix">
        <caption>{g.state === 'UP' ? '上涨' : '下跌'}样本中的股票收益相关性</caption>
        <thead>
          <tr>
            <th>股票</th>
            {m.stocks.map((c) => (
              <th key={c}>{stock(c)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {m.stocks.map((a) => (
            <tr key={a}>
              <th>{stock(a)}</th>
              {m.stocks.map((b) => {
                const rho =
                  a === b
                    ? 1
                    : g.pairs.find((p) => (p.left === a && p.right === b) || (p.left === b && p.right === a))
                        ?.rho;
                return (
                  <td
                    key={b}
                    style={{
                      background:
                        rho == null
                          ? 'transparent'
                          : `rgb(${rho < 0 ? '185 132 196' : '64 169 167'} / ${0.07 + Math.abs(rho) * 0.36})`,
                    }}
                  >
                    {rho?.toFixed(2) ?? '—'}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        {m.stocks.length} 只观察股票 · {m.proxySize} 只独立代理成分 · {m.observations} 个共同收益观测 ·{' '}
        {m.flat} 个持平日。
      </p>
    </>
  );
}
function Executions({ m, names }: { m: Execution; names: Evidence['names'] }) {
  return (
    <>
      <div className="qs-amounts">
        {[
          ['扣费前盈亏', m.beforeFees],
          ['交易费用', m.fees],
          ['扣费后盈亏', m.net],
        ].map(([label, value]) => (
          <div key={label}>
            <small>{label}（元）</small>
            <b>{money(Number(value))}</b>
          </div>
        ))}
      </div>
      <p>
        {m.observations} 个账户观测 · {m.fills} 笔成交
      </p>
      <details className="qs-disclosure">
        <summary>查看买卖原因和执行次数</summary>
        <table className="qs-table">
          <thead>
            <tr>
              <th>股票名称</th>
              <th>方向</th>
              <th>事件</th>
              <th>原因</th>
              <th>次数</th>
            </tr>
          </thead>
          <tbody>
            {m.events.map((e, i) => (
              <tr key={i}>
                <th>{names[e.code] || '名称待补'}</th>
                <td>{e.side === 'BUY' ? '买入' : '卖出'}</td>
                <td>{e.action}</td>
                <td>{e.reason}</td>
                <td>{e.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </>
  );
}
export function StudyEvidence({ evidence: e }: { evidence: Evidence }) {
  if ('legs' in e.metrics) {
    return <Allocations m={e.metrics} names={e.names} />;
  }
  if ('groups' in e.metrics) {
    return <Correlations m={e.metrics} names={e.names} />;
  }
  return <Executions m={e.metrics} names={e.names} />;
}
