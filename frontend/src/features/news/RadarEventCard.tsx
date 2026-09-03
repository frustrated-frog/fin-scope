import type { RadarEvent } from './researchRadarTypes';
import { api } from '../../shared/api/client';

export function RadarEventCard({ event, addToast, onResearch, onOpen, onStateChange }: {
  event: RadarEvent;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onResearch?: (eventId: number, question: string) => void;
  onOpen: (event: RadarEvent) => void;
  onStateChange?: (event: RadarEvent, patch: { followed?: boolean; disposition?: 'ACTIVE' | 'LATER' | 'IGNORED' }) => void;
}) {
  const reasons = event.reasons.filter((reason) => reason !== event.watchlistExplanation);

  return (
    <article className={`radar-event-card ${event.watchlistRelated ? 'is-related' : ''}`}>
      <div className="radar-event-score" aria-label={`研究优先级 ${event.priorityScore} 分`}>
        <strong>{event.priorityScore}</strong><span>研究优先级</span>
        {event.hotspotScore != null ? <small title={event.hotspotExplanation || '按来源广度、传播速度、来源权威、新意、跨源扩散和持续性计算'}>热点 {event.hotspotScore}</small> : null}
      </div>
      <div className="radar-event-body">
        <div className="radar-event-meta">
          <div className="radar-event-badges">
            <span className="radar-recommendation">{event.recommendation}</span>
            {event.hotspotLifecycleState ? <span className="radar-hotspot-state">热点 · {hotspotLifecycleLabel(event.hotspotLifecycleState)}</span> : null}
            {event.interpretationStatus === 'SUCCESS' ? <span className="radar-interpretation-ready">已有解读</span> : null}
            {!event.read ? <span className="radar-event-unread">未读</span> : null}
            {event.followed ? <span className="radar-event-followed">临时关注中</span> : null}
          </div>
          <time dateTime={event.lastSeenAt}>{formatTime(event.lastSeenAt)}</time>
        </div>
        <h3>{event.title}</h3>
        <p className="radar-event-summary">{event.summary}</p>
        <p className={event.watchlistRelated ? 'radar-watchlist-reason related' : 'radar-watchlist-reason'}>
          {event.watchlistExplanation}
        </p>
        <ul className="radar-reason-list">
          {reasons.slice(0, 3).map((reason) => <li key={reason}>{reason}</li>)}
        </ul>
        <div className="radar-event-actions">
          <button type="button" className="ghost-button" aria-label={`记入大事记：${event.title}`} onClick={() => void api('/api/major-events', { method: 'POST', body: JSON.stringify({ originType: 'RADAR_EVENT', originKey: String(event.id), occurredDate: event.lastSeenAt?.slice(0, 10) }) }).then(() => addToast('已记入大事记', 'success')).catch((error) => addToast(error instanceof Error ? error.message : '记入大事记失败', 'error'))}>记入大事记</button>
          <button type="button" className="ghost-button" onClick={() => onOpen(event)}>查看解读</button>
          <button type="button" className="secondary-button" onClick={() => onResearch?.(event.id, event.suggestedResearchQuestion)}>围绕此事研究</button>
        </div>
        <div className="radar-event-disposition">
          <button type="button" aria-pressed={Boolean(event.followed)} onClick={() => onStateChange?.(event,{followed:!event.followed})}>{event.followed?'取消临时关注':'临时关注'}</button>
          <button type="button" aria-pressed={event.disposition==='LATER'} onClick={() => onStateChange?.(event,{disposition:event.disposition==='LATER'?'ACTIVE':'LATER'})}>{event.disposition==='LATER'?'移回进行中':'稍后看'}</button>
          <button type="button" onClick={() => onStateChange?.(event,{disposition:'IGNORED'})}>忽略</button>
        </div>
      </div>
    </article>
  );
}

function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--:--';
}
function hotspotLifecycleLabel(value: string) {
  if (value === 'RISING') return '热度上升';
  if (value === 'COOLING') return '热度回落';
  if (value === 'DISCOVERED') return '刚发现';
  if (value === 'QUIET') return '趋于平稳';
  return '持续观察';
}
