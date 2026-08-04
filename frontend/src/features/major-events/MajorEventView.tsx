import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MajorEvent } from '../../shared/types';

export function MajorEventView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<MajorEvent[]>([]);
  const [originType, setOriginType] = useState('');
  const load = async (type = originType) => { try { setItems(await api<MajorEvent[]>(`/api/major-events${type ? `?originType=${type}` : ''}`)); } catch (e) { addToast(e instanceof Error ? e.message : '大事记加载失败', 'error'); } };
  useEffect(() => { void load(''); }, []);
  const groups = items.reduce<Record<string, MajorEvent[]>>((all, item) => { const key = item.occurredDate.slice(0, 7); (all[key] ??= []).push(item); return all; }, {});
  return <section className="major-events"><header><p className="eyebrow">Historical market record</p><h3>大事记</h3><p>按事件发生时间回看重要市场事件与当时判断。</p><label>来源类型<select aria-label="来源类型" value={originType} onChange={(e) => { setOriginType(e.target.value); void load(e.target.value); }}><option value="">全部来源</option><option value="NEWS_ITEM">实时资讯</option><option value="ARTICLE">文章</option><option value="RADAR_EVENT">研究雷达</option></select></label></header>{Object.entries(groups).map(([month, values]) => <section key={month}><h4>{month.replace('-', ' 年 ')} 月</h4>{values.map((item) => <article key={item.id}><time>{item.occurredDate}</time><div><span>{item.originType} · {item.categoryCode || '未分类'}</span><h5>{item.title}</h5><p>{item.summary}</p>{item.note && <blockquote>当时判断：{item.note}</blockquote>}{item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">查看来源</a>}</div></article>)}</section>)}</section>;
}
