import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import {
  FollowedSector,
  MarketIndexQuote,
  ResourceState,
  SectorCategory,
  SectorMarketOverview,
  WatchlistItem
} from '../../shared/types';
import { preserveValidQuotes } from './watchlistFormatters';
import { aggregateMarketDataQuality, AggregatedMarketDataQuality } from './marketDataQuality';

type LoadResult = { failed: boolean; degradedCount: number };

function emptyOverview(category: SectorCategory): SectorMarketOverview {
  return { category, qualityStatus: 'UNAVAILABLE', leaders: [], laggards: [] };
}

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function fingerprint(investments: WatchlistItem[], indices: MarketIndexQuote[], sectors: FollowedSector[]) {
  return JSON.stringify([
    investments.map((item) => [item.code, item.price, item.changePct, item.quoteValid]),
    indices.map((item) => [item.code, item.price, item.changePct, item.quoteValid]),
    sectors.map((item) => [item.code, item.price, item.changePct, item.quoteValid])
  ]);
}

export function useWatchlistDashboardData() {
  const [sectorCategory, setSectorCategory] = useState<SectorCategory>('INDUSTRY');
  const [investments, setInvestments] = useState<ResourceState<WatchlistItem[]>>({ data: [], phase: 'idle' });
  const [indices, setIndices] = useState<ResourceState<MarketIndexQuote[]>>({ data: [], phase: 'idle' });
  const [sectorOverview, setSectorOverview] = useState<ResourceState<SectorMarketOverview>>({
    data: emptyOverview('INDUSTRY'), phase: 'idle'
  });
  const [followedSectors, setFollowedSectors] = useState<ResourceState<FollowedSector[]>>({ data: [], phase: 'idle' });
  const [refreshing, setRefreshing] = useState(false);
  const [refreshStatus, setRefreshStatus] = useState<string>();
  const [refreshQuality, setRefreshQuality] = useState<AggregatedMarketDataQuality>();
  const investmentsRef = useRef(investments);
  const indicesRef = useRef(indices);
  const followsRef = useRef(followedSectors);
  const refreshSequence = useRef(0);
  const overviewSequence = useRef(0);

  useEffect(() => { investmentsRef.current = investments; }, [investments]);
  useEffect(() => { indicesRef.current = indices; }, [indices]);
  useEffect(() => { followsRef.current = followedSectors; }, [followedSectors]);

  const loadInvestments = useCallback(async (force = false): Promise<LoadResult> => {
    setInvestments((current) => ({ ...current, phase: current.data.length ? 'refreshing' : 'loading', error: undefined }));
    try {
      const values = await api<WatchlistItem[]>(`/api/watchlist${force ? '?refresh=true' : ''}`);
      const ordinary = values.filter((item) => item.type === 'STOCK' || item.type === 'FUND');
      const merged = preserveValidQuotes(ordinary, investmentsRef.current.data, (item) => `${item.type}:${item.code}`);
      const next = { data: merged.items, phase: 'ready' as const, updatedAt: new Date().toISOString() };
      investmentsRef.current = next;
      setInvestments(next);
      return { failed: false, degradedCount: merged.degradedCount };
    } catch (error) {
      const next = { ...investmentsRef.current, phase: 'error' as const, error: messageOf(error, '自选列表加载失败') };
      investmentsRef.current = next;
      setInvestments(next);
      return { failed: true, degradedCount: 0 };
    }
  }, []);

  const loadIndices = useCallback(async (force = false): Promise<LoadResult> => {
    setIndices((current) => ({ ...current, phase: current.data.length ? 'refreshing' : 'loading', error: undefined }));
    try {
      const values = await api<MarketIndexQuote[]>(`/api/market-indices${force ? '?refresh=true' : ''}`);
      const merged = preserveValidQuotes(values, indicesRef.current.data, (item) => item.code);
      const next = { data: merged.items, phase: 'ready' as const, updatedAt: new Date().toISOString() };
      indicesRef.current = next;
      setIndices(next);
      return { failed: false, degradedCount: merged.degradedCount };
    } catch (error) {
      const next = { ...indicesRef.current, phase: 'error' as const, error: messageOf(error, '市场指数加载失败') };
      indicesRef.current = next;
      setIndices(next);
      return { failed: true, degradedCount: 0 };
    }
  }, []);

  const loadSectorOverview = useCallback(async (category: SectorCategory, force = false): Promise<LoadResult> => {
    const sequence = ++overviewSequence.current;
    setSectorOverview((current) => ({
      ...current,
      data: current.data.category === category ? current.data : emptyOverview(category),
      phase: current.data.category === category && (current.data.leaders.length || current.data.laggards.length)
        ? 'refreshing' : 'loading',
      error: undefined
    }));
    try {
      const suffix = force ? '&refresh=true' : '';
      const value = await api<SectorMarketOverview>(
        `/api/sector-market/overview?category=${category}&limit=5${suffix}`
      );
      if (!value || !Array.isArray(value.leaders) || !Array.isArray(value.laggards)) {
        throw new Error('板块排行响应格式不正确');
      }
      if (sequence !== overviewSequence.current) return { failed: false, degradedCount: 0 };
      setSectorOverview({
        data: value,
        phase: value.qualityStatus === 'UNAVAILABLE' ? 'error' : 'ready',
        error: value.qualityStatus === 'UNAVAILABLE' ? (value.warning || '板块排行暂不可用') : undefined,
        warning: value.qualityStatus === 'STALE_FALLBACK'
          ? (value.warning || '当前展示最近一次可用数据') : value.warning,
        updatedAt: value.retrievedAt
      });
      return {
        failed: value.qualityStatus === 'UNAVAILABLE',
        degradedCount: ['STALE_FALLBACK', 'PARTIAL_FRESH'].includes(value.qualityStatus) ? 1 : 0
      };
    } catch (error) {
      if (sequence !== overviewSequence.current) return { failed: false, degradedCount: 0 };
      const message = messageOf(error, '板块排行加载失败');
      setSectorOverview((current) => {
        const hasSnapshot = current.data.leaders.length > 0 || current.data.laggards.length > 0;
        return hasSnapshot
          ? { ...current, phase: 'ready', error: undefined, warning: `排行刷新失败，继续展示最近数据：${message}` }
          : { ...current, phase: 'error', error: message };
      });
      return { failed: true, degradedCount: 0 };
    }
  }, []);

  const loadFollowedSectors = useCallback(async (force = false): Promise<LoadResult> => {
    setFollowedSectors((current) => ({ ...current, phase: current.data.length ? 'refreshing' : 'loading', error: undefined }));
    try {
      const values = await api<FollowedSector[]>(`/api/sector-market/follows${force ? '?refresh=true' : ''}`);
      const merged = preserveValidQuotes(values, followsRef.current.data, (item) => item.code);
      const next = { data: merged.items, phase: 'ready' as const, updatedAt: new Date().toISOString() };
      followsRef.current = next;
      setFollowedSectors(next);
      return { failed: false, degradedCount: merged.degradedCount };
    } catch (error) {
      const next = { ...followsRef.current, phase: 'error' as const, error: messageOf(error, '关注板块加载失败') };
      followsRef.current = next;
      setFollowedSectors(next);
      return { failed: true, degradedCount: 0 };
    }
  }, []);

  useEffect(() => {
    void Promise.all([loadInvestments(), loadIndices(), loadFollowedSectors()]);
  }, [loadFollowedSectors, loadIndices, loadInvestments]);

  useEffect(() => {
    void loadSectorOverview(sectorCategory);
  }, [loadSectorOverview, sectorCategory]);

  const refreshAll = useCallback(async () => {
    if (refreshing) return;
    const sequence = ++refreshSequence.current;
    const before = fingerprint(investmentsRef.current.data, indicesRef.current.data, followsRef.current.data);
    setRefreshing(true);
    setRefreshStatus('正在从行情源获取最新数据…');
    const results = await Promise.all([
      loadInvestments(true), loadIndices(true), loadSectorOverview(sectorCategory, true), loadFollowedSectors(true)
    ]);
    if (sequence !== refreshSequence.current) return;
    const failedCount = results.reduce((sum, result) => sum + (result.failed ? 1 : 0) + result.degradedCount, 0);
    const after = fingerprint(investmentsRef.current.data, indicesRef.current.data, followsRef.current.data);
    const time = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(new Date());
    setRefreshStatus(failedCount
      ? `部分行情刷新失败，已保留可用数据 · ${time}`
      : before === after ? `已刷新，行情暂无变化 · ${time}` : `行情已刷新 · ${time}`);
    setRefreshQuality(failedCount ? {
      status: 'PARTIAL_FRESH',
      warning: '部分行情请求失败，页面已保留仍然可用的数据。',
      degradedCount: failedCount
    } : undefined);
    setRefreshing(false);
  }, [loadFollowedSectors, loadIndices, loadInvestments, loadSectorOverview, refreshing, sectorCategory]);

  const marketDataQuality = useMemo(() => aggregateMarketDataQuality([
    ...investments.data,
    ...indices.data,
    ...(['ready', 'error'].includes(sectorOverview.phase) ? [sectorOverview.data] : []),
    ...followedSectors.data,
    ...(refreshQuality ? [{
      qualityStatus: refreshQuality.status,
      warning: refreshQuality.warning,
      staleAgeSeconds: refreshQuality.staleAgeSeconds,
      sourceCode: refreshQuality.sourceCode
    }] : [])
  ]), [followedSectors.data, indices.data, investments.data, refreshQuality, sectorOverview.data]);

  return {
    investments,
    indices,
    sectorOverview,
    followedSectors,
    sectorCategory,
    setSectorCategory,
    loadInvestments,
    loadSectorOverview,
    loadFollowedSectors,
    refreshAll,
    refreshing,
    refreshStatus,
    marketDataQuality
  };
}
