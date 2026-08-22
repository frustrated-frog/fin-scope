import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import type { MajorEvent } from '../../shared/types';

const originLabel: Record<MajorEvent['originType'], string> = {
  NEWS_ITEM: '实时资讯',
  ARTICLE: '文章研究',
  RADAR_EVENT: '研究雷达'
};

const originFilters: Array<{ value: '' | MajorEvent['originType']; label: string }> = [
  { value: '', label: '全部' },
  { value: 'NEWS_ITEM', label: '实时资讯' },
  { value: 'ARTICLE', label: '文章研究' },
  { value: 'RADAR_EVENT', label: '研究雷达' }
];

function formatArchiveDate(value?: string) {
  return value ? value.slice(0, 10).replace(/-/g, '.') : '—';
}

export function MajorEventView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<MajorEvent[]>([]);
  const [originType, setOriginType] = useState<'' | MajorEvent['originType']>('');
  const [archiveItems, setArchiveItems] = useState<MajorEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const load = async (type: '' | MajorEvent['originType']) => {
    setIsLoading(true);
    try {
      const nextItems = await api<MajorEvent[]>(`/api/major-events${type ? `?originType=${type}` : ''}`);
      setItems(nextItems);
      if (!type) {
        setArchiveItems(nextItems);
      }
    } catch (error) {
      addToast(error instanceof Error ? error.message : '大事记加载失败', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void load('');
  }, []);

  const sortedItems = useMemo(() => [...items].sort((left, right) => right.occurredDate.localeCompare(left.occurredDate)), [items]);
  const groups = useMemo(() => sortedItems.reduce<Record<string, MajorEvent[]>>((all, item) => {
    const key = item.occurredDate.slice(0, 7);
    (all[key] ??= []).push(item);
    return all;
  }, {}), [sortedItems]);
  const archiveStats = useMemo(() => {
    const referenceItems = originType ? items : archiveItems;
    return {
      count: referenceItems.length,
      monthCount: new Set(referenceItems.map(item => item.occurredDate.slice(0, 7))).size,
      latestDate: [...referenceItems].sort((left, right) => right.occurredDate.localeCompare(left.occurredDate))[0]?.occurredDate
    };
  }, [archiveItems, items, originType]);
  const sourceCounts = useMemo(() => archiveItems.reduce<Record<MajorEvent['originType'], number>>((counts, item) => {
    counts[item.originType] += 1;
    return counts;
  }, { NEWS_ITEM: 0, ARTICLE: 0, RADAR_EVENT: 0 }), [archiveItems]);

  const selectOrigin = (value: '' | MajorEvent['originType']) => {
    setOriginType(value);
    void load(value);
  };

  return (
    <section className="major-events">
      <header className="major-events-hero">
        <div className="major-events-hero-copy">
          <p className="major-events-kicker"><span aria-hidden="true" />Market archive · 大事记</p>
          <h3>市场记忆</h3>
          <p>市场不是一条新闻，而是一串在当时做出的判断。按发生日保存关键变化，给未来的自己留下可回看的坐标。</p>
        </div>
        <dl className="major-events-stats" aria-label="档案概览">
          <div>
            <dt>档案规模</dt>
            <dd>{archiveStats.count} <span>条记录</span></dd>
          </div>
          <div>
            <dt>时间跨度</dt>
            <dd>{archiveStats.monthCount} <span>个覆盖月份</span></dd>
          </div>
          <div>
            <dt>最新坐标</dt>
            <dd><span>最近记录</span> {formatArchiveDate(archiveStats.latestDate)}</dd>
          </div>
        </dl>
      </header>

      <div className="major-events-toolbar">
        <div>
          <strong>事件档案</strong>
          <span>{originType ? `正在查看${originLabel[originType]}` : '按发生时间倒序'}</span>
        </div>
        <div className="major-events-filters" role="group" aria-label="按来源筛选">
          {originFilters.map(filter => {
            const count = filter.value ? sourceCounts[filter.value] : archiveItems.length;
            return (
              <button
                type="button"
                key={filter.value || 'ALL'}
                aria-pressed={originType === filter.value}
                onClick={() => selectOrigin(filter.value)}
              >
                {filter.label} <span>{count}</span>
              </button>
            );
          })}
        </div>
      </div>

      {isLoading ? (
        <div className="major-events-loading" role="status">
          <span aria-hidden="true" />
          <p>正在整理时间坐标…</p>
        </div>
      ) : sortedItems.length ? (
        <div className="major-events-feed" role="feed" aria-label="大事记时间轴">
          {Object.entries(groups).map(([month, values]) => (
            <section key={month} className="major-event-month" aria-labelledby={`major-event-month-${month}`}>
              <div className="major-event-month-heading">
                <h4 id={`major-event-month-${month}`}>{month.slice(0, 4)} 年 {month.slice(5, 7)} 月</h4>
                <span>{values.length} 件归档</span>
              </div>
              <div className="major-event-month-track">
                {values.map(item => (
                  <article key={item.id} className={`major-event-entry major-event-entry--${item.originType.toLowerCase()}`}>
                    <time dateTime={item.occurredDate}>
                      <strong>{item.occurredDate.slice(-2)}</strong>
                      <span>{item.occurredDate.slice(5, 7)} / {item.occurredDate.slice(0, 4)}</span>
                    </time>
                    <span className="major-event-node" aria-hidden="true" />
                    <div className="major-event-copy">
                      <div className="major-event-meta">
                        <span className="major-event-origin">{originLabel[item.originType]}</span>
                        <span>{item.categoryCode || '未分类'}</span>
                        {item.sourceName && <span>{item.sourceName}</span>}
                      </div>
                      <h5>{item.title}</h5>
                      {item.summary && <p>{item.summary}</p>}
                      {item.note && (
                        <blockquote>
                          <span>当时判断</span>
                          {item.note}
                        </blockquote>
                      )}
                      {item.sourceUrl && (
                        <a href={item.sourceUrl} target="_blank" rel="noreferrer">
                          打开原始来源
                          <svg viewBox="0 0 16 16" aria-hidden="true">
                            <path d="M5 11 11 5M6 5h5v5" />
                          </svg>
                        </a>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      ) : (
        <div className="major-events-empty">
          <span className="major-events-empty-mark" aria-hidden="true">⌁</span>
          <strong>{originType ? `还没有${originLabel[originType]}记录` : '时间轴还没有第一个坐标'}</strong>
          <p>{originType ? '切换到其他来源，或继续从资讯与雷达中沉淀关键事件。' : '在资讯或雷达中点按「记入大事记」，关键事件会按发生日期归档在这里。'}</p>
        </div>
      )}
    </section>
  );
}
