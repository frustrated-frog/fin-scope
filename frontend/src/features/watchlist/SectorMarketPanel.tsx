import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import {
  FollowedSector,
  ResourceState,
  SectorCategory,
  SectorMarketEntry,
  SectorMarketOverview,
  SectorMarketSearchResult
} from '../../shared/types';
import { changeClass, formatPct, formatPrice, formatTurnover } from './watchlistFormatters';

type Props = {
  overview: ResourceState<SectorMarketOverview>;
  follows: ResourceState<FollowedSector[]>;
  category: SectorCategory;
  setCategory: (category: SectorCategory) => void;
  reloadFollows: () => Promise<unknown>;
  retryOverview: () => Promise<unknown>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onStartAttribution: (sector: FollowedSector) => void;
  onOpenAttribution: (sector: FollowedSector) => void;
};

function categoryLabel(category: SectorCategory) {
  return category === 'INDUSTRY' ? '行业' : '概念';
}

export function SectorMarketPanel({
  overview,
  follows,
  category,
  setCategory,
  reloadFollows,
  retryOverview,
  addToast,
  onStartAttribution,
  onOpenAttribution
}: Props) {
  const [pendingCode, setPendingCode] = useState<string>();
  const [query, setQuery] = useState('');
  const [searchResult, setSearchResult] = useState<SectorMarketSearchResult>();
  const [searchPhase, setSearchPhase] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [searchError, setSearchError] = useState<string>();
  const followedCodes = useMemo(() => new Set(follows.data.map((item) => item.code)), [follows.data]);

  useEffect(() => {
    const normalized = query.trim();
    if (!normalized) {
      setSearchResult(undefined);
      setSearchPhase('idle');
      setSearchError(undefined);
      return;
    }
    let active = true;
    const timer = window.setTimeout(async () => {
      setSearchPhase('loading');
      setSearchError(undefined);
      try {
        const result = await api<SectorMarketSearchResult>(
          `/api/sector-market/search?q=${encodeURIComponent(normalized)}&category=ALL&limit=10`
        );
        if (!active) return;
        setSearchResult(result);
        setSearchPhase('ready');
      } catch (error) {
        if (!active) return;
        setSearchPhase('error');
        setSearchError(error instanceof Error ? error.message : '板块搜索失败');
      }
    }, 260);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [query]);

  async function toggleFollow(code: string, isFollowed: boolean) {
    if (pendingCode) return;
    setPendingCode(code);
    try {
      await api(`/api/sector-market/follows/${code}`, { method: isFollowed ? 'DELETE' : 'PUT' });
      await reloadFollows();
      addToast(isFollowed ? '已取消板块关注' : '已关注板块', isFollowed ? 'info' : 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '板块关注操作失败', 'error');
    } finally {
      setPendingCode(undefined);
    }
  }

  function renderRanking(title: string, tone: 'leader' | 'laggard', items: SectorMarketEntry[]) {
    return (
      <div className={`sector-rank-column sector-rank-${tone}`}>
        <div className="sector-rank-head">
          <span>{title}</span>
          <small>涨跌幅 · 成交额</small>
        </div>
        {items.length ? (
          <ol className="sector-rank-list">
            {items.map((item, index) => {
              const isFollowed = followedCodes.has(item.code);
              return (
                <li key={item.code}>
                  <span className="sector-rank-index">{String(index + 1).padStart(2, '0')}</span>
                  <div className="sector-rank-identity">
                    <strong>{item.name}</strong>
                    <span>{item.code}{item.leaderStockName ? ` · 领涨 ${item.leaderStockName}` : ''}</span>
                  </div>
                  <div className="sector-rank-quote">
                    <strong className={changeClass(item.changePct)}>{formatPct(item.changePct)}</strong>
                    <span>{formatTurnover(item.turnover) || '--'}</span>
                  </div>
                  <button
                    className={`sector-follow-button${isFollowed ? ' is-followed' : ''}`}
                    type="button"
                    aria-label={`${isFollowed ? '取消关注' : '关注'}-${item.name}`}
                    title={isFollowed ? '取消关注' : '关注板块'}
                    disabled={pendingCode === item.code}
                    onClick={() => toggleFollow(item.code, isFollowed)}
                  >
                    <span aria-hidden="true">{isFollowed ? '★' : '☆'}</span>
                  </button>
                </li>
              );
            })}
          </ol>
        ) : (
          <p className="sector-empty-copy">当前快照没有可排行数据</p>
        )}
      </div>
    );
  }

  const overviewEmpty = overview.data.leaders.length === 0 && overview.data.laggards.length === 0;

  return (
    <section className="panel wide sector-market-panel" aria-labelledby="sector-market-title">
      <header className="sector-market-header">
        <div>
          <span className="sector-market-kicker">MARKET STRUCTURE</span>
          <h3 id="sector-market-title">板块行情</h3>
          <p>从全市场强弱结构中发现方向，把值得持续跟踪的板块留在这里。</p>
        </div>
        <div className="sector-category-tabs" aria-label="板块分类">
          {(['INDUSTRY', 'CONCEPT'] as SectorCategory[]).map((value) => (
            <button
              key={value}
              type="button"
              className={category === value ? 'is-active' : ''}
              aria-pressed={category === value}
              onClick={() => setCategory(value)}
            >
              {categoryLabel(value)}板块
            </button>
          ))}
        </div>
      </header>

      <div className="sector-market-scan">
        <div className="sector-market-scan-head">
          <div>
            <strong>全市场扫描</strong>
            <span>{categoryLabel(category)}板块 · 同一行情快照</span>
          </div>
          {overview.data.qualityStatus === 'STALE' && <span className="sector-quality is-stale">最近可用数据</span>}
          {overview.phase === 'refreshing' && <span className="sector-quality">更新中</span>}
        </div>
        {overview.error && overviewEmpty ? (
          <div className="sector-resource-error" role="alert">
            <span>{overview.error}</span>
            <button type="button" className="ghost-button compact-button" onClick={() => retryOverview()}>重试</button>
          </div>
        ) : (
          <>
            {overview.warning && <p className="sector-quality-warning">{overview.warning}</p>}
            <div className="sector-rank-grid">
              {renderRanking('领涨方向', 'leader', overview.data.leaders)}
              {renderRanking('承压方向', 'laggard', overview.data.laggards)}
            </div>
          </>
        )}
      </div>

      <div className="sector-follow-workspace">
        <div className="sector-follow-heading">
          <div>
            <strong>我的关注</strong>
            <span>{follows.data.length} 个板块</span>
          </div>
          <div className="sector-search-wrap">
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索板块名称或 BK 代码"
              aria-label="搜索板块"
            />
            {searchPhase === 'loading' && <span className="sector-search-state">搜索中…</span>}
            {query.trim() && searchPhase !== 'idle' && (
              <div className="sector-search-results" role="listbox" aria-label="板块搜索结果">
                {searchPhase === 'error' ? (
                  <p role="alert">{searchError}</p>
                ) : searchPhase === 'ready' && searchResult?.items.length === 0 ? (
                  <p>未找到匹配板块</p>
                ) : searchResult?.items.map((item) => {
                  const isFollowed = followedCodes.has(item.code);
                  return (
                    <div className="sector-search-item" role="option" aria-selected={isFollowed} key={item.code}>
                      <span><strong>{item.name}</strong><small>{categoryLabel(item.category)} · {item.code}</small></span>
                      <em className={changeClass(item.changePct)}>{formatPct(item.changePct)}</em>
                      <button
                        type="button"
                        disabled={pendingCode === item.code}
                        onClick={() => toggleFollow(item.code, isFollowed)}
                      >{isFollowed ? '取消关注' : '关注'}</button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {follows.error && follows.data.length === 0 ? (
          <p className="sector-resource-error" role="alert">{follows.error}</p>
        ) : follows.phase === 'loading' && follows.data.length === 0 ? (
          <p className="sector-empty-copy">正在加载关注板块…</p>
        ) : follows.data.length === 0 ? (
          <p className="sector-empty-copy">还没有关注板块。可以从上方排行榜点星标，或搜索任意板块。</p>
        ) : (
          <div className="sector-follow-grid">
            {follows.data.map((sector) => (
              <article className="sector-follow-card" data-testid={`followed-sector-${sector.code}`} key={sector.code}>
                <button
                  className="sector-follow-remove"
                  type="button"
                  aria-label={`取消关注-${sector.name || sector.code}`}
                  onClick={() => toggleFollow(sector.code, true)}
                >×</button>
                <div className="sector-follow-card-head">
                  <span>{sector.code}</span>
                  <strong>{sector.name || sector.code}</strong>
                </div>
                {sector.quoteValid ? (
                  <div className="sector-follow-quote">
                    <strong>{formatPrice(sector.price)}</strong>
                    <span className={changeClass(sector.changePct)}>{formatPct(sector.changePct)}</span>
                    <small>成交额 {formatTurnover(sector.turnover) || '--'}</small>
                  </div>
                ) : <p className="sector-card-note">{sector.quoteNote || '暂无行情'}</p>}
                {sector.attributionSummary && sector.attributionReportId && (
                  <button type="button" className="sector-attribution-summary" onClick={() => onOpenAttribution(sector)}>
                    <span>{sector.attributionReportDate || '最近归因'}</span>
                    <strong>{sector.attributionSummary}</strong>
                  </button>
                )}
                <button type="button" className="watchlist-attr-button" onClick={() => onStartAttribution(sector)}>
                  🔬 深度归因
                </button>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
