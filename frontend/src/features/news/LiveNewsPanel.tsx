import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { useViewRevision } from '../../shared/api/useViewRevision';
import { dateTime } from '../investment-observation/reactionTypes';
import { NewsReportDrawer } from './NewsReportDrawer';
import { initialQuery, queryParams } from './newsWindowTypes';
import type { NewsPage, NewsQuery, NewsReport, SavedFilter } from './newsWindowTypes';
import './newsWindow.css';

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

  return (
    <section className={`news-window${compact ? ' is-compact' : ''}`} aria-label="市场资讯">
      <header className="news-window-header">
        <div>
          <span className="news-window-eyebrow">资讯观察</span>
          <h1>市场正在发生</h1>
          <p>追踪原始报道、内容更新与股票反应</p>
        </div>
        <div className="news-window-actions">
          <button type="button" onClick={onOpenMajorEvents}>
            查看大事记
          </button>
          <button type="button" disabled={syncing} onClick={() => void refreshSources()}>
            {syncing ? '提交中…' : '同步来源'}
          </button>
        </div>
      </header>
      <form
        className="news-window-filters"
        onSubmit={(event) => {
          event.preventDefault();
          apply(draft);
        }}
      >
        <label className="news-window-search">
          检索完整窗口
          <input
            type="search"
            aria-label="搜索资讯"
            value={draft.query}
            placeholder="公司、行业或事件关键词"
            onChange={(event) => setDraft({ ...draft, query: event.target.value })}
            maxLength={100}
          />
        </label>
        <label>
          排除词
          <input
            value={draft.exclude}
            placeholder="不包含的关键词"
            onChange={(event) => setDraft({ ...draft, exclude: event.target.value })}
            maxLength={100}
          />
        </label>
        <label>
          来源
          <select value={draft.source} onChange={(event) => setDraft({ ...draft, source: event.target.value })}>
            <option value="ALL">全部来源</option>
            {(page?.sources ?? []).map((source) => (
              <option key={source}>{source}</option>
            ))}
          </select>
        </label>
        <label>
          时间
          <select value={draft.hours} onChange={(event) => setDraft({ ...draft, hours: Number(event.target.value) })}>
            <option value={36}>最近36小时</option>
            <option value={24}>最近24小时</option>
            <option value={6}>最近6小时</option>
          </select>
        </label>
        <label>
          类型
          <select value={draft.kind} onChange={(event) => setDraft({ ...draft, kind: event.target.value })}>
            <option value="ALL">全部内容</option>
            <option value="FLASH">实时快讯</option>
            <option value="ARTICLE">要闻文章</option>
          </select>
        </label>
        <label className="news-window-check">
          <input
            type="checkbox"
            checked={draft.unreadOnly}
            onChange={(event) => setDraft({ ...draft, unreadOnly: event.target.checked })}
          />
          只看未读
        </label>
        <button type="submit" className="news-window-primary">
          检索
        </button>
      </form>
      <nav className="news-window-categories" aria-label="资讯分类">
        {[{ code: 'ALL', name: '全部' }, ...categories, { code: 'UNCLASSIFIED', name: '未归类' }].map((category) => (
          <button
            type="button"
            key={category.code}
            aria-pressed={query.category === category.code}
            onClick={() => apply({ ...query, category: category.code })}
          >
            {category.name}
            <span>{page?.categoryCounts?.[category.code] ?? 0}</span>
          </button>
        ))}
      </nav>
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
      <div className="news-window-status">
        <span>{page ? `找到 ${page.total} 条 · 按发布时间排序` : '正在读取新闻…'}</span>
        <button type="button" aria-pressed={compact} onClick={() => setCompact(!compact)}>
          {compact ? '舒适阅读' : '紧凑阅读'}
        </button>
      </div>
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
      <div className="news-window-list" aria-busy={loading}>
        {loading && !page ? <p className="news-window-empty">正在读取已采集新闻…</p> : null}
        {page?.items.map((item) => (
          <article key={item.id} className={`news-window-row${item.unread ? ' is-unread' : ''}`}>
            <div className="news-window-time">
              <time>{dateTime(item.publishedAt).slice(5)}</time>
              <span>{item.sourceName}</span>
            </div>
            <button type="button" className="news-window-story" onClick={() => setSelected(item)}>
              <div className="news-window-labels">
                {item.unread ? <span className="news-window-unread">未读</span> : null}
                <span>{item.categoryName ?? '未归类'}</span>
                {item.historicalBackfill ? <span>补充收录</span> : null}
                {item.contentVersion > 1 ? <span>原文更新 · v{item.contentVersion}</span> : null}
              </div>
              <h2>{highlight(item.title, query.query)}</h2>
              <p>{highlight(item.content, query.query)}</p>
            </button>
            <span className="news-window-chevron" aria-hidden="true">
              ↗
            </span>
          </article>
        ))}
        {page && !page.items.length ? (
          <div className="news-window-empty">
            <h2>没有匹配的新闻</h2>
            <p>尝试缩短关键词、放宽来源，或同步最新资讯。</p>
            <button type="button" onClick={() => apply(initialQuery)}>
              清除筛选
            </button>
          </div>
        ) : null}
      </div>
      <footer className="news-window-pagination">
        <span>
          第 {(page?.page ?? 0) + 1} 页 · 每页 {query.size} 条
        </span>
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
      </footer>
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
