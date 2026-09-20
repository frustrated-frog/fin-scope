import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { useViewRevision } from '../../shared/api/useViewRevision';
import { dateTime } from '../investment-observation/reactionTypes';
import { NewsReportDrawer } from './NewsReportDrawer';
import { initialQuery, queryParams } from './newsWindowTypes';
import type { NewsPage, NewsQuery, NewsReport, SavedFilter } from './newsWindowTypes';
import './newsWindow.css';
import { FlowField } from '../../shared/visuals/fluid/FlowField';

export function LiveNewsPanel({
  setMessage,
  addToast,
  onOpenMajorEvents,
}: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onOpenMajorEvents?: () => void;
}) {
  const [query, setQuery] = useState<NewsQuery>(initialQuery);
  const [draft, setDraft] = useState(initialQuery);
  const [page, setPage] = useState<NewsPage>();
  const [pending, setPending] = useState<NewsPage>();
  const [filters, setFilters] = useState<SavedFilter[]>([]);
  const [filterName, setFilterName] = useState('');
  const [categories, setCategories] = useState<Array<{ code: string; name: string }>>([]);
  const [selected, setSelected] = useState<NewsReport>();
  const [savedItems, setSavedItems] = useState<Set<string>>(new Set());
  const [compact, setCompact] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [syncing, setSyncing] = useState(false);
  const request = useRef(0);
  const pageRef = useRef<NewsPage>();
  const queryRef = useRef(query);
  queryRef.current = query;

  async function load(target: NewsQuery, background = false) {
    const sequence = ++request.current;
    if (!background) {
      setLoading(true);
    }
    try {
      const next = await api<NewsPage>(`/api/news/window?${queryParams(target)}`);
      if (!next || !Array.isArray(next.items)) {
        throw new Error('资讯接口未返回有效列表，请确认后端已更新后重试');
      }
      if (sequence !== request.current) {
        return;
      }
      if (background && pageRef.current) {
        const before = pageRef.current;
        const changed =
          before.total !== next.total ||
          before.items.map((item) => `${item.id}:${item.contentVersion}`).join('|') !==
            next.items.map((item) => `${item.id}:${item.contentVersion}`).join('|');
        if (changed) {
          setPending(next);
        }
      } else {
        pageRef.current = next;
        setPage(next);
        setPending(undefined);
      }
      setError('');
      setMessage('已读取本地新闻窗口');
    } catch (cause) {
      if (sequence === request.current) {
        setError(cause instanceof Error ? cause.message : '新闻读取失败，请重试');
      }
    } finally {
      if (sequence === request.current) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void load(query);
    return () => {
      request.current++;
    };
  }, [query]);

  useEffect(() => {
    let active = true;
    void Promise.all([
      api<SavedFilter[]>('/api/news/window/filters'),
      api<Array<{ code: string; name: string }>>('/api/news/categories'),
    ])
      .then(([saved, enabled]) => {
        if (active) {
          setFilters(Array.isArray(saved) ? saved : []);
          setCategories(Array.isArray(enabled) ? enabled : []);
        }
      })
      .catch(() => {
        if (active) {
          addToast('保存的筛选或分类暂时无法读取，可继续浏览资讯', 'info');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useViewRevision(['news'], () => {
    void api<SavedFilter[]>('/api/news/window/filters')
      .then((values) => {
        if (Array.isArray(values)) {
          setFilters(values);
        }
      })
      .catch(() => {
        /* Keep the last known counts if the backend is temporarily unavailable. */
      });
    void load({ ...queryRef.current, page: 0, asOfSequence: 0, asOfTime: undefined }, true);
  });

  function apply(next: NewsQuery) {
    setDraft(next);
    setPending(undefined);
    setQuery({ ...next, page: 0, asOfSequence: 0, asOfTime: undefined });
  }

  function markRead(id: string, version: number) {
    const update = (current?: NewsPage) =>
      current
        ? {
            ...current,
            items: current.items.map((item) =>
              item.id === id
                ? {
                    ...item,
                    readVersion: Math.max(item.readVersion, version),
                    unread: version < item.contentVersion,
                  }
                : item,
            ),
          }
        : current;
    setPage(update);
    pageRef.current = update(pageRef.current);
  }

  async function saveFilter() {
    try {
      const saved = await api<SavedFilter>('/api/news/window/filters', {
        method: 'POST',
        body: JSON.stringify({ name: filterName, query }),
      });
      setFilters((current) => [...current, saved]);
      setFilterName('');
      addToast('筛选已保存', 'success');
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '保存失败', 'error');
    }
  }

  async function removeFilter(id: string) {
    try {
      await api(`/api/news/window/filters/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      });
      setFilters((current) => current.filter((item) => item.id !== id));
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '删除失败', 'error');
    }
  }

  async function refreshSources() {
    setSyncing(true);
    try {
      const accepted = await api<boolean>('/api/news/refresh', {
        method: 'POST',
      });
      addToast(accepted ? '已开始后台同步，新资讯到达后会提示' : '后台正在同步，请稍候', 'info');
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '同步提交失败', 'error');
    } finally {
      setSyncing(false);
    }
  }


  async function saveMajorEvent(item: NewsReport) {
    try {
      await api('/api/major-events', { method: 'POST', body: JSON.stringify({
        originType: 'NEWS_ITEM', originKey: item.id,
        occurredDate: (item.publishedAt ?? item.firstSeenAt).slice(0, 10),
      }) });
      setSavedItems((current) => new Set(current).add(item.id));
      addToast('已记入大事记', 'success');
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '记入大事记失败', 'error');
    }
  }

  const flashes = (page?.items ?? []).filter((item) => item.kind !== 'ARTICLE');
  const articles = (page?.items ?? []).filter((item) => item.kind === 'ARTICLE');

  function story(item: NewsReport, depth: boolean, index: number) {
    const content = <>
      <div className={depth ? 'news-card-meta' : 'news-flash-meta'}>
        <span>{item.sourceName}</span><small>{item.sourceTier}</small>
        {item.unread ? <em>未读</em> : null}
        {item.contentVersion > 1 ? <em>原文更新 · v{item.contentVersion}</em> : null}
      </div>
      <h3>{highlight(item.title, query.query)}</h3>
      <p>{highlight(item.content, query.query)}</p>
      {depth ? <span className="news-card-action">阅读详情与原文 <b aria-hidden="true">↗</b></span> : null}
    </>;
    const body = <>
      <button type="button" className="news-story-open news-item-link" aria-label={`阅读资讯：${item.title}`} onClick={() => setSelected(item)}>{content}</button>
      <button type="button" className={`major-event-save${savedItems.has(item.id) ? ' is-saved' : ''}`}
        disabled={savedItems.has(item.id)} aria-label={`记入大事记：${item.title}`} onClick={() => void saveMajorEvent(item)}>
        {savedItems.has(item.id) ? '✓ 已记入大事记' : '+ 记入大事记'}
      </button>
      <div className="news-classification"><span>{item.categoryName ?? '未归类'}</span>
        <button type="button" className="ghost-button" onClick={() => setSelected(item)}>详情与分类</button>
      </div>
    </>;
    return depth
      ? <article key={item.id} className="news-depth-card" data-flow-surface={item.categoryCode || 'review'}>{body}</article>
      : <article key={item.id} className={index === 0 ? 'news-flash-item is-latest' : 'news-flash-item'}>
          <time dateTime={item.publishedAt}>{dateTime(item.publishedAt).slice(-5)}<small>{dateTime(item.publishedAt).slice(5, 10)}</small></time>
          <span className="news-pulse-dot" aria-hidden="true" />
          <div className="news-flash-content" data-flow-surface={item.categoryCode || 'active'}>{body}</div>
        </article>;
  }

  return (
    <FlowField mode="workspace" label="市场资讯面板" className="glass-workspace">
    <section className={`news-view${compact ? ' is-compact' : ''}`} aria-label="市场资讯">
      <header className="news-command-bar" data-flow-surface="TECHNOLOGY">
        <div className="news-command-copy">
          <div className="news-live-label"><span aria-hidden="true" /> LIVE MARKET WIRE</div>
          <h1>市场正在发生</h1>
          <p>{page?.items[0]?.title ?? '追踪原始报道、内容更新与股票反应'}</p>
        </div>
        <div className="news-sync-state" aria-live="polite">
          <span>{page ? `${page.sources.length} 个资讯来源` : '连接中'}</span>
          <strong>{page?.asOfTime ? `查询时间 ${dateTime(page.asOfTime)}` : '等待首批资讯'}</strong>
          <button type="button" className="ghost-button news-refresh" aria-label="刷新资讯" disabled={syncing} onClick={() => void refreshSources()}>
            {syncing ? '提交中…' : '立即刷新'}
          </button>
          <button type="button" className="major-events-link" onClick={onOpenMajorEvents}>查看大事记 <span aria-hidden="true">→</span></button>
        </div>
      </header>
      {page?.sourceHealth?.length ? <details className="news-source-health">
        <summary>
          <span className="news-health-title"><i aria-hidden="true" />来源同步</span>
          <span className="news-health-summary">
            {page.sourceHealth.every((item) => item.status === 'HEALTHY')
              ? `${page.sourceHealth.length} 个渠道运行正常`
              : `${page.sourceHealth.filter((item) => item.status === 'HEALTHY').length} / ${page.sourceHealth.length} 个渠道正常`}
          </span>
          <span className="news-health-toggle">详情 <span aria-hidden="true">⌄</span></span>
        </summary>
        <div className="news-health-grid">
          {page.sourceHealth.map((item) => <div className="news-health-item" key={item.providerCode}>
            <div><strong>{providerLabel(item.providerCode)}</strong>
              <span className={item.status === 'HEALTHY' ? 'news-health-ok' : 'news-health-warning'}>
                {({ HEALTHY: '已同步', DEGRADED: '保留旧资讯', UNAVAILABLE: '暂不可用', WAITING: '等待同步' } as Record<string, string>)[item.status] ?? '状态未知'}
              </span>
            </div>
            <span>最近成功 <time>{item.lastSuccessAt ? dateTime(item.lastSuccessAt) : '暂无'}</time></span>
            {item.status !== 'HEALTHY' && item.lastAttemptAt
              ? <span>最近尝试 <time>{dateTime(item.lastAttemptAt)}</time></span> : null}
          </div>)}
        </div>
      </details> : null}

      <nav className="news-category-rail" aria-label="资讯分类">
        {[{ code: 'ALL', name: '全部' }, ...categories, { code: 'UNCLASSIFIED', name: '未归类' }].map((category) => (
          <button type="button" key={category.code} className={query.category === category.code ? 'active' : ''}
            aria-pressed={query.category === category.code} onClick={() => apply({ ...query, category: category.code })}>
            <span>{category.name}</span><b>{page?.categoryCounts?.[category.code] ?? 0}</b>
          </button>
        ))}
      </nav>
      <form className="news-filter-rail" onSubmit={(event) => { event.preventDefault(); apply(draft); }}>
        <div className="news-search-control">
          <label className="news-search"><span aria-hidden="true">⌕</span>
            <input type="search" aria-label="搜索资讯" value={draft.query} placeholder="搜索公司、行业或事件"
              maxLength={100} onChange={(event) => setDraft({ ...draft, query: event.target.value })} />
          </label>
          <button type="submit" className="news-search-submit">检索</button>
        </div>
        <label className="news-time-filter"><span>时间范围</span>
          <select aria-label="资讯时间范围" value={draft.hours} onChange={(event) => apply({ ...draft, hours: Number(event.target.value) })}>
            {[6, 12, 24, 36].map((value) => <option key={value} value={value}>最近 {value} 小时</option>)}
          </select>
        </label>
        <div className="news-source-filter" role="group" aria-label="资讯来源">
          {['ALL', ...(page?.sources ?? [])].map((source) => <button type="button" key={source}
            className={query.source === source ? 'active' : ''} onClick={() => apply({ ...draft, source })}>
            {source === 'ALL' ? '全部来源' : providerLabel(source)}
          </button>)}
        </div>
        <details className="news-advanced-filters"><summary>更多筛选</summary>
          <div>
            <label>排除词<input value={draft.exclude} placeholder="不包含的关键词" maxLength={100} onChange={(event) => setDraft({ ...draft, exclude: event.target.value })} /></label>
            <label>类型<select value={draft.kind} onChange={(event) => setDraft({ ...draft, kind: event.target.value })}>
              <option value="ALL">全部内容</option><option value="FLASH">实时快讯</option><option value="ARTICLE">要闻文章</option>
            </select></label>
            <label className="news-unread-filter"><input type="checkbox" checked={draft.unreadOnly} onChange={(event) => setDraft({ ...draft, unreadOnly: event.target.checked })} />只看未读</label>
            <button type="button" className="ghost-button" aria-pressed={compact} onClick={() => setCompact(!compact)}>{compact ? '舒适阅读' : '紧凑阅读'}</button>
          </div>
        </details>
      </form>
      <div className="news-window-saved">
        <span>我的筛选</span>
        {filters.map((filter) => (
          <span className="news-window-filter-chip" key={filter.id}>
            <button type="button" onClick={() => apply(filter.query)}>
              {filter.name}
              {filter.unreadCount ? (
                <span className="news-filter-unread" aria-label={`${filter.unreadCount} 条未读`}>
                  {filter.unreadCount}
                </span>
              ) : null}
            </button>
            <button type="button" aria-label={`删除筛选：${filter.name}`} onClick={() => void removeFilter(filter.id)}>
              ×
            </button>
          </span>
        ))}
        <details>
          <summary>保存当前筛选</summary>
          <div>
            <input
              aria-label="筛选名称"
              placeholder="例如：芯片订单"
              maxLength={40}
              value={filterName}
              onChange={(event) => setFilterName(event.target.value)}
            />
            <button type="button" disabled={!filterName.trim()} onClick={() => void saveFilter()}>
              保存
            </button>
          </div>
        </details>
      </div>
      <nav className="news-pagination" aria-label="资讯分页">
        <span>
          共 {page?.total ?? 0} 条 · 第 {(page?.page ?? 0) + 1} / {Math.max(1, Math.ceil((page?.total ?? 0) / query.size))} 页
        </span>
        <div className="news-page-actions">
        <button
          type="button"
          disabled={loading || !page || page.page === 0}
          onClick={() =>
            setQuery({
              ...query,
              page: (page?.page ?? 0) - 1,
              asOfSequence: page?.asOfSequence ?? 0,
              asOfTime: page?.asOfTime,
            })
          }
        >
          上一页
        </button>
        <button
          type="button"
          disabled={loading || !page || (page.page + 1) * page.size >= page.total}
          onClick={() =>
            setQuery({
              ...query,
              page: (page?.page ?? 0) + 1,
              asOfSequence: page?.asOfSequence ?? 0,
              asOfTime: page?.asOfTime,
            })
          }
        >
          下一页
        </button>
        </div>
      </nav>
      {error ? (
        <div className="news-window-error" role="alert">
          {error}
          <button type="button" onClick={() => void load(query)}>
            重试
          </button>
        </div>
      ) : null}
      {pending ? (
        <button
          type="button"
          className="news-window-update"
          onClick={() => {
            setQuery({
              ...query,
              page: 0,
              asOfSequence: 0,
              asOfTime: undefined,
            });
          }}
        >
          有新报道或内容更新，点击查看
        </button>
      ) : null}

      <div className="news-board" aria-busy={loading}>
        <section className="news-flash-panel" aria-labelledby="news-flash-heading">
          <div className="news-section-heading"><div><span>01 · LIVE SIGNAL</span><h2 id="news-flash-heading">实时快讯</h2></div><strong>{flashes.length} 条</strong></div>
          {loading && !page ? <div className="news-skeleton" aria-label="正在加载资讯"><span /><span /><span /></div>
            : flashes.length ? <div className="news-timeline" role="feed" aria-label="实时快讯时间线">{flashes.map((item, index) => story(item, false, index))}</div>
            : <div className="news-empty"><p>没有匹配的实时快讯</p></div>}
        </section>
        <aside className="news-depth-panel" role="region" aria-label="深度资讯">
          <div className="news-section-heading"><div><span>02 · READ DEEPER</span><h2>要闻精华</h2></div><strong>{articles.length} 篇</strong></div>
          <div className="news-depth-list">{articles.length ? articles.map((item, index) => story(item, true, index))
            : <div className="news-empty"><p>暂无匹配的深度资讯</p></div>}</div>
        </aside>
      </div>
      {selected ? (
        <NewsReportDrawer
          key={selected.id}
          item={selected}
          categories={categories}
          onClose={() => setSelected(undefined)}
          onOpen={setSelected}
          onRead={markRead}
          onChanged={() => void load(query)}
          addToast={addToast}
        />
      ) : null}
    </section>
    </FlowField>
  );
}

function highlight(text: string, query: string) {
  const offset = query ? text.toLocaleLowerCase().indexOf(query.toLocaleLowerCase()) : -1;
  if (offset < 0) {
    return text;
  }
  return (
    <>
      {text.slice(0, offset)}
      <mark>{text.slice(offset, offset + query.length)}</mark>
      {text.slice(offset + query.length)}
    </>
  );
}

function providerLabel(code: string) {
  if (code.startsWith('CLS')) {
    return code.endsWith('_DIGEST') ? '财联社 · 要闻' : '财联社';
  }
  if (code.startsWith('THS')) {
    return code.endsWith('_DIGEST') ? '同花顺 · 要闻' : '同花顺';
  }
  if (code.startsWith('EASTMONEY')) {
    return code.endsWith('_DIGEST') ? '东方财富 · 要闻' : '东方财富';
  }
  return code;
}
