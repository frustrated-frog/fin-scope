import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';

type NewsFeedItem = {
  id: string;
  kind: 'FLASH' | 'ARTICLE';
  title: string;
  content: string;
  url?: string;
  publishedAt?: string;
  providerCode: string;
  sourceName: string;
  sourceTier: string;
  categoryCode?: string;
  categoryName?: string;
  agentCategoryCode?: string;
  classificationConfidence?: number;
  classificationReason?: string;
  reviewStatus?: 'AUTO_CONFIRMED' | 'PENDING_REVIEW' | 'CONFIRMED' | 'CORRECTED';
  manuallyReviewed?: boolean;
  manualReason?: string;
};

type NewsFeedSnapshot = {
  items: NewsFeedItem[];
  warnings: string[];
  refreshedAt: string;
  sourceCount: number;
  categoryCounts?: Record<string, number>;
  unclassifiedCount?: number;
};

type NewsCategory = {
  code: string;
  name: string;
  classificationGuidance?: string;
  enabled?: boolean;
  displayOrder?: number;
};

const ALL_CATEGORY: NewsCategory = { code: 'ALL', name: '全部' };
const PENDING_REVIEW_CATEGORY: NewsCategory = { code: 'PENDING_REVIEW', name: '待确认' };
const REFRESH_INTERVAL_MS = 45_000;

export function LiveNewsPanel({ setMessage, addToast }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [snapshot, setSnapshot] = useState<NewsFeedSnapshot>();
  const [categories, setCategories] = useState<NewsCategory[]>([ALL_CATEGORY]);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [pendingSnapshot, setPendingSnapshot] = useState<NewsFeedSnapshot>();
  const [pendingCount, setPendingCount] = useState(0);
  const [query, setQuery] = useState('');
  const [source, setSource] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const mounted = useRef(true);
  const snapshotRef = useRef<NewsFeedSnapshot>();
  const selectedCategoryRef = useRef('ALL');
  const requestSequence = useRef(0);

  async function load(manual = false, polling = false, category = selectedCategoryRef.current) {
    const requestId = ++requestSequence.current;
    try {
      if (manual) setLoading(true);
      const response = await api<NewsFeedSnapshot>(`/api/news?category=${encodeURIComponent(category)}&limit=100`);
      const next: NewsFeedSnapshot = {
        ...response,
        items: Array.isArray(response?.items) ? response.items : [],
        warnings: Array.isArray(response?.warnings) ? response.warnings : [],
        refreshedAt: response?.refreshedAt ?? new Date().toISOString(),
        sourceCount: response?.sourceCount ?? 0
      };
      if (!mounted.current || requestId !== requestSequence.current || category !== selectedCategoryRef.current) return;
      const current = snapshotRef.current;
      const currentIds = new Set(current?.items.map((item) => item.id) ?? []);
      const added = polling && current ? next.items.filter((item) => !currentIds.has(item.id)).length : 0;
      if (added > 0) {
        setPendingSnapshot(next);
        setPendingCount(added);
      } else {
        snapshotRef.current = next;
        setSnapshot(next);
        setPendingSnapshot(undefined);
        setPendingCount(0);
      }
      setMessage(next.warnings.length ? '资讯已更新，部分来源暂不可用' : '资讯流已同步');
      if (manual) addToast('资讯已更新', 'success');
    } catch (error) {
      if (!mounted.current) return;
      const message = error instanceof Error ? error.message : '资讯刷新失败';
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
    setPendingSnapshot(undefined);
    setPendingCount(0);
    setQuery('');
    setSource('ALL');
    setLoading(true);
    void load(false, false, code);
  }

  function applyPendingSnapshot() {
    if (!pendingSnapshot) return;
    snapshotRef.current = pendingSnapshot;
    setSnapshot(pendingSnapshot);
    setPendingSnapshot(undefined);
    setPendingCount(0);
  }

  async function reviewClassification(itemId: string, categoryCode: string, reason: string) {
    try {
      await api('/api/news/classifications/review', {
        method: 'POST',
        body: JSON.stringify({ itemId, categoryCode, reason })
      });
      addToast('分类复核已保存', 'success');
      await load(false, false, selectedCategoryRef.current);
    } catch (error) {
      const message = error instanceof Error ? error.message : '分类复核保存失败';
      addToast(message, 'error');
      throw error;
    }
  }

  async function saveMajorEvent(item: NewsFeedItem) {
    try {
      await api('/api/major-events', { method: 'POST', body: JSON.stringify({ originType: 'NEWS_ITEM', originKey: item.id, title: item.title, summary: item.content, sourceName: item.sourceName, sourceUrl: item.url, categoryCode: item.categoryCode, occurredDate: item.publishedAt ? item.publishedAt.slice(0, 10) : new Date().toISOString().slice(0, 10) }) });
      addToast('已记入大事记', 'success');
    } catch (error) { addToast(error instanceof Error ? error.message : '记入大事记失败', 'error'); }
  }

  useEffect(() => {
    mounted.current = true;
    void load();
    void api<NewsCategory[]>('/api/news/categories')
      .then((values) => {
        if (mounted.current) setCategories([ALL_CATEGORY, ...values.filter((value) =>
          value.code !== 'ALL' && value.code !== 'PENDING_REVIEW'), PENDING_REVIEW_CATEGORY]);
      })
      .catch(() => undefined);
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(false, true, selectedCategoryRef.current);
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
  const businessCategories = categories.filter((category) =>
    category.code !== 'ALL' && category.code !== 'PENDING_REVIEW');

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

      <nav className="news-category-rail" aria-label="资讯分类">
        {categories.map((category) => (
          <button type="button" key={category.code} className={selectedCategory === category.code ? 'active' : ''}
            aria-pressed={selectedCategory === category.code} onClick={() => switchCategory(category.code)}>
            <span>{category.name}</span><b>{snapshot?.categoryCounts?.[category.code] ?? 0}</b>
          </button>
        ))}
        <span className="news-unclassified-count">待分类 {snapshot?.unclassifiedCount ?? 0}</span>
      </nav>

      <div className="news-filter-rail">
        <label className="news-search">
          <span>检索</span>
          <input type="search" aria-label="搜索资讯" placeholder="搜索公司、行业或事件" value={query} onChange={(event) => setQuery(event.target.value)} />
        </label>
        <div className="news-source-filter" role="group" aria-label="资讯来源">
          <button type="button" className={source === 'ALL' ? 'active' : ''} onClick={() => setSource('ALL')}>全部来源</button>
          {sources.map((item) => <button type="button" key={item} className={source === item ? 'active' : ''} onClick={() => setSource(item)}>{item}</button>)}
        </div>
      </div>

      {snapshot?.warnings.length ? <div className="news-degraded" role="status" title={snapshot.warnings.join('\n')}><span aria-hidden="true">!</span>部分来源暂不可用，已展示可用资讯</div> : null}
      {pendingCount > 0 ? <button type="button" className="news-update-notice" onClick={applyPendingSnapshot}>发现 {pendingCount} 条新资讯</button> : null}

      <div className="news-board">
        <section className="news-flash-panel" aria-labelledby="news-flash-heading">
          <div className="news-section-heading"><div><span>01 · LIVE SIGNAL</span><h2 id="news-flash-heading">实时快讯</h2></div><strong>{flashes.length} 条</strong></div>
          {loading && !snapshot ? <NewsSkeleton /> : flashes.length ? (
            <div className="news-timeline" role="feed" aria-label="实时快讯时间线">
              {flashes.map((item, index) => <FlashItem key={item.id} item={item} latest={index === 0}
                categories={businessCategories} onReview={reviewClassification} onSave={saveMajorEvent} />)}
            </div>
          ) : <EmptyState label="没有匹配的实时快讯" />}
        </section>

        <aside className="news-depth-panel" role="region" aria-label="深度资讯">
          <div className="news-section-heading"><div><span>02 · READ DEEPER</span><h2>要闻精华</h2></div><strong>{articles.length} 篇</strong></div>
          <div className="news-depth-list">{articles.length ? articles.map((item) => <ArticleCard key={item.id} item={item}
            categories={businessCategories} onReview={reviewClassification} onSave={saveMajorEvent} />) : <EmptyState label="暂无匹配的深度资讯" />}</div>
        </aside>
      </div>
    </section>
  );
}

function FlashItem({ item, latest, categories, onReview, onSave }: {
  item: NewsFeedItem;
  latest: boolean;
  categories: NewsCategory[];
  onReview: (itemId: string, categoryCode: string, reason: string) => Promise<void>;
  onSave: (item: NewsFeedItem) => void;
}) {
  const content = <><div className="news-flash-meta"><span>{item.sourceName}</span><small>{item.sourceTier}</small>{latest ? <em>NEW</em> : null}</div><h3>{item.title}</h3><p>{item.content}</p></>;
  return <article className={latest ? 'news-flash-item is-latest' : 'news-flash-item'}><time dateTime={item.publishedAt}>{formatTime(item.publishedAt)}<small>{formatDate(item.publishedAt)}</small></time><span className="news-pulse-dot" aria-hidden="true" /><div className="news-flash-content">{item.url ? <a href={item.url} target="_blank" rel="noreferrer" className="news-item-link">{content}</a> : content}<button type="button" onClick={() => onSave(item)} aria-label={`记入大事记：${item.title}`}>记入大事记</button><ClassificationReview item={item} categories={categories} onReview={onReview} /></div></article>;
}

function ArticleCard({ item, categories, onReview, onSave }: {
  item: NewsFeedItem;
  categories: NewsCategory[];
  onReview: (itemId: string, categoryCode: string, reason: string) => Promise<void>;
  onSave: (item: NewsFeedItem) => void;
}) {
  const body = <><div className="news-card-meta"><span>{item.sourceName}</span><time dateTime={item.publishedAt}>{formatDateTime(item.publishedAt)}</time></div><h3>{item.title}</h3><p>{item.content}</p><span className="news-card-action">阅读原文 <b aria-hidden="true">↗</b></span></>;
  return <article className="news-depth-card">{item.url ? <a className="news-item-link" href={item.url} target="_blank" rel="noreferrer">{body}</a> : body}<button type="button" onClick={() => onSave(item)} aria-label={`记入大事记：${item.title}`}>记入大事记</button><ClassificationReview item={item} categories={categories} onReview={onReview} /></article>;
}

function ClassificationReview({ item, categories, onReview }: {
  item: NewsFeedItem;
  categories: NewsCategory[];
  onReview: (itemId: string, categoryCode: string, reason: string) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [categoryCode, setCategoryCode] = useState(item.categoryCode ?? categories[0]?.code ?? '');
  const [reason, setReason] = useState(item.manualReason ?? '');
  const [saving, setSaving] = useState(false);
  if (!item.categoryCode) return <div className="news-classification is-pending">Agent 分类中</div>;
  const status = item.reviewStatus === 'PENDING_REVIEW' ? '待确认'
    : item.reviewStatus === 'CORRECTED' ? '已纠正'
      : item.reviewStatus === 'CONFIRMED' ? '已确认' : '已分类';
  const confidence = item.classificationConfidence == null ? '--' : `${Math.round(item.classificationConfidence * 100)}%`;

  async function save() {
    if (!categoryCode || saving) return;
    setSaving(true);
    try {
      await onReview(item.id, categoryCode, reason.trim());
      setOpen(false);
    } catch {
      // The parent keeps the current snapshot and reports the error.
    } finally {
      setSaving(false);
    }
  }

  return <div className={`news-classification ${item.reviewStatus === 'PENDING_REVIEW' ? 'needs-review' : ''}`}>
    <div className="news-classification-summary">
      <span>{item.categoryName ?? item.categoryCode}</span><b>{confidence}</b><em>{status}</em>
      {categories.length ? <button type="button" aria-label="确认或修正分类" onClick={() => setOpen((value) => !value)}>确认/修正</button> : null}
    </div>
    {item.classificationReason ? <p>{item.classificationReason}</p> : null}
    {open ? <div className="news-classification-form">
      <label>分类<select aria-label="调整分类" value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)}>{categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}</select></label>
      <label>备注<input aria-label="复核备注" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="可选：说明调整依据" /></label>
      <button type="button" onClick={() => void save()} disabled={saving}>{saving ? '保存中' : '保存分类'}</button>
    </div> : null}
  </div>;
}

function NewsSkeleton() { return <div className="news-skeleton" aria-label="正在加载资讯"><span /><span /><span /></div>; }
function EmptyState({ label }: { label: string }) { return <div className="news-empty"><span aria-hidden="true">∅</span><p>{label}</p></div>; }
function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string, seconds = false) { const date = parseDate(value); return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', ...(seconds ? { second: '2-digit' as const } : {}), hour12: false }).format(date) : '--:--'; }
function formatDate(value?: string) { const date = parseDate(value); return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date).replace('/', '.') : '--.--'; }
function formatDateTime(value?: string) { return `${formatDate(value)} ${formatTime(value)}`; }
