import { CapitalFlowPoint, CapitalFlowStreak, MarketIntelCapitalOverview } from './marketIntelTypes';

function formatMoney(value?: number) {
  if (value === undefined || value === null) return '--';
  const absolute = Math.abs(value);
  const sign = value > 0 ? '+' : value < 0 ? '-' : '';
  if (absolute >= 1e8) return `${sign}${(absolute / 1e8).toFixed(2)} 亿`;
  if (absolute >= 1e4) return `${sign}${(absolute / 1e4).toFixed(0)} 万`;
  return `${sign}${absolute.toFixed(0)}`;
}

function formatTime(value: string, includeDate = false) {
  const date = new Date(value);
  return new Intl.DateTimeFormat('zh-CN', includeDate
    ? { month: '2-digit', day: '2-digit' }
    : { hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
}

function formatVolume(value?: number) {
  if (value === undefined || value === null) return '--';
  if (Math.abs(value) >= 1e8) return `${(value / 1e8).toFixed(2)} 亿手`;
  if (Math.abs(value) >= 1e4) return `${(value / 1e4).toFixed(2)} 万手`;
  return `${value.toFixed(0)} 手`;
}

function formatNumber(value?: number) {
  return value === undefined || value === null ? '--' : value.toFixed(2);
}

function formatPercent(value?: number, signed = false) {
  if (value === undefined || value === null) return '--';
  const sign = signed && value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

function flowShare(point: CapitalFlowPoint) {
  if (point.mainNetInflowSharePct !== undefined && point.mainNetInflowSharePct !== null) {
    return point.mainNetInflowSharePct;
  }
  if (point.mainNetInflow === undefined || point.mainNetInflow === null || !point.intervalTradeAmount) return undefined;
  return point.mainNetInflow / point.intervalTradeAmount * 100;
}

function flowTone(value?: number) {
  if (!value) return 'flat';
  return value > 0 ? 'in' : 'out';
}

function newestFirst(points: CapitalFlowPoint[]) {
  return [...points].sort((left, right) => Date.parse(right.observedAt) - Date.parse(left.observedAt));
}

function evidenceKey(point: CapitalFlowPoint, scope: 'intraday' | 'daily') {
  return `${scope}:${point.id ?? point.observedAt}`;
}

function EvidenceRow({ point, daily = false, maxAmount }: { point: CapitalFlowPoint; daily?: boolean; maxAmount: number }) {
  const amount = Math.abs(point.intervalTradeAmount ?? 0);
  const width = maxAmount ? Math.max(8, amount / maxAmount * 100) : 8;
  return (
    <li className="market-intel-evidence-row">
      <time dateTime={point.observedAt}>{formatTime(point.observedAt, daily)}</time>
      <div className="market-intel-amount-track" aria-label={`成交额 ${formatMoney(point.intervalTradeAmount)}`}>
        <span style={{ width: `${width}%` }} />
      </div>
      <strong>{formatMoney(point.intervalTradeAmount)}</strong>
      <span className={`market-intel-flow ${flowTone(point.mainNetInflow)}`}>
        {formatMoney(point.mainNetInflow)}
      </span>
      <small className="market-intel-row-metrics">
        <span>量 {formatVolume(point.tradeVolume)}</span>
        <span>占比 {formatPercent(flowShare(point), true)}</span>
        <span>换手 {formatPercent(point.turnoverRate)}</span>
        <span>量比 {formatNumber(point.volumeRatio)}</span>
      </small>
    </li>
  );
}

function streakText(streak: CapitalFlowStreak) {
  if (!streak.periods || streak.direction === 'FLAT') return '当前没有连续净流入或净流出';
  const direction = streak.direction === 'INFLOW' ? '净流入' : '净流出';
  const minuteMatch = streak.granularity?.match(/^MINUTE_(\d+)$/);
  const period = streak.granularity === 'DAY_1'
    ? '个交易日'
    : `个 ${minuteMatch?.[1] ?? '--'} 分钟区间`;
  return `连续${direction} ${streak.periods} ${period}`;
}

export function CapitalBehaviorPanel({ overview }: { overview: MarketIntelCapitalOverview }) {
  const intradayMax = Math.max(0, ...overview.intradayTimeline.map((point) => Math.abs(point.intervalTradeAmount ?? 0)));
  const displayedIntradayTimeline = newestFirst(overview.intradayTimeline);
  const displayedDailyTrend = newestFirst(overview.dailyTrend);
  const dailyMax = Math.max(0, ...overview.dailyTrend.map((point) => Math.abs(point.intervalTradeAmount ?? 0)));
  const latest = overview.dailyTrend[overview.dailyTrend.length - 1]
    ?? overview.intradayTimeline[overview.intradayTimeline.length - 1];
  const latestMetrics = overview.metrics?.latest;

  return (
    <>
      {latestMetrics && (
        <section className="market-intel-metrics" aria-labelledby="capital-metrics-heading">
          <header>
            <div>
              <p className="market-intel-kicker">Current market context · 最新成交环境</p>
              <h3 id="capital-metrics-heading">核心资金指标</h3>
            </div>
            <span className="market-intel-metric-note">
              资金占比 = 主力净额 ÷ 同周期成交额
              {latestMetrics.observedAt && <time dateTime={latestMetrics.observedAt}>截至 {formatTime(latestMetrics.observedAt, true)} {formatTime(latestMetrics.observedAt)}</time>}
            </span>
          </header>
          <dl className="market-intel-metric-grid">
            <div><dt>成交金额</dt><dd>{formatMoney(latestMetrics.tradeAmount)}</dd></div>
            <div><dt>成交量</dt><dd>{formatVolume(latestMetrics.tradeVolume)}</dd></div>
            <div><dt>换手率</dt><dd>{formatPercent(latestMetrics.turnoverRate)}</dd></div>
            <div><dt>量比</dt><dd>{formatNumber(latestMetrics.volumeRatio)}</dd></div>
            <div className={`flow-${flowTone(latestMetrics.mainNetInflowSharePct)}`}>
              <dt>主力净额占比</dt><dd>{formatPercent(latestMetrics.mainNetInflowSharePct, true)}</dd>
            </div>
          </dl>
          <div className="market-intel-streaks" aria-label="资金连续性">
            <span>{streakText(overview.metrics!.intradayStreak)}</span>
            <span>{streakText(overview.metrics!.dailyStreak)}</span>
          </div>
          <div className="market-intel-objective-tags" aria-label="客观异常标签">
            <strong>客观异常</strong>
            {overview.metrics!.objectiveTags.length ? overview.metrics!.objectiveTags.map((tag) => (
              <details key={`${tag.code}-${tag.window}`}>
                <summary>{tag.label}</summary>
                <small>{tag.explanation} · 规则版本 {tag.version}</small>
              </details>
            )) : <small>当前未触发规则定义的异常</small>}
          </div>
        </section>
      )}
      <section className="market-intel-ledger" aria-labelledby="capital-ledger-heading">
        <header>
          <div>
            <p className="market-intel-kicker">Intraday tape · 资金与成交同步观察</p>
            <h3 id="capital-ledger-heading">资金证据带</h3>
          </div>
          <div className="market-intel-legend" aria-label="图例">
            <span><i className="in" />净流入</span>
            <span><i className="out" />净流出</span>
          </div>
        </header>
        {overview.intradayTimeline.length ? (
          <ol className="market-intel-evidence-list">
            {displayedIntradayTimeline.map((point) => (
              <EvidenceRow key={evidenceKey(point, 'intraday')} point={point} maxAmount={intradayMax} />
            ))}
          </ol>
        ) : <p className="market-intel-empty">当前没有分钟级资金点，刷新后再查看盘中节奏。</p>}
      </section>

      <section className="market-intel-trend" aria-labelledby="capital-trend-heading">
        <header>
          <div>
            <p className="market-intel-kicker">5 / 10 / 20 日观察窗</p>
            <h3 id="capital-trend-heading">日线资金趋势</h3>
          </div>
          {latest && (
            <div className={`market-intel-net-total ${flowTone(latest.mainNetInflow)}`}>
              <span>最新主力净额</span>
              <strong>{formatMoney(latest.mainNetInflow)}</strong>
            </div>
          )}
        </header>
        <ol className="market-intel-evidence-list daily">
          {displayedDailyTrend.map((point) => (
            <EvidenceRow key={evidenceKey(point, 'daily')} point={point} daily maxAmount={dailyMax} />
          ))}
        </ol>
      </section>
    </>
  );
}
