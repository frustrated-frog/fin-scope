import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';

type NewsKind = 'FLASH' | 'ARTICLE';

type NewsFeedItem = {
  id: string;
  kind: NewsKind;
  title: string;
  content: string;
  url?: string;
  publishedAt?: string;
  providerCode: string;
  sourceName: string;
  sourceTier: string;
};

type NewsFeedSnapshot = {
  items: NewsFeedItem[];
  warnings: string[];
  refreshedAt: string;
  sourceCount: number;
};

const REFRESH_INTERVAL_MS = 45_000;

export function NewsView({
  setMessage,
  addToast
}: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [snapshot, setSnapshot] = useState<NewsFeedSnapshot>();
  const [query, setQuery] = useState('');
  const [source, setSource] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const mounted = useRef(true);

  async function load(manual = false) {
    try {
      if (manual) setLoading(true);
      const next = await api<NewsFeedSnapshot>('/api/news?limit=100');
      if (!mounted.current) return;
      setSnapshot(next);
      setMessage(next.warnings.length ? '资讯已更新，部分来源暂不可用' : '资讯流已同步');
      if (manual) addToast('资讯已更新', 'success');
    } catch (error) {
      if (!mounted.current) return;
      const message = error instanceof Error ? error.message : '资讯刷新失败';
      setMessage(message);
      if (manual) addToast(message, 'error');
    } finally {
      if (mounted.current) setLoading(false);
    }
  }

  useEffect(() => {
    mounted.current = true;
    void load();
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load();
    }, REFRESH_INTERVAL_MS);
    return () => {
      mounted.current = false;
      window.clearInterval(timer);
    };
  }, []);

  const sources = useMemo(() => Array.from(new Set(snapshot?.items.map((item) => item.sourceName) ?? [])), [snapshot]);
  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return (snapshot?.items ?? []).filter((item) => {
      const matchesSource = source === 'ALL' || item.sourceName === source;
      const haystack = `${item.title} ${item.content} ${item.sourceName}`.toLocaleLowerCase();
      return matchesSource && (!normalized || haystack.includes(normalized));
    });
  }, [query, snapshot, source]);
  const flashes = filtered.filter((item) => item.kind === 'FLASH');
  const articles = filtered.filter((item) => item.kind === 'ARTICLE');
  const latest = flashes[0] ?? articles[0];

  return (
    <section className="news-view" aria-label="市场资讯">
      <header className="news-command-bar">
        <div className="news-command-copy">
          <div className="news-live-label"><span aria-hidden="true" /> LIVE MARKET WIRE</div>
          <h1>市场正在发生</h1>
          <p>{latest?.title ?? '正在连接公开资讯来源…'}</p>
        </div>
        <div className="news-sync-state" aria-live="polite">
          <span>{snapshot ? `${snapshot.sourceCount} 个独立来源` : '连接中'}</span>
          <strong>{snapshot ? `更新于 ${formatTime(snapshot.refreshedAt, true)}` : '等待首批资讯'}</strong>
          <button type="button" className="ghost-button news-refresh" aria-label="刷新资讯" onClick={() => void load(true)} disabled={loading}>
            {loading ? '同步中' : '立即刷新'}
          </button>
        </div>
      </header>

      <div className="news-filter-rail">
        <label className="news-search">
          <span>检索</span>
          <input type="search" aria-label="搜索资讯" placeholder="搜索公司、行业或事件" value={query} onChange={(event) => setQuery(event.target.value)} />
        </label>
        <div className="news-source-filter" role="group" aria-label="资讯来源">
          <button type="button" className={source === 'ALL' ? 'active' : ''} onClick={() => setSource('ALL')}>全部来源</button>
          {sources.map((item) => (
            <button type="button" key={item} className={source === item ? 'active' : ''} onClick={() => setSource(item)}>{item}</button>
          ))}
        </div>
      </div>

      {snapshot?.warnings.length ? (
        <div className="news-degraded" role="status" title={snapshot.warnings.join('\n')}>
          <span aria-hidden="true">!</span>
          部分来源暂不可用，已展示可用资讯
        </div>
      ) : null}

      <div className="news-board">
        <section className="news-flash-panel" aria-labelledby="news-flash-heading">
          <div className="news-section-heading">
            <div><span>01 · LIVE SIGNAL</span><h2 id="news-flash-heading">实时快讯</h2></div>
            <strong>{flashes.length} 条</strong>
          </div>
          {loading && !snapshot ? <NewsSkeleton /> : flashes.length ? (
            <div className="news-timeline" role="feed" aria-label="实时快讯时间线">
              {flashes.map((item, index) => <FlashItem key={item.id} item={item} latest={index === 0} />)}
            </div>
          ) : <EmptyState label="没有匹配的实时快讯" />}
        </section>

        <aside className="news-depth-panel" role="region" aria-label="深度资讯">
          <div className="news-section-heading">
            <div><span>02 · READ DEEPER</span><h2>要闻精华</h2></div>
            <strong>{articles.length} 篇</strong>
          </div>
          <div className="news-depth-list">
            {articles.length ? articles.map((item) => <ArticleCard key={item.id} item={item} />) : <EmptyState label="暂无匹配的深度资讯" />}
          </div>
        </aside>
      </div>
    </section>
  );
}

function FlashItem({ item, latest }: { item: NewsFeedItem; latest: boolean }) {
  const content = (
    <>
      <div className="news-flash-meta"><span>{item.sourceName}</span><small>{item.sourceTier}</small>{latest ? <em>NEW</em> : null}</div>
      <h3>{item.title}</h3>
      <p>{item.content}</p>
    </>
  );
  return (
    <article className={latest ? 'news-flash-item is-latest' : 'news-flash-item'}>
      <time dateTime={item.publishedAt}>{formatTime(item.publishedAt)}<small>{formatDate(item.publishedAt)}</small></time>
      <span className="news-pulse-dot" aria-hidden="true" />
      {item.url ? <a href={item.url} target="_blank" rel="noreferrer" className="news-flash-content">{content}</a> : <div className="news-flash-content">{content}</div>}
    </article>
  );
}

function ArticleCard({ item }: { item: NewsFeedItem }) {
  const body = <><div className="news-card-meta"><span>{item.sourceName}</span><time dateTime={item.publishedAt}>{formatDateTime(item.publishedAt)}</time></div><h3>{item.title}</h3><p>{item.content}</p><span className="news-card-action">阅读原文 <b aria-hidden="true">↗</b></span></>;
  return item.url
    ? <a className="news-depth-card" href={item.url} target="_blank" rel="noreferrer">{body}</a>
    : <article className="news-depth-card">{body}</article>;
}

function NewsSkeleton() {
  return <div className="news-skeleton" aria-label="正在加载资讯"><span /><span /><span /></div>;
}

function EmptyState({ label }: { label: string }) {
  return <div className="news-empty"><span aria-hidden="true">∅</span><p>{label}</p></div>;
}

function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string, seconds = false) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', ...(seconds ? { second: '2-digit' as const } : {}), hour12: false }).format(date) : '--:--';
}
function formatDate(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date).replace('/', '.') : '--.--';
}
function formatDateTime(value?: string) { return `${formatDate(value)} ${formatTime(value)}`; }
