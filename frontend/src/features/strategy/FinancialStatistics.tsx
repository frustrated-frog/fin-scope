import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../shared/api/client';
import './FinancialStatistics.css';

const indices: Record<string, string> = {
  'SH:000001': '上证指数',
  'SZ:399006': '创业板指',
  'SH:000300': '沪深300',
  'SZ:399001': '深证成指',
  'SH:000852': '中证1000',
};
const stageName: Record<string, string> = {
  RISK_ON: '风险偏好上升',
  HIGH_LEVEL_DIVERGENCE: '高位分化',
  SELL_OFF: '抛售',
  POST_SELL_OFF_REPAIR: '下跌后修复',
  RANGE_ROTATION: '震荡轮动',
};
const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date());
type Scalar = number | null;
const pct = (v: Scalar) => (v == null ? '—' : (v * 100).toFixed(2) + '%');
const fmt = (v: Scalar) => (v == null ? '—' : v.toFixed(3));
type Query = {
  asset: string;
  peer: string;
  asOf: string;
  days: number;
  window: number;
  annualRiskFree: number;
};
type Point = { weight: number; annualReturn: number; annualVol: number };
type Source = {
  n: number;
  first: string | null;
  last: string | null;
  provider: string | null;
  digest: string;
  adjustment: string[];
};
type Result = {
  start: string;
  end: string;
  sources: Record<string, Source>;
  benchmark: string;
  distribution: {
    n: number;
    mean: number;
    logMean: number;
    compound: number;
    volatility: Scalar;
    skew: Scalar;
    excessKurtosis: Scalar;
    percentiles: number[];
    bins: number[];
    lagOne: Scalar;
    absoluteLagOne: Scalar;
    lagPairs: number;
    meanInterval: { low: number; high: number; lags: number } | null;
  };
  pair: {
    n: number;
    correlation: Scalar;
    rolling: { day: string; value: Scalar }[];
    frontier: { minimum: Point; points: Point[]; allocations: Point[] } | null;
  };
  pricing: { n: number; beta: Scalar; annualAlpha: Scalar; rSquared: Scalar };
  regimes: { label: string; n: number; mean: Scalar }[];
  marketStages: { stage: string; n: number; mean: number }[];
};
function Metric({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="fs-metric">
      <small>{label}</small>
      <strong>{value}</strong>
      {note && <span>{note}</span>}
    </div>
  );
}
function Histogram({ counts, n }: { counts: number[]; n: number }) {
  const bins = ['低于 −3%', '−3 至 −1%', '−1 至 0%', '0 至 1%', '1 至 3%', '至少 3%'];
  const max = Math.max(1, ...counts);
  return (
    <figure className="fs-figure">
      <figcaption>
        <h4>日收益落在哪些区间？</h4>
        <small>{n} 个有效交易日</small>
      </figcaption>
      <div className="fs-hist">
        {counts.map((c, i) => (
          <div key={bins[i]} title={bins[i] + ' · ' + pct(c / n)}>
            <div className="fs-column">
              <i style={{ height: Math.max(0, (75 * c) / max) + '%' }} data-loss={i < 3}>
                <b>{c}</b>
              </i>
            </div>
            <small>{bins[i]}</small>
          </div>
        ))}
      </div>
      <p>柱高为天数。各区间含左端点，0% 计入第四组。</p>
    </figure>
  );
}
function Rolling({ rows, window }: { rows: Result['pair']['rolling']; window: number }) {
  const usable = rows.filter((r) => r.value != null);
  const x = (i: number) => 44 + (i * 570) / Math.max(1, rows.length - 1);
  let path = '',
    previous = false;
  for (const [i, r] of rows.entries()) {
    if (r.value == null) {
      previous = false;
      continue;
    }
    path += (previous ? 'L' : 'M') + x(i).toFixed(2) + ' ' + (86 - 65 * r.value).toFixed(2) + ' ';
    previous = true;
  }
  return (
    <figure className="fs-figure">
      <figcaption>
        <h4>两种资产何时同涨同跌？</h4>
        <small>{window} 日滚动相关性</small>
      </figcaption>
      {usable.length ? (
        <svg viewBox="0 0 640 190" role="img" aria-label="滚动相关性曲线">
          {[-1, 0, 1].map((v) => (
            <g key={v}>
              <line x1="44" x2="614" y1={86 - 65 * v} y2={86 - 65 * v} />
              <text x="12" y={91 - 65 * v}>
                {v}
              </text>
            </g>
          ))}
          <path d={path} className="fs-line" />
          {usable.length === 1 && (
            <circle cx={x(rows.indexOf(usable[0]))} cy={86 - 65 * usable[0].value!} r="3" />
          )}
          <text x="44" y="180">
            {rows[0].day}
          </text>
          <text x="614" y="180" textAnchor="end">
            {rows[rows.length - 1].day}
          </text>
        </svg>
      ) : (
        <p>连续共同收益不足，或窗口内收益没有变化。</p>
      )}
      <p>
        最新有效窗口{' '}
        {usable.length ? usable[usable.length - 1].day + ' · ' + fmt(usable[usable.length - 1].value) : '—'}
        。缺失交易日会重新累计窗口。
      </p>
    </figure>
  );
}
function Frontier({
  frontier,
  asset,
  peer,
}: {
  frontier: NonNullable<Result['pair']['frontier']>;
  asset: string;
  peer: string;
}) {
  const [weight, setWeight] = useState(50);
  const all = frontier.allocations,
    selected = all[weight],
    best = frontier.minimum;
  const cloud = [...all, best];
  const bounds = (key: 'annualVol' | 'annualReturn') => [
    Math.min(...cloud.map((p) => p[key])),
    Math.max(...cloud.map((p) => p[key])),
  ];
  const [xmin, xmax] = bounds('annualVol'),
    [ymin, ymax] = bounds('annualReturn');
  const x = (p: Point) => 76 + (xmax > xmin ? (p.annualVol - xmin) / (xmax - xmin) : 0.5) * 472;
  const y = (p: Point) => 148 - (ymax > ymin ? (p.annualReturn - ymin) / (ymax - ymin) : 0.5) * 108;
  const path = (points: Point[]) =>
    points.map((p, i) => (i ? 'L' : 'M') + x(p).toFixed(2) + ' ' + y(p).toFixed(2)).join(' ');
  return (
    <figure className="fs-figure">
      <figcaption>
        <h4>两资产有效前沿</h4>
        <small>只做多 · 资金全额投入</small>
      </figcaption>
      <svg viewBox="0 0 620 197" role="img" aria-label="资金配置、预期收益与风险">
        <line x1="76" x2="548" y1="148" y2="148" />
        <line x1="76" x2="76" y1="40" y2="148" />
        <text x="76" y="19">
          样本年化平均收益
        </text>
        <text x="5" y="46">
          {pct(ymax)}
        </text>
        <text x="5" y="147">
          {pct(ymin)}
        </text>
        <text x="76" y="178">
          {pct(xmin)}
        </text>
        <text x="548" y="178" textAnchor="end">
          {pct(xmax)}
        </text>
        <text x="311" y="188" textAnchor="middle">
          年化波动率
        </text>
        <path d={path(all)} className="fs-possible" />
        <path d={path(frontier.points)} className="fs-line" />
        <circle cx={x(best)} cy={y(best)} r="4" className="fs-min" />
        <circle cx={x(selected)} cy={y(selected)} r="6" className="fs-choice" />
      </svg>
      <p>青线为有效组合，金点为最小方差，白点为所选组合。</p>
      <label className="fs-slider">
        <span>
          {asset} <b>{weight}%</b>
        </span>
        <input
          aria-label="第一种资产的组合权重"
          type="range"
          min="0"
          max="100"
          value={weight}
          onChange={(e) => setWeight(+e.target.value)}
        />
        <span>
          {peer} <b>{100 - weight}%</b>
        </span>
      </label>
      <p>
        所选组合年化波动 <b>{pct(selected.annualVol)}</b>，样本年化平均收益 <b>{pct(selected.annualReturn)}</b>
        。最小方差对应第一种资产 {pct(best.weight)}。
      </p>
    </figure>
  );
}
function StateTable({ title, rows }: { title: string; rows: { label: string; n: number; mean: Scalar }[] }) {
  return (
    <section className="fs-box">
      <h4>{title}</h4>
      {rows.length ? (
        <table>
          <thead>
            <tr>
              <th>状态</th>
              <th>样本天数</th>
              <th>资产日均收益</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.label}>
                <td>{r.label}</td>
                <td>{r.n}</td>
                <td>{pct(r.mean)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p>区间内缺少在前一交易日实际形成的可用状态记录。</p>
      )}
    </section>
  );
}
function Findings({ r, q, names }: { r: Result; q: Query; names: Record<string, string> }) {
  const [view, choose] = useState('收益统计');
  const d = r.distribution,
    ci = d.meanInterval;
  return (
    <>
      <div className="fs-reporthead">
        <div>
          <small>
            {r.start} — {r.end}
          </small>
          <h3>
            {names[q.asset]} <span>与</span> {names[q.peer]}
          </h3>
        </div>
        <nav aria-label="金融研究视角">
          {['收益统计', '市场状态', '定价与组合'].map((s) => (
            <button key={s} aria-pressed={view === s} onClick={() => choose(s)}>
              {s}
            </button>
          ))}
        </nav>
      </div>
      {view === '收益统计' && (
        <>
          <div className="fs-metrics">
            <Metric
              label={names[q.asset] + ' · 样本内复合收益'}
              value={pct(d.compound)}
              note={d.n + ' 个有效日收益连乘'}
            />
            <Metric label="日均算术收益" value={pct(d.mean)} note={'日均对数收益 ' + pct(d.logMean)} />
            <Metric label="年化波动率" value={pct(d.volatility)} note="样本标准差 × √252" />
            <Metric
              label="两资产全期相关性"
              value={fmt(r.pair.correlation)}
              note={r.pair.n + ' 个共同收益日'}
            />
          </div>
          <div className="fs-grid">
            <Histogram counts={d.bins} n={d.n} />
            <Rolling rows={r.pair.rolling} window={q.window} />
          </div>
          <div className="fs-grid">
            <section className="fs-box">
              <h4>收益分布与尾部</h4>
              <p>
                5%、50%、95% 分位数分别为 <b>{d.percentiles.map(pct).join(' / ')}</b>。
              </p>
              <p>
                偏度 {fmt(d.skew)} · 超额峰度 {fmt(d.excessKurtosis)}。负偏度表示样本左尾更长。
              </p>
            </section>
            <section className="fs-box">
              <h4>时间依赖与估计误差</h4>
              <p>
                相邻日收益相关 {fmt(d.lagOne)}，绝对收益相关 {fmt(d.absoluteLagOne)}，共 {d.lagPairs} 对。
              </p>
              <p>
                {ci
                  ? '日均收益近似 95% 区间 ' +
                    pct(ci.low) +
                    ' 至 ' +
                    pct(ci.high) +
                    '（Newey–West，' +
                    ci.lags +
                    ' 阶）。'
                  : '有效样本少于 30 天，暂不估计日均收益区间。'}
              </p>
            </section>
          </div>
        </>
      )}
      {view === '市场状态' && (
        <>
          <div className="fs-grid">
            <StateTable title="沪深300当日涨跌" rows={r.regimes} />
            <StateTable
              title="前一交易日的市场状态"
              rows={r.marketStages.map((g) => ({ ...g, label: stageName[g.stage] ?? g.stage }))}
            />
          </div>
          <p className="fs-note">
            左表描述同一天的联动。右表匹配市场脉搏在前一交易日实际生成、质量就绪的判断，覆盖{' '}
            {r.marketStages.reduce((n, g) => n + g.n, 0)} / {d.n} 天。收盘到收盘收益含隔夜变化，仅供状态比较。
          </p>
        </>
      )}
      {view === '定价与组合' && (
        <>
          <div className="fs-metrics">
            <Metric label="市场 Beta" value={fmt(r.pricing.beta)} note="相对沪深300" />
            <Metric label="回归 Alpha · 年化" value={pct(r.pricing.annualAlpha)} note="日截距 × 252" />
            <Metric
              label="市场解释度 R²"
              value={pct(r.pricing.rSquared)}
              note={r.pricing.n + ' 个与基准共同收益日'}
            />
            <Metric label="年无风险利率假设" value={pct(q.annualRiskFree)} note="回归使用日等效利率" />
          </div>
          <div className="fs-grid">
            {r.pair.frontier ? (
              <Frontier frontier={r.pair.frontier} asset={names[q.asset]} peer={names[q.peer]} />
            ) : (
              <p>两种资产至少需要三个共同收益日。</p>
            )}
            <section className="fs-box fs-math">
              <h4>市场风险和组合风险</h4>
              <p>
                用资产的超额收益对沪深300超额收益作回归。Beta 描述对市场波动的敏感度，Alpha 是该样本的回归截距。
              </p>
              <code>rᵢ − rf = α + β (rₘ − rf) + ε</code>
              <p>
                两种资产的权重、各自方差和共同波动决定组合风险。拖动权重，观察同样一对资产的风险与预期收益怎样变化。
              </p>
              <code>σ²ₚ = w²σ²₁ + (1−w)²σ²₂ + 2w(1−w) Cov₁₂</code>
              <p>前沿使用历史均值和样本协方差估计，未计交易成本。</p>
            </section>
          </div>
        </>
      )}
      <details className="fs-sources">
        <summary>行情来源与计算口径</summary>
        <p>
          取本机最新归档的已完成收盘价，只保留同一价格口径、可核验的相邻交易日。历史行情修订后结果可能变化。复合收益只连乘有效样本。年化采用
          252 个交易日。
        </p>
        <table>
          <thead>
            <tr>
              <th>标的</th>
              <th>有效区间</th>
              <th>天数</th>
              <th>价格口径</th>
              <th>来源与样本指纹</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(r.sources).map(([key, s]) => (
              <tr key={key}>
                <td>{names[key]}</td>
                <td>
                  {s.first ?? '—'} 至 {s.last ?? '—'}
                </td>
                <td>{s.n}</td>
                <td>{s.adjustment.map((a) => (a === 'QFQ' ? '前复权' : '原始收盘')).join(' / ') || '—'}</td>
                <td>
                  {s.provider ?? '无行情'} · {s.digest.slice(0, 10)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </>
  );
}
export function FinancialStatistics({ onBack }: { onBack: () => void }) {
  const [form, setForm] = useState<Query>(() => ({
    asset: 'SH:000001',
    peer: 'SZ:399006',
    asOf: today(),
    days: 180,
    window: 20,
    annualRiskFree: 0,
  }));
  const [query, run] = useState(form),
    [names, setNames] = useState(indices),
    [membersError, setMembersError] = useState(false);
  const [record, setRecord] = useState<{ query: Query; result: Result }>(),
    [error, setError] = useState('');
  useEffect(() => {
    const ctrl = new AbortController();
    api<{ code: string; name: string; type: string }[]>('/api/watchlist/members', { signal: ctrl.signal })
      .then((items) => {
        const list = { ...indices };
        for (const s of items) {
          if (s.type === 'STOCK' && /^(6|0|3)\d{5}$/.test(s.code)) {
            list[(s.code[0] === '6' ? 'SH:' : 'SZ:') + s.code] = s.name;
          }
        }
        if (!ctrl.signal.aborted) {
          setNames(list);
        }
      })
      .catch(() => {
        if (!ctrl.signal.aborted) {
          setMembersError(true);
        }
      });
    return () => ctrl.abort();
  }, []);
  useEffect(() => {
    const ctrl = new AbortController();
    setError('');
    api<Result>('/api/quant/financial-statistics', {
      method: 'POST',
      body: JSON.stringify(query),
      signal: ctrl.signal,
    })
      .then((result) => {
        if (!ctrl.signal.aborted) {
          setRecord({ query, result });
        }
      })
      .catch((e) => {
        if (!ctrl.signal.aborted) {
          setError(e instanceof Error ? e.message : '行情统计读取失败');
        }
      });
    return () => ctrl.abort();
  }, [query]);
  const update = <K extends keyof Query>(key: K, value: Query[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));
  function submit(e: FormEvent) {
    e.preventDefault();
    run({ ...form });
  }
  return (
    <main className="fs-work">
      <header>
        <div>
          <small>量化研究总览 / 金融研究</small>
          <h2>金融统计与组合研究</h2>
        </div>
        <button onClick={onBack}>返回研究总览</button>
      </header>
      <form onSubmit={submit} className="fs-form">
        {(['asset', 'peer'] as const).map((key, i) => (
          <label key={key}>
            {i ? '对照资产' : '研究资产'}
            <select
              aria-label={i ? '对照资产' : '研究资产'}
              value={form[key]}
              onChange={(e) => update(key, e.target.value)}
            >
              {Object.entries(names).map(([k, n]) => (
                <option key={k} value={k}>
                  {n}
                </option>
              ))}
            </select>
          </label>
        ))}
        <label>
          截止日
          <input
            type="date"
            required
            max={today()}
            value={form.asOf}
            onChange={(e) => update('asOf', e.target.value)}
          />
        </label>
        <label>
          研究区间
          <select aria-label="研究区间" value={form.days} onChange={(e) => update('days', +e.target.value)}>
            {[90, 180, 365, 730, 1095].map((n) => (
              <option key={n} value={n}>
                {n} 自然日
              </option>
            ))}
          </select>
        </label>
        <label>
          滚动窗口
          <select aria-label="滚动窗口" value={form.window} onChange={(e) => update('window', +e.target.value)}>
            {[5, 10, 20, 60, 120].map((n) => (
              <option key={n} value={n}>
                {n} 交易日
              </option>
            ))}
          </select>
        </label>
        <label>
          无风险年利率（%）
          <input
            type="number"
            min="0"
            max="25"
            step="0.01"
            required
            value={Number((form.annualRiskFree * 100).toFixed(2))}
            onChange={(e) => update('annualRiskFree', +e.target.value / 100)}
          />
        </label>
        <button className="fs-calculate" disabled={form.asset === form.peer}>
          计算分析
        </button>
      </form>
      {form.asset === form.peer && <p role="status">请选择两种不同的资产。</p>}
      {membersError && <p>自选股列表暂时无法读取，仍可选择市场指数。</p>}
      {error ? (
        <p role="alert">
          {error} <button onClick={() => run({ ...query })}>重试</button>
        </p>
      ) : record?.query === query ? (
        <Findings key={JSON.stringify(query)} q={query} r={record.result} names={names} />
      ) : (
        <p role="status">正在对齐收盘数据并计算研究结果…</p>
      )}
    </main>
  );
}
