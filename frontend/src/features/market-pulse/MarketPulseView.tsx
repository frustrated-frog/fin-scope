import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { SectorOpportunityMap } from './SectorOpportunityMap';
import type { DailyMarketReview, MarketBreadth, MarketEventConfirmation, MarketInternalHistoryPoint, MarketPulseBackfillResult, MarketPulseHistoryPoint, MarketPulseWorkspace, MarketRegime } from './marketPulseTypes';

const stageLabels: Record<string, string> = {
  RISK_ON: '放量进攻',
  HIGH_LEVEL_DIVERGENCE: '高位分歧',
  SELL_OFF: '风险释放',
  POST_SELL_OFF_REPAIR: '急跌后修复',
  RANGE_ROTATION: '震荡轮动',
  INSUFFICIENT_DATA: '数据不足'
};

const stateLabels: Record<string, string> = {
  UPTREND: '上行', RANGE: '震荡', DOWNTREND: '下行',
  EXPANDING: '放量', NORMAL: '常态', SHRINKING: '缩量',
  HIGH: '偏高', NEUTRAL: '中性', LOW: '偏低',
  FAST: '快速', SLOW: '缓慢',
  EMERGING: '萌芽', ACCELERATING: '加速', PERSISTENT: '持续', OVERHEATED: '过热',
  FADING: '退潮', REVERSING: '反转试探', WEAK: '弱势', INSUFFICIENT_DATA: '数据不足',
  CONFIRMED: '同向确认', UNCONFIRMED: '事件未获确认', MARKET_LEADING: '行情先行', QUIET: '低响应'
};

type ViewProps = {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
  onOpenStockDiscovery?: () => void;
};

function label(value?: string) {
  return value ? stateLabels[value] ?? stageLabels[value] ?? value : '—';
}

function percent(value?: number, sourceIsPercent = false) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  const normalized = sourceIsPercent ? value : value * 100;
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}%`;
}

function marketAmount(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${(value / 1_000_000_000_000).toFixed(2)} 万亿`;
}

function dateText(value?: string | number[]) {
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')}`;
  }
  return value ?? '—';
}

function isStockDiscoveryCopy(value: string) {
  return value.includes('股票') || value.includes('候选') || value.includes('模型门禁');
}

function marketLevelItems(items?: string[]) {
  return items?.filter(item => !isStockDiscoveryCopy(item)) ?? [];
}

function MarketPulseWarnings({ warnings }: { warnings?: string[] }) {
  const items = marketLevelItems(warnings);
  if (!items.length) {
    return null;
  }
  return (
    <details className="market-pulse-warning">
      <summary><strong>数据说明</strong><span>{items[0]}</span>{items.length > 1 && <small>另有 {items.length - 1} 项</small>}</summary>
      {items.length > 1 && <ul>{items.slice(1).map(item => <li key={item}>{item}</li>)}</ul>}
    </details>
  );
}

function MarketTape({ regimes }: { regimes: MarketRegime[] }) {
  return (
    <section className="market-pulse-tape" aria-label="市场节奏轨">
      <header>
        <div><span>05D RHYTHM</span><h4>市场节奏轨</h4></div>
        <small>每个节点是当日收盘后的冻结判断</small>
      </header>
      <div className="market-pulse-tape-track">
        {regimes.length ? regimes.slice(0, 5).reverse().map((item) => {
          const dailyReturn = item.features?.return1d;
          const direction = dailyReturn != null && dailyReturn < 0 ? 'down' : 'up';
          return (
            <article key={dateText(item.businessDate)} className={`market-pulse-tape-node ${direction}`}>
              <time>{dateText(item.businessDate).slice(5)}</time>
              <span aria-hidden="true" />
              <strong>{stageLabels[item.marketStage ?? ''] ?? '待判断'}</strong>
              <small>{percent(dailyReturn)}</small>
            </article>
          );
        }) : <p className="market-pulse-inline-empty">积累每日快照后，这里会显示行情节奏。</p>}
      </div>
    </section>
  );
}

function MarketBreadthPanel({ breadth }: { breadth?: MarketBreadth }) {
  const advance = breadth?.advanceCount ?? 0;
  const decline = breadth?.declineCount ?? 0;
  const flat = breadth?.flatCount ?? 0;
  const total = Math.max(1, breadth?.validCount ?? advance + decline + flat);
  const advanceWidth = advance / total * 100;
  const flatWidth = flat / total * 100;
  const declineWidth = Math.max(0, 100 - advanceWidth - flatWidth);
  return (
    <section className="market-pulse-breadth" aria-label="市场宽度">
      <header>
        <div><span>MARKET INTERNALS</span><h3>市场宽度</h3></div>
        <p>{breadth?.interpretation ?? '尚未获得全市场涨跌分布。'}</p>
        <small>{breadth?.businessDate ?? '—'} · {breadth?.sourceFamily ?? '来源不可用'} · {breadth?.qualityStatus ?? 'UNAVAILABLE'}</small>
      </header>
      <div className="market-pulse-breadth-lower">
        <div className="market-pulse-distribution">
          <div className="market-pulse-distribution-labels"><span><b>{advance.toLocaleString('zh-CN')}</b> 上涨</span><span><b>{flat.toLocaleString('zh-CN')}</b> 平盘</span><span><b>{decline.toLocaleString('zh-CN')}</b> 下跌</span></div>
          <div className="market-pulse-distribution-bar" aria-label={`上涨 ${advance}，平盘 ${flat}，下跌 ${decline}`}><i className="advance" style={{ width: `${advanceWidth}%` }} /><i className="flat" style={{ width: `${flatWidth}%` }} /><i className="decline" style={{ width: `${declineWidth}%` }} /></div>
          <small>上涨比例 {breadth?.advanceRatio == null ? '—' : percent(breadth.advanceRatio)}</small>
        </div>
        <dl className="market-pulse-breadth-stats">
          <div><dt>两市成交</dt><dd>{marketAmount(breadth?.totalAmount)}</dd></div>
          <div><dt>涨停 / 跌停</dt><dd>{breadth?.limitUpCount ?? '—'} / {breadth?.limitDownCount ?? '—'}</dd></div>
          <div><dt>涨跌中位数</dt><dd className={(breadth?.medianChangePct ?? 0) < 0 ? 'negative' : 'positive'}>{percent(breadth?.medianChangePct, true)}</dd></div>
        </dl>
      </div>
      <div className="market-pulse-internals-grid">
        <ReturnDistributionPanel breadth={breadth} />
        <TrendBreadthPanel breadth={breadth} />
        <NewHighLowPanel breadth={breadth} />
      </div>
    </section>
  );
}

function ReturnDistributionPanel({ breadth }: { breadth?: MarketBreadth }) {
  const buckets = breadth?.returnDistribution ?? [];
  const maximum = Math.max(1, ...buckets.map(item => item.count));
  return (
    <section className="market-pulse-internal-card market-pulse-return-histogram">
      <header><div><span>RETURN PROFILE</span><h4>涨跌幅分布</h4></div><small>观察尾部风险与赚钱效应是否同时扩散</small></header>
      {buckets.length ? <div className="market-pulse-histogram-bars">
        {buckets.map(item => <div key={item.code} className={item.code.startsWith('UP') ? 'positive' : item.code.startsWith('DOWN') ? 'negative' : 'flat'}>
          <strong>{item.count.toLocaleString('zh-CN')}</strong>
          <i aria-label={`${item.label} ${item.count} 家`}><b style={{ height: `${Math.max(4, item.count / maximum * 100)}%` }} /></i>
          <small>{item.label}</small>
        </div>)}
      </div> : <p className="market-pulse-inline-empty">当日涨跌幅分档尚未生成。</p>}
    </section>
  );
}

function TrendBreadthPanel({ breadth }: { breadth?: MarketBreadth }) {
  const trend = breadth?.trendBreadth;
  const rows = [
    ['MA20', trend?.ma20Ratio, trend?.ma20ValidCount],
    ['MA60', trend?.ma60Ratio, trend?.ma60ValidCount],
    ['MA120', trend?.ma120Ratio, trend?.ma120ValidCount],
    ['MA250', trend?.ma250Ratio, trend?.ma250ValidCount]
  ] as const;
  return (
    <section className="market-pulse-internal-card market-pulse-trend-breadth">
      <header><div><span>TREND PARTICIPATION</span><h4>趋势宽度</h4></div><small>站上各周期均线的股票比例</small></header>
      <div className="market-pulse-trend-list">
        {rows.map(([labelText, ratio, count]) => <article key={labelText}>
          <div><strong>{labelText}</strong><small>{count ? `${count.toLocaleString('zh-CN')} 只有效样本` : '样本不足'}</small></div>
          <span>{ratio == null ? '—' : percent(ratio)}</span>
          <i><b style={{ width: `${Math.max(0, Math.min(100, (ratio ?? 0) * 100))}%` }} /></i>
        </article>)}
      </div>
    </section>
  );
}

function NewHighLowPanel({ breadth }: { breadth?: MarketBreadth }) {
  const highLow = breadth?.newHighLow;
  const rows = [
    ['20 日', highLow?.high20Count, highLow?.low20Count],
    ['60 日', highLow?.high60Count, highLow?.low60Count],
    ['250 日', highLow?.high250Count, highLow?.low250Count]
  ] as const;
  return (
    <section className="market-pulse-internal-card market-pulse-high-low">
      <header><div><span>LEADERSHIP</span><h4>新高 / 新低</h4></div><small>识别强势扩散还是弱势尾部增多</small></header>
      <div className="market-pulse-high-low-list">
        {rows.map(([window, high, low]) => <article key={window}>
          <strong>{window}</strong><span className="positive"><b>{high ?? '—'}</b> 新高</span><span className="negative"><b>{low ?? '—'}</b> 新低</span>
        </article>)}
      </div>
      <dl className="market-pulse-ad-line">
        <div><dt>净上涨家数</dt><dd className={(breadth?.netAdvances ?? 0) < 0 ? 'negative' : 'positive'}>{signedInteger(breadth?.netAdvances)}</dd></div>
        <div><dt>A-D Line</dt><dd className={(breadth?.advanceDeclineLine ?? 0) < 0 ? 'negative' : 'positive'}>{signedInteger(breadth?.advanceDeclineLine)}</dd></div>
      </dl>
    </section>
  );
}

function signedInteger(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value > 0 ? '+' : ''}${Math.round(value).toLocaleString('zh-CN')}`;
}

function EventRow({ item }: { item: MarketEventConfirmation }) {
  return (
    <article className="market-pulse-event-row">
      <div className="market-pulse-event-axis" aria-hidden="true">
        <i style={{ left: `${Math.min(100, item.eventScore)}%`, bottom: `${Math.min(100, item.marketReactionScore)}%` }} />
      </div>
      <div>
        <span>{item.sectorName ?? '未映射行业'} · {label(item.confirmationState)}</span>
        <h4>{item.title}</h4>
        <p>{item.evidence?.[0] ?? '正在等待更多独立证据'}</p>
      </div>
      <dl>
        <div><dt>事件</dt><dd>{item.eventScore}</dd></div>
        <div><dt>行情</dt><dd>{item.marketReactionScore}</dd></div>
      </dl>
    </article>
  );
}

function ReviewList({ title, items, tone }: { title: string; items?: string[]; tone?: 'risk' | 'watch' }) {
  return (
    <section className={`market-pulse-review-list ${tone ? `is-${tone}` : ''}`}>
      <h4>{title}</h4>
      {items?.length ? <ul>{items.map(item => <li key={item}>{item}</li>)}</ul> : <p>当前没有新增变化。</p>}
    </section>
  );
}

function DailyReviewPanel({ review, breadth }: { review?: DailyMarketReview; breadth?: MarketBreadth }) {
  if (!review) {
    return <section className="market-pulse-review-empty"><strong>今日复盘尚未生成</strong><p>刷新今日判断后，将按冻结行情事实生成复盘结论。</p></section>;
  }
  return (
    <section className="market-pulse-review" aria-label="今日收盘复盘">
      <header>
        <div><span>DAILY CLOSE REVIEW</span><h3>{review.headline ?? '今日结论待生成'}</h3></div>
        <small>{review.qualityStatus ?? 'PARTIAL'} · 规则复盘 · {review.businessDate ?? '—'}</small>
      </header>
      <div className="market-pulse-review-overview">
        <article><span>市场内部</span><p>{review.breadthConclusion ?? '市场宽度暂不可用。'}</p></article>
      </div>
      <MarketChangeSummary breadth={breadth} />
      <div className="market-pulse-review-columns">
        <ReviewList title="正在增强" items={marketLevelItems(review.leadingSectors)} />
        <ReviewList title="正在降温" items={marketLevelItems(review.weakeningSectors)} />
        <ReviewList title="行业催化" items={marketLevelItems(review.confirmedEvents)} />
      </div>
      <div className="market-pulse-review-actions">
        <ReviewList title="当前风险" items={marketLevelItems(review.riskSignals)} tone="risk" />
        <ReviewList title="明日观察" items={marketLevelItems(review.nextSessionWatchlist)} tone="watch" />
      </div>
    </section>
  );
}

function MarketChangeSummary({ breadth }: { breadth?: MarketBreadth }) {
  const summary = breadth?.changeSummary;
  if (!summary) {
    return null;
  }
  return (
    <section className="market-pulse-change-summary" aria-label="今日结构变化">
      <header><div><span>DAY-OVER-DAY</span><h4>今日结构变化</h4></div><strong>{summary.headline ?? '结构变化待判断'}</strong><small>对比 {summary.previousBusinessDate ?? '上一交易日'}</small></header>
      <ul>{summary.changes?.map(item => <li key={item}>{item}</li>)}</ul>
    </section>
  );
}

function pathFor(points: MarketInternalHistoryPoint[], value: (point: MarketInternalHistoryPoint) => number | undefined,
                 y: (value: number) => number) {
  const width = 940;
  return points.reduce((path, point, index) => {
    const current = value(point);
    if (current == null || !Number.isFinite(current)) {
      return path;
    }
    const x = points.length === 1 ? width / 2 : index / (points.length - 1) * width;
    return `${path}${path ? ' L' : 'M'} ${x.toFixed(1)} ${y(current).toFixed(1)}`;
  }, '');
}

function symmetricExtent(values: number[]) {
  return Math.max(1, ...values.map(value => Math.abs(value)));
}

function MarketInternalsHistory({ points }: { points?: MarketInternalHistoryPoint[] }) {
  const values = points ?? [];
  const [selectedIndex, setSelectedIndex] = useState(Math.max(0, values.length - 1));
  useEffect(() => {
    setSelectedIndex(Math.max(0, values.length - 1));
  }, [values.length]);
  if (!values.length) {
    return <section className="market-pulse-internals-history"><p className="market-pulse-inline-empty">积累全 A 日 K 后，这里会显示 60 日内部结构轨迹。</p></section>;
  }
  const selected = values[Math.min(selectedIndex, values.length - 1)];
  const balances = values.map(item => (item.newHigh20Count ?? 0) - (item.newLow20Count ?? 0));
  const adValues = values.map(item => item.advanceDeclineLine ?? 0);
  const balanceExtent = symmetricExtent(balances);
  const adExtent = symmetricExtent(adValues);
  const selectedX = values.length === 1 ? 470 : selectedIndex / (values.length - 1) * 940;
  return (
    <section className="market-pulse-internals-history" aria-label="60 日市场内部轨迹">
      <header><div><span>60D INTERNALS</span><h3>60 日市场内部轨迹</h3></div><p>同轴观察参与度、趋势宽度、新高新低和 A-D Line。</p></header>
      <div className="market-pulse-internals-legend"><span className="advance">上涨比例</span><span className="ma20">MA20</span><span className="ma60">MA60</span><span className="balance">新高 - 新低</span><span className="ad">A-D Line</span></div>
      <div className="market-pulse-internals-chart">
        <svg viewBox="0 0 940 320" role="img" aria-label="市场内部结构 60 日多轨图">
          <line x1="0" y1="120" x2="940" y2="120" className="divider" />
          <line x1="0" y1="215" x2="940" y2="215" className="divider" />
          <line x1="0" y1="167.5" x2="940" y2="167.5" className="zero" />
          <line x1="0" y1="267.5" x2="940" y2="267.5" className="zero" />
          <path d={pathFor(values, point => point.advanceRatio, value => 108 - value * 88)} className="advance" />
          <path d={pathFor(values, point => point.ma20Ratio, value => 108 - value * 88)} className="ma20" />
          <path d={pathFor(values, point => point.ma60Ratio, value => 108 - value * 88)} className="ma60" />
          <path d={pathFor(values, point => (point.newHigh20Count ?? 0) - (point.newLow20Count ?? 0), value => 167.5 - value / balanceExtent * 35)} className="balance" />
          <path d={pathFor(values, point => point.advanceDeclineLine, value => 267.5 - value / adExtent * 35)} className="ad" />
          <line x1={selectedX} y1="12" x2={selectedX} y2="305" className="selected" />
        </svg>
        <input type="range" min="0" max={values.length - 1} value={Math.min(selectedIndex, values.length - 1)} aria-label="选择市场内部轨迹日期" onChange={event => setSelectedIndex(Number(event.target.value))} />
      </div>
      <div className="market-pulse-internals-selected">
        <time>{selected.businessDate ?? '—'}</time>
        <span><small>上涨比例</small><strong>{percent(selected.advanceRatio)}</strong></span>
        <span><small>MA20 / MA60</small><strong>{percent(selected.ma20Ratio)} / {percent(selected.ma60Ratio)}</strong></span>
        <span><small>新高 - 新低</small><strong>{signedInteger((selected.newHigh20Count ?? 0) - (selected.newLow20Count ?? 0))}</strong></span>
        <span><small>A-D Line</small><strong>{signedInteger(selected.advanceDeclineLine)}</strong></span>
        <span><small>涨跌中位数</small><strong>{percent(selected.medianChangePct, true)}</strong></span>
      </div>
    </section>
  );
}

function HistoryPanel({ points, internalPoints, backfilling, onBackfill, onSelect }: {
  points?: MarketPulseHistoryPoint[];
  internalPoints?: MarketInternalHistoryPoint[];
  backfilling: boolean;
  onBackfill: () => void;
  onSelect: (date: string) => void;
}) {
  return (
    <section className="market-pulse-history">
      <header>
        <div><span>20D EVOLUTION</span><h3>近 20 日市场演变</h3></div>
        <div className="market-pulse-history-actions">
          <p>每一行都来自当日冻结快照，不使用后续数据回填当时判断。</p>
          <button type="button" onClick={onBackfill} disabled={backfilling}>{backfilling ? '正在补全五日判断…' : '补全 8.17–8.21 判断'}</button>
        </div>
      </header>
      <MarketInternalsHistory points={internalPoints} />
      <div className="market-pulse-history-head"><span>日期 / 状态</span><span>当日结论</span><span>市场宽度</span><span>成交额</span><span>领涨行业</span><span>操作</span></div>
      {points?.length ? points.map(point => (
        <article className="market-pulse-history-row" key={point.businessDate}>
          <div><time>{point.businessDate ?? '—'}</time><strong>{stageLabels[point.marketStage ?? ''] ?? '待判断'}</strong><small>置信度 {point.confidenceScore ?? 0}</small></div>
          <p>{point.headline ?? '该日尚无复盘文案'}</p>
          <div className="market-pulse-history-breadth"><strong>{point.advanceRatio == null ? '—' : percent(point.advanceRatio)}</strong><i><b style={{ width: `${Math.max(0, Math.min(100, (point.advanceRatio ?? 0) * 100))}%` }} /></i><small>中位数 {percent(point.medianChangePct, true)}</small></div>
          <strong>{marketAmount(point.totalAmount)}</strong>
          <div><strong>{point.leadingSectorName ?? '—'}</strong><small>{point.leadingSectorScore == null ? '无评分' : `${point.leadingSectorScore} 分`}</small></div>
          <button type="button" className="market-pulse-history-open" disabled={!point.businessDate} onClick={() => point.businessDate && onSelect(point.businessDate)}>查看当日复盘</button>
        </article>
      )) : <p className="market-pulse-inline-empty">积累每日快照后，这里会显示市场演变。</p>}
    </section>
  );
}

export function MarketPulseView({ addToast, setMessage, onOpenStockDiscovery }: ViewProps) {
  const [workspace, setWorkspace] = useState<MarketPulseWorkspace | null>(null);
  const [dates, setDates] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [backfilling, setBackfilling] = useState(false);
  const [view, setView] = useState<'review' | 'breadth' | 'rotation' | 'history'>('review');
  const loadRequest = useRef(0);
  const autoRepairAttempted = useRef(false);

  const load = async (date?: string) => {
    const requestId = ++loadRequest.current;
    setLoading(true);
    try {
      const [nextWorkspace, nextDates] = await Promise.all([
        api<MarketPulseWorkspace>(date ? `/api/market-pulse/${date}` : '/api/market-pulse/latest'),
        api<string[]>('/api/market-pulse/dates')
      ]);
      if (requestId !== loadRequest.current) return;
      setWorkspace(nextWorkspace);
      setDates(nextDates);
    } catch (error) {
      if (requestId !== loadRequest.current) return;
      addToast(error instanceof Error ? error.message : '市场机会加载失败', 'error');
    } finally {
      if (requestId === loadRequest.current) setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const refresh = async (notify = true) => {
    setRefreshing(true);
    setMessage('正在刷新市场机会判断');
    try {
      await api('/api/market-pulse/refresh', { method: 'POST' });
      await load();
      if (notify) {
        addToast('市场机会判断已刷新', 'success');
      }
      setMessage('市场机会判断已刷新');
    } catch (error) {
      if (notify) {
        addToast(error instanceof Error ? error.message : '市场机会刷新失败', 'error');
      }
      setMessage('市场机会刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    if (loading || refreshing || !workspace || autoRepairAttempted.current) {
      return;
    }
    autoRepairAttempted.current = true;
    const historyRequiresRepair = workspace.historyPoints?.some(point =>
      point.advanceRatio == null || point.totalAmount == null || point.medianChangePct == null
    ) ?? false;
    const requiresRepair = workspace.qualityStatus === 'UNAVAILABLE'
      || workspace.breadth?.qualityStatus === 'UNAVAILABLE'
      || historyRequiresRepair;
    if (!requiresRepair) {
      return;
    }
    void refresh(false);
  }, [loading, refreshing, workspace]);

  const backfillPreviousWeek = async () => {
    setBackfilling(true);
    setMessage('正在补全 8.17–8.21 市场判断');
    try {
      const result = await api<MarketPulseBackfillResult>(
        '/api/market-pulse/backfill?startDate=2026-08-17&endDate=2026-08-21',
        { method: 'POST' }
      );
      await load();
      setView('history');
      const completed = result.results?.length ?? 0;
      const tone = result.status === 'FAILED' ? 'error' : 'success';
      addToast(`已补全 ${completed} 个交易日判断`, tone);
      setMessage(`上一周市场判断已补全 ${completed} 天`);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '上一周判断补全失败', 'error');
      setMessage('上一周市场判断补全失败');
    } finally {
      setBackfilling(false);
    }
  };

  const openHistoricalReview = async (date: string) => {
    await load(date);
    setView('review');
  };

  const regime = workspace?.regime;
  const sectors = useMemo(() => [...(workspace?.sectors ?? [])].sort((a, b) => b.rotationScore - a.rotationScore), [workspace]);

  if (loading && !workspace) {
    return <section className="market-pulse-page"><div className="market-pulse-loading" role="status">正在校准市场状态与行业轮动…</div></section>;
  }

  if (!workspace || workspace.qualityStatus === 'UNAVAILABLE') {
    return (
      <section className="market-pulse-page">
        <div className="market-pulse-empty">
          <span aria-hidden="true">⌁</span><h3>还没有第一份市场判断</h3>
          <p>刷新后会冻结市场内部结构、行业轮动和 Radar 事件变化。</p>
          <button type="button" onClick={() => void refresh()} disabled={refreshing}>刷新今日判断</button>
        </div>
      </section>
    );
  }

  return (
    <section className="market-pulse-page">
      <header className="market-pulse-hero">
        <div className="market-pulse-hero-main">
          <p className="market-pulse-kicker">MARKET REGIME · {workspace.businessDate ?? 'LATEST'}</p>
          <h3>{stageLabels[regime?.marketStage ?? ''] ?? '等待判断'}</h3>
          <p>{regime?.explanation ?? '正在等待足够行情数据形成判断。'}</p>
          <div className="market-pulse-regime-strip" aria-label="市场状态维度">
            <span><small>趋势</small><strong>{label(regime?.trendState)}</strong></span>
            <span><small>流动性</small><strong>{label(regime?.liquidityState)}</strong></span>
            <span><small>风险偏好</small><strong>{label(regime?.riskAppetiteState)}</strong></span>
            <span><small>轮动速度</small><strong>{label(regime?.rotationState)}</strong></span>
          </div>
        </div>
        <div className="market-pulse-confidence">
          <span className={`quality-${workspace.qualityStatus.toLowerCase()}`}>{workspace.qualityStatus}</span>
          <strong>{regime?.confidenceScore ?? 0}</strong>
          <small>判断置信度 / 100</small>
          <i><b style={{ width: `${regime?.confidenceScore ?? 0}%` }} /></i>
        </div>
        <div className="market-pulse-controls">
          <label><span>历史截面</span><select value={workspace.businessDate ?? ''} onChange={(event) => void load(event.target.value)}>
            {!dates.length && <option value={workspace.businessDate}>{workspace.businessDate}</option>}
            {dates.map(date => <option key={date} value={date}>{date}</option>)}
          </select></label>
          <button type="button" aria-label="刷新今日判断" onClick={() => void refresh()} disabled={refreshing}>{refreshing ? '正在计算…' : '刷新今日判断'}</button>
        </div>
      </header>

      <MarketPulseWarnings warnings={workspace.warnings} />

      <nav className="market-pulse-tabs" role="tablist" aria-label="市场机会视图">
        <button type="button" role="tab" aria-selected={view === 'review'} onClick={() => setView('review')}>今日雷达</button>
        <button type="button" role="tab" aria-selected={view === 'breadth'} onClick={() => setView('breadth')}>市场宽度</button>
        <button type="button" role="tab" aria-selected={view === 'rotation'} onClick={() => setView('rotation')}>行业轮动</button>
        <button type="button" role="tab" aria-selected={view === 'history'} onClick={() => setView('history')}>历史演变</button>
      </nav>

      {view === 'review' && <DailyReviewPanel review={workspace.dailyReview} breadth={workspace.breadth} />}

      {view === 'history' && <HistoryPanel points={workspace.historyPoints} internalPoints={workspace.breadth?.history} backfilling={backfilling} onBackfill={() => void backfillPreviousWeek()} onSelect={(date) => void openHistoricalReview(date)} />}

      {view === 'breadth' && <>

      <MarketBreadthPanel breadth={workspace.breadth} />

      <MarketTape regimes={workspace.recentRegimes ?? []} />
      </>}

      {view === 'rotation' && <>

      <SectorOpportunityMap sectors={sectors} onOpenStockDiscovery={onOpenStockDiscovery} />

      <section className="market-pulse-events">
        <header><div><span>CATALYST WATCH</span><h3>事件与行情确认</h3></div><p>把行业催化与盘面反应放在一起，帮助区分突发异动和持续主线。</p></header>
        <div>{(workspace.eventConfirmations ?? []).length ? workspace.eventConfirmations?.slice(0, 6).map(item => <EventRow item={item} key={`${item.radarEventId}-${item.title}`} />) : <p className="market-pulse-inline-empty">近 48 小时没有新的行业催化。</p>}</div>
      </section>

      <section className="market-pulse-discovery-handoff" aria-label="股票发现入口">
        <div><span>NEXT / STOCK DISCOVERY</span><h3>行业方向已经看清，个股筛选去股票发现</h3><p>Market Pulse 保留市场与行业视角；候选池、模型排序和单股研究继续由现有股票发现页面负责。</p></div>
        <button type="button" onClick={onOpenStockDiscovery}>进入股票发现</button>
      </section>
      </>}

      <footer className="market-pulse-disclaimer"><span>研究边界</span><p>研究候选不是买入指令。页面用于提高研究优先级，最终决策仍需核验公司基本面、估值、流动性与个人风险承受能力。</p><time>{workspace.generatedAt ? `生成于 ${workspace.generatedAt.replace('T', ' ').slice(0, 16)}` : ''}</time></footer>
    </section>
  );
}
