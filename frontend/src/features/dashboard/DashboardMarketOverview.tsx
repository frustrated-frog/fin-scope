import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { DataQualityNotice } from '../../shared/components/DataQualityNotice';
import { Table } from '../../shared/components/Table';
import { aggregateMarketDataQuality } from '../../shared/marketData/marketDataQuality';
import type { SectorMarketEntry, View, WatchlistItem } from '../../shared/types';
import type { MarketPulseWorkspace } from '../market-pulse/marketPulseTypes';
import { AttributionReaderView } from '../watchlist/AttributionReaderView';
import { WatchlistKlineDrawer } from '../watchlist/WatchlistKlineDrawer';
import { useWatchlistDashboardData } from '../watchlist/useWatchlistDashboardData';
import { changeClass, formatPct, formatPrice, formatTurnover } from '../watchlist/watchlistFormatters';
import './DashboardMarketOverview.css';

function quoteValue(value?: number) {
  return value != null && Number.isFinite(value) ? value : undefined;
}

function quoteDate(value?: string) {
  return value?.match(/^\d{4}-\d{2}-\d{2}/)?.[0];
}

function snapshotLabel(value?: string) {
  const date = quoteDate(value);
  if (!date) {
    return '行情时间未知';
  }
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date());
  const time = value?.match(/[T ](\d{2}:\d{2})/)?.[1];
  return `${date}${time ? ` ${time}` : ''}${date !== today ? ' · 最近交易快照' : ''}`;
}

function qualityLabel(value?: string) {
  if (value === 'STALE_FALLBACK' || value === 'STALE') {
    return '旧快照';
  }
  if (value === 'UNAVAILABLE') {
    return '数据不可用';
  }
  if (value === 'PARTIAL_FRESH' || value === 'PARTIAL') {
    return '部分数据';
  }
  return '';
}

export function DashboardMarketOverview({ refreshRevision, onChangeView }: {
  refreshRevision: number;
  onChangeView: (view: View) => void;
}) {
  const market = useWatchlistDashboardData('/api/sector-market/movements');
  const [pulse, setPulse] = useState<MarketPulseWorkspace | null>(null);
  const [pulseError, setPulseError] = useState('');
  const [pulseLoading, setPulseLoading] = useState(true);
  const [localRevision, setLocalRevision] = useState(0);
  const [sortByChange, setSortByChange] = useState(false);
  const [stockDetail, setStockDetail] = useState<WatchlistItem | null>(null);
  const [attribution, setAttribution] = useState<WatchlistItem | null>(null);
  const refreshAllRef = useRef(market.refreshAll);
  refreshAllRef.current = market.refreshAll;
  const previousRevision = useRef(refreshRevision);

  useEffect(() => {
    const controller = new AbortController();
    setPulseLoading(true);
    setPulseError('');
    api<MarketPulseWorkspace>('/api/market-pulse/latest', { signal: controller.signal })
      .then((value) => {
        if (!controller.signal.aborted) {
          setPulse(value);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setPulseError('市场宽度刷新失败；已有快照仍保留，可点击刷新行情重试。');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setPulseLoading(false);
        }
      });
    return () => controller.abort();
  }, [refreshRevision, localRevision]);

  useEffect(() => {
    if (previousRevision.current !== refreshRevision) {
      previousRevision.current = refreshRevision;
      void refreshAllRef.current();
    }
  }, [refreshRevision]);

  const breadth = pulse?.qualityStatus === 'UNAVAILABLE' || pulse?.breadth?.qualityStatus === 'UNAVAILABLE' ? undefined : pulse?.breadth;
  const breadthDate = breadth?.businessDate || pulse?.businessDate;
  const amountChange = quoteValue(breadth?.changeSummary?.totalAmountChangeRatio);
  const stocks = market.investments.data.filter((item) => item.type === 'STOCK');
  if (sortByChange) {
    stocks.sort((left, right) =>
      (right.quoteValid && right.qualityStatus !== 'UNAVAILABLE' ? quoteValue(right.changePct) ?? -Infinity : -Infinity)
      - (left.quoteValid && left.qualityStatus !== 'UNAVAILABLE' ? quoteValue(left.changePct) ?? -Infinity : -Infinity));
  }
  const refreshing = market.refreshing || pulseLoading;
  const sectors = market.sectorOverview;

  function refreshQuotes() {
    setLocalRevision((current) => current + 1);
    void market.refreshAll();
  }

  function fiveDayReturn(entry: SectorMarketEntry) {
    if (market.sectorCategory !== 'INDUSTRY' || pulse?.qualityStatus === 'UNAVAILABLE') {
      return undefined;
    }
    const date = quoteDate(entry.quoteTime);
    if (!pulse?.businessDate || date && date !== pulse.businessDate) {
      return undefined;
    }
    const rotation = pulse.sectors?.find((item) => item.sectorCode === entry.code || item.sectorName === entry.name);
    return quoteValue(rotation?.return5d);
  }

  function sectorBoard(label: string, entries: SectorMarketEntry[]) {
    return (
      <article className="dashboard-sector-board" aria-label={label}>
        <h4>{label}</h4>
        <Table
          headers={['板块', '当日', '近 5 日']}
          rows={entries.slice(0, 5).map((entry) => {
            const change = quoteValue(entry.changePct);
            const fiveDay = fiveDayReturn(entry);
            return [
              <span key={entry.code} className="dashboard-sector-name"><strong>{entry.name}</strong><small>{entry.leaderStockName ? `领涨股 ${entry.leaderStockName}` : entry.code}</small></span>,
              <span key="daily" className={changeClass(change)}>{formatPct(change)}</span>,
              <span key="five-day" className={`dashboard-sector-return ${changeClass(fiveDay)}`}>{formatPct(fiveDay)}{fiveDay != null && <small>截至 {pulse?.businessDate}</small>}</span>
            ];
          })}
          empty={sectors.phase === 'loading' ? '正在加载板块行情…' : '暂无板块行情，刷新行情后重试。'}
        />
      </article>
    );
  }

  return (
    <div className="dashboard-market-sections">
      <section aria-labelledby="dashboard-market-heading">
        <div className="dashboard-section-heading">
          <div><span className="dashboard-section-kicker">MARKET / SNAPSHOT</span><h3 id="dashboard-market-heading">市场概况</h3></div>
          <div className="dashboard-market-actions">
            <button className="ghost-button" type="button" onClick={() => onChangeView('marketPulse')}>查看市场状态</button>
            <button className="ghost-button" type="button" disabled={refreshing} onClick={refreshQuotes}>{refreshing ? '行情加载中…' : '刷新行情'}</button>
          </div>
        </div>
        <DataQualityNotice quality={aggregateMarketDataQuality([
          ...stocks, ...market.indices.data, ...market.followedSectors.data,
          ...(['ready', 'error'].includes(sectors.phase) ? [sectors.data] : [])
        ])} />
        {market.refreshStatus && <p className="dashboard-market-note" role="status">{market.refreshStatus}</p>}
        {market.indices.error && <p className="dashboard-market-note" role="status">指数加载失败，已保留可用行情。请刷新行情重试。</p>}
        <div className="dashboard-index-strip">
          {market.indices.data.map((index) => {
            const valid = index.quoteValid && index.qualityStatus !== 'UNAVAILABLE';
            const change = valid ? quoteValue(index.changePct) : undefined;
            return (
              <button className="dashboard-index-quote" type="button" key={index.code} onClick={() => onChangeView('marketPulse')}>
                <span>{index.name}<small>{qualityLabel(index.qualityStatus)}</small></span>
                <strong>{formatPrice(valid ? quoteValue(index.price) : undefined)}</strong>
                <b className={changeClass(change)}>{formatPct(change)}</b>
                <small>{valid ? snapshotLabel(index.asOf) : index.quoteNote || '暂无有效行情'}</small>
              </button>
            );
          })}
          {!market.indices.data.length && <p className="dashboard-market-empty">{market.indices.phase === 'loading' ? '正在加载市场指数…' : '暂无指数行情，请刷新行情重试。'}</p>}
        </div>
        <div className="dashboard-market-breadth" aria-label="市场涨跌与成交">
          <div className="dashboard-breadth-counts"><span>上涨 <strong className="watchlist-up">{breadth?.advanceCount ?? '—'}</strong></span><span>下跌 <strong className="watchlist-down">{breadth?.declineCount ?? '—'}</strong></span><span>平盘 <strong>{breadth?.flatCount ?? '—'}</strong></span></div>
          <div><span>全市场成交额</span><strong>{formatTurnover(quoteValue(breadth?.totalAmount)) || '—'}</strong><small>较上一交易日 {formatPct(amountChange == null ? undefined : amountChange * 100)}</small></div>
          <div className="dashboard-breadth-date"><span>市场宽度交易日</span><strong>{breadthDate || '—'}</strong><small>{qualityLabel(pulse?.qualityStatus) || qualityLabel(breadth?.qualityStatus) || '以对应交易日快照为准'}</small></div>
        </div>
        {pulseError && <p className="dashboard-market-note" role="status">{pulseError}</p>}
        {!!breadth?.warnings?.length && <p className="dashboard-market-note">部分市场数据使用备用来源或暂不可用，可在市场状态中查看详情。</p>}
      </section>

      <section aria-labelledby="dashboard-sectors-heading">
        <div className="dashboard-section-heading">
          <div><span className="dashboard-section-kicker">SECTORS / MOVEMENT</span><h3 id="dashboard-sectors-heading">板块动向</h3></div>
          <div className="dashboard-market-actions" role="group" aria-label="板块分类">
            <button className="ghost-button" type="button" aria-pressed={market.sectorCategory === 'INDUSTRY'} onClick={() => market.setSectorCategory('INDUSTRY')}>行业</button>
            <button className="ghost-button" type="button" aria-pressed={market.sectorCategory === 'CONCEPT'} onClick={() => market.setSectorCategory('CONCEPT')}>概念</button>
            <button className="ghost-button" type="button" onClick={() => onChangeView('watchlist')}>查看板块行情</button>
          </div>
        </div>
        <p className="dashboard-market-note">采集时间 {sectors.data.retrievedAt?.replace('T', ' ').slice(0, 16) || '未知'} · 行情时间以来源为准；近 5 日为标注日期的行业快照</p>
        {(sectors.error || sectors.warning || sectors.data.warning) && <p className="dashboard-market-note" role="status">{sectors.error || sectors.warning || sectors.data.warning}</p>}
        <div className="dashboard-sector-grid">
          {sectorBoard('领涨板块', sectors.data.qualityStatus === 'UNAVAILABLE' ? [] : sectors.data.leaders)}
          {sectorBoard('领跌板块', sectors.data.qualityStatus === 'UNAVAILABLE' ? [] : sectors.data.laggards)}
        </div>
        <div className="dashboard-followed-sectors" aria-label="我关注的板块">
          <span>我关注的板块</span>
          {market.followedSectors.data.map((sector) => (
            <button className="ghost-button" type="button" key={sector.code} onClick={() => onChangeView('watchlist')}>
              {sector.name || sector.code} <b className={changeClass(sector.quoteValid && sector.qualityStatus !== 'UNAVAILABLE' ? quoteValue(sector.changePct) : undefined)}>{formatPct(sector.quoteValid && sector.qualityStatus !== 'UNAVAILABLE' ? quoteValue(sector.changePct) : undefined)}</b>
              <small>{snapshotLabel(sector.asOf || sector.quoteDate)} {qualityLabel(sector.qualityStatus)}</small>
            </button>
          ))}
          {!market.followedSectors.data.length && <small>{market.followedSectors.phase === 'loading' ? '正在加载关注板块…' : '可在自选观察中添加关注板块。'}</small>}
          {market.followedSectors.error && <small role="status">关注板块加载失败，请刷新行情重试。</small>}
        </div>
      </section>

      <section aria-labelledby="dashboard-stocks-heading">
        <div className="dashboard-section-heading">
          <div><span className="dashboard-section-kicker">WATCHLIST / STOCKS</span><h3 id="dashboard-stocks-heading">自选股速览</h3></div>
          <div className="dashboard-market-actions">
            <button className="ghost-button" type="button" aria-pressed={sortByChange} onClick={() => setSortByChange((current) => !current)}>{sortByChange ? '恢复自选顺序' : '按涨跌幅排序'}</button>
            <button className="ghost-button" type="button" onClick={() => onChangeView('watchlist')}>全部自选（{stocks.length}）</button>
          </div>
        </div>
        {market.investments.error && <p className="dashboard-market-note" role="status">自选行情加载失败，已保留可用数据。请刷新行情重试。</p>}
        <div className="dashboard-stock-table">
          <Table headers={['股票', '现价', '涨跌幅', '成交额', '行情时间', '最新归因']} rows={stocks.slice(0, 8).map((stock) => {
            const valid = stock.quoteValid && stock.qualityStatus !== 'UNAVAILABLE';
            const change = valid ? quoteValue(stock.changePct) : undefined;
            return [
              <button key="stock" className="dashboard-stock-name" type="button" onClick={() => setStockDetail(stock)}><strong>{stock.name || stock.code}</strong><small>{stock.code}</small></button>,
              formatPrice(valid ? quoteValue(stock.price) : undefined),
              <span key="change" className={changeClass(change)}>{formatPct(change)}</span>,
              formatTurnover(valid ? quoteValue(stock.turnover) : undefined) || '—',
              <span key="time" className="dashboard-stock-time">{snapshotLabel(stock.quoteTime || stock.asOf || stock.quoteDate)}<small>{valid ? qualityLabel(stock.qualityStatus) : stock.quoteNote || '暂无有效行情'}</small></span>,
              stock.attributionReportId ? <button key="attribution" className="dashboard-attribution-link" type="button" onClick={() => setAttribution(stock)}><span>{stock.attributionSummary || '查看已有归因'}</span><small>归因日期 {stock.attributionReportDate || '未知'}</small></button> : <span key="empty" className="muted">暂无归因</span>
            ];
          })} empty={market.investments.phase === 'loading' ? '正在加载自选股…' : '还没有自选股票，进入自选观察添加你关注的公司。'} />
        </div>
        {attribution && <AttributionReaderView reportId={attribution.attributionReportId!} code={attribution.code} type="STOCK" name={attribution.name} changePct={attribution.attributionChangePct} onBack={() => setAttribution(null)} />}
      </section>
      {stockDetail && <WatchlistKlineDrawer item={stockDetail} returnLabel="返回首页" onClose={() => setStockDetail(null)} />}
    </div>
  );
}
