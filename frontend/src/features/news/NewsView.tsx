import {useEffect, useMemo, useRef, useState} from 'react';

import {api} from '../../shared/api/client';
import {useViewRevision} from '../../shared/api/useViewRevision';
import {LiveNewsPanel} from './LiveNewsPanel';
import {RadarEventCard} from './RadarEventCard';
import {RadarEventDetailDrawer} from './RadarEventDetailDrawer';
import {matchesRadarState, RadarStateFilters} from './RadarStateFilters';
import {RadarNotificationPanel} from './RadarNotificationPanel';
import type {
    RadarEvent,
    RadarEventDetail,
    RadarStateFilter,
    RadarWorkspaceState,
    ResearchRadarSnapshot
} from './researchRadarTypes';

type NewsCategory = { code: string; name: string; enabled?: boolean; displayOrder?: number };
const ALL_CATEGORY: NewsCategory = {code: 'ALL', name: '全部'};
const RELATED_CATEGORY: NewsCategory = {code: 'RELATED', name: '与我相关'};

export function NewsView({
                             setMessage,
                             addToast,
                             onResearch,
                             onOpenMajorEvents,
                             initialRadarEventId,
                             onInitialRadarEventOpened
                         }: {
    setMessage: (message: string) => void;
    addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
    onResearch?: (eventId: number, question: string) => void;
    onOpenMajorEvents?: () => void;
    initialRadarEventId?: number | null;
    onInitialRadarEventOpened?: () => void;
}) {
    const [mode, setMode] = useState<'live' | 'radar'>(initialRadarEventId ? 'radar' : 'live');
    const props = {setMessage, addToast, onResearch, onOpenMajorEvents};
    return (
        <section className="news-workspace" aria-label="News Wire 工作区">
            <nav className="news-mode-switcher" aria-label="News Wire 视图">
                <button type="button" className={mode === 'live' ? 'active' : ''} aria-pressed={mode === 'live'}
                        onClick={() => setMode('live')}>实时资讯
                </button>
                <button type="button" className={mode === 'radar' ? 'active' : ''} aria-pressed={mode === 'radar'}
                        onClick={() => setMode('radar')}>研究雷达
                </button>
            </nav>
            {mode === 'live' ?
                <LiveNewsPanel setMessage={setMessage} addToast={addToast} onOpenMajorEvents={onOpenMajorEvents}/> : (
                    <ResearchRadarPanel {...props} initialEventId={initialRadarEventId}
                                        onInitialEventOpened={onInitialRadarEventOpened}/>
                )}
        </section>
    );
}

function ResearchRadarPanel({setMessage, addToast, onResearch, initialEventId, onInitialEventOpened}: {
    setMessage: (message: string) => void;
    addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
    onResearch?: (eventId: number, question: string) => void;
    initialEventId?: number | null;
    onInitialEventOpened?: () => void;
}) {
    const [snapshot, setSnapshot] = useState<ResearchRadarSnapshot>();
    const [baseSnapshot, setBaseSnapshot] = useState<ResearchRadarSnapshot>();
    const [followedCount, setFollowedCount] = useState(0);
    const [categories, setCategories] = useState<NewsCategory[]>([ALL_CATEGORY, RELATED_CATEGORY]);
    const [selectedCategory, setSelectedCategory] = useState('ALL');
    const [selectedEvent, setSelectedEvent] = useState<RadarEvent>();
    const [query, setQuery] = useState('');
    const [stateFilter, setStateFilter] = useState<RadarStateFilter>('ALL');
    const [loading, setLoading] = useState(true);
    const mounted = useRef(true);
    const snapshotRef = useRef<ResearchRadarSnapshot>();
    const selectedCategoryRef = useRef('ALL');
    const stateFilterRef = useRef<RadarStateFilter>('ALL');
    const requestSequence = useRef(0);

    async function load(manual = false, selection = selectedCategoryRef.current, refresh = true) {
        const requestId = ++requestSequence.current;
        const watchlistOnly = selection === 'RELATED';
        const category = watchlistOnly ? 'ALL' : selection;
        const requestedState = stateFilterRef.current;
        try {
            if (manual) setLoading(true);
            const followedPath = '/api/research-radar/followed?limit=20';
            const path = requestedState === 'FOLLOWED' ? followedPath
                : `/api/research-radar?category=${encodeURIComponent(category)}&watchlistOnly=${watchlistOnly}&limit=20&state=${requestedState}${refresh ? '' : '&refresh=false'}`;
            const [rawNext, rawFollowed] = requestedState === 'FOLLOWED'
                ? [await api<ResearchRadarSnapshot>(followedPath), undefined]
                : await Promise.all([api<ResearchRadarSnapshot>(path), api<ResearchRadarSnapshot>(followedPath)]);
            const next = normalizeRadarSnapshot(rawNext);
            const followed = rawFollowed ? normalizeRadarSnapshot(rawFollowed) : next;
            if (!mounted.current || requestId !== requestSequence.current || selection !== selectedCategoryRef.current) return;
            setFollowedCount(followed.events.length);
            if (requestedState === 'ALL') setBaseSnapshot(next);
            snapshotRef.current = next;
            setSnapshot(next);
            setMessage((next.warnings ?? []).length ? '雷达已更新，当前使用部分最近结果' : '研究雷达已同步');
            if (manual) addToast('研究雷达已更新', 'success');
        } catch (error) {
            if (!mounted.current) return;
            const message = error instanceof Error ? error.message : '研究雷达刷新失败';
            setMessage(message);
            if (manual) addToast(message, 'error');
        } finally {
            if (mounted.current && requestId === requestSequence.current) setLoading(false);
        }
    }

    function switchCategory(code: string) {
        if (code === selectedCategoryRef.current) return;
        selectedCategoryRef.current = code;
        setSelectedCategory(code);
        setQuery('');
        setSelectedEvent(undefined);
        setLoading(true);
        void load(false, code, false);
    }

    function switchState(value: RadarStateFilter) {
        stateFilterRef.current = value;
        setStateFilter(value);
        setSelectedEvent(undefined);
        setLoading(true);
        void load(false, selectedCategoryRef.current, false);
    }

    function replaceEvent(next: RadarEvent) {
        setBaseSnapshot((current) => current ? normalizeRadarSnapshot({
            ...current,
            events: current.events.map((item) => item.id === next.id ? next : item)
        }) : current);
        setSnapshot((current) => {
            if (!current) return current;
            const removeFromFollowList = stateFilterRef.current === 'FOLLOWED' && (!next.followed || next.disposition === 'IGNORED');
            const updated = normalizeRadarSnapshot({
                ...current,
                events: removeFromFollowList ? current.events.filter((item) => item.id !== next.id) : current.events.map((item) => item.id === next.id ? next : item)
            });
            snapshotRef.current = updated;
            return updated;
        });
        setSelectedEvent((current) => current?.id === next.id ? next : current);
    }

    function openEvent(item: RadarEvent) {
        const next = {...item, read: true};
        replaceEvent(next);
        setSelectedEvent(next);
    }

    async function updateState(item: RadarEvent, patch: {
        followed?: boolean;
        disposition?: 'ACTIVE' | 'LATER' | 'IGNORED'
    }) {
        try {
            const state = await api<RadarWorkspaceState>(`/api/research-radar/events/${item.id}/state`, {
                method: 'PATCH',
                body: JSON.stringify(patch)
            });
            replaceEvent({...item, read: state.read, followed: state.followed, disposition: state.disposition});
            if (state.followed !== item.followed) {
                setFollowedCount((current) => Math.max(0, current + (state.followed ? 1 : -1)));
            }
            addToast('事件处理状态已更新', 'success');
        } catch (error) {
            addToast(error instanceof Error ? error.message : '事件状态更新失败', 'error');
        }
    }

    function openNotificationEvent(eventId: number) {
        const item = snapshotRef.current?.events.find((value) => value.id === eventId);
        if (item) openEvent(item);
    }

    useViewRevision(['radar'], () => {
        if (document.visibilityState === 'visible') void load(false, selectedCategoryRef.current, false);
    });

    useEffect(() => {
        mounted.current = true;
        void load();
        void api<NewsCategory[]>('/api/news/categories').then((values) => {
            if (mounted.current) setCategories([ALL_CATEGORY, RELATED_CATEGORY, ...values.filter((value) => value.code !== 'ALL')]);
        }).catch(() => undefined);
        return () => {
            mounted.current = false;
        };
    }, []);

    useEffect(() => {
        if (!initialEventId) return;
        let active = true;
        void api<RadarEventDetail>(`/api/research-radar/events/${initialEventId}`)
            .then((detail) => {
                if (active) setSelectedEvent(detail.event);
            })
            .catch(() => {
                const item = snapshotRef.current?.events.find((event) => event.id === initialEventId);
                if (active && item) setSelectedEvent(item);
            })
            .finally(() => {
                if (active) onInitialEventOpened?.();
            });
        return () => {
            active = false;
        };
    }, [initialEventId]);

    const normalizedQuery = query.trim().toLocaleLowerCase();
    const events = useMemo(() => (snapshot?.events ?? []).filter((event) =>
        !normalizedQuery || `${event.title} ${event.summary} ${event.watchlistExplanation}`.toLocaleLowerCase().includes(normalizedQuery)
    ), [normalizedQuery, snapshot]);
    const contextSnapshot = baseSnapshot ?? snapshot;
    const contextEvents = baseSnapshot?.events ?? [];
    const stateCounts: Record<RadarStateFilter, number> = {
        ALL: contextEvents.filter((item) => matchesRadarState(item, 'ALL')).length,
        UNREAD: contextEvents.filter((item) => matchesRadarState(item, 'UNREAD')).length,
        FOLLOWED: followedCount,
        LATER: contextEvents.filter((item) => matchesRadarState(item, 'LATER')).length,
        IGNORED: contextEvents.filter((item) => matchesRadarState(item, 'IGNORED')).length
    };
    const radarRefreshing = contextSnapshot?.productionStatus?.running || contextSnapshot?.warnings?.some((warning) => warning.includes('后台生产') || warning.includes('雷达正在刷新')) || false;
    const productionFailed = contextSnapshot?.productionStatus?.status === 'FAILED';
    const productionStatusWarning = contextSnapshot?.productionStatus?.warning;
    const degradedTitle = [contextSnapshot?.warnings?.join('\n'), productionStatusWarning].filter(Boolean).join('\n');

    return (
        <section className="news-view radar-view" aria-label="研究雷达">
            <header className="news-command-bar radar-command-bar">
                <div className="news-command-copy">
                    <div className="news-live-label"><span aria-hidden="true"/> PERSONAL RESEARCH RADAR</div>
                    <h1>先看值得研究的事</h1>
                    <p>系统自动合并重复资讯，并用固定规则解释为什么值得关注。你不需要配置策略。</p>
                </div>
                <div className="news-sync-state" aria-live="polite">
                    <span>{contextSnapshot ? `${contextSnapshot.overview.eventCount} 件事 · ${contextSnapshot.overview.sourceCount} 个来源` : '连接中'}</span>
                    <strong>{contextSnapshot ? `更新于 ${formatTime(contextSnapshot.refreshedAt)}` : '等待首批资讯'}</strong>
                    {contextSnapshot?.productionStatus?.running ? <small>后台生产中 · 页面读取最近快照</small> : null}
                    <button type="button" className="ghost-button news-refresh" aria-label="刷新资讯"
                            onClick={() => void refreshRadar()} disabled={loading}>
                        {loading ? '同步中' : '立即刷新'}
                    </button>
                </div>
            </header>

            <nav className="news-category-rail" aria-label="雷达分类">
                {categories.map((category) => (
                    <button type="button" key={category.code}
                            className={selectedCategory === category.code ? 'active' : ''}
                            aria-pressed={selectedCategory === category.code}
                            onClick={() => switchCategory(category.code)}>{category.name}</button>
                ))}
            </nav>

            <div className="radar-overview" aria-label="雷达概览">
                <article><span>值得关注</span><strong>{contextSnapshot?.overview.eventCount ?? 0}</strong></article>
                <article><span>重点事件</span><strong>{contextSnapshot?.overview.highPriorityCount ?? 0}</strong></article>
                <article><span>与我相关</span><strong>{contextSnapshot?.overview.watchlistRelatedCount ?? 0}</strong></article>
                <label className="news-search"><span>检索</span><input type="search" aria-label="搜索资讯"
                                                                       placeholder="搜索公司、行业或事件" value={query}
                                                                       onChange={(e) => setQuery(e.target.value)}/></label>
            </div>
            <div className="radar-work-rail"><RadarStateFilters value={stateFilter} counts={stateCounts}
                                                                onChange={switchState}/><RadarNotificationPanel
                hint={contextEvents.reduce((sum, item) => sum + (item.unreadNotificationCount ?? 0), 0)}
                onOpenEvent={openNotificationEvent}/></div>

            {(contextSnapshot?.warnings?.length || productionFailed || productionStatusWarning) ?
                <div className="news-degraded" role="status" title={degradedTitle}><span
                    aria-hidden="true">!</span>{productionFailed ? '雷达最近一次生产失败，当前展示此前快照' : radarRefreshing ? '雷达正在后台生产，当前展示最近一次热点快照' : productionStatusWarning ? '部分来源本次未更新，已展示最近结果' : '实时来源暂不可用，当前展示最近一次雷达结果'}
                </div> : null}

            <div className="news-board radar-board radar-board-single" data-testid="research-radar-board">
                <section className="news-flash-panel radar-focus-panel" aria-labelledby="radar-focus-heading">
                    <div className="news-section-heading">
                        <div><span>RESEARCH FIRST</span><h2 id="radar-focus-heading">高优先级事件</h2></div>
                        <strong>{events.length} 件</strong></div>
                    {loading && !snapshot ? <NewsSkeleton/> : events.length ?
                        <div className="radar-event-list">{events.map((item) => <RadarEventCard key={item.id}
                                                                                                event={item}
                                                                                                addToast={addToast}
                                                                                                onResearch={onResearch}
                                                                                                onOpen={openEvent}
                                                                                                onStateChange={(target, patch) => void updateState(target, patch)}/>)}</div> :
                        <EmptyState label="当前筛选下没有事件"/>}
                </section>
            </div>
            {selectedEvent ? <RadarEventDetailDrawer event={selectedEvent} onClose={() => setSelectedEvent(undefined)}
                                                     onEventChange={replaceEvent}/> : null}
        </section>
    );

    async function refreshRadar() {
        try {
            setLoading(true);
            const accepted = await api<boolean>('/api/research-radar/refresh', {method: 'POST'});
            if (accepted) {
                setMessage('雷达正在后台生产…');
                addToast('已提交雷达刷新', 'info');
            } else {
                setMessage('雷达正在后台生产，继续展示最近快照');
            }
        } catch (error) {
            const message = error instanceof Error ? error.message : '雷达刷新提交失败';
            setMessage(message);
            addToast(message, 'error');
        } finally {
            if (mounted.current) setLoading(false);
        }
    }
}

function NewsSkeleton() {
    return <div className="news-skeleton" aria-label="正在加载雷达"><span/><span/><span/></div>;
}

function EmptyState({label}: { label: string }) {
    return <div className="news-empty"><span aria-hidden="true">∅</span><p>{label}</p></div>;
}

function formatTime(value?: string) {
    const date = value ? new Date(value) : undefined;
    return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
    }).format(date) : '--:--';
}

function normalizeRadarSnapshot(snapshot: ResearchRadarSnapshot): ResearchRadarSnapshot {
    const events = [...new Map(snapshot.events.map((event) => [event.id, event])).values()];
    if (events.length === snapshot.events.length) return snapshot;
    return {
        ...snapshot,
        events,
        overview: {
            eventCount: events.length,
            highPriorityCount: events.filter((event) => event.priorityScore >= 75).length,
            watchlistRelatedCount: events.filter((event) => event.watchlistRelated).length,
            sourceCount: events.reduce((total, event) => total + event.sourceCount, 0)
        }
    };
}
