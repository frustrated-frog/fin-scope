import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import type { DailyMarketReview, MarketBreadth, MarketEventConfirmation, MarketPulseHistoryPoint, MarketPulseWorkspace, MarketRegime, SectorRotation } from './marketPulseTypes';

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

function money(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value >= 0 ? '+' : ''}${(value / 100000000).toFixed(1)} 亿`;
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
      <div className="market-pulse-index-grid">
        {(breadth?.indices ?? []).map(item => (
          <article key={item.code}>
            <span>{item.name}</span><strong>{item.close?.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) ?? '—'}</strong>
            <dl><div><dt>1日</dt><dd className={(item.return1d ?? 0) < 0 ? 'negative' : 'positive'}>{percent(item.return1d, true)}</dd></div><div><dt>20日</dt><dd>{percent(item.return20d, true)}</dd></div></dl>
          </article>
        ))}
        {!(breadth?.indices ?? []).length && <p className="market-pulse-inline-empty">五大指数截面暂不可用。</p>}
      </div>
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
    </section>
  );
}

function SectorRow({ item, rank }: { item: SectorRotation; rank: number }) {
  return (
    <article className="market-pulse-sector-row">
      <span className="market-pulse-rank">{String(rank).padStart(2, '0')}</span>
      <div>
        <span className={`market-pulse-stage stage-${item.stage?.toLowerCase()}`}>{label(item.stage)}</span>
        <h4>{item.sectorName}</h4>
        <small>{item.explanations?.[0] ?? '等待更多历史形成解释'}</small>
      </div>
      <dl>
        <div><dt>1 日</dt><dd className={(item.return1d ?? 0) < 0 ? 'negative' : 'positive'}>{percent(item.return1d, true)}</dd></div>
        <div><dt>5 日</dt><dd className={(item.return5d ?? 0) < 0 ? 'negative' : 'positive'}>{percent(item.return5d, true)}</dd></div>
        <div><dt>主力净流入</dt><dd>{money(item.mainNetInflow)}</dd></div>
      </dl>
      <div className="market-pulse-score" aria-label={`轮动评分 ${item.rotationScore}`}>
        <strong>{item.rotationScore}</strong><span>轮动分</span>
        <i><b style={{ width: `${item.rotationScore}%` }} /></i>
      </div>
    </article>
  );
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
      {items?.length ? <ul>{items.map(item => <li key={item}>{item}</li>)}</ul> : <p>当前没有形成可验证条目。</p>}
    </section>
  );
}

function DailyReviewPanel({ review }: { review?: DailyMarketReview }) {
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
        <article><span>指数全景</span><p>{review.indexOverview ?? '指数截面不可用。'}</p></article>
        <article><span>市场内部</span><p>{review.breadthConclusion ?? '市场宽度不可用。'}</p></article>
      </div>
      <div className="market-pulse-review-columns">
        <ReviewList title="获得历史确认的主线" items={review.leadingSectors} />
        <ReviewList title="退潮与承压方向" items={review.weakeningSectors} />
        <ReviewList title="事件 × 行情确认" items={review.confirmedEvents} />
      </div>
      <div className="market-pulse-review-actions">
        <ReviewList title="当前风险" items={review.riskSignals} tone="risk" />
        <ReviewList title="下一交易日验证清单" items={review.nextSessionWatchlist} tone="watch" />
      </div>
      <footer><strong>量化证据</strong>{review.evidence?.length ? review.evidence.map(item => <span key={item}>{item}</span>) : <span>当前证据不足</span>}</footer>
    </section>
  );
}

function HistoryPanel({ points }: { points?: MarketPulseHistoryPoint[] }) {
  return (
    <section className="market-pulse-history">
      <header><div><span>20D EVOLUTION</span><h3>近 20 日市场演变</h3></div><p>每一行都来自当日冻结快照，不使用后续数据回填当时判断。</p></header>
      <div className="market-pulse-history-head"><span>日期 / 状态</span><span>当日结论</span><span>市场宽度</span><span>成交额</span><span>领涨行业</span></div>
      {points?.length ? points.map(point => (
        <article className="market-pulse-history-row" key={point.businessDate}>
          <div><time>{point.businessDate ?? '—'}</time><strong>{stageLabels[point.marketStage ?? ''] ?? '待判断'}</strong><small>置信度 {point.confidenceScore ?? 0}</small></div>
          <p>{point.headline ?? '该日尚无复盘文案'}</p>
          <div className="market-pulse-history-breadth"><strong>{point.advanceRatio == null ? '—' : percent(point.advanceRatio)}</strong><i><b style={{ width: `${Math.max(0, Math.min(100, (point.advanceRatio ?? 0) * 100))}%` }} /></i><small>中位数 {percent(point.medianChangePct, true)}</small></div>
          <strong>{marketAmount(point.totalAmount)}</strong>
          <div><strong>{point.leadingSectorName ?? '—'}</strong><small>{point.leadingSectorScore == null ? '无评分' : `${point.leadingSectorScore} 分`}</small></div>
        </article>
      )) : <p className="market-pulse-inline-empty">积累每日快照后，这里会显示市场演变。</p>}
    </section>
  );
}

export function MarketPulseView({ addToast, setMessage }: ViewProps) {
  const [workspace, setWorkspace] = useState<MarketPulseWorkspace | null>(null);
  const [dates, setDates] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [view, setView] = useState<'review' | 'structure' | 'history'>('review');
  const loadRequest = useRef(0);

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

  const refresh = async () => {
    setRefreshing(true);
    setMessage('正在刷新市场机会判断');
    try {
      await api('/api/market-pulse/refresh', { method: 'POST' });
      await load();
      addToast('市场机会判断已刷新', 'success');
      setMessage('市场机会判断已刷新');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '市场机会刷新失败', 'error');
      setMessage('市场机会刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const regime = workspace?.regime;
  const sectors = useMemo(() => [...(workspace?.sectors ?? [])].sort((a, b) => b.rotationScore - a.rotationScore), [workspace]);
  const candidates = workspace?.candidates ?? [];

  if (loading && !workspace) {
    return <section className="market-pulse-page"><div className="market-pulse-loading" role="status">正在校准市场状态与行业轮动…</div></section>;
  }

  if (!workspace || workspace.qualityStatus === 'UNAVAILABLE') {
    return (
      <section className="market-pulse-page">
        <div className="market-pulse-empty">
          <span aria-hidden="true">⌁</span><h3>还没有第一份市场判断</h3>
          <p>刷新后会冻结指数特征、行业轮动、Radar 事件确认和通过门禁的股票研究候选。</p>
          <button type="button" onClick={refresh} disabled={refreshing}>刷新今日判断</button>
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
          <button type="button" aria-label="刷新今日判断" onClick={refresh} disabled={refreshing}>{refreshing ? '正在计算…' : '刷新今日判断'}</button>
        </div>
      </header>

      {(workspace.warnings ?? []).length > 0 && <div className="market-pulse-warning"><strong>数据边界</strong>{workspace.warnings?.join('；')}</div>}

      <nav className="market-pulse-tabs" role="tablist" aria-label="市场机会视图">
        <button type="button" role="tab" aria-selected={view === 'review'} onClick={() => setView('review')}>今日复盘</button>
        <button type="button" role="tab" aria-selected={view === 'structure'} onClick={() => setView('structure')}>市场结构</button>
        <button type="button" role="tab" aria-selected={view === 'history'} onClick={() => setView('history')}>历史演变</button>
      </nav>

      {view === 'review' && <DailyReviewPanel review={workspace.dailyReview} />}

      {view === 'history' && <HistoryPanel points={workspace.historyPoints} />}

      {view === 'structure' && <>

      <MarketBreadthPanel breadth={workspace.breadth} />

      <MarketTape regimes={workspace.recentRegimes ?? []} />

      <div className="market-pulse-decision-grid">
        <section className="market-pulse-sectors">
          <header><div><span>SECTOR ROTATION</span><h3>行业轮动</h3></div><p>只按可回溯行情和资金特征排序；历史不足的行业不会进入机会前列。</p></header>
          <div>{sectors.length ? sectors.slice(0, 10).map((item, index) => <SectorRow item={item} rank={index + 1} key={item.sectorCode} />) : <p className="market-pulse-inline-empty">行业行情暂不可用。</p>}</div>
        </section>

        <section className="market-pulse-events">
          <header><div><span>EVENT × PRICE</span><h3>事件与行情确认</h3></div><p>右上象限代表事件强、市场也响应；它提升研究优先级，但不单独触发候选。</p></header>
          <div>{(workspace.eventConfirmations ?? []).length ? workspace.eventConfirmations?.slice(0, 6).map(item => <EventRow item={item} key={`${item.radarEventId}-${item.title}`} />) : <p className="market-pulse-inline-empty">近 48 小时没有可确认的行业事件。</p>}</div>
        </section>
      </div>

      <section className="market-pulse-candidates">
        <header>
          <div><span>VERIFIED RESEARCH QUEUE</span><h3>股票研究候选</h3></div>
          <p><strong>{candidates.length}</strong> / 5 · 行业轮动与股票模型必须同时通过门禁</p>
        </header>
        {candidates.length ? <div className="market-pulse-candidate-grid">{candidates.map((item, index) => (
          <article key={item.instrumentCode}>
            <header><span>{String(index + 1).padStart(2, '0')}</span><div><small>{item.instrumentCode} · {item.sectorName}</small><h4>{item.name}</h4></div><strong>{item.calibratedProbability == null ? '—' : `${Math.round(item.calibratedProbability * 100)}%`}<small>校准概率</small></strong></header>
            <blockquote>{item.whyNow}</blockquote>
            <div><section><h5>为什么进入研究队列</h5><ul>{(item.reasons ?? []).map(reason => <li key={reason}>{reason}</li>)}</ul></section><section><h5>主要风险</h5><ul>{(item.risks ?? []).map(risk => <li key={risk}>{risk}</li>)}</ul></section></div>
            <footer><strong>失效条件</strong><span>{(item.invalidationConditions ?? []).join('；') || '尚未定义'}</span></footer>
          </article>
        ))}</div> : <div className="market-pulse-candidate-empty"><strong>今天可以没有股票候选</strong><p>没有标的同时通过行业轮动、模型健康度与稳健性门禁。保留现金和继续观察也是研究结论。</p></div>}
      </section>
      </>}

      <footer className="market-pulse-disclaimer"><span>研究边界</span><p>研究候选不是买入指令。页面用于提高研究优先级，最终决策仍需核验公司基本面、估值、流动性与个人风险承受能力。</p><time>{workspace.generatedAt ? `生成于 ${workspace.generatedAt.replace('T', ' ').slice(0, 16)}` : ''}</time></footer>
    </section>
  );
}
