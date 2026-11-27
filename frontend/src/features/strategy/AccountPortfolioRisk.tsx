import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import './AccountPortfolioRisk.css';

type Metric = number | null;
interface Holding {
  code: string;
  name?: string;
  industry: string | null;
  industryAsOf?: string;
  weight: Metric;
  riskShare: Metric;
  marginalVol: Metric;
  value: Metric;
  quantity: number;
  stale: boolean;
  days: Metric;
}
interface Industry {
  industry: string | null;
  count: number;
  weight: Metric;
  riskShare: Metric;
}
interface Risk {
  days: number;
  marketDays: number;
  first: string | null;
  last: string | null;
  annualVol: Metric;
  beta: Metric;
  systematicShare: Metric;
  residualShare: Metric;
  var95: Metric;
  es95: Metric;
  strongestPair: { left: string; right: string; rho: number } | null;
}
interface Shock {
  kind: 'MARKET' | 'EQUITY' | 'SECTOR' | 'HISTORY';
  shock?: number;
  change: number;
  day?: string;
  industry?: string;
}
interface Report {
  state: 'VALUED' | 'UNPRICED' | 'CASH';
  at: string;
  equity: number;
  cash: number;
  receivable: Metric;
  exposure: Metric;
  source: string | null;
  holdings: Holding[];
  industries: Industry[];
  fromDay: string;
  throughDay: string;
  risk: Risk | null;
  shocks: Shock[];
  drawdown: {
    current: number;
    maximum: number;
    peakAt: string | null;
    troughAt: string | null;
    recoveredAt: string | null;
    observations: number;
    underwaterDays: number;
  };
}
type Response = Report | { state: 'NO_OBSERVATION' };
const percent = (value: Metric, places = 1) => (value === null ? '—' : (value * 100).toFixed(places) + '%');
const currency = (value: Metric) =>
  value === null
    ? '—'
    : '¥ ' + value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const stamp = (at: string) => at.slice(0, 16).replace('T', ' ');
const tabs = ['持仓与行业', '市场与尾部风险', '压力与回撤'] as const;
type Tab = (typeof tabs)[number];

function Meter({ value, risk = false }: { value: Metric; risk?: boolean }) {
  return (
    <div className="arp-meter" data-risk={risk} data-negative={value !== null && value < 0}>
      <b>{percent(value)}</b>
      <span aria-hidden="true">
        <i style={{ width: Math.min(100, Math.abs(value ?? 0) * 100) + '%' }} />
      </span>
    </div>
  );
}

function Positions({ report, name }: { report: Report; name: (code: string) => string }) {
  const rows = [...report.holdings].sort((a, b) => (b.weight ?? 0) - (a.weight ?? 0));
  const known = rows.filter((row) => row.industry);
  const largest = report.industries.find((group) => group.industry && group.weight !== null);
  return (
    <>
      {largest && (
        <div className="arp-concentration">
          <span>最大已知行业仓位</span>
          <strong>{largest.industry}</strong>
          <b>{percent(largest.weight)}</b>
          <small>占账户权益 · {largest.count} 只股票</small>
        </div>
      )}
      {rows.length > 0 && (
        <div className="arp-scroll">
          <table className="arp-table">
            <thead>
              <tr>
                <th>股票 / 行业</th>
                <th>持仓市值</th>
                <th>资金权重</th>
                <th>风险贡献</th>
                <th title="从现金调入一个百分点仓位时，组合年化波动率的局部变化">增仓 1%¹</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.code}>
                  <th scope="row">
                    {row.name || name(row.code)}
                    <small title={row.industryAsOf && '行业观测日 ' + row.industryAsOf}>
                      {row.industry || '行业待核实'}
                      {row.stale && ' · 沿用上次估值'}
                    </small>
                  </th>
                  <td>
                    {currency(row.value)}
                    <small>{row.quantity.toLocaleString('zh-CN')} 股</small>
                  </td>
                  <td>
                    <Meter value={row.weight} />
                  </td>
                  <td>
                    <Meter value={row.riskShare} risk />
                  </td>
                  <td>
                    {row.marginalVol == null
                      ? '—'
                      : (row.marginalVol > 0 ? '+' : '') + row.marginalVol.toFixed(2) + ' 个百分点'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {rows.length > 0 && (
        <p className="arp-note">
          ¹ 从现金调入账户权益的 1%
          至该股，估算年化波动率的边际变化。风险贡献按协方差分摊，负值表示抵消了部分组合波动。
        </p>
      )}
      {rows.length > 0 && (
        <section className="arp-industries">
          <div className="arp-line">
            <h6>行业集中度</h6>
            <small>
              已核实 {known.length} / {rows.length} 只
            </small>
          </div>
          <div className="arp-industry-list">
            {report.industries.map((group) => (
              <div key={group.industry || 'unknown'}>
                <strong>{group.industry || '行业待核实'}</strong>
                <small>{group.count} 只持仓</small>
                <b>
                  {percent(group.weight)} <span>资金</span>
                </b>
                <b>
                  {percent(group.riskShare)} <span>风险</span>
                </b>
              </div>
            ))}
          </div>
        </section>
      )}
      <div className="arp-balances">
        <span>
          账户权益 <b>{currency(report.equity)}</b>
        </span>
        <span>
          现金 <b>{currency(report.cash)}</b>
        </span>
        {!!report.receivable && (
          <span>
            应收权益 <b>{currency(report.receivable)}</b>
          </span>
        )}
      </div>
    </>
  );
}

function MarketRisk({ report, name }: { report: Report; name: (code: string) => string }) {
  const risk = report.risk;
  if (!risk) {
    return (
      <p className="arp-note">
        {report.state === 'CASH' ? '该时点为空仓，无股票风险暴露。' : '缺少能与账户权益对平的历史持仓市值。'}
      </p>
    );
  }
  const pair = risk.strongestPair;
  return (
    <>
      <div className="arp-summary">
        <div>
          <span>组合年化波动率</span>
          <strong>{percent(risk.annualVol, 2)}</strong>
        </div>
        <div>
          <span>相对沪深 300 的 Beta</span>
          <strong>{risk.beta?.toFixed(2) ?? '—'}</strong>
        </div>
        <div>
          <span>单日 95% VaR</span>
          <strong>{percent(risk.var95, 2)}</strong>
        </div>
        <div>
          <span>尾部平均损失 ES</span>
          <strong>{percent(risk.es95, 2)}</strong>
        </div>
      </div>
      <div className="arp-decomposition">
        <h6>市场能解释多少组合波动？</h6>
        {risk.systematicShare !== null ? (
          <>
            <div className="arp-split">
              <i style={{ width: percent(risk.systematicShare) }} />
            </div>
            <div className="arp-line">
              <span>
                沪深 300 可解释的方差 <b>{percent(risk.systematicShare)}</b>
              </span>
              <span>
                剩余方差 <b>{percent(risk.residualShare)}</b>
              </span>
            </div>
          </>
        ) : (
          <p>需要至少 20 个与基准对齐的交易日，并且两组收益均有波动。</p>
        )}
        <p>Beta 衡量组合对指数的历史敏感度。剩余方差还可能包含行业因素。</p>
      </div>
      {pair && (
        <div className="arp-pair">
          <span>相关性绝对值最高</span>
          <b>
            {name(pair.left)} × {name(pair.right)}
          </b>
          <strong>{pair.rho.toFixed(2)}</strong>
        </div>
      )}
      <p className="arp-note">
        VaR 是历史单日损失的 95% 分位数，ES 是最差 5% 样本的平均损失。两者至少需要 60 个共同收益日。
      </p>
    </>
  );
}

function Stress({ report }: { report: Report }) {
  const dd = report.drawdown;
  return (
    <>
      {report.shocks.length > 0 && (
        <div className="arp-shocks">
          {report.shocks.map((s) => (
            <div key={s.kind}>
              <span>
                {s.kind === 'EQUITY'
                  ? '全部持股下跌 10%'
                  : s.kind === 'MARKET'
                    ? '沪深 300 下跌 5%'
                    : s.kind === 'SECTOR'
                      ? s.industry + '下跌 10%'
                      : '窗口内最差交易日'}
              </span>
              <small>
                {s.kind === 'HISTORY' ? s.day : s.kind === 'MARKET' ? '按历史 Beta 估算' : '其余资产价格固定'}
              </small>
              <strong data-gain={s.change > 0}>{currency(report.equity * s.change)}</strong>
              <b>{percent(s.change, 2)}</b>
            </div>
          ))}
        </div>
      )}
      <section className="arp-drawdown">
        <div className="arp-line">
          <h6>账户实际回撤</h6>
          <small>{dd.observations} 个权益观测点</small>
        </div>
        <div className="arp-summary">
          <div>
            <span>历史最大回撤</span>
            <strong>{percent(dd.maximum, 2)}</strong>
          </div>
          <div>
            <span>距最近高点</span>
            <strong>{percent(dd.current, 2)}</strong>
          </div>
          <div>
            <span>当前水下天数</span>
            <strong>
              {dd.underwaterDays} <small>自然日</small>
            </strong>
          </div>
        </div>
        {dd.peakAt && dd.troughAt && (
          <p>
            最大回撤从 {stamp(dd.peakAt)} 的高点至 {stamp(dd.troughAt)} 的低点，
            {dd.recoveredAt ? '于 ' + stamp(dd.recoveredAt) + ' 收复' : '截至所选时点仍未收复'}。
          </p>
        )}
      </section>
      <p className="arp-note">压力情景按所选持仓静态估算。实际回撤按账户初始本金和此后记录的权益逐点计算。</p>
    </>
  );
}

export function AccountPortfolioRisk({
  version,
  day,
  days,
  tick,
  onDay,
  name,
}: {
  version: number;
  day: string;
  days: string[];
  tick?: string;
  onDay: (day: string) => void;
  name: (code: string) => string;
}) {
  const [windowDays, setWindowDays] = useState(60);
  const [tab, setTab] = useState<Tab>('持仓与行业');
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<{ key: string; data?: Response; error?: string }>();
  const path =
    '/api/quant/paper/versions/' +
    version +
    '/portfolio-risk?window=' +
    windowDays +
    (day ? '&day=' + day : '');
  const key = [path, tick, retry].join('|');
  useEffect(() => {
    const controller = new AbortController();
    api<Response>(path, { signal: controller.signal })
      .then((data) => {
        if (!data?.state) {
          throw new Error('账户风险数据暂不可用');
        }
        if (!controller.signal.aborted) {
          setResult({ key, data });
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setResult({ key, error: error instanceof Error ? error.message : '读取账户风险失败' });
        }
      });
    return () => controller.abort();
  }, [key, path]);
  const current = result?.key === key ? result : undefined;
  const data = current?.data;
  const report = data?.state === 'NO_OBSERVATION' ? undefined : data;
  const risk = report?.risk;
  const hasDays = days.length > 0;
  const atName = (code: string) => report?.holdings.find((row) => row.code === code)?.name || name(code);
  return (
    <section className="arp" aria-label="模拟账户组合风险">
      <div className="arp-head">
        <div>
          <h5>组合风险分析</h5>
          <small>{report ? stamp(report.at) + ' 估值' : '模拟账户持仓'}</small>
        </div>
        <div className="arp-controls">
          {hasDays && (
            <label>
              持仓日
              <select aria-label="风险持仓日" value={day} onChange={(e) => onDay(e.target.value)}>
                {days.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            回看窗口
            <select
              aria-label="风险回看窗口"
              value={windowDays}
              onChange={(e) => setWindowDays(Number(e.target.value))}
            >
              {[20, 60, 120].map((n) => (
                <option key={n} value={n}>
                  {n} 个交易日
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <div className="arp-tabs" aria-label="风险分析视图">
        {tabs.map((t) => (
          <button type="button" key={t} aria-pressed={t === tab} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>
      {!current && <p role="status">正在核对账户估值与历史行情…</p>}
      {current?.error && (
        <p role="alert">
          {current.error}{' '}
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            重试
          </button>
        </p>
      )}
      {data?.state === 'NO_OBSERVATION' && <p>该日尚无账户权益记录。</p>}
      {report && (
        <>
          {report.state === 'UNPRICED' && (
            <p className="arp-notice">此时点缺少可核对的逐股估值，资金权重和风险分解暂缺。</p>
          )}
          {report.state === 'CASH' && tab === '持仓与行业' && <p>此时点账户为空仓。</p>}
          {risk && risk.days < 20 && (
            <p className="arp-notice">
              全部持仓仅有 {risk.days} 个共同收益日，波动率和风险贡献至少需要 20 日。
            </p>
          )}
          {tab === '持仓与行业' ? (
            <Positions report={report} name={name} />
          ) : tab === '市场与尾部风险' ? (
            <MarketRisk report={report} name={atName} />
          ) : (
            <Stress report={report} />
          )}
          <footer>
            {report.source && (
              <span>
                {
                  (
                    { BOOK: '账本估值', SNAPSHOT: '归档估值', MATCHED_QUOTES: '同刻行情核对估值' } as Record<
                      string,
                      string
                    >
                  )[report.source]
                }
              </span>
            )}
            {risk && (
              <span>
                以当时仓位回看 · {report.fromDay} 至 {report.throughDay} · 持仓共同 {risk.days} 日 / 与基准对齐{' '}
                {risk.marketDays} 日
              </span>
            )}
          </footer>
        </>
      )}
    </section>
  );
}
