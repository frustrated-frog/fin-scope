import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MajorEvent } from '../../shared/types';

const originLabel: Record<MajorEvent['originType'], string> = { NEWS_ITEM: '实时资讯', ARTICLE: '文章研究', RADAR_EVENT: '研究雷达' };

export function MajorEventView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<MajorEvent[]>([]); const [originType, setOriginType] = useState('');
  const load = async (type = originType) => { try { setItems(await api<MajorEvent[]>(`/api/major-events${type ? `?originType=${type}` : ''}`)); } catch (e) { addToast(e instanceof Error ? e.message : '大事记加载失败', 'error'); } };
  useEffect(() => { void load(''); }, []);
  const groups = useMemo(() => items.reduce<Record<string, MajorEvent[]>>((all, item) => { const key = item.occurredDate.slice(0, 7); (all[key] ??= []).push(item); return all; }, {}), [items]);
  return <section className="major-events"><header className="major-events-hero"><div><p className="eyebrow">PERSONAL MARKET ARCHIVE</p><h3>大事记</h3><p>把当时重要的事，留给未来的自己复盘。</p></div><div className="major-events-summary"><strong>{items.length}</strong><span>已记录大事</span></div></header><div className="major-events-toolbar"><label>查看范围<select aria-label="来源类型" value={originType} onChange={(e) => { setOriginType(e.target.value); void load(e.target.value); }}><option value="">全部来源</option><option value="NEWS_ITEM">实时资讯</option><option value="ARTICLE">文章研究</option><option value="RADAR_EVENT">研究雷达</option></select></label><span>按事件发生日排列</span></div>{Object.entries(groups).map(([month, values]) => <section key={month} className="major-event-month"><div className="major-event-month-heading"><h4>{month.replace('-', ' 年 ')} 月</h4><span>{values.length} 件</span></div>{values.map((item) => <article key={item.id} className="major-event-entry"><time><strong>{item.occurredDate.slice(-2)}</strong><span>{item.occurredDate.slice(5, 7)} 月</span></time><div className="major-event-copy"><span className="major-event-origin">{originLabel[item.originType]} · {item.categoryCode || '未分类'}</span><h5>{item.title}</h5>{item.summary && <p>{item.summary}</p>}{item.note && <blockquote>当时判断：{item.note}</blockquote>}{item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">查看当时来源 <span>↗</span></a>}</div></article>)}</section>)}{!items.length && <div className="major-events-empty"><strong>还没有记录的大事</strong><p>在资讯或雷达中点按「记入大事记」，事件会按发生日期归档在这里。</p></div>}</section>;
}
