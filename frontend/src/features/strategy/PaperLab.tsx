import { FormEvent, useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import './PaperLab.css';

export interface PaperSeed {
  hypothesis: string;
  factorCode?: string;
  academyVersionId?: number;
}
interface Term {
  feature: string;
  window: number;
  weight: number;
}
interface Rule {
  name: string;
  hypothesis: string;
  terms: Term[];
  entry: number;
  exit: number;
  slots: number;
}
interface Spec {
  rule: Rule;
  capital: number;
  codes: string[];
  source: string;
  pool: string;
  origin: string;
  parent?: number;
}
interface Position {
  lots: [string, number][];
  cost: number;
  price: number;
}
interface Book {
  equity: number;
  cash: number;
  fills: number;
  fees: number;
  realized: number;
  positions: Record<string, Position>;
  control?: Book;
  last?: string;
}
export interface PaperVersion {
  created?: string;
  id: number;
  spec: Spec;
  started?: string;
  status?: string;
  book?: Book;
}
interface Tick {
  at: string;
  equity: number;
  benchmark: number;
  control?: number;
}
interface Decision {
  action: string;
  side: string;
  code: string;
  quantity?: number;
  price?: number;
  fee?: number;
  rank: number;
  score: number;
  reason: string;
  available: string;
}
interface Point extends Tick {
  cash?: number;
  holdings?: Record<string, number>;
  events: Decision[];
  scores: Record<string, number | null>;
  reason: string;
}
interface Detail {
  stockNames?: Record<string, string>;
  review?: { at: string; model: string; text: string };
  daily: Tick[];
  points: Point[];
  days: string[];
  day: string;
  history: { curve: Tick[]; fills: number; fees: number };
}
const endpoint = '/api/quant/paper/versions';
const currency = (n: number) =>
  n.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
const pct = (n: number) => (n > 0 ? '+' : '') + (n * 100).toFixed(2) + '%';
const features: Record<string, string> = {
  momentum: '区间涨跌幅',
  volatility: '收益波动率',
  volumeSurprise: '相对放量',
  closeLocation: '收盘区间位置',
};
async function loadVersions() {
  const rows = await api<PaperVersion[]>(endpoint);
  if (!Array.isArray(rows)) {
    throw new Error('盘中实验服务响应格式无效，请确认行情服务和后端版本一致');
  }
  return rows;
}

const errText = (e: unknown) =>
  e instanceof Error ? e.message : '实验读取失败';

function Trace({
  points,
  capital,
  onDay,
  selected,
}: {
  points: Tick[];
  capital: number;
  onDay?: (v: string) => void;
  selected?: string;
}) {
  if (!points.length) {
    return <div className="paper-trace-pending">
      <i aria-hidden="true"/>
      <div><strong>{onDay ? '等待首个盘中行情截面' : '暂无回测净值数据'}</strong>
        {onDay && <p>收到行情后，这里会同时绘制账户与基准的净值。</p>}</div>
    </div>;
  }
  const values = [
    capital,
    ...points.flatMap((p) => [p.equity, p.benchmark, p.control ?? p.equity]),
  ];
  const low = Math.min(...values),
    high = Math.max(...values);
  const y = (v: number) => 126 - ((v - low) / (high - low || 1)) * 100;
  const x = (i: number) => 50 + (538 * i) / Math.max(points.length - 1, 1);
  const line = (k: 'equity' | 'benchmark' | 'control') =>
    points
      .map((p, i) => (i ? 'L' : 'M') + x(i) + ',' + y(p[k] ?? capital))
      .join(' ');
  const last = points.length - 1;
  return (
    <div className="quant-chart">
      <div className="paper-chart-key">
        <span>策略净值</span>
        <span>股票池等权基准</span>
        {points[0].control !== undefined && <span>原因子对照</span>}
      </div>
      <svg
        viewBox="0 0 600 146"
        role="img"
        aria-label="策略与股票池等权基准净值"
      >
        {[high, (high + low) / 2, low].map((value, index) => (
          <g key={index}>
            <text className="paper-axis-label" x="1" y={29 + index * 50}>
              {(value / capital).toFixed(3)}
            </text>
            <path d={`M46 ${26 + index * 50} H600`} className="quant-gridline" />
          </g>
        ))}
        {points[0].control !== undefined && (
          <path d={line('control')} className="paper-control" />
        )}
        <path d={line('benchmark')} className="quant-benchmark-path" />
        <path d={line('equity')} className="quant-strategy-path" />
        {onDay && selected && (
          <path
            d={`M${x(
              Math.max(
                0,
                points.findIndex((p) => p.at.startsWith(selected)),
              ),
            )} 18v110`}
            className="paper-cursor"
          />
        )}
        <circle
          cx={x(last)}
          cy={y(points[last].equity)}
          r="4"
          fill="var(--quant-cyan)"
        />
        {onDay &&
          points.map((p, i) => (
            <rect
              key={p.at}
              x={x(i) - 269 / Math.max(1, points.length - 1)}
              y="0"
              width={538 / Math.max(1, points.length - 1)}
              height="146"
              fill="transparent"
              onClick={() => onDay(p.at.slice(0, 10))}
            >
              <title>
                {p.at.slice(0, 10)} · ¥{currency(p.equity)}
              </title>
            </rect>
          ))}
      </svg>
      <div className="paper-axis">
        <span>{points[0].at.slice(0, 16).replace('T', ' ')}</span>
        <span>{points[last].at.slice(0, 16).replace('T', ' ')}</span>
      </div>
    </div>
  );
}

function Decisions({
  detail,
  selectDay,
  stockName,
}: {
  stockName: (code: string) => string;
  detail: Detail;
  selectDay: (day: string) => void;
}) {
  return (
    <section className="quant-readout">
      <header>
        <h4>盘中决策回放</h4>
        {detail.days.length > 0 && (
          <select
            aria-label="回看交易日"
            value={detail.day}
            onChange={(e) => selectDay(e.target.value)}
          >
            {detail.days.map((day) => (
              <option key={day}>{day}</option>
            ))}
          </select>
        )}
      </header>
      {!detail.points.length && (
        <p>取得第一根可用的五分钟行情后，这里会记录当时的排名、信号与成交。</p>
      )}
      <div className="paper-events">
        {[...detail.points].reverse().map((p) => (
          <details key={p.at}>
            <summary>
              <time>{p.at.slice(11, 16)}</time>
              <b>
                {p.events.length
                  ? p.events.map((e) => e.action + ' · ' + stockName(e.code)).join(' / ')
                  : '继续观察'}
              </b>
              <span>¥{currency(p.equity)}</span>
            </summary>
            {p.events.map((e, i) => (
              <p key={i}>
                <strong>
                  {e.side === 'BUY' ? '买入' : '卖出'} {stockName(e.code)}
                </strong>{' '}
                · {e.reason} · 排名 {e.rank} · 得分 {e.score.toFixed(4)}
                {e.quantity
                  ? ` · ${e.quantity} 股 × ¥${e.price?.toFixed(3)} · 费用 ¥${e.fee}`
                  : ''}{' '}
                · 信号 {e.available.slice(11, 19)}
              </p>
            ))}
            <p>{p.reason} · 当时的因子得分</p>
            {p.holdings && (
              <p>
                现金 ¥{currency(p.cash ?? 0)} ·{' '}
                {Object.entries(p.holdings)
                  .map(([code, q]) => stockName(code) + ' ' + q + ' 股')
                  .join(' / ') || '空仓'}
              </p>
            )}
            <div className="paper-scores">
              {Object.entries(p.scores)
                .sort((a, b) => (b[1] ?? -Infinity) - (a[1] ?? -Infinity))
                .map(([code, score]) => (
                  <span key={code}>
                    {stockName(code)} <b>{score?.toFixed(4) ?? '缺数'}</b>
                  </span>
                ))}
            </div>
          </details>
        ))}
      </div>
    </section>
  );
}

export function usePaperCatalog() {
  const [rows, setRows] = useState<PaperVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let alive = true;
    let timer: number;
    async function read() {
      try {
        const result = await loadVersions();
        if (alive) { setRows(result); setError(''); }
      } catch (e) {
        if (alive) { setError(errText(e)); }
      } finally {
        if (alive) {
          setLoading(false);
          timer = window.setTimeout(read, 30000);
        }
      }
    }
    void read();
    return () => { alive = false; window.clearTimeout(timer); };
  }, [revision]);
  return { rows, loading, error, refresh: () => setRevision(n => n + 1) };
}

type PaperCatalog = ReturnType<typeof usePaperCatalog>;

export function PaperLibrary({ factors, onOpen, catalog }: {
  factors?: boolean;
  onOpen: (id: number) => void;
  catalog: PaperCatalog;
}) {
  const { rows, error } = catalog;
  return (
    <details className="paper-library">
      <summary>
        <span className="research-library-title">{factors ? 'AI 盘中因子库' : 'AI 盘中策略库'}
          <small>{factors ? '查看每项因子的计算窗口与权重' : '查看已回测的盘中规则与研究假设'}</small></span>
        <span className="research-library-count">{catalog.loading || error ? '—' : rows.length} 个版本</span>
      </summary>
      {error && <p role="alert">{error}</p>}
      {!catalog.loading && !error && !rows.length && (
        <p>完成首次盘中策略回测后，已验证的规则与因子公式会出现在这里。</p>
      )}
      {rows.map((v) => (
        <button type="button" key={v.id} onClick={() => onOpen(v.id)}>
          <strong>{v.spec.rule.name}</strong>
          <span>
            {factors
              ? v.spec.rule.terms
                  .map(
                    (t) =>
                      t.weight +
                      ' × ' +
                      features[t.feature] +
                      '(' +
                      t.window +
                      ')',
                  )
                  .join(' + ')
              : v.spec.rule.hypothesis}
          </span>
          <b>查看实验 ↗</b>
        </button>
      ))}
    </details>
  );
}

export function PaperLab({ seed, initialId, comparison, catalog, accountsOnly = false,
  onAccount, onExperiment, onCompare, onBrowse }: {
  seed?: PaperSeed;
  initialId?: number;
  comparison?: PaperVersion;
  catalog: PaperCatalog;
  accountsOnly?: boolean;
  onAccount: (id: number) => void;
  onExperiment: (id: number) => void;
  onCompare: (base: PaperVersion) => void;
  onBrowse: () => void;
}) {
  const versions = accountsOnly ? catalog.rows.filter(v => v.started) : catalog.rows;
  const [selectedId, setId] = useState(initialId);
  const id = accountsOnly && !versions.some(v => v.id === selectedId) ? versions[0]?.id : selectedId;
  const [detail, setDetail] = useState<Detail>();
  const [day, setDay] = useState('');
  const editing = !accountsOnly && !initialId && Boolean(seed || comparison);
  const [hypothesis, setHypothesis] = useState(comparison ? '保持交易规则，检验加入波动率能否改善原因子的选股效果。' : seed?.hypothesis ?? '');
  const [capital, setCapital] = useState(comparison ? String(comparison.spec.capital) : '');
  const [pool, setPool] = useState(comparison?.spec.pool ?? 'DISCOVERY');
  const parent = comparison;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const active = versions.find((v) => v.id === id);
  const spec = active?.spec;
  const book = active?.book;
  useEffect(() => {
    let alive = true;
    setDetail(undefined);
    if (!id) {
      return;
    }
    async function read() {
      try {
        const value = await api<Detail>(
          endpoint + '/' + id + (day ? '?day=' + day : ''),
        );
        if (alive) {
          setDetail(value);
        }
      } catch (e) {
        if (alive) {
          setError(errText(e));
        }
      }
    }
    void read();
    const timer = window.setInterval(read, 30000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [id, day, refresh]);
  async function generate(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const item = await api<{ id: number }>(endpoint, {
        method: 'POST',
        body: JSON.stringify({
          hypothesis,
          capital: Number(capital),
          pool,
          parent: parent?.id,
          academyVersionId: seed?.academyVersionId,
          factorCode: seed?.factorCode,
        }),
      });
      catalog.refresh();
      onExperiment(item.id);
    } catch (e) {
      setError(errText(e));
    } finally {
      setBusy(false);
    }
  }
  async function deploy() {
    setBusy(true);
    setError('');
    try {
      if (!id) { return; }
      await api(endpoint + '/' + id + '/deploy', { method: 'POST' });
      catalog.refresh();
      onAccount(id);
    } catch (e) {
      setError(errText(e));
    } finally {
      setBusy(false);
    }
  }
  async function review() {
    setBusy(true);
    setError('');
    try {
      await api(endpoint + '/' + id + '/review', { method: 'POST' });
      setRefresh((n) => n + 1);
    } catch (e) {
      setError(errText(e));
    } finally {
      setBusy(false);
    }
  }
  const equity = book?.equity ?? spec?.capital ?? 0;
  const latest = detail?.daily[detail.daily.length - 1];
  const prior = detail?.daily[detail.daily.length - 2]?.equity ?? spec?.capital;
  const control = book?.control;
  function stockName(code: string) {
    if (!detail) {
      return '正在读取名称';
    }
    const index = spec?.codes.indexOf(code) ?? -1;
    return detail.stockNames?.[code] ?? (index >= 0 ? `待补名股票（${index + 1}）` : '待补名股票');
  }
  return (
    <section className="quant-results paper-lab" aria-label={accountsOnly ? '模拟实盘' : '盘中实验'}>
      {(error || catalog.error) && <div className="quant-catalog-error" role="alert"><span>{error || catalog.error}</span>
        <button onClick={() => { setError(''); catalog.refresh(); setRefresh(n => n + 1); }}>重新读取</button></div>}
      {editing && (
        <form className="quant-readout paper-form" onSubmit={generate}>
          <header>
            <h4>{parent ? '因子对照实验' : '新建盘中规则'}</h4>
            <button
              type="button"
              className="quant-record-link" disabled={busy}
              onClick={onBrowse}
            >
              取消
            </button>
          </header>
          <label>
            研究方向（选填）
            <textarea
              maxLength={1200}
              value={hypothesis}
              onChange={(e) => setHypothesis(e.target.value)}
              placeholder="留空由 AI 提出假设。也可以写一句你想验证的想法。"
            />
          </label>
          <div className="paper-fields">
            <label>
              每个账户的初始本金（元）
              <input
                type="number"
                required
                min=".01"
                max="10000000000"
                step=".01"
                disabled={!!parent}
                value={capital}
                onChange={(e) => setCapital(e.target.value)}
              />
            </label>
            <label>
              实验股票池
              <select
                disabled={!!parent}
                value={pool}
                onChange={(e) => setPool(e.target.value)}
              >
                <option value="DISCOVERY">股票发现候选池</option>
                <option value="WATCHLIST">我的自选股</option>
              </select>
            </label>
          </div>
          <p className="research-method-note">
            {parent
              ? '沿用原版本的本金、股票名单、仓位与买卖阈值，只改变因子公式。'
              : '创建时固定股票池，最多取前 24 只沪深 A 股，再用最近可用的五分钟行情回测。'}
          </p>
          <button className="quant-action" disabled={busy}>
            {busy ? '正在生成规则并回测…' : 'AI 编写规则并回测'}
          </button>
        </form>
      )}
      {!editing && <div className={accountsOnly && versions.length ? 'quant-experiment-grid' : 'quant-results'}>
        {accountsOnly && versions.length > 0 && <aside className="quant-run-list quant-panel">
          <div className="quant-panel-title"><h4>模拟账户</h4></div>
          {versions.map(v => <button key={v.id} className={v.id === id ? 'active' : ''} aria-pressed={v.id === id}
            onClick={() => { setDay(''); setId(v.id); }}>
            <i data-status="SUCCEEDED"/><span><b>{v.spec.rule.name}</b><small>实验 #{v.id}</small></span>
            <em>{v.book && pct(v.book.equity / v.spec.capital - 1)}</em>
          </button>)}
        </aside>}
        <div className="quant-results">
          {!active || !spec ? (
            <div className="quant-catalog-empty">
              <strong>{catalog.loading ? '正在读取…' : accountsOnly ? '暂无运行中的模拟账户' : '正在读取实验版本'}</strong>
              {accountsOnly && !catalog.loading && <><p>在实验室查看回测结果，启动模拟后可在这里跟踪账户。</p>
                <button onClick={catalog.rows.length ? () => onExperiment(catalog.rows[0].id) : onBrowse}>
                  {catalog.rows.length ? '查看最近的回测' : '前往实验室'}</button></>}
            </div>
          ) : (
            <>
              <section className="quant-readout paper-account">
                <header>
                  <div>
                    <small>
                      实验 #{active.id} · {active.status ?? '历史回测已完成'}
                    </small>
                    <h4>{spec.rule.name}</h4>
                  </div>
                  {accountsOnly && <button className="quant-action" onClick={() => onExperiment(active.id)}>查看原实验</button>}
                  {!accountsOnly && active.started && <button className="quant-action" onClick={() => onAccount(active.id)}>查看模拟账户</button>}
                  {!active.started && (
                    <button
                      disabled={busy}
                      className="quant-action"
                      onClick={deploy}
                    >
                      {spec.parent ? '启动对照模拟' : '启动模拟'}
                    </button>
                  )}
                </header>
                {!accountsOnly && <p>{spec.rule.hypothesis}</p>}
                {accountsOnly && book && <div className="quant-metrics paper-metrics">
                  <div><span>账户总资产（元）</span><strong>{currency(equity)}</strong></div>
                  <div><span>累计收益</span><strong>{pct(equity / spec.capital - 1)}</strong></div>
                  <div><span>当日盈亏（元）</span><strong>{latest && prior !== undefined ? currency(latest.equity-prior) : '—'}</strong></div>
                  <div><span>相对等权基准</span><strong>{latest ? pct((latest.equity-latest.benchmark)/spec.capital) : '—'}</strong></div>
                </div>}
                {accountsOnly && <details><summary>策略假设</summary><p>{spec.rule.hypothesis}</p></details>}
                {!accountsOnly && <p>初始本金 ¥{currency(spec.capital)} · 五分钟 · {spec.codes.length} 只股票</p>}
                {accountsOnly && active.started && (
                  <>
                    <Trace
                      points={detail?.daily ?? []}
                      capital={spec.capital}
                      selected={detail?.day}
                      onDay={setDay}
                    />
                    <p>
                      启动于 {active.started.slice(0, 16).replace('T', ' ')} ·
                      最近行情{' '}
                      {book?.last?.slice(0, 16).replace('T', ' ') ?? '等待中'}
                    </p>
                  </>
                )}
                {accountsOnly && control && (
                  <p>
                    同期原因子账户 #{spec.parent} · 净值 ¥
                    {currency(control.equity)} · 因子版本收益差{' '}
                    {pct((equity - control.equity) / spec.capital)} ·
                    两个账户同时启动
                  </p>
                )}
              </section>
              {!accountsOnly && <section className="quant-readout">
                <header>
                  <h4>策略与因子</h4>
                  <button className="quant-action" onClick={() => onCompare(active)}>新建因子对照</button>
                </header>
                <p>{spec.origin}</p>
                <div className="paper-formula">
                  {spec.rule.terms.map((t, i) => (
                    <span key={i}>
                      {i > 0 ? ' + ' : ''}
                      {t.weight} × {features[t.feature]}({t.window})
                    </span>
                  ))}
                </div>
                <p>
                  以五分钟为一根 K 线。买入得分 ≥ {spec.rule.entry}，卖出得分 ≤{' '}
                  {spec.rule.exit}，最多持有 {spec.rule.slots} 只。
                </p>
                <details>
                  <summary>
                    {spec.source} · {spec.codes.length} 只
                  </summary>
                  <p>{spec.codes.map(stockName).join(' · ')}</p>
                </details>
              </section>}
              {!accountsOnly && detail && (
                <section className="quant-readout">
                  <header>
                    <h4>历史回测</h4>
                    <small>
                      {detail.history.fills} 笔成交 · 费用 ¥
                      {currency(detail.history.fees)}
                    </small>
                  </header>
                  <p>
                    固定当前股票池回放，含事后选样影响。区间收益{' '}
                    {pct(
                      (detail.history.curve[detail.history.curve.length - 1]
                        ?.equity ?? spec.capital) /
                        spec.capital -
                        1,
                    )}
                    。
                  </p>
                  <details>
                    <summary>成交与费用假设</summary>
                    <p>
                      五分钟信号之后的可用 K
                      线按开盘价模拟成交，单笔最多使用该根成交量的
                      5%。买卖滑点各 5 个基点，佣金万三、最低 5
                      元，卖出另计万五印花税。执行
                      T+1；一字线跳过。当前账本未计分红送转。
                    </p>
                  </details>
                  <Trace points={detail.history.curve} capital={spec.capital} />
                </section>
              )}
              {accountsOnly && book && (
                <section className="quant-readout">
                  <header>
                    <h4>持仓与现金</h4>
                    <small>
                      {book.fills} 笔成交 · 累计费用 ¥{currency(book.fees)}
                    </small>
                  </header>
                  <dl className="paper-cash-ledger">
                    <div><dt>可用现金</dt><dd>¥{currency(book.cash)}</dd></div>
                    <div><dt>已实现盈亏</dt><dd>¥{currency(book.realized)}</dd></div>
                    <div><dt>未实现盈亏</dt><dd>¥{currency(book.equity - spec.capital - book.realized)}</dd></div>
                  </dl>
                  {Object.entries(book.positions).map(([code, pos]) => (
                    <div className="paper-position" key={code}>
                      <b>{stockName(code)}</b>
                      <span>{pos.lots.reduce((a, l) => a + l[1], 0)} 股</span>
                      <span>现价 ¥{pos.price}</span>
                      <span>持仓成本 ¥{currency(pos.cost)}</span>
                    </div>
                  ))}
                </section>
              )}
              {accountsOnly && detail && (
                <section className="quant-readout">
                  <header>
                    <h4>AI 收益复盘</h4>
                    <button className="quant-action"
                      onClick={review}
                      disabled={busy || !detail.daily.length}
                    >
                      {busy ? '处理中…' : '复盘当前表现'}
                    </button>
                  </header>
                  <p>
                    {detail.days.length} 个观察日 · {book?.fills ?? 0}{' '}
                    笔模拟成交{!detail.daily.length && '。记录首个交易日的行情后，即可生成复盘。'}
                  </p>
                  {detail.review && (
                    <>
                      <small>
                        {detail.review.at.slice(0, 16).replace('T', ' ')} ·{' '}
                        {detail.review.model}
                      </small>
                      <p style={{ whiteSpace: 'pre-wrap' }}>
                        {detail.review.text.replace(/(?<![\w.])(?:SH|SZ)?(\d{6})(?:\.(?:SH|SZ))?(?![\w]|\.\d)/gi,
                          (match, code: string) => spec.codes.includes(code) ? stockName(code) : match)}
                      </p>
                    </>
                  )}
                </section>
              )}
              {accountsOnly && detail && (
                <Decisions detail={detail} selectDay={setDay} stockName={stockName} />
              )}
            </>
          )}
        </div>
      </div>}
    </section>
  );
}
