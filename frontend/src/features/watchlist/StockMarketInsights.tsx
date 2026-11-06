import type { StockBenchmarkComparison, StockHistorySummary } from './stockMarketAnalysis';
import type { StockMarketHistory } from './watchlistDailyBarCache';
import './stockMarketInsights.css';

export function StockMarketInsights({ summary, comparison, history }: {
  summary: StockHistorySummary;
  comparison: StockBenchmarkComparison;
  history: StockMarketHistory;
}) {
  const latest = summary.bars[summary.bars.length - 1];

  return <div className="stock-insights">
    <section className="stock-insight-section" aria-labelledby="stock-performance-title">
      <header><h3 id="stock-performance-title">多周期表现</h3><span>完整交易日 · 前复权收盘价</span></header>
      <div className="stock-performance-grid">
        {summary.performance.map((period) => <article key={period.sessions} title={period.reason ?? `${period.startDate} 至 ${period.endDate}`}>
          <span>{period.label}</span>
          <strong className={directionClass(period.returnPct)}>{percent(period.returnPct)}</strong>
          {period.returnPct != null && <PerformanceSparkline values={summary.bars.slice(-(period.sessions + 1)).map((bar) => bar.close)} label={`${period.label}收盘走势`} />}
          <small>{period.maxDrawdownPct == null ? period.reason : `最大回撤 ${period.maxDrawdownPct.toFixed(2)}%`}</small>
        </article>)}
        <article title={summary.ytd.reason ?? `基点 ${summary.ytd.startDate}，截至 ${summary.ytd.endDate}`}>
          <span>{summary.ytd.year ?? ''} 年初至今</span>
          <strong className={directionClass(summary.ytd.returnPct)}>{percent(summary.ytd.returnPct)}</strong>
          {summary.ytd.returnPct != null && <PerformanceSparkline values={summary.bars.filter((bar) => bar.tradeDate >= summary.ytd.startDate!).map((bar) => bar.close)} label="年初至今收盘走势" />}
          <small>{summary.ytd.reason ?? `基点 ${summary.ytd.startDate}`}</small>
        </article>
      </div>
    </section>

    <section className="stock-insight-section" aria-labelledby="stock-technical-title">
      <header><h3 id="stock-technical-title">量价观察</h3><span>截至 {latest?.tradeDate ?? '暂无有效日线'}</span></header>
      <dl className="stock-observation-grid">
        {summary.movingAverages.map((average) => <div key={average.sessions} title={average.reason}>
          <dt>距 MA{average.sessions}</dt><dd className={directionClass(average.distancePct)}>{percent(average.distancePct)}</dd>
          <small>{average.value == null ? average.reason : `均线 ${price(average.value)}`}</small>
        </div>)}
        <div title={summary.volume.reason}><dt>相对此前 20 日均量</dt>
          <dd>{summary.volume.ratio == null ? '—' : `${summary.volume.ratio.toFixed(2)} 倍`}</dd>
          <small>{summary.volume.reason ?? '排除当日 · 完整日成交量'}</small>
        </div>
        <div><dt>连续涨跌</dt><dd>{summary.bars.length < 2 ? '—' : summary.streak.direction === 'FLAT' ? '收平' : `${summary.streak.days} 日${summary.streak.direction === 'UP' ? '连涨' : '连跌'}`}</dd>
          <small>以前一交易日收盘为基准</small>
        </div>
      </dl>
    </section>

    <section className="stock-insight-section" aria-labelledby="stock-comparison-title">
      <header><h3 id="stock-comparison-title">相对强弱 · {history.benchmarkName || '沪深300'}</h3>
        <span>{comparison.startDate && comparison.endDate ? `${comparison.startDate} — ${comparison.endDate}` : '共同交易日对比'}</span>
      </header>
      {comparison.points.length < 2 ? <p className="stock-insight-empty">{comparison.reason || '基准历史暂不可用'}</p> : <>
        <div className="stock-comparison-summary">
          <span><i className="stock-curve-key is-stock" />个股 <b className={directionClass(comparison.stockReturnPct)}>{percent(comparison.stockReturnPct)}</b></span>
          <span><i className="stock-curve-key is-benchmark" />基准 <b>{percent(comparison.benchmarkReturnPct)}</b></span>
          <span>相对基准 <b className={directionClass(comparison.excessReturnPp)}>{signed(comparison.excessReturnPp)} 个百分点</b></span>
        </div>
        <RelativePerformanceChart comparison={comparison} />
        <p className="stock-insight-note">共同起点归零 · {comparison.sampleCount} 个共同交易日 · 不补齐缺失日期</p>
      </>}
    </section>

    <section className="stock-insight-section" aria-labelledby="stock-levels-title">
      <header><h3 id="stock-levels-title">近期价格位置</h3><span>此前完整交易日 · 不含当日</span></header>
      <div className="stock-level-grid">{summary.levels.map((level) => <article key={level.sessions}>
        <strong>此前 {level.sessions} 日</strong>
        {level.high == null || level.low == null ? <p className="stock-insight-empty">{level.reason}</p> : <>
          <div className="stock-level-values"><span>低点 {price(level.low)}</span><span>高点 {price(level.high)}</span></div>
          <div className="stock-level-track"><i style={{ left: `${Math.max(0, Math.min(100, level.locationPct ?? 50))}%` }} /></div>
          <p>距高点 {percent(level.distanceToHighPct)} · 距低点 {percent(level.distanceToLowPct)}</p>
        </>}
      </article>)}</div>
    </section>

    <section className="stock-insight-section" aria-labelledby="stock-events-title">
      <header><h3 id="stock-events-title">近期量价事件</h3><span>最近 20 个完整交易日 · 自动计算</span></header>
      {summary.events.length === 0 ? <p className="stock-insight-empty">{summary.bars.length < 21 ? '历史不足，暂不能判断完整窗口事件' : '最近窗口未出现所列量价事件'}</p>
        : <ul className="stock-event-list">{summary.events.slice().reverse().map((event) =>
          <li key={`${event.date}-${event.code}`}><time>{event.date}</time><strong>{event.label}</strong>
            <span>{event.ratio == null ? `收盘 ${price(event.price)} · 参考 ${price(event.referenceValue)}` : `当日成交量为此前均量的 ${event.ratio.toFixed(2)} 倍`}</span>
          </li>)}</ul>}
    </section>
  </div>;
}

function PerformanceSparkline({ values, label }: { values: number[]; label: string }) {
  const low = Math.min(...values);
  const high = Math.max(...values);
  const span = high - low;
  const path = values.map((value, index) => `${index === 0 ? 'M' : 'L'}${(2 + index / Math.max(1, values.length - 1) * 116).toFixed(2)},${(span === 0 ? 16 : 29 - (value - low) / span * 26).toFixed(2)}`).join(' ');
  return <svg className="stock-performance-sparkline" viewBox="0 0 120 32" role="img" aria-label={label}>
    <path d={path} />
  </svg>;
}

function RelativePerformanceChart({ comparison }: { comparison: StockBenchmarkComparison }) {
  const points = comparison.points;
  const values = points.flatMap((point) => [point.stockReturnPct, point.benchmarkReturnPct]);
  const min = Math.min(0, ...values);
  const max = Math.max(0, ...values);
  const span = max - min || 1;
  const y = (value: number) => 145 - (value - min) / span * 120;
  const x = (index: number) => 55 + index / Math.max(1, points.length - 1) * 710;
  const path = (field: 'stockReturnPct' | 'benchmarkReturnPct') => points.map((point, index) =>
    `${index === 0 ? 'M' : 'L'}${x(index).toFixed(2)},${y(point[field]).toFixed(2)}`).join(' ');
  return <svg className="stock-comparison-chart" viewBox="0 0 800 180" role="img" aria-label="个股与沪深300累计涨跌幅对比">
    <line className="stock-chart-grid" x1="55" x2="765" y1={y(0)} y2={y(0)} />
    <text x="4" y={y(max) + 4}>{max.toFixed(1)}%</text><text x="4" y={y(min) + 4}>{min.toFixed(1)}%</text>
    <path className="stock-curve is-benchmark" d={path('benchmarkReturnPct')} />
    <path className="stock-curve is-stock" d={path('stockReturnPct')} />
    <text x="55" y="173">{comparison.startDate}</text><text x="765" y="173" textAnchor="end">{comparison.endDate}</text>
  </svg>;
}

export function percent(value?: number) {
  return value == null || !Number.isFinite(value) ? '—' : `${signed(value)}%`;
}

export function signed(value?: number) {
  return value == null || !Number.isFinite(value) ? '—' : `${value > 0 ? '+' : ''}${value.toFixed(2)}`;
}

function price(value?: number) {
  return value == null || !Number.isFinite(value) ? '—' : value.toFixed(2);
}

function directionClass(value?: number) {
  return value == null || value === 0 ? '' : value > 0 ? 'watchlist-up' : 'watchlist-down';
}
