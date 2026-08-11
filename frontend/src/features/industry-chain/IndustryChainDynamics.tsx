import type { IndustryChainEventFeed, IndustryChainEventItem, IndustryChainGraph } from './industryChainTypes';

const DIRECTION_LABELS: Record<string, string> = {
  POSITIVE: '正向', NEGATIVE: '负向', MIXED: '多空交织', UNCERTAIN: '待验证'
};
const MECHANISM_LABELS: Record<string, string> = {
  SUPPLY: '供给', DEMAND: '需求', PRICE: '价格', CAPACITY: '产能', POLICY: '政策', ORDER: '订单', TECHNOLOGY: '技术'
};
const HORIZON_LABELS: Record<string, string> = { SHORT: '短期', MEDIUM: '中期', LONG: '长期' };

export function IndustryChainDynamics({
  graph, feed, selectedEventId, hours, loading,
  onSelectEvent, onHoursChange, onRefresh, onOpenNewsEvent
}: {
  graph: IndustryChainGraph;
  feed?: IndustryChainEventFeed;
  selectedEventId?: number;
  hours: number;
  loading: boolean;
  onSelectEvent: (eventId: number) => void;
  onHoursChange: (hours: number) => void;
  onRefresh: () => void;
  onOpenNewsEvent: (eventId: number) => void;
}) {
  const selected = feed?.events.find((event) => event.eventId === selectedEventId) ?? feed?.events[0];
  return (
    <aside className="ic-dynamics" aria-label="链上动态">
      <header className="ic-dynamics-head">
        <div><span>Live chain pulse</span><strong>链上动态</strong></div>
        <button type="button" onClick={onRefresh} disabled={loading} aria-label="刷新链上动态">↻</button>
      </header>
      <div className="ic-window-switch" aria-label="动态时间范围">
        {[{ value: 24, label: '24 小时' }, { value: 168, label: '7 天' }, { value: 720, label: '30 天' }].map((item) => (
          <button type="button" key={item.value} className={hours === item.value ? 'is-active' : ''}
            aria-pressed={hours === item.value} onClick={() => onHoursChange(item.value)}>{item.label}</button>
        ))}
      </div>
      {loading && !feed ? <div className="ic-dynamics-state"><i />正在映射事件影响…</div> : null}
      {!loading && feed && feed.events.length === 0 ? (
        <div className="ic-dynamics-empty"><span>NO SIGNAL</span><strong>时间窗内暂无链上动态</strong><p>这里直接复用 News Wire 已聚合的事件，不会为了填满页面制造弱关联。</p></div>
      ) : null}
      {feed?.events.length ? (
        <>
          <div className="ic-event-stream">
            {feed.events.map((event) => (
              <button type="button" key={event.eventId}
                className={event.eventId === selected?.eventId ? 'is-active' : ''}
                onClick={() => onSelectEvent(event.eventId)}>
                <time>{formatTime(event.lastSeenAt)}</time>
                <span className={`ic-event-direction is-${event.impact.direction.toLocaleLowerCase()}`}>
                  {DIRECTION_LABELS[event.impact.direction]}
                </span>
                <strong>{event.title}</strong>
                <small>{event.sourceCount} 个来源 · 热度 {event.hotspotScore}</small>
              </button>
            ))}
          </div>
          {selected ? <EventDetail graph={graph} event={selected} onOpenNewsEvent={onOpenNewsEvent} /> : null}
        </>
      ) : null}
    </aside>
  );
}

function EventDetail({ graph, event, onOpenNewsEvent }: {
  graph: IndustryChainGraph;
  event: IndustryChainEventItem;
  onOpenNewsEvent: (eventId: number) => void;
}) {
  const nodeNames = new Map(graph.nodes.map((node) => [node.nodeKey, node.name]));
  return (
    <section className="ic-event-detail" aria-label="事件影响详情">
      <div className="ic-impact-tags">
        <span>{MECHANISM_LABELS[event.impact.mechanism]}</span>
        <span>{HORIZON_LABELS[event.impact.horizon]}</span>
        <span>{event.impact.confidence === 'HIGH' ? '高置信' : event.impact.confidence === 'MEDIUM' ? '中置信' : '低置信'}</span>
      </div>
      <p>{event.impact.impactSummary}</p>
      <div className="ic-propagation-path">
        <span>IMPACT PATH</span>
        <ol>{event.impact.pathNodeKeys.map((key) => <li key={key}>{nodeNames.get(key) ?? key}</li>)}</ol>
      </div>
      <button type="button" className="ic-open-news" aria-label="在 News Wire 查看"
        onClick={() => onOpenNewsEvent(event.eventId)}>
        在 News Wire 查看 <span>↗</span>
      </button>
    </section>
  );
}

function formatTime(value?: string) {
  if (!value) return '--:--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(5, 16).replace('T', ' ');
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
}
