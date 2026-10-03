import type { WatchFocus } from '../watchlist/watchFocusTypes';
import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { StockDiscoveryLatest } from '../strategy/quantTypes';
import type {
  SectorRotation,
  StockDiscoveryMarketContext,
} from './marketPulseTypes';
import {
  industryChanges,
  relativeChanges,
  opportunitySectors,
  sectorFollows,
  stockCode,
  type PersonalChange,
} from './personalMarket';
import './PersonalMarketPanel.css';
import type { DailyResearch } from './marketResearchTypes';
import { buildResearchContext } from './marketResearch';

type Props = {
  businessDate: string;
  sectors: SectorRotation[];
  refreshKey?: string;
  onOpenWatchlist?: (code?: string, reportId?: number) => void;
  onOpenEvent?: (change: PersonalChange) => void;
  onOpenStockDiscovery?: (context?: StockDiscoveryMarketContext) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
};
const filters = [
  ['ALL', '全部'],
  ['COMPANY', '公司动态'],
  ['INDUSTRY', '行业变化'],
  ['TRACKING', '跟踪进展'],
] as const;
export function PersonalMarketPanel({
  businessDate,
  sectors,
  refreshKey,
  onOpenWatchlist,
  onOpenEvent,
  onOpenStockDiscovery,
  addToast,
}: Props) {
  const [focuses, setFocuses] = useState<WatchFocus[]>([]);
  const [changes, setChanges] = useState<PersonalChange[]>([]);
  const [research, setResearch] = useState<DailyResearch>();
  const [latest, setLatest] = useState<StockDiscoveryLatest>();
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [filter, setFilter] = useState<string>('ALL');
  const [adding, setAdding] = useState('');
  const [added, setAdded] = useState<string[]>([]);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setChanges([]);
    setLatest(undefined);
    setResearch(undefined);
    setFocuses([]);
    setErrors([]);
    void Promise.allSettled([
      api<WatchFocus[]>('/api/watchlist/focuses', { signal: abort.signal }),
      api<PersonalChange[]>(
        `/api/market-pulse/personal?businessDate=${businessDate}`,
        { signal: abort.signal },
      ),
      api<StockDiscoveryLatest>('/api/quant/stock-discoveries/latest', {
        signal: abort.signal,
      }),
      api<DailyResearch>(`/api/market-pulse/research/${businessDate}`, {
        signal: abort.signal,
      }),
    ]).then(([focus, personal, discovery, daily]) => {
      if (abort.signal.aborted) {
        return;
      }
      const failures: string[] = [];
      if (focus.status === 'fulfilled' && Array.isArray(focus.value)) {
        setFocuses(focus.value);
      } else {
        failures.push('自选关注暂未加载');
      }
      if (personal.status === 'fulfilled' && Array.isArray(personal.value)) {
        setChanges(personal.value);
      } else {
        failures.push('公司动态暂未加载');
      }
      if (discovery.status === 'fulfilled') {
        setLatest(discovery.value);
      } else {
        failures.push('股票候选暂未加载');
      }
      if (
        daily.status === 'fulfilled' &&
        daily.value?.businessDate === businessDate &&
        Array.isArray(daily.value.stocks)
      ) {
        setResearch(daily.value);
      }
      setErrors(failures);
      setLoading(false);
    });
    return () => abort.abort();
  }, [businessDate, refreshKey, attempt]);
  const report =
    latest && 'report' in latest && latest.report?.as_of_date === businessDate
      ? latest.report
      : undefined;
  const allChanges = [
    ...changes,
    ...relativeChanges(research, sectors, focuses, businessDate, report),
    ...industryChanges(sectors, focuses, businessDate, report),
  ].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
  const visible = allChanges.filter(
    (item) => filter === 'ALL' || item.category === filter,
  );
  const opportunities = opportunitySectors(sectors, focuses, report);
  const watched = new Set([
    ...focuses.filter((f) => f.type === 'STOCK').map((f) => stockCode(f.code)),
    ...added,
  ]);
  function openSector(name: string) {
    onOpenStockDiscovery?.(
      buildResearchContext(
        sectors.filter((sector) => sector.sectorName === name),
        businessDate,
      ),
    );
  }
  async function add(code: string) {
    setAdding(code);
    try {
      await api('/api/watchlist', {
        method: 'POST',
        body: JSON.stringify({
          code: stockCode(code),
          type: 'STOCK',
          groupName: '市场研究',
        }),
      });
      setAdded((values) => [...values, stockCode(code)]);
      setAttempt((value) => value + 1);
      addToast('已加入自选，可填写关注理由', 'success');
    } catch (error) {
      addToast(
        error instanceof Error ? error.message : '加入自选失败',
        'error',
      );
    } finally {
      setAdding('');
    }
  }
  return (
    <section className="mp-personal" aria-label="个人市场研究">
      <div className="mp-personal-main">
        <header>
          <div>
            <h3>与你有关的今日变化</h3>
            <p>截至 {businessDate} · 汇集近七日动态与当日行业变化</p>
          </div>
          <button onClick={() => onOpenWatchlist?.()}>管理自选</button>
        </header>
        <div
          className="mp-personal-filters"
          role="group"
          aria-label="关注变化分类"
        >
          {filters.map(([key, label]) => (
            <button
              key={key}
              aria-pressed={filter === key}
              onClick={() => setFilter(key)}
            >
              {label}
              <span>
                {key === 'ALL'
                  ? allChanges.length
                  : allChanges.filter((item) => item.category === key).length}
              </span>
            </button>
          ))}
        </div>
        {loading && (
          <p role="status" className="mp-personal-empty">
            正在整理你的关注动态…
          </p>
        )}
        {!!errors.length && (
          <p className="mp-personal-empty" role="status">
            {errors.join('；')}{' '}
            <button onClick={() => setAttempt((value) => value + 1)}>
              重新加载
            </button>
          </p>
        )}
        {!loading &&
          !focuses.length &&
          !errors.includes('自选关注暂未加载') && (
            <div className="mp-personal-empty">
              <strong>从你的第一只自选开始</strong>
              <p>
                把关注的公司加入自选，这里就会汇集它们的事件、研究更新和相关方向。
              </p>
              <button onClick={() => onOpenWatchlist?.()}>添加自选股</button>
            </div>
          )}
        {!loading && focuses.length > 0 && !visible.length && (
          <div className="mp-personal-empty">
            <strong>这个分类暂无新变化</strong>
            <p>
              已关注 {focuses.length}{' '}
              个标的。可以先看看右侧机会，或补充你接下来想观察的事情。
            </p>
            <button onClick={() => onOpenWatchlist?.()}>完善关注理由</button>
          </div>
        )}
        {!loading && (
          <div className="mp-personal-list">
            {visible.slice(0, 12).map((item) => (
              <article key={item.id}>
                <div className="mp-personal-rowhead">
                  <strong>{item.name || item.code}</strong>
                  <span>
                    {filters.find(([key]) => key === item.category)?.[1]}
                  </span>
                  <time>{item.occurredAt?.slice(0, 10)}</time>
                </div>
                <h4>{item.title}</h4>
                {item.summary && <p>{item.summary}</p>}
                {(item.reason || item.nextWatch) && (
                  <details>
                    <summary>我的关注点</summary>
                    {item.reason && <p>关注理由：{item.reason}</p>}
                    {item.nextWatch && <p>接下来观察：{item.nextWatch}</p>}
                  </details>
                )}
                <div className="mp-personal-actions">
                  {item.category === 'INDUSTRY' ? (
                    <button onClick={() => openSector(item.sectorName!)}>
                      研究这个方向
                    </button>
                  ) : (
                    <>
                      {(item.eventKey || item.sampleId) && (
                        <button onClick={() => onOpenEvent?.(item)}>
                          查看事件
                        </button>
                      )}
                      {item.reportId && (
                        <button
                          onClick={() =>
                            onOpenWatchlist?.(item.code, item.reportId)
                          }
                        >
                          查看归因
                        </button>
                      )}
                      <button onClick={() => onOpenWatchlist?.(item.code)}>
                        查看公司
                      </button>
                    </>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
        {visible.length > 12 && (
          <p className="mp-personal-empty">
            展示最近 12 条，共 {visible.length} 条动态。
            <button
              onClick={() =>
                onOpenEvent?.({
                  id: '',
                  category: 'TRACKING',
                  code: '',
                  title: '',
                  occurredAt: businessDate,
                })
              }
            >
              进入投资观察
            </button>
          </p>
        )}
      </div>
      <aside
        className="mp-personal-opportunities"
        aria-label="关注方向的新机会"
      >
        <header>
          <div>
            <h3>关注方向的新机会</h3>
            <p>从熟悉的方向，发现新的研究对象</p>
          </div>
        </header>
        {opportunities.map((sector) => {
          const related = sectorFollows(sector, focuses, report);
          const candidates =
            report?.candidates
              .filter(
                (item) =>
                  item.admitted &&
                  item.sector_names.includes(sector.sectorName) &&
                  !watched.has(stockCode(item.code)),
              )
              .slice(0, 2) ?? [];
          return (
            <article key={sector.sectorCode}>
              <div className="mp-personal-rowhead">
                <strong>{sector.sectorName}</strong>
                <span>
                  {related.length
                    ? `关联 ${related.length} 只自选`
                    : '其他市场方向'}
                </span>
              </div>
              <h4>当日上涨 {sector.return1d?.toFixed(2)}%</h4>
              <p>
                {related.length
                  ? `你关注的 ${related.map((item) => item.name || item.code).join('、')} 与这个方向相关。`
                  : '拓展关注范围，看看这个方向的新变化。'}
              </p>
              {candidates.map((item) => (
                <div className="mp-personal-candidate" key={item.code}>
                  <span>
                    {item.name}
                    <small>{item.code} · 已有发现名单</small>
                  </span>
                  <button
                    disabled={!!adding}
                    onClick={() => void add(item.code)}
                  >
                    {adding === item.code ? '添加中…' : '加入自选'}
                  </button>
                </div>
              ))}
              <button onClick={() => openSector(sector.sectorName)}>
                查看方向与候选 →
              </button>
            </article>
          );
        })}
        {!opportunities.length && (
          <p className="mp-personal-empty">
            当前截面暂无上涨行业。可以进入股票发现查看已有研究名单。
          </p>
        )}
        <button
          className="mp-personal-discover"
          onClick={() => onOpenStockDiscovery?.()}
        >
          浏览全部股票发现
        </button>
      </aside>
    </section>
  );
}
