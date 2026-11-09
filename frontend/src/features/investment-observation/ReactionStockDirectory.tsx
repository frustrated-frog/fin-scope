import type { RefObject } from 'react';
import { dateTime, subtypeLabels } from './reactionTypes';
import type { ReactionStockPage, ReactionStockSummary } from './reactionTypes';

export function ReactionStockDirectory({
  data,
  loading,
  failed,
  trackingOnly,
  listRef,
  onOpenStock,
  onOpenNews,
}: {
  data?: ReactionStockPage;
  loading: boolean;
  failed: boolean;
  trackingOnly: boolean;
  listRef: RefObject<HTMLDivElement>;
  onOpenStock: (stock: ReactionStockSummary) => void;
  onOpenNews?: (query: string) => void;
}) {
  const match = data?.matchedStock;
  return (
    <div className="reaction-browser-list reaction-stock-directory" ref={listRef} aria-busy={loading}>
      <div className="reaction-browser-columns reaction-stock-grid">
        <span>股票</span>
        <span>最近事件</span>
        <span>观察记录</span>
      </div>
      {loading ? (
        <p className="reaction-empty" role="status">正在读取股票…</p>
      ) : data?.items.length ? data.items.map((stock) => (
        <button key={stock.code} type="button" className="reaction-browser-row reaction-stock-grid"
          onClick={() => onOpenStock(stock)}>
          <span className="reaction-stock-identity">
            <strong>{stock.name || stock.code}</strong>
            {stock.name && <small>{stock.code}</small>}
          </span>
          <span className="reaction-stock-latest">
            <span className="reaction-browser-meta">
              <span className="reaction-browser-event-type" data-tone={stock.latestSubtype?.startsWith('EARNINGS') ? 'earnings'
                : stock.latestSubtype?.startsWith('CONTRACT') ? 'contract' : 'operating'}>
                {subtypeLabels[stock.latestSubtype || ''] || '事件观察'}
              </span>
              {stock.latestPublishedAt && <time dateTime={stock.latestPublishedAt}>{dateTime(stock.latestPublishedAt)}</time>}
            </span>
            <strong title={stock.latestTitle}>{stock.latestTitle}</strong>
          </span>
          <span className="reaction-stock-counts">
            <span data-active={stock.trackingCount > 0}>跟踪中 <b>{stock.trackingCount}</b></span>
            <span>历史案例 <b>{stock.historyCount}</b></span>
          </span>
          <svg className="reaction-browser-row-arrow" aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d="m6 3 5 5-5 5" /></svg>
        </button>
      )) : failed ? null : (
        <div className="reaction-empty reaction-stock-empty">
          <h4>{match ? `${match.name || match.code}暂无观察案例` : '没有符合条件的股票'}</h4>
          <p>{match ? `${match.code} · 可以在实时资讯中查看最近报道。` : trackingOnly
            ? '可以取消「仅看有跟踪事件」查看已有历史案例的股票。'
            : '可尝试其他股票简称或代码。'}</p>
          {match && onOpenNews && (
            <button type="button" className="reaction-stock-back"
              onClick={() => onOpenNews(match.name || match.code.split('.')[0])}>搜索实时资讯 →</button>
          )}
        </div>
      )}
    </div>
  );
}
