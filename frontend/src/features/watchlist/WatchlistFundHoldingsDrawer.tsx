import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import type { FundHoldingDetail, FundHoldingPosition } from '../../shared/types';
import { changeClass } from './watchlistFormatters';

/** 基金披露持仓工作台：持仓事实与盘中行情在同一刷新批次内展示。 */
export function WatchlistFundHoldingsDrawer({ item, onClose }: {
  item: { code: string; name?: string };
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<FundHoldingDetail>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const backdrop = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const requestSequence = useRef(0);
  const onCloseRef = useRef(onClose);
  const previousFocus = useRef<HTMLElement | null>(
    document.activeElement instanceof HTMLElement ? document.activeElement : null
  );
  onCloseRef.current = onClose;

  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError('');
    try {
      const value = await api<FundHoldingDetail>(
        `/api/watchlist/${item.code}/fund-holdings?refresh=true`
      );
      if (sequence === requestSequence.current) {
        setDetail(value);
      }
    } catch (loadError) {
      if (sequence === requestSequence.current) {
        setError(loadError instanceof Error ? loadError.message : '基金持仓数据暂不可用');
      }
    } finally {
      if (sequence === requestSequence.current) {
        setLoading(false);
      }
    }
  }, [item.code]);

  useEffect(() => {
    document.documentElement.classList.add('watchlist-kline-open');
    closeButton.current?.focus();
    void load();
    return () => {
      requestSequence.current++;
      document.documentElement.classList.remove('watchlist-kline-open');
      previousFocus.current?.focus();
    };
  }, [load]);

  useLayoutEffect(() => {
    const overlay = backdrop.current;
    const workspace = overlay?.closest<HTMLElement>('.workspace');
    const topbar = workspace?.querySelector<HTMLElement>('.topbar');
    if (!overlay || !workspace || !topbar) return;

    const updateBounds = () => {
      const compact = window.innerWidth <= 980;
      const workspaceBounds = workspace.getBoundingClientRect();
      const topbarBounds = topbar.getBoundingClientRect();
      overlay.style.setProperty(
        '--watchlist-kline-left', `${compact ? 0 : Math.max(0, workspaceBounds.left)}px`
      );
      overlay.style.setProperty('--watchlist-kline-top', `${Math.max(0, topbarBounds.bottom)}px`);
    };

    updateBounds();
    window.addEventListener('resize', updateBounds);
    const observer = typeof ResizeObserver === 'undefined'
      ? undefined : new ResizeObserver(updateBounds);
    observer?.observe(workspace);
    observer?.observe(topbar);
    return () => {
      window.removeEventListener('resize', updateBounds);
      observer?.disconnect();
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  const name = detail?.fundName || item.name || item.code;

  return (
    <div
      ref={backdrop}
      className="watchlist-kline-backdrop watchlist-fund-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCloseRef.current();
      }}
    >
      <section
        className="watchlist-kline-modal watchlist-fund-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="watchlist-fund-title"
      >
        <header className="watchlist-kline-header watchlist-fund-header">
          <div className="watchlist-kline-identity">
            <span>FUND DISCLOSURE · LIVE QUOTES</span>
            <h2 id="watchlist-fund-title">{name} <small>持仓透视</small></h2>
            <p>
              {item.code} · {detail
                ? <span>{detail.disclosureDate
                  ? `最近披露 ${detail.disclosureDate}`
                  : '尚无持仓披露'}</span>
                : <span>最近公开持仓</span>}
            </p>
          </div>
          <div className="watchlist-kline-head-actions">
            {detail?.quoteAsOf && (
              <div className="watchlist-fund-asof">
                <span>行情截至</span>
                <strong>{formatDateTime(detail.quoteAsOf)}</strong>
                <small>{detail.quoteSource || '行情源未标注'}</small>
              </div>
            )}
            <button
              type="button"
              className="watchlist-kline-refresh"
              aria-label="刷新基金持仓和股票行情"
              onClick={() => void load()}
              disabled={loading}
            >↻</button>
            <button
              ref={closeButton}
              type="button"
              className="watchlist-kline-close"
              aria-label="关闭基金持仓详情"
              onClick={() => onCloseRef.current()}
            >×</button>
          </div>
        </header>

        <div className="watchlist-kline-content watchlist-fund-content">
          {loading && !detail ? (
            <div className="watchlist-kline-pending" aria-live="polite">
              <span aria-hidden="true" />
              <strong>正在核对披露持仓与实时行情…</strong>
            </div>
          ) : error ? (
            <div className="watchlist-kline-pending is-error" aria-live="polite">
              <strong>持仓详情加载失败</strong>
              <p>{error}</p>
              <button type="button" className="ghost-button" aria-label="重新加载基金持仓" onClick={() => void load()}>
                重新加载
              </button>
            </div>
          ) : detail ? (
            <>
              <div className="watchlist-fund-disclosure-note" role="note">
                <span aria-hidden="true">i</span>
                <p>{detail.note}</p>
              </div>

              {detail.disclosureDate && (
                <dl className="watchlist-fund-summary" aria-label="基金持仓估算摘要">
                  <div>
                    <dt>前十大披露权重</dt>
                    <dd>{formatPct(detail.topHoldingsWeightPct)}</dd>
                  </div>
                  <div>
                    <dt>实时估算覆盖</dt>
                    <dd>{detail.estimatedHoldingCount} / {detail.totalHoldingCount}</dd>
                  </div>
                  <div>
                    <dt>合计估算贡献</dt>
                    <dd className={changeClass(detail.estimatedContributionPct)}>
                      {formatContribution(detail.estimatedContributionPct)}
                    </dd>
                  </div>
                </dl>
              )}

              {detail.quoteWarning && (
                <p className="watchlist-fund-quality-note" role="status">{detail.quoteWarning}</p>
              )}

              {detail.holdings.length === 0 ? (
                <div className="watchlist-fund-empty">
                  <strong>{detail.disclosureDate
                    ? '最近披露期没有股票投资明细'
                    : '尚无公开持仓'}</strong>
                  <p>{detail.disclosureDate
                    ? '债券基金、货币基金或未直接持股的基金可能出现这种情况。'
                    : '基金成立时间较短或尚未发布定期报告时，可能暂时没有股票持仓数据。'}</p>
                </div>
              ) : (
                <HoldingsTable holdings={detail.holdings} />
              )}
            </>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function HoldingsTable({ holdings }: { holdings: FundHoldingPosition[] }) {
  const maxWeight = Math.max(...holdings.map((holding) => holding.weightPct), 0.01);
  return (
    <div className="watchlist-fund-table-wrap">
      <table className="watchlist-fund-table">
        <thead>
          <tr>
            <th>股票</th>
            <th>披露权重</th>
            <th>最新价</th>
            <th>今日涨跌</th>
            <th>估算贡献</th>
          </tr>
        </thead>
        <tbody>
          {holdings.map((holding) => (
            <tr key={`${holding.rank}-${holding.stockCode}`}>
              <td data-label="股票">
                <div className="watchlist-fund-stock">
                  <span>{String(holding.rank).padStart(2, '0')}</span>
                  <strong>{holding.stockName}<small>{holding.stockCode}</small></strong>
                </div>
              </td>
              <td data-label="披露权重">
                <div className="watchlist-fund-weight">
                  <strong>{formatPct(holding.weightPct)}</strong>
                  <i aria-hidden="true"><b style={{ width: `${(holding.weightPct / maxWeight) * 100}%` }} /></i>
                </div>
              </td>
              <td data-label="最新价" className="watchlist-fund-number">
                {holding.quoteValid ? formatNumber(holding.latestPrice) : '--'}
              </td>
              <td data-label="今日涨跌" className={`watchlist-fund-number ${changeClass(holding.changePct)}`}>
                {holding.quoteValid ? formatSignedPct(holding.changePct) : '--'}
              </td>
              <td data-label="估算贡献" className={`watchlist-fund-contribution ${changeClass(holding.estimatedContributionPct)}`}>
                {holding.estimatedContributionPct == null
                  ? <><strong>--</strong>{holding.quoteNote && <small>{holding.quoteNote}</small>}</>
                  : <strong>{formatContribution(holding.estimatedContributionPct)}</strong>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatNumber(value?: number) {
  return value == null ? '--' : value.toFixed(2);
}

function formatPct(value?: number) {
  return value == null ? '--' : `${value.toFixed(2)}%`;
}

function formatSignedPct(value?: number) {
  return value == null ? '--' : `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatContribution(value?: number) {
  return value == null ? '--' : `${value >= 0 ? '+' : ''}${value.toFixed(3)} 个百分点`;
}

function formatDateTime(value: string) {
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/);
  return match ? `${match[1]} ${match[2]}` : value;
}
