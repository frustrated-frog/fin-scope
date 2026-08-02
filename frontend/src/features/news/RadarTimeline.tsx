import type { RadarTimelineEntry } from './researchRadarTypes';

export function RadarTimeline({ items = [] }: { items?: RadarTimelineEntry[] }) {
  return items.length ? <ol className="radar-event-timeline">{items.map((item) => <li key={item.id}>
    <time dateTime={item.occurredAt}>{format(item.occurredAt)}</time><div><strong>{item.title}</strong>{item.summary ? <p>{item.summary}</p> : null}</div>
  </li>)}</ol> : <p className="radar-empty-copy">时间线会在来源、证据或研究结论出现变化后更新。</p>;
}
function format(value: string) { const date=new Date(value);return Number.isNaN(date.getTime())?'--':new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(date); }
