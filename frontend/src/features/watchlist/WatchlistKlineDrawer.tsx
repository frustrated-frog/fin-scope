import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';

import { KlineChart } from './KlineChart';
import { StockSupplyChainPanel } from './StockSupplyChainPanel';
import { loadWatchlistMarketHistory, StockMarketHistory } from './watchlistDailyBarCache';
import { compareStockBenchmark, summarizeStockHistory } from './stockMarketAnalysis';
import { StockMarketInsights } from './StockMarketInsights';
import { StockFundamentalsPanel } from './StockFundamentalsPanel';
import { acquireWatchlistOverlayScrollLock } from './watchlistOverlayScrollLock';

/** 自选标的日线行情工作台：在原页面之上展示，不卸载自选列表。 */
export function WatchlistKlineDrawer({ item, onClose, returnLabel, onOpenIndustryChain }: {
  item: { code: string; name?: string; market?: string };
  onClose: () => void;
  returnLabel?: string;
  onOpenIndustryChain?: (code: string) => void;
}) {
  const [history, setHistory] = useState<StockMarketHistory>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'chart' | 'supply-chain' | 'fundamentals'>('chart');
  const requestVersion = useRef(0);
  const backdrop = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    return acquireWatchlistOverlayScrollLock();
  }, []);

  useLayoutEffect(() => {
    const overlay = backdrop.current;
    const workspace = overlay?.closest<HTMLElement>('.workspace');
    const topbar = workspace?.querySelector<HTMLElement>('.topbar');
    if (!overlay || !workspace || !topbar) {
      return;
    }

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
    const version = ++requestVersion.current;
    closeButton.current?.focus();
    setHistory(undefined);
    setActiveTab('chart');
    setLoading(true);
    setError('');
    void loadWatchlistMarketHistory(item.code)
      .then((value) => {
        if (requestVersion.current === version) {
          setHistory(value);
          setLoading(false);
        }
      })
      .catch((loadError) => {
        if (requestVersion.current === version) {
          setError(loadError instanceof Error ? loadError.message : '日线加载失败');
          setLoading(false);
        }
      });
    return () => {
      requestVersion.current += 1;
    };
  }, [item.code]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const bars = history?.stock.bars;
  const summary = useMemo(() => summarizeStockHistory(
    bars?.every((bar) => bar.adjustment === 'QFQ') ? bars : []
  ), [bars]);
  const comparison = useMemo(() => compareStockBenchmark(summary.bars,
    history?.benchmark?.bars.every((bar) => bar.adjustment === 'QFQ') ? history.benchmark.bars : []
  ), [summary, history]);
  const latest = bars && bars.length > 0 ? bars[bars.length - 1] : undefined;
  const previous = bars && bars.length > 1 ? bars[bars.length - 2] : undefined;
  const changePct = summary.bars.length === 0 ? undefined : latest?.changePct
    ?? (latest && previous && previous.close ? ((latest.close! - previous.close) / previous.close) * 100 : undefined);
  const name = item.name || item.code;
  const periodHigh = summary.bars.slice(-120).reduce<number | undefined>((highest, bar) => Math.max(highest ?? bar.high, bar.high), undefined);
  const periodLow = summary.bars.slice(-120).reduce<number | undefined>((lowest, bar) => Math.min(lowest ?? bar.low, bar.low), undefined);
  const return20 = summary.performance.find((period) => period.sessions === 20)?.returnPct;

  function refresh() {
    const version = ++requestVersion.current;
    setLoading(true);
    setError('');
    void loadWatchlistMarketHistory(item.code, { force: true })
      .then((value) => {
        if (requestVersion.current === version) {
          setHistory(value);
          setLoading(false);
        }
      })
      .catch((loadError) => {
        if (requestVersion.current === version) {
          setError(loadError instanceof Error ? loadError.message : '日线加载失败');
          setLoading(false);
        }
      });
  }

  const tabTitle = activeTab === 'chart' ? '行情图表' : activeTab === 'supply-chain' ? '产业链结论' : '财务与估值';

  return (
    <div ref={backdrop} className="watchlist-kline-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="watchlist-kline-modal" role="dialog" aria-modal="true" aria-labelledby="watchlist-kline-title">
        <header className="watchlist-kline-header">
          <div className="watchlist-kline-identity">
            <span>{activeTab === 'chart' ? 'MARKET VIEW · DAILY' : activeTab === 'supply-chain' ? 'SUPPLY CHAIN · OVERVIEW' : 'FINANCIALS · VALUATION'}</span>
            <h2 id="watchlist-kline-title">{name} <small>{tabTitle}</small></h2>
            <p>{item.code}{item.market ? ` · ${item.market}` : ''} · {activeTab === 'chart' ? '完整日线 · 自动量价观察' : activeTab === 'supply-chain' ? '上下游关系与核心结论' : '季度趋势与真实估值档案'}</p>
          </div>
          <div className="watchlist-kline-head-actions">
            {returnLabel && (
              <button type="button" className="watchlist-kline-return" aria-label={returnLabel} onClick={onClose}>
                <span aria-hidden="true">←</span>{returnLabel}
              </button>
            )}
            {activeTab === 'chart' && latest && (
              <div className="watchlist-kline-quote" aria-label={`${latest.tradeDate} 收盘行情`}>
                <span>{latest.tradeDate}</span>
                <strong>¥ {fmt(latest.close)}</strong>
                <em className={changePct != null && changePct >= 0 ? 'watchlist-up' : 'watchlist-down'}>
                  {changePct != null ? `${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%` : '--'}
                </em>
              </div>
            )}
            {activeTab === 'chart' && <button type="button" className="watchlist-kline-refresh" aria-label="刷新日线数据" onClick={refresh} disabled={loading}>↻</button>}
            <button ref={closeButton} type="button" className="watchlist-kline-close" aria-label="关闭行情图表" onClick={onClose}>×</button>
          </div>
        </header>

        <nav className="watchlist-stock-tabs" role="tablist" aria-label="股票详情">
          <button type="button" role="tab" aria-selected={activeTab === 'chart'} onClick={() => setActiveTab('chart')}>行情走势</button>
          <button type="button" role="tab" aria-selected={activeTab === 'supply-chain'} onClick={() => setActiveTab('supply-chain')}>产业链</button>
          <button type="button" role="tab" aria-selected={activeTab === 'fundamentals'} onClick={() => setActiveTab('fundamentals')}>财务与估值</button>
        </nav>

        <div className={`watchlist-kline-content${activeTab === 'supply-chain' ? ' is-supply-chain' : ''}`}>
          {activeTab === 'fundamentals' ? <StockFundamentalsPanel code={item.code} /> : activeTab === 'supply-chain' ? (
            <StockSupplyChainPanel code={item.code} name={name} onOpenIndustryChain={onOpenIndustryChain} />
          ) : loading ? (
            <div className="watchlist-kline-pending" aria-live="polite"><span aria-hidden="true" /><strong>正在加载日线…</strong></div>
          ) : error ? (
            <div className="watchlist-kline-pending is-error" aria-live="polite"><strong>日线加载失败</strong><p>{error}</p></div>
          ) : (
            <section className="watchlist-kline-chart-stage" aria-label="日线图">
              <div className="watchlist-kline-chart-label">
                <span>日 K · 最近 {Math.min(bars?.length ?? 0, 120)} 个完整交易日</span>
                {summary.bars.length > 0 ? <span className="watchlist-kline-ma-legend"><i>MA5</i><i>MA20</i><i>MA60</i></span> : <span>复权口径待核验 · 仅展示原始蜡烛图</span>}
              </div>
              <KlineChart bars={(bars ?? []).slice(-120)} events={summary.events} levels={summary.levels} showMovingAverages={summary.bars.length > 0} />
            </section>
          )}
          {activeTab === 'chart' && !loading && !error && history && <StockMarketInsights summary={summary} comparison={comparison} history={history} />}
          {activeTab === 'chart' && !loading && bars && bars.length > 0 && latest && (
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
  if (value == null) {
    return '--';
  }
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(2)} 亿`;
  }
  return `${(value / 10_000).toFixed(0)} 万`;
}

function formatPercent(value?: number, signed = false) {
  return value == null ? '--' : `${signed && value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}
