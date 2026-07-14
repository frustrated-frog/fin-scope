import { CapitalFlowPoint, MarketIntelCapitalOverview } from './marketIntelTypes';

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

function flowTone(value?: number) {
  if (!value) return 'flat';
  return value > 0 ? 'in' : 'out';
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
      <small>换手 {point.turnoverRate?.toFixed(2) ?? '--'}%</small>
    </li>
  );
}

export function CapitalBehaviorPanel({ overview }: { overview: MarketIntelCapitalOverview }) {
  const intradayMax = Math.max(0, ...overview.intradayTimeline.map((point) => Math.abs(point.intervalTradeAmount ?? 0)));
  const dailyMax = Math.max(0, ...overview.dailyTrend.map((point) => Math.abs(point.intervalTradeAmount ?? 0)));
  const latest = overview.dailyTrend[overview.dailyTrend.length - 1]
    ?? overview.intradayTimeline[overview.intradayTimeline.length - 1];

  return (
    <>
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
            {overview.intradayTimeline.map((point) => (
              <EvidenceRow key={point.id} point={point} maxAmount={intradayMax} />
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
          {overview.dailyTrend.map((point) => (
            <EvidenceRow key={point.id} point={point} daily maxAmount={dailyMax} />
          ))}
        </ol>
      </section>
    </>
  );
}
