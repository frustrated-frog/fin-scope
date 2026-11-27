import { BacktestOverview, PortfolioOverview } from './QuantAnalytics';
import { FormEvent, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { PaperResearchEvidence, type SymbolCoverage, type Trial } from './PaperResearchEvidence';
import { ClassicEditor, ClassicEvidence, ClassicSelection, defaultRecipe, type ClassicRecipe, type ClassicRecord, type Family, type Selection } from './ClassicStrategies';
import { ForwardPnl, type ObservedPnl } from './ForwardPnl';
import { WalkForward } from './WalkForward';
import { ParameterGrid } from './ParameterGrid';
import { ReturnAttribution } from './ReturnAttribution';
import { PerformanceReport, PlanReport, type ExecutionCosts, type RebalancePlan, type Performance } from './ExecutionReport';
import './PaperLab.css';

export interface PaperSeed {
  classic?: Family;
  hypothesis: string;
  factorCode?: string;
  academyVersionId?: number;
}
interface Term {
  feature: string;
  window: number;
  weight: number;
}
interface BasicRule {
  kind?: undefined;
  name: string;
  hypothesis: string;
  terms: Term[];
  entry: number;
  exit: number;
  slots: number;
}
interface PythonRule {
  kind: 'python';
  recipe?: ClassicRecipe;
  executionVersion?: number;
  exDateAccounting?: boolean;
  name: string;
  hypothesis: string;
  factor: string;
  code: string;
  snapshot: string;
}
type Rule = BasicRule | PythonRule;
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
  pricedAt?: string;
  lots: [string, number][];
  cost: number;
  price: number;
}
interface Book {
  executionCosts?:ExecutionCosts;
  rebalancePlan?:RebalancePlan;
  equity: number;
  cash: number;
  fills: number;
  fees: number;
  realized: number;
  dividendReceivable?: number;
  stale?: string[];
  positions: Record<string, Position>;
  control?: Book;
  last?: string;
}
export interface PaperVersion {
  comparisonId?: number | null;
  created?: string;
  id: number;
  spec: Spec;
  lifecycle?: 'RUNNING' | 'PAUSED' | 'STOPPED' | 'ARCHIVED';
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
  signalReason?: string;
}
interface Point extends Tick {
  rebalance?:RebalancePlan;
  selection?: Selection;
  cash?: number;
  holdings?: Record<string, number>;
  events: Decision[];
  controlEvents?: Decision[];
  shortHistory?: string[];
  unverifiedActions?: string[];
  scores: Record<string, number | null>;
  reason: string;
  previousAt?: string;
  missedBars?: number;
  stale?: string[];
}
interface TailModel {
  features: string[];
  coefficients: number[];
  formulas?: Record<string, { formula: string; reason: string }>;
  samplesByCode?: Record<string, number>;
  symbols: number;
  samples: number;
  dates: number;
  firstDay: string;
  lastLabel: string;
}

const factorLabels: Record<string, string> = {
  return5m: '5 分钟收益', return15m: '15 分钟收益', return60m: '60 分钟收益',
  return1d: '已收盘日收益', trend20d: '20 日趋势', trend60d: '60 日趋势',
};

interface Detail {
  forwardPerformance?:Performance;
  executionHealth?: {date:string; due:number; executed:number; days:{date:string; missed:number; missingTimes:string[]}[]} | null;
  daySummary?: { attribution?: ObservedPnl; at: string; pnl: number; fills: number; fees: number } | null;
  stockNames?: Record<string, string>;
  review?: { at: string; model: string; text: string };
  daily: Tick[];
  points: Point[];
  days: string[];
  day: string;
  forwardEvidence?: { sessions: number; fills: number; status: string; reasons: string[]; maxDrawdown: number; feeRate: number; relativeReturn: number } | null;
  history: ClassicRecord & { model?: TailModel; factorAuthor?: string; members?: {code: string; joined: string}[];
    researchCoverage?: Record<string, {days: number; bars: number; completeDays: number; measuredVolumeBars: number}>; unverifiedSymbols?: number; incompleteActionTerms?: number; researchPoolSize?: number; controlFees?: number; controlFills?: number; developmentTrials?: Trial[]; validationMode?: string; engine?: string; symbolCoverage?: Record<string,SymbolCoverage>; curve: Tick[]; fills: number; fees: number; firstDay?: string; lastDay?: string;
    days?: number; validationStart?: string; developmentReturn?: number; validationReturn?: number;
    validationBenchmark?: number; controlReturn?: number; estimatedVolumeBars?: number; coverage?: Record<string, number> };
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

export function PaperTrace({
  points,
  capital,
  onDay,
  selected,
  controlLabel = '原因子对照',
}: {
  points: Tick[];
  capital: number;
  onDay?: (v: string) => void;
  selected?: string;
  controlLabel?: string;
}) {
  const fillId = useId();
  const [cursor,setCursor] = useState<number>();
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
  const index = Math.min(cursor ?? last,last);
  const observation = points[index];
  return (
    <div className="quant-chart">
      <div className="paper-chart-key">
        <span>策略净值</span>
        <span>股票池等权基准</span>
        {points[0].control !== undefined && <span>{controlLabel}</span>}
      </div>
      <svg
        viewBox="0 0 600 146"
        role="img"
        aria-label="策略与股票池等权基准净值"
      >
        <defs><linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
          <stop stopColor="#65c9df" stopOpacity=".23"/><stop offset="1" stopColor="#65c9df" stopOpacity=".01"/>
        </linearGradient></defs>
        <path d={line('equity')+' L'+x(last)+' 126 H50 Z'} fill={'url(#'+fillId+')'}/>
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
          cx={x(onDay ? last : index)}
          cy={y(onDay ? points[last].equity : observation.equity)}
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
      {!onDay && points.length>1 && <label className="qh-inspect"><span>{observation.at.slice(5,16).replace('T',' ')} · 策略 {(observation.equity/capital).toFixed(3)} · 基准 {(observation.benchmark/capital).toFixed(3)}</span>
        <input type="range" aria-label="逐时点查看回测净值" min="0" max={last} value={index} onChange={e=>setCursor(Number(e.target.value))}/></label>}
      <div className="paper-axis">
        <span>{points[0].at.slice(0, 16).replace('T', ' ')}</span>
        <span>{points[last].at.slice(0, 16).replace('T', ' ')}</span>
      </div>
    </div>
  );
}

function valueTone(value: number) {
  if (value > 0) {
    return 'positive';
  }
  return value < 0 ? 'negative' : 'neutral';
}

function LedgerAmount({ value }: { value: number }) {
  return <><span className="paper-currency-mark">¥</span>{currency(value).replace(/^-/, '−')}</>;
}

function Holdings({ book, capital, stockName }: {
  book: Book;
  capital: number;
  stockName: (code: string) => string;
}) {
  const unrealized = Math.round((book.equity - capital - book.realized) * 100) / 100;
  const positions = Object.entries(book.positions);
  return <section className="quant-readout paper-glass paper-holdings">
    <header><h4>持仓与现金</h4><small>{book.fills} 笔成交 · 累计费用 ¥{currency(book.fees)}</small></header>
    <dl className="paper-cash-ledger">
      <div><dt>可用现金</dt><dd key={book.cash}><LedgerAmount value={book.cash} /></dd></div>
      {!!book.dividendReceivable && <div><dt>应收股息</dt><dd><LedgerAmount value={book.dividendReceivable} /></dd></div>}
      <div><dt>已实现盈亏</dt><dd key={book.realized} data-tone={valueTone(book.realized)}><LedgerAmount value={book.realized} /></dd></div>
      <div><dt>未实现盈亏</dt><dd key={unrealized} data-tone={valueTone(unrealized)}><LedgerAmount value={unrealized} /></dd></div>
    </dl>
    {positions.length > 0 ? <table className="paper-position-table" aria-label="当前持仓">
      <thead><tr><th>股票</th><th>持股数</th><th>现价</th><th>持仓成本</th></tr></thead>
      <tbody>{positions.map(([code, pos]) => <tr className="paper-position" key={code}>
        <th scope="row"><b>{stockName(code)}</b></th>
        <td>{pos.lots.reduce((total, lot) => total + lot[1], 0)} 股</td>
        <td>¥{pos.price}{book.stale?.includes(code) && <small> · 最近估值 {pos.pricedAt?.slice(5,16).replace('T',' ')}</small>}</td><td>¥{currency(pos.cost)}</td>
      </tr>)}</tbody>
    </table> : <p>当前空仓</p>}
  </section>;
}

function FactorScores({ scores, stockName }: { scores: Point['scores']; stockName: (code: string) => string }) {
  const ranked = Object.entries(scores).sort((a, b) => (b[1] ?? -Infinity) - (a[1] ?? -Infinity));
  const extent = Math.max(0, ...ranked.map(([, score]) => Math.abs(score ?? 0)));
  return <section className="paper-factor-board" aria-label="当时的因子得分">
    <h5>当时的因子得分</h5>
    <ol className="paper-scores">
      {ranked.map(([code, score]) => <li key={code}>
        <span>{stockName(code)}</span><b>{score?.toFixed(4) ?? '缺数'}</b>
        <i className="paper-score-axis" aria-hidden="true">
          {score !== null && extent > 0 && <i data-direction={valueTone(score)}
            style={{ width: Math.abs(score) / extent * 50 + '%' }}/>}
        </i>
      </li>)}
    </ol>
  </section>;
}

function DecisionList({events, stockName}: {events: Decision[]; stockName: (code: string) => string}) {
  return (<div className="paper-decision-list">{events.map((e, i) => <div className="paper-decision" key={i} data-side={e.side}>
                <div className="paper-decision-title">
                  <strong>{e.side === 'BUY' ? '买入' : '卖出'} {stockName(e.code)}</strong>
                  <span data-blocked={e.action === '未成交'}>{e.action}</span>
                  <time>信号 {e.available.slice(11, 19)}</time>
                </div>
                <p>{e.reason}</p>
                {e.signalReason && e.signalReason !== e.reason && <p>{e.signalReason}</p>}
                <div className="paper-decision-values">
                  <span>排名 <b>{e.rank}</b></span><span>得分 <b>{e.score.toFixed(4)}</b></span>
                  {e.quantity ? <span>{e.quantity} 股 × ¥{e.price?.toFixed(3)} · 费用 ¥{e.fee}</span> : null}
                </div>
              </div>)}</div>);
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
    <section className="quant-readout paper-glass paper-replay">
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
                  : p.controlEvents?.length ? '对照账户有交易记录' : '继续观察'}
              </b>
              <span>¥{currency(p.equity)}</span>
            </summary>
            <div className="paper-replay-body">
              <DecisionList events={p.events} stockName={stockName}/>
              {!!p.controlEvents?.length && <details><summary>{detail.history.model ? '因子等权对照' : '原策略对照'} · {p.controlEvents.length} 条记录</summary>
                <DecisionList events={p.controlEvents} stockName={stockName}/>
              </details>}
              {p.selection && <ClassicSelection value={p.selection} name={stockName}/>}
              {p.rebalance && <PlanReport plan={p.rebalance} name={stockName}/>}
              <p className="paper-point-reason">{p.reason}</p>
              {p.holdings && <div className="paper-point-book">
                <strong>现金 ¥{currency(p.cash ?? 0)}</strong>
                {Object.entries(p.holdings).map(([code, q]) => <span key={code}>{stockName(code)} {q} 股</span>)}
                {!Object.keys(p.holdings).length && <span>空仓</span>}
              </div>}
              {!!p.stale?.filter(c => !p.unverifiedActions?.includes(c)).length && <p>缺少本根行情 · {p.stale.filter(c => !p.unverifiedActions?.includes(c)).map(stockName).join('、')}</p>}
              {!!p.unverifiedActions?.length && <p>除权权益待核验，本时点暂缓交易并沿用上次估值 · {p.unverifiedActions.map(stockName).join('、')}</p>}
              {!!p.shortHistory?.length && <p>历史除权资料待核验，仅使用可核验的行情窗口 · {p.shortHistory.map(stockName).join('、')}</p>}
              {p.missedBars ? <p>此前有 {p.missedBars} 根盘中行情未执行。</p> : null}
              <FactorScores scores={p.scores} stockName={stockName}/>
            </div>
          </details>
        ))}
      </div>
    </section>
  );
}

export function lifecycleLabel(v: PaperVersion) {
  if (!v.started) { return '已回测'; }
  return ({ RUNNING: '模拟中', PAUSED: '已暂停', STOPPED: '已停止', ARCHIVED: '已归档' })[v.lifecycle ?? 'RUNNING'];
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
        <span className="research-library-title">{factors ? '策略附带的因子说明' : 'AI 盘中策略库'}
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
              ? v.spec.rule.kind === 'python' ? v.spec.rule.factor : v.spec.rule.terms
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

export function PaperLab({ seed, initialId, initialDay, initialAnalysis = '总览', onAnalysisResult, comparison, catalog, accountsOnly = false,
  onAccount, onExperiment, onCompare, onBrowse }: {
  seed?: PaperSeed;
  initialId?: number;
  initialDay?: string;
  initialAnalysis?: string;
  onAnalysisResult?: (id: number) => void;
  comparison?: PaperVersion;
  catalog: PaperCatalog;
  accountsOnly?: boolean;
  onAccount: (id: number) => void;
  onExperiment: (id: number) => void;
  onCompare: (base: PaperVersion, question?: string) => void;
  onBrowse: () => void;
}) {
  const versions = accountsOnly ? catalog.rows.filter(v => v.started && (v.lifecycle !== 'ARCHIVED' || v.id === initialId)) : catalog.rows;
  const [selectedId, setId] = useState(initialId);
  const id = accountsOnly && !versions.some(v => v.id === selectedId) ? versions[0]?.id : selectedId;
  const [detail, setDetail] = useState<Detail>();
  const [requestedView, setAnalysisView] = useState(initialAnalysis);
  const [day, setDay] = useState(initialDay ?? '');
  const editing = !accountsOnly && !initialId && Boolean(seed || comparison);
  const [hypothesis, setHypothesis] = useState(seed?.hypothesis || (comparison && !(comparison.spec.rule.kind === 'python' && comparison.spec.rule.recipe) ? '保持交易规则，检验加入波动率能否改善原因子的选股效果。' : ''));
  const [capital, setCapital] = useState(comparison ? String(comparison.spec.capital) : '');
  const [pool, setPool] = useState(comparison?.spec.pool ?? 'DISCOVERY');
  const originalRecipe = comparison?.spec.rule.kind === 'python' ? comparison.spec.rule.recipe : undefined;
  const [recipe, setRecipe] = useState<ClassicRecipe>({maxFeeBps:0, riskWindow:10, targetVol:0, stopLoss:0, drawdownStop:0,
    ...(originalRecipe ?? {...defaultRecipe, family:seed?.classic ?? defaultRecipe.family})});
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [autoStart, setAutoStart] = useState(!seed?.classic && !originalRecipe);
  const submission = useRef<{ body: string; token: string }>();
  const parent = comparison;
  const [engine, setEngine] = useState(originalRecipe || seed?.classic ? 'classic' : comparison && comparison.spec.rule.kind !== 'python' ? 'luna' : 'codex');
  const [autoIterate, setAutoIterate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const active = versions.find((v) => v.id === id);
  const spec = active?.spec;
  const classic = spec?.rule.kind === 'python' ? spec.rule.recipe : undefined;
  const canReplay = !!classic && spec?.rule.kind==='python' && spec.rule.executionVersion===2;
  const analysisView = !canReplay && ['滚动验证','参数实验'].includes(requestedView) ? '总览' : requestedView;
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
      const coding = engine !== 'luna';
      const body = JSON.stringify(engine === 'classic' ? {recipe, question:hypothesis.trim() || undefined, reference:parent?.id, autoStart, capital:Number(capital),
        startDate:startDate || undefined, endDate:endDate || undefined} : engine === 'ridge' ? { autoStart, capital: Number(capital) } : { hypothesis, autoStart, capital: Number(capital), pool: parent ? undefined : pool, parent: parent?.id,
        academyVersionId: seed?.academyVersionId, factorCode: seed?.factorCode });
      const key = engine + body + autoIterate;
      if (coding && submission.current?.body !== key) {
        submission.current = {body: key, token: crypto.randomUUID()};
      }
      const item = await api<{ id: number }>(engine === 'classic' ? endpoint + '/jobs/classic' : engine === 'ridge' ? endpoint + '/jobs/ridge' : coding ? endpoint + '/jobs?autoIterate=' + (autoStart && autoIterate) : endpoint, {
        method: 'POST', body, headers: coding ? { 'Idempotency-Key': submission.current!.token } : undefined,
      });
      catalog.refresh();
      if (coding) {
        onBrowse();
      } else if (autoStart) {
        onAccount(item.id);
      } else {
        onExperiment(item.id);
      }
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
  async function operate(action: string) {
    if (action === 'state/STOPPED' && !window.confirm('停止后将冻结账户并结束调仓，仍可查看历史记录。确认停止？')) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api(endpoint + '/' + id + '/' + action, { method: 'POST' });
      catalog.refresh();
      if (action === 'replay' || action === 'state/ARCHIVED') {
        onBrowse();
      }
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
      await api(endpoint + '/' + id + '/review?day=' + encodeURIComponent(detail?.day ?? ''), { method: 'POST' });
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
            <h4>{parent ? '策略对照实验' : '新建盘中实验'}</h4>
            <button
              type="button"
              className="quant-record-link" disabled={busy}
              onClick={onBrowse}
            >
              取消
            </button>
          </header>
          <label>研究方式
            <select aria-label="策略引擎" value={engine} disabled={!!parent || busy || seed?.factorCode?.startsWith('paper:')} onChange={e => {setEngine(e.target.value); if (e.target.value === 'classic') {setAutoStart(false);} }}>
              <option value="codex">自由编写策略 · Codex Python</option><option value="luna">验证因子规则 · Luna</option>
              {(originalRecipe || (!parent && !seed?.academyVersionId && !seed?.factorCode)) && <option value="classic">组合策略组件 · 可视化构建</option>}
              {!parent && !seed?.academyVersionId && !seed?.factorCode && <option value="ridge">对照因子权重 · Ridge 与等权</option>}
            </select>
          </label>
          {engine === 'classic' && <label>本次研究问题（选填）<textarea rows={2} maxLength={2000} value={hypothesis}
            onChange={event=>setHypothesis(event.target.value)} placeholder="记录这次想验证的问题和判断标准"/></label>}
          {engine === 'classic' && <><ClassicEditor recipe={recipe} onChange={setRecipe} fixedBase={!!parent}/>
            {parent ? <p>沿用原实验的股票名单、区间和行情快照。</p> : <fieldset className="classic-date-range"><legend>回测区间</legend>
              <div className="classic-fields"><label>回测开始日<input type="date" value={startDate} max={endDate || undefined} required={!!endDate} onChange={e=>setStartDate(e.target.value)}/></label>
              <label>回测结束日<input type="date" value={endDate} min={startDate || undefined} required={!!startDate} onChange={e=>setEndDate(e.target.value)}/></label></div>
              <p>留空使用最近收盘日及之前 30 个自然日。选择更长区间时按已归档的行情回放。</p></fieldset>}</>}
          {engine !== 'ridge' && engine !== 'classic' && <label>
            研究方向（选填）
            <textarea
              maxLength={1200}
              value={hypothesis}
              onChange={(e) => setHypothesis(e.target.value)}
              placeholder="留空由 AI 提出假设。也可以写一句你想验证的想法。"
            />
          </label>}
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
            {engine !== 'classic' && <label>
              实验股票池
              <select
                disabled={!!parent || engine === 'ridge'}
                value={engine === 'ridge' ? 'COHORT' : pool}
                onChange={(e) => setPool(e.target.value)}
              >
                {(engine === 'ridge' || pool === 'COHORT') && <option value="COHORT">按首次入池时间冻结的研究名单</option>}
                <option value="DISCOVERY">股票发现候选池</option>
                <option value="WATCHLIST">我的自选股</option>
              </select>
            </label>}
          </div>
          {engine !== 'classic' && <p className="research-method-note">
            {parent
              ? engine === 'codex' ? '沿用原版本的行情快照和本金。新旧策略在同一时间窗口独立执行。' : '沿用原版本的本金、股票名单、仓位与买卖阈值，只改变因子公式。'
              : engine === 'ridge' ? 'Codex 用开发期数据提出因子，Ridge 学习权重。两组在同一股票池于 14:45 选股，次日开盘后卖出，各用一份本金。'
              : engine === 'codex' ? 'Codex 编写因子、仓位和调仓逻辑，在隔离环境试跑。按交易日留出最后一段行情验证。' : '创建时固定股票池，最多取前 24 只沪深 A 股，再用最近可用的五分钟行情回测。'}
          </p>}
          <label className="paper-start-option">
            <input type="checkbox" checked={autoStart} disabled={busy} onChange={e => setAutoStart(e.target.checked)}/>
            回测完成后自动启动模拟账户
          </label>
          {engine === 'codex' && autoStart && <label className="paper-start-option">
            <input type="checkbox" checked={autoIterate} disabled={busy} onChange={e => setAutoIterate(e.target.checked)}/>
            满五个模拟交易日后，结合成交和风险决定是否研究对照策略
          </label>}
          <button className="quant-action" disabled={busy}>
            {engine === 'classic' ? busy ? '正在提交…' : (autoStart ? '回测并启动模拟' : '运行经典策略回测') : engine === 'ridge' ? busy ? '正在提交…' : '训练 Ridge 并对照回测' : engine === 'codex' ? busy ? '正在提交…' : '提交 Codex 研究' : busy ? '正在生成规则并回测…' : autoStart ? '回测并启动模拟' : 'AI 编写规则并回测'}
          </button>
        </form>
      )}
      {!editing && <div className={accountsOnly && versions.length ? 'quant-experiment-grid' : 'quant-results'}>
        {accountsOnly && versions.length > 0 && <aside className="quant-run-list quant-panel">
          <div className="quant-panel-title"><h4>模拟账户</h4></div>
          {versions.map(v => <button key={v.id} className={v.id === id ? 'active' : ''} aria-pressed={v.id === id}
            onClick={() => { setDay(''); setId(v.id); }}>
            <i data-status="SUCCEEDED"/><span><b>{v.spec.rule.name}</b><small>实验 #{v.id} · {lifecycleLabel(v)}</small></span>
            <em>{v.book && pct(v.book.equity / v.spec.capital - 1)}</em>
          </button>)}
        </aside>}
        <div className="quant-results">
          {!active || !spec ? (
            <div className="quant-catalog-empty">
              <strong>{catalog.loading ? '正在读取…' : accountsOnly ? '暂无运行中的模拟账户' : '请选择已有的回测版本'}</strong>
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
                  {!accountsOnly && <button className="quant-action" onClick={() => onCompare(active)}>{classic ? '沿用数据做对照' : spec.rule.kind === 'python' ? '衍生对照策略' : '新建因子对照'}</button>}
                </header>
                {accountsOnly && active.started && <div className="paper-account-actions">
                  {(!active.lifecycle || active.lifecycle === 'RUNNING') &&
                    <button type="button" data-action="pause" disabled={busy} onClick={() => operate('state/PAUSED')}>暂停模拟</button>}
                  {active.lifecycle === 'PAUSED' && <button type="button" data-action="resume" disabled={busy} onClick={() => operate('state/RUNNING')}>恢复模拟</button>}
                  {(!active.lifecycle || ['RUNNING','PAUSED'].includes(active.lifecycle)) &&
                    <button type="button" data-action="stop" disabled={busy} onClick={() => operate('state/STOPPED')}>停止账户</button>}
                  {active.lifecycle === 'STOPPED' && <button type="button" data-action="archive" disabled={busy} onClick={() => operate('state/ARCHIVED')}>归档账户</button>}
                </div>}
                {!accountsOnly && <p>{spec.rule.hypothesis}</p>}
                {classic && spec.origin && spec.origin!==spec.rule.name && <p>本次研究问题 · {spec.origin}</p>}
                {accountsOnly && book && <div className="quant-metrics paper-metrics">
                  <div><span>账户总资产（元）</span><strong>{currency(equity)}</strong></div>
                  <div><span>累计收益</span><strong>{pct(equity / spec.capital - 1)}</strong></div>
                  <div><span>当日盈亏（元）</span><strong>{latest && prior !== undefined ? currency(latest.equity-prior) : '—'}</strong></div>
                  <div><span>相对等权基准</span><strong>{latest ? pct((latest.equity-latest.benchmark)/spec.capital) : '—'}</strong></div>
                </div>}
                {accountsOnly && book && detail && <PortfolioOverview book={book} capital={spec.capital} daily={detail.daily}
                  performance={detail.forwardPerformance} name={stockName}/>}
                {accountsOnly && detail?.daySummary?.attribution && <ForwardPnl value={detail.daySummary.attribution} name={stockName}/>}
                {accountsOnly && detail && <section className="qh-events"><h5>最近投资决策 <small>{detail.day}</small></h5>
                  {detail.points.filter(point=>point.events?.length).slice(-3).reverse().map(point=>
                    <div key={point.at}><time>{point.at.slice(11,16)}</time><span>{point.events.map((event,index)=><span key={index}>
                      <b>{event.side==='BUY'?'买入':'卖出'} {stockName(event.code)}</b> · {event.action}{event.quantity ? ' · '+event.quantity+' 股' : ''} {event.reason}</span>)}</span></div>)}
                  {!detail.points.some(point=>point.events?.length) && <p>所选交易日暂无买卖事件。</p>}</section>}
                {accountsOnly && <details><summary>策略假设</summary><p>{spec.rule.hypothesis}</p></details>}
                {!accountsOnly && <p>初始本金 ¥{currency(spec.capital)} · 五分钟撮合 · {classic ? '快照末期股票池 ' : ''}{spec.codes.length} 只</p>}
                {accountsOnly && detail?.executionHealth && <div className="paper-execution-health">
                  <strong>{detail.executionHealth.date} 执行</strong><span>{detail.executionHealth.executed} / {detail.executionHealth.due} 个到期时点已运行</span>
                  {detail.executionHealth.days.some(day => day.missed > 0) && <details><summary>查看近七天漏跑时点</summary>
                    {detail.executionHealth.days.filter(day => day.missed > 0).map(day =>
                      <p key={day.date}>{day.date} · {day.missed} 次 · {day.missingTimes.join(' · ')}</p>)}</details>}
                </div>}
                {accountsOnly && active.started && (
                  <>
                    <PaperTrace
                      points={detail?.daily ?? []}
                      capital={spec.capital}
                      controlLabel={detail?.history.model ? '因子等权对照' : undefined}
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
                    {spec.parent ? '同期原因子账户 #' + spec.parent : '同期因子等权账户'} · 净值 ¥
                    {currency(control.equity)} · 扣费后收益差{' '}
                    {pct((equity - control.equity) / spec.capital)} ·
                    两个账户同时启动
                  </p>
                )}
              </section>
              {!accountsOnly && detail && (
                <section className="quant-readout">
                  <header>
                    <h4>历史回测</h4>
                    <small>
                      {detail.history.fills} 笔成交 · 费用 ¥
                      {currency(detail.history.fees)}
                    </small>
                  </header>
                  <nav className="qh-tabs" aria-label="回测分析维度">
                    {['总览', '收益归因', ...(detail.history.comparison ? ['同数据对照'] : []), ...(classic ? ['组合与交易'] : []), ...(canReplay ? ['参数实验','滚动验证'] : []), '研究依据'].map(view=>
                      <button key={view} aria-pressed={view===analysisView} onClick={()=>setAnalysisView(view)}>{view}</button>)}
                  </nav>
                  {analysisView==='总览' && <BacktestOverview history={detail.history} capital={spec.capital}>
                  <PaperTrace points={detail.history.curve} capital={spec.capital} controlLabel={detail.history.model ? '因子等权对照' : undefined} />
                  </BacktestOverview>}
                  {analysisView==='滚动验证' && classic && <WalkForward key={active.id} version={active.id} recipe={classic}/>}
                  {analysisView==='参数实验' && classic && <ParameterGrid key={active.id} version={active.id} recipe={classic}
                    capital={spec.capital} onResult={onAnalysisResult ?? onExperiment}/>}
                  {analysisView==='收益归因' && <ReturnAttribution key={active.id} version={active} onCompare={onCompare}/>}
                  {analysisView==='同数据对照' && detail.history.comparison && <section className="qh-comparison">
                    <h5>同一份冻结行情 · 实验 #{detail.history.comparison.version} 与 #{active.id}</h5>
                    <table><thead><tr><th>指标</th><th>原实验 #{detail.history.comparison.version}</th><th>本次实验 #{active.id}</th></tr></thead>
                      <tbody>{[
                        ['扣费收益',pct(detail.history.comparison.netReturn),detail.history.validationReturn!=null ? pct(detail.history.validationReturn) : '—'],
                        ['日末最大回撤',detail.history.comparison.performance?.maxDrawdown!=null ? pct(-detail.history.comparison.performance.maxDrawdown) : '—', detail.history.performance?.maxDrawdown!=null ? pct(-detail.history.performance.maxDrawdown) : '—'],
                        ['实际成交',detail.history.comparison.fills+' 笔',detail.history.fills+' 笔'],
                        ['累计费用','¥ '+currency(detail.history.comparison.fees),'¥ '+currency(detail.history.fees)]
                      ].map(([label,base,current])=><tr key={label}><th>{label}</th><td>{base}</td><td>{current}</td></tr>)}</tbody></table>
                    {detail.history.comparison.executionVersion===1 && <p>原版本采用单次信号执行，两次实验的执行方式也有差异。</p>}
                    <button className="qh-link" onClick={() => onExperiment(detail.history.comparison!.version)}>打开原实验 #{detail.history.comparison.version} ↗</button></section>}
                  {analysisView==='组合与交易' && classic && <ClassicEvidence history={detail.history} recipe={classic} name={stockName}/>}
                  {analysisView==='研究依据' && <>
                  {detail.history.performance && <PerformanceReport value={detail.history.performance} costs={detail.history.costs}/>}
                  {!classic && spec.rule.kind === 'python' && <div className="paper-validation">
                    <div><small>{detail.history.model ? '训练期内回放收益' : '开发段收益'}</small><b>{pct(detail.history.developmentReturn ?? 0)}</b></div>
                    <div><small>留出段收益</small><b>{pct(detail.history.validationReturn ?? 0)}</b></div>
                    <div><small>留出段等权基准</small><b>{pct(detail.history.validationBenchmark ?? 0)}</b></div>
                    {detail.history.controlReturn !== undefined && <div><small>{detail.history.model ? '因子等权对照' : '父版本同期收益'}</small><b>{pct(detail.history.controlReturn)}</b></div>}
                  </div>}
                  {!classic && <p>
                    {detail.history.model ? '交易池按入池顺序预先固定。' : '固定当前股票池回放，含事后选样影响。'}{spec.rule.kind === 'python' ? '留出段收益' : '区间收益'}{' '}
                    {pct(
                      (detail.history.curve[detail.history.curve.length - 1]
                        ?.equity ?? spec.capital) /
                        spec.capital -
                        1,
                    )}
                    。
                  </p>}
                  {detail.history.model && <details className="paper-model-study" open>
                    <summary>{detail.history.factorAuthor ? 'Codex 因子与 Ridge 权重' : 'Ridge 模型与训练样本'}</summary>
                    <p>{detail.history.model.firstDay} 至 {detail.history.model.lastLabel} · {detail.history.model.dates} 个信号日 · {detail.history.model.samples} 个有效股日样本</p>
                    <p>登记 {detail.history.researchPoolSize} 只，{detail.history.model.symbols} 只有合格训练样本。开发段拟合后冻结权重，每个交易日在训练中占相同权重。</p>
                    <dl>{detail.history.model.features.map((feature, i) => <div key={feature}>
                      <dt>{factorLabels[feature] ?? feature}</dt><dd>{(detail.history.model!.coefficients[i] * 100).toFixed(3)}%</dd>
                      {detail.history.model!.formulas?.[feature] && <dd className="paper-factor-definition">
                        <code>{detail.history.model!.formulas[feature].formula}</code>
                        <p>{detail.history.model!.formulas[feature].reason}</p>
                      </dd>}
                    </div>)}</dl>
                    <p>系数表示因子上升一个训练标准差时，预测隔夜收益的变化。留出段对照成交 {detail.history.controlFills} 笔，费用 ¥ {currency(detail.history.controlFees ?? 0)}。</p>
                    {!!(detail.history.unverifiedSymbols || detail.history.incompleteActionTerms) && <p>
                      留出段有 {detail.history.unverifiedSymbols} 只股票缺少完整除权核验，{detail.history.incompleteActionTerms} 次除权只有日期，相关权益可能影响净值。
                    </p>}
                  </details>}
                  {detail.history.researchCoverage && detail.history.members && <details><summary>研究股票池与开发期覆盖 · {detail.history.members.length} 只</summary>
                    <div className="paper-coverage-scroll"><table className="paper-position-table">
                      <thead><tr><th>股票</th><th>首次入池</th><th>完整交易日</th><th>实测量 / 总根数</th><th>训练样本</th></tr></thead>
                      <tbody>{detail.history.members.map(member => {
                        const coverage = detail.history.researchCoverage?.[member.code];
                        return <tr key={member.code}><th>{stockName(member.code)}</th><td>{member.joined.slice(0,10)}</td>
                          <td>{coverage?.completeDays ?? 0}</td><td>{coverage?.measuredVolumeBars ?? 0} / {coverage?.bars ?? 0}</td>
                          <td>{detail.history.model?.samplesByCode?.[member.code] ?? 0}</td></tr>;
                      })}</tbody>
                    </table></div>
                  </details>}
                  {detail.history.developmentTrials && <><h5>开发段候选与因子评价</h5><PaperResearchEvidence trials={detail.history.developmentTrials}/></>}
                  {spec.rule.kind === 'python' && <details><summary>行情快照与复验 · {detail.history.days} 个交易日</summary>
                    <p>{detail.history.firstDay} 至 {detail.history.lastDay}。{classic ? '按各时点已经发布的名单回放，图中展示整个区间净值。' : `从 ${detail.history.validationStart} 起独立记账。图中展示留出段净值。`}
                      {detail.history.engine?.startsWith('cn-paper-5m-v1') ? '旧版撮合使用估算成交量。' : '缺少真实成交量的行情仅用于估值和计算因子。'}
                      {detail.history.validationMode === 'SHARED_WITH_PARENT' && '手动对照复用父策略的历史窗口。'}
                      {detail.history.validationMode === 'NEW_AFTER_FORWARD' && '留出行情采自触发本次研究的复盘之后。'}
                      {detail.history.validationMode === 'NEW_AFTER_FACTOR' && '本次验证使用所选因子可用之后的行情。'}</p>
                    <p>快照指纹 {(detail.history.classicSnapshots ?? [spec.rule.snapshot]).map(digest =>
                      <code key={digest} title={digest}>{digest.slice(0,16)} </code>)}</p>
                    {detail.history.symbolCoverage && <div className="paper-coverage-scroll"><table className="paper-position-table"><thead><tr><th>股票</th><th>有行情 / 池内日期</th><th>完整日</th><th>真实量 / 总根数</th><th>除权核验</th></tr></thead>
                      <tbody>{Object.entries(detail.history.symbolCoverage).map(([c,v]) => <tr key={c}><th>{stockName(c)}</th><td>{v.days} / {v.poolDays}</td><td>{v.completeDays}</td><td>{v.measuredVolumeBars} / {v.bars}</td><td>{v.actionsVerified ? (v.exDates?.length ?? 0)+' 个日期' : '缺资料'}{v.unsupportedActions ? ' · 含配股或未分类事件' : ''}</td></tr>)}</tbody></table></div>}
                    {!detail.history.symbolCoverage && detail.history.coverage && <p>{Object.entries(detail.history.coverage).map(([code,n]) => stockName(code) + ' ' + n + ' 根').join(' · ')}</p>}
                    <button className="quant-record-link" disabled={busy} onClick={() => operate('replay')}>用原始源码和快照复验</button>
                  </details>}
                  <details>
                    <summary>成交与费用假设</summary>
                    <p>
                      五分钟信号之后的可用 K
                      线按开盘价模拟成交，单笔最多使用该根成交量的
                      5%。买卖滑点各 5 个基点，佣金万三、最低 5
                      元，卖出另计万五印花税。执行
                      T+1，一字线跳过。{spec.rule.kind === 'python' && spec.rule.exDateAccounting ? '已核验的现金分红计入应收，送股计入持仓。来源仅提供除权日，到账及上市日未知的权益保持锁定。配股按不认购处理。' : '此版本账本未计分红送转。'}
                    </p>
                  </details></>}

                </section>
              )}
              {!accountsOnly && analysisView==='研究依据' && <section className="quant-readout">
                <header>
                  <h4>策略与因子</h4>
                </header>
                <p>{detail?.history.factorAuthor ? spec.rule.hypothesis : spec.origin}</p>
                {spec.rule.kind === 'python' ? <>
                  <p>{spec.rule.factor}</p>
                  <details className="paper-source"><summary>查看 Python 策略源码</summary>
                    <pre tabIndex={0}><code>{spec.rule.code}</code></pre>
                  </details>
                </> : <><div className="paper-formula">
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
                </>}
                <details>
                  <summary>
                    {spec.source} · {classic ? '冻结时 ' : ''}{spec.codes.length} 只
                  </summary>
                  <p>{spec.codes.map(stockName).join(' · ')}</p>
                </details>
              </section>}
              {accountsOnly && detail?.forwardEvidence && <section className="quant-readout paper-glass"><header><h4>前向研究观察</h4><small>{detail.forwardEvidence.status}</small></header>
                <p>已观察 {detail.forwardEvidence.sessions} 个交易日 · {detail.forwardEvidence.fills} 笔成交 · 最大回撤 {pct(detail.forwardEvidence.maxDrawdown)} · 费用占本金 {pct(detail.forwardEvidence.feeRate)} · 相对基准 {pct(detail.forwardEvidence.relativeReturn)}</p>
                {detail.forwardEvidence.reasons.length > 0 && <p>{detail.forwardEvidence.reasons.join(' · ')}</p>}</section>}
              {accountsOnly && book && <><Holdings book={book} capital={spec.capital} stockName={stockName}/>
                {book.rebalancePlan && <PlanReport visual plan={book.rebalancePlan} name={stockName}/>}
                {detail?.forwardPerformance && <PerformanceReport value={detail.forwardPerformance} costs={book.executionCosts}/>}</>}
              {accountsOnly && control && <details className="quant-readout paper-glass"><summary>
                {detail?.history.model ? '因子等权对照' : '原策略对照'}持仓 · {control.fills} 笔成交
              </summary><Holdings book={control} capital={spec.capital} stockName={stockName}/></details>}
              {accountsOnly && detail && (
                <section className="quant-readout">
                  <header>
                    <div><h4>AI 收益复盘</h4>
                      {detail.days.length > 0 && <select aria-label="复盘交易日" value={detail.day} onChange={e => setDay(e.target.value)}>
                        {detail.days.map(date => <option key={date}>{date}</option>)}
                      </select>}
                    </div>
                    <button className="quant-action"
                      onClick={review}
                      disabled={busy || !detail.points.length}
                    >
                      {busy ? '正在复盘…' : '复盘所选交易日'}
                    </button>
                  </header>
                  <p>
                    {detail.daySummary && <>截至 {detail.daySummary.at.slice(11, 16)} · 当日盈亏 ¥{currency(detail.daySummary.pnl)}
                      {' · '}{detail.daySummary.fills} 笔成交 · 费用 ¥{currency(detail.daySummary.fees)}。</>}
                    {' '}有行情记录的交易日，系统从 15:10 起自动生成复盘。
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
