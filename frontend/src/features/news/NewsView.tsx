import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { LiveNewsPanel } from './LiveNewsPanel';
import { RadarEventCard } from './RadarEventCard';
import { RadarEventDetailDrawer } from './RadarEventDetailDrawer';
import type { RadarEvent, ResearchRadarSnapshot } from './researchRadarTypes';

type NewsCategory = { code: string; name: string; enabled?: boolean; displayOrder?: number };
const ALL_CATEGORY: NewsCategory = { code: 'ALL', name: '全部' };
const RELATED_CATEGORY: NewsCategory = { code: 'RELATED', name: '与我相关' };
const REFRESH_INTERVAL_MS = 45_000;

export function NewsView({ setMessage, addToast, onResearch }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onResearch?: (eventId: number, question: string) => void;
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
  onResearch?: (eventId: number, question: string) => void;
}) {
  const [snapshot, setSnapshot] = useState<ResearchRadarSnapshot>();
  const [categories, setCategories] = useState<NewsCategory[]>([ALL_CATEGORY, RELATED_CATEGORY]);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedEvent, setSelectedEvent] = useState<RadarEvent>();
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const mounted = useRef(true);
  const snapshotRef = useRef<ResearchRadarSnapshot>();
  const selectedCategoryRef = useRef('ALL');
  const requestSequence = useRef(0);

  async function load(manual = false, selection = selectedCategoryRef.current) {
    const requestId = ++requestSequence.current;
    const watchlistOnly = selection === 'RELATED';
    const category = watchlistOnly ? 'ALL' : selection;
    try {
      if (manual) setLoading(true);
      const next = await api<ResearchRadarSnapshot>(`/api/research-radar?category=${encodeURIComponent(category)}&watchlistOnly=${watchlistOnly}&limit=20`);
      if (!mounted.current || requestId !== requestSequence.current || selection !== selectedCategoryRef.current) return;
      snapshotRef.current = next;
      setSnapshot(next);
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
    setSelectedEvent(undefined); setLoading(true); void load(false, code);
  }

  useEffect(() => {
    mounted.current = true; void load();
    void api<NewsCategory[]>('/api/news/categories').then((values) => {
      if (mounted.current) setCategories([ALL_CATEGORY, RELATED_CATEGORY, ...values.filter((value) => value.code !== 'ALL')]);
    }).catch(() => undefined);
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(false, selectedCategoryRef.current);
    }, REFRESH_INTERVAL_MS);
    return () => { mounted.current = false; window.clearInterval(timer); };
  }, []);

  const normalizedQuery = query.trim().toLocaleLowerCase();
  const events = useMemo(() => (snapshot?.events ?? []).filter((event) =>
    !normalizedQuery || `${event.title} ${event.summary} ${event.watchlistExplanation}`.toLocaleLowerCase().includes(normalizedQuery)
  ), [normalizedQuery, snapshot]);
  const latestChanges = useMemo(() => (snapshot?.latestChanges ?? snapshot?.events ?? []).filter((event) =>
    !normalizedQuery || `${event.title} ${event.summary} ${event.changeSummary ?? ''}`.toLocaleLowerCase().includes(normalizedQuery)
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

      <div className="news-board radar-board radar-board-single" data-testid="research-radar-board">
        <section className="radar-latest-panel" aria-labelledby="radar-latest-heading">
          <div className="news-section-heading"><div><span>01 · CHANGE TAPE</span><h2 id="radar-latest-heading">最新变化</h2></div><strong>{latestChanges.length} 件</strong></div>
          {loading && !snapshot ? <NewsSkeleton /> : latestChanges.length ? (
            <div className="radar-change-tape">{latestChanges.map((item) => (
              <button type="button" className="radar-change-item" key={item.id} onClick={() => setSelectedEvent(item)}>
                <div><span>{changeTypeLabel(item.changeType)}</span><time dateTime={item.lastSeenAt}>{formatTime(item.lastSeenAt)}</time></div>
                <strong>{item.title}</strong>
                <p>{item.changeSummary || item.summary}</p>
              </button>
            ))}</div>
          ) : <EmptyState label="暂时没有新的事件变化" />}
        </section>
        <section className="news-flash-panel radar-focus-panel" aria-labelledby="radar-focus-heading">
          <div className="news-section-heading"><div><span>02 · RESEARCH FIRST</span><h2 id="radar-focus-heading">高优先级事件</h2></div><strong>{events.length} 件</strong></div>
          {loading && !snapshot ? <NewsSkeleton /> : events.length ? <div className="radar-event-list">{events.map((item) => <RadarEventCard key={item.id} event={item} onResearch={onResearch} onOpen={setSelectedEvent} />)}</div> : <EmptyState label="暂时没有匹配的聚合事件" />}
        </section>
      </div>
      {selectedEvent ? <RadarEventDetailDrawer event={selectedEvent} onClose={() => setSelectedEvent(undefined)} /> : null}
    </section>
  );
}

function NewsSkeleton() { return <div className="news-skeleton" aria-label="正在加载雷达"><span /><span /><span /></div>; }
function EmptyState({ label }: { label: string }) { return <div className="news-empty"><span aria-hidden="true">∅</span><p>{label}</p></div>; }
function formatTime(value?: string) { const date = value ? new Date(value) : undefined; return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--:--'; }
function changeTypeLabel(value?: string) { if (value === 'MULTI_SOURCE') return '多源确认'; if (value === 'EVIDENCE_ADDED') return '新增证据'; if (value === 'MATERIAL_UPDATE') return '实质进展'; if (value === 'NEW_EVENT') return '新事件'; return '事件更新'; }
