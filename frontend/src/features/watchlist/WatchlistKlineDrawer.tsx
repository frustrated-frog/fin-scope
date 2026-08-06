import { useEffect, useLayoutEffect, useRef, useState } from 'react';

import { DailyBarPoint, KlineChart } from './KlineChart';
import { loadWatchlistDailyBars } from './watchlistDailyBarCache';

/** 自选标的日线行情工作台：在原页面之上展示，不卸载自选列表。 */
export function WatchlistKlineDrawer({ item, onClose }: {
  item: { code: string; name?: string; market?: string };
  onClose: () => void;
}) {
  const [bars, setBars] = useState<DailyBarPoint[]>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const backdrop = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);

  useLayoutEffect(() => {
    const overlay = backdrop.current;
    const workspace = overlay?.closest<HTMLElement>('.workspace');
    const topbar = workspace?.querySelector<HTMLElement>('.topbar');
    if (!overlay || !workspace || !topbar) return;

    const updateBounds = () => {
      const compact = window.innerWidth <= 980;
      const workspaceBounds = workspace.getBoundingClientRect();
      const topbarBounds = topbar.getBoundingClientRect();
      overlay.style.setProperty('--watchlist-kline-left', `${compact ? 0 : Math.max(0, workspaceBounds.left)}px`);
      overlay.style.setProperty('--watchlist-kline-top', `${Math.max(0, topbarBounds.bottom)}px`);
    };

    updateBounds();
    window.addEventListener('resize', updateBounds);
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(updateBounds);
    observer?.observe(workspace);
    observer?.observe(topbar);
    return () => {
      window.removeEventListener('resize', updateBounds);
      observer?.disconnect();
    };
  }, []);

  useEffect(() => {
    let stopped = false;
    closeButton.current?.focus();
    setLoading(true);
    setError('');
    void loadWatchlistDailyBars(item.code)
      .then((values) => { if (!stopped) { setBars(values); setLoading(false); } })
      .catch((loadError) => { if (!stopped) { setError(loadError instanceof Error ? loadError.message : '日线加载失败'); setLoading(false); } });

    function onKeyDown(event: KeyboardEvent) { if (event.key === 'Escape') onClose(); }
    document.addEventListener('keydown', onKeyDown);
    return () => { stopped = true; document.removeEventListener('keydown', onKeyDown); };
  }, [item.code, onClose]);

  const latest = bars && bars.length > 0 ? bars[bars.length - 1] : undefined;
  const previous = bars && bars.length > 1 ? bars[bars.length - 2] : undefined;
  const changePct = latest?.changePct
    ?? (latest && previous && previous.close ? ((latest.close! - previous.close) / previous.close) * 100 : undefined);
  const name = item.name || item.code;
  const periodHigh = bars?.reduce<number | undefined>((highest, bar) => bar.high == null ? highest : Math.max(highest ?? bar.high, bar.high), undefined);
  const periodLow = bars?.reduce<number | undefined>((lowest, bar) => bar.low == null ? lowest : Math.min(lowest ?? bar.low, bar.low), undefined);
  const return20 = bars && bars.length >= 21 && latest?.close != null && bars[bars.length - 21].close
    ? ((latest.close - bars[bars.length - 21].close!) / bars[bars.length - 21].close!) * 100
    : undefined;

  function refresh() {
    setLoading(true);
    setError('');
    void loadWatchlistDailyBars(item.code, { force: true })
      .then((values) => { setBars(values); setLoading(false); })
      .catch((loadError) => { setError(loadError instanceof Error ? loadError.message : '日线加载失败'); setLoading(false); });
  }

  return (
    <div ref={backdrop} className="watchlist-kline-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="watchlist-kline-modal" role="dialog" aria-modal="true" aria-labelledby="watchlist-kline-title">
        <header className="watchlist-kline-header">
          <div className="watchlist-kline-identity">
            <span>MARKET VIEW · DAILY</span>
            <h2 id="watchlist-kline-title">{name} <small>行情图表</small></h2>
            <p>{item.code}{item.market ? ` · ${item.market}` : ''} · 最近 120 个交易日</p>
          </div>
          <div className="watchlist-kline-head-actions">
            {latest && (
              <div className="watchlist-kline-quote" aria-label={`${latest.tradeDate} 收盘行情`}>
                <span>{latest.tradeDate}</span>
                <strong>¥ {fmt(latest.close)}</strong>
                <em className={changePct != null && changePct >= 0 ? 'watchlist-up' : 'watchlist-down'}>
                  {changePct != null ? `${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%` : '--'}
                </em>
              </div>
            )}
            <button type="button" className="watchlist-kline-refresh" aria-label="刷新日线数据" onClick={refresh} disabled={loading}>↻</button>
            <button ref={closeButton} type="button" className="watchlist-kline-close" aria-label="关闭行情图表" onClick={onClose}>×</button>
          </div>
        </header>

        <div className="watchlist-kline-content">
          {loading ? (
            <div className="watchlist-kline-pending" aria-live="polite"><span aria-hidden="true" /><strong>正在加载日线…</strong></div>
          ) : error ? (
            <div className="watchlist-kline-pending is-error" aria-live="polite"><strong>日线加载失败</strong><p>{error}</p></div>
          ) : (
            <section className="watchlist-kline-chart-stage" aria-label="日线图">
              <div className="watchlist-kline-chart-label">
                <span>日 K · 价格走势</span>
                <span className="watchlist-kline-ma-legend"><i>MA5</i><i>MA20</i><i>MA60</i></span>
              </div>
              <KlineChart bars={bars ?? []} />
            </section>
          )}
          {!loading && bars && bars.length > 0 && latest && (
            <dl className="watchlist-kline-meta" aria-label="最新行情与区间指标">
              <div><dt>开盘</dt><dd>{fmt(latest.open)}</dd></div>
              <div><dt>最高</dt><dd>{fmt(latest.high)}</dd></div>
              <div><dt>最低</dt><dd>{fmt(latest.low)}</dd></div>
              <div><dt>成交量</dt><dd>{formatVolume(latest.volume)}</dd></div>
              <div><dt>成交额</dt><dd>{formatAmount(latest.amount)}</dd></div>
              <div><dt>换手率</dt><dd>{formatPercent(latest.turnoverRate)}</dd></div>
              <div><dt>20 日涨跌</dt><dd className={return20 != null && return20 >= 0 ? 'watchlist-up' : 'watchlist-down'}>{formatPercent(return20, true)}</dd></div>
              <div><dt>区间高 / 低</dt><dd>{fmt(periodHigh)} / {fmt(periodLow)}</dd></div>
            </dl>
          )}
        </div>
      </section>
    </div>
  );
}

function fmt(value?: number) {
  return value != null ? value.toFixed(2) : '--';
}

function formatVolume(value?: number) {
  return value != null ? `${(value / 10_000).toFixed(0)} 万` : '--';
}

function formatAmount(value?: number) {
  if (value == null) return '--';
  if (value >= 100_000_000) return `${(value / 100_000_000).toFixed(2)} 亿`;
  return `${(value / 10_000).toFixed(0)} 万`;
}

function formatPercent(value?: number, signed = false) {
  return value == null ? '--' : `${signed && value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}
