import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { LiveNewsPanel } from './LiveNewsPanel';
import { RadarEventCard } from './RadarEventCard';
import type { RadarNewsItem, ResearchRadarSnapshot } from './researchRadarTypes';

type NewsCategory = { code: string; name: string; enabled?: boolean; displayOrder?: number };
const ALL_CATEGORY: NewsCategory = { code: 'ALL', name: '全部' };
const RELATED_CATEGORY: NewsCategory = { code: 'RELATED', name: '与我相关' };
const REFRESH_INTERVAL_MS = 45_000;

export function NewsView({ setMessage, addToast, onResearch }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onResearch?: (question: string) => void;
}) {
  const [mode, setMode] = useState<'live' | 'radar'>('live');
  const props = { setMessage, addToast, onResearch };
  return (
    <section className="news-workspace" aria-label="News Wire 工作区">
      <nav className="news-mode-switcher" aria-label="News Wire 视图">
        <button type="button" className={mode === 'live' ? 'active' : ''} aria-pressed={mode === 'live'} onClick={() => setMode('live')}>实时资讯</button>
        <button type="button" className={mode === 'radar' ? 'active' : ''} aria-pressed={mode === 'radar'} onClick={() => setMode('radar')}>研究雷达</button>
      </nav>
      {mode === 'live' ? <LiveNewsPanel setMessage={setMessage} addToast={addToast} /> : <ResearchRadarPanel {...props} />}
    </section>
  );
}

function ResearchRadarPanel({ setMessage, addToast, onResearch }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onResearch?: (question: string) => void;
}) {
  const [snapshot, setSnapshot] = useState<ResearchRadarSnapshot>();
  const [categories, setCategories] = useState<NewsCategory[]>([ALL_CATEGORY, RELATED_CATEGORY]);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [pendingSnapshot, setPendingSnapshot] = useState<ResearchRadarSnapshot>();
  const [pendingCount, setPendingCount] = useState(0);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const mounted = useRef(true);
  const snapshotRef = useRef<ResearchRadarSnapshot>();
  const selectedCategoryRef = useRef('ALL');
  const requestSequence = useRef(0);

  async function load(manual = false, polling = false, selection = selectedCategoryRef.current) {
    const requestId = ++requestSequence.current;
    const watchlistOnly = selection === 'RELATED';
    const category = watchlistOnly ? 'ALL' : selection;
    try {
      if (manual) setLoading(true);
      const next = await api<ResearchRadarSnapshot>(`/api/research-radar?category=${encodeURIComponent(category)}&watchlistOnly=${watchlistOnly}&limit=20`);
      if (!mounted.current || requestId !== requestSequence.current || selection !== selectedCategoryRef.current) return;
      const current = snapshotRef.current;
      const currentIds = new Set(current?.liveItems.map((item) => item.id) ?? []);
      const added = polling && current ? next.liveItems.filter((item) => !currentIds.has(item.id)).length : 0;
      if (added > 0) { setPendingSnapshot(next); setPendingCount(added); }
      else { snapshotRef.current = next; setSnapshot(next); setPendingSnapshot(undefined); setPendingCount(0); }
      setMessage(next.warnings.length ? '雷达已更新，当前使用部分最近结果' : '研究雷达已同步');
      if (manual) addToast('研究雷达已更新', 'success');
    } catch (error) {
      if (!mounted.current) return;
      const message = error instanceof Error ? error.message : '研究雷达刷新失败';
      setMessage(message); if (manual) addToast(message, 'error');
    } finally {
      if (mounted.current && requestId === requestSequence.current) setLoading(false);
    }
  }

  function switchCategory(code: string) {
    if (code === selectedCategoryRef.current) return;
    selectedCategoryRef.current = code; setSelectedCategory(code); setQuery('');
    setPendingSnapshot(undefined); setPendingCount(0); setLoading(true); void load(false, false, code);
  }

  function applyPendingSnapshot() {
    if (!pendingSnapshot) return;
    snapshotRef.current = pendingSnapshot; setSnapshot(pendingSnapshot); setPendingSnapshot(undefined); setPendingCount(0);
  }

  useEffect(() => {
    mounted.current = true; void load();
    void api<NewsCategory[]>('/api/news/categories').then((values) => {
      if (mounted.current) setCategories([ALL_CATEGORY, RELATED_CATEGORY, ...values.filter((value) => value.code !== 'ALL')]);
    }).catch(() => undefined);
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(false, true, selectedCategoryRef.current);
    }, REFRESH_INTERVAL_MS);
    return () => { mounted.current = false; window.clearInterval(timer); };
  }, []);

  const normalizedQuery = query.trim().toLocaleLowerCase();
  const events = useMemo(() => (snapshot?.events ?? []).filter((event) =>
    !normalizedQuery || `${event.title} ${event.summary} ${event.watchlistExplanation}`.toLocaleLowerCase().includes(normalizedQuery)
  ), [normalizedQuery, snapshot]);
  const liveItems = useMemo(() => (snapshot?.liveItems ?? []).filter((item) =>
    !normalizedQuery || `${item.title} ${item.content} ${item.sourceName}`.toLocaleLowerCase().includes(normalizedQuery)
  ), [normalizedQuery, snapshot]);
  const radarRefreshing = snapshot?.warnings.some((warning) => warning.includes('雷达正在刷新')) ?? false;

  return (
    <section className="news-view radar-view" aria-label="研究雷达">
      <header className="news-command-bar radar-command-bar">
        <div className="news-command-copy">
          <div className="news-live-label"><span aria-hidden="true" /> PERSONAL RESEARCH RADAR</div>
          <h1>先看值得研究的事</h1>
          <p>系统自动合并重复资讯，并用固定规则解释为什么值得关注。你不需要配置策略。</p>
        </div>
        <div className="news-sync-state" aria-live="polite">
          <span>{snapshot ? `${snapshot.overview.eventCount} 件事 · ${snapshot.overview.sourceCount} 个来源` : '连接中'}</span>
          <strong>{snapshot ? `更新于 ${formatTime(snapshot.refreshedAt)}` : '等待首批资讯'}</strong>
          <button type="button" className="ghost-button news-refresh" aria-label="刷新资讯" onClick={() => void load(true)} disabled={loading}>
            {loading ? '同步中' : '立即刷新'}
          </button>
        </div>
      </header>

      <nav className="news-category-rail" aria-label="雷达分类">
        {categories.map((category) => (
          <button type="button" key={category.code} className={selectedCategory === category.code ? 'active' : ''}
            aria-pressed={selectedCategory === category.code} onClick={() => switchCategory(category.code)}>{category.name}</button>
        ))}
      </nav>

      <div className="radar-overview" aria-label="雷达概览">
        <article><span>值得关注</span><strong>{snapshot?.overview.eventCount ?? 0}</strong></article>
        <article><span>重点事件</span><strong>{snapshot?.overview.highPriorityCount ?? 0}</strong></article>
        <article><span>与我相关</span><strong>{snapshot?.overview.watchlistRelatedCount ?? 0}</strong></article>
        <label className="news-search"><span>检索</span><input type="search" aria-label="搜索资讯" placeholder="搜索公司、行业或事件" value={query} onChange={(e) => setQuery(e.target.value)} /></label>
      </div>

      {snapshot?.warnings.length ? <div className="news-degraded" role="status" title={snapshot.warnings.join('\n')}><span aria-hidden="true">!</span>{radarRefreshing ? '雷达正在后台刷新，当前展示最近一次结果' : '实时来源暂不可用，当前展示最近一次雷达结果'}</div> : null}
      {pendingCount > 0 ? <button type="button" className="news-update-notice" onClick={applyPendingSnapshot}>发现 {pendingCount} 条新资讯</button> : null}

      <div className="news-board radar-board" data-testid="research-radar-board">
        <section className="news-flash-panel radar-focus-panel" aria-labelledby="radar-focus-heading">
          <div className="news-section-heading"><div><span>01 · RESEARCH FIRST</span><h2 id="radar-focus-heading">今天值得关注</h2></div><strong>{events.length} 件</strong></div>
          {loading && !snapshot ? <NewsSkeleton /> : events.length ? <div className="radar-event-list">{events.map((item) => <RadarEventCard key={item.id} event={item} onResearch={onResearch} />)}</div> : <EmptyState label="暂时没有匹配的聚合事件" />}
        </section>
        <aside className="news-depth-panel radar-live-panel" aria-labelledby="radar-live-heading">
          <div className="news-section-heading"><div><span>02 · LIVE CONTEXT</span><h2 id="radar-live-heading">实时发生</h2></div><strong>{liveItems.length} 条</strong></div>
          <div className="radar-live-list">{liveItems.length ? liveItems.map((item, index) => <LiveItem key={item.id} item={item} latest={index === 0} />) : <EmptyState label="暂无匹配的实时资讯" />}</div>
        </aside>
      </div>
    </section>
  );
}

function LiveItem({ item, latest }: { item: RadarNewsItem; latest: boolean }) {
  const body = <><div className="news-flash-meta"><span>{item.sourceName}</span><small>{item.sourceTier}</small>{latest ? <em>NEW</em> : null}</div><strong className="radar-live-title">{item.title}</strong><p>{item.content}</p></>;
  return <article className={latest ? 'radar-live-item is-latest' : 'radar-live-item'}><time dateTime={item.publishedAt}>{formatTime(item.publishedAt)}</time>{item.url ? <a href={item.url} target="_blank" rel="noreferrer">{body}</a> : <div>{body}</div>}</article>;
}
function NewsSkeleton() { return <div className="news-skeleton" aria-label="正在加载雷达"><span /><span /><span /></div>; }
function EmptyState({ label }: { label: string }) { return <div className="news-empty"><span aria-hidden="true">∅</span><p>{label}</p></div>; }
function formatTime(value?: string) { const date = value ? new Date(value) : undefined; return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--:--'; }
