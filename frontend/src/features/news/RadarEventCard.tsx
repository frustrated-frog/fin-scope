import type { RadarEvent } from './researchRadarTypes';

export function RadarEventCard({ event, onResearch, onOpen }: {
  event: RadarEvent;
  onResearch?: (question: string) => void;
  onOpen: (event: RadarEvent) => void;
}) {
  const reasons = event.reasons.filter((reason) => reason !== event.watchlistExplanation);

  return (
    <article className={`radar-event-card ${event.watchlistRelated ? 'is-related' : ''}`}>
      <div className="radar-event-score" aria-label={`研究优先级 ${event.priorityScore} 分`}>
        <strong>{event.priorityScore}</strong><span>优先级</span>
      </div>
      <div className="radar-event-body">
        <div className="radar-event-meta">
          <div className="radar-event-badges">
            <span className="radar-recommendation">{event.recommendation}</span>
            {event.interpretationStatus === 'SUCCESS' ? <span className="radar-interpretation-ready">已有解读</span> : null}
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
          <button type="button" className="ghost-button" onClick={() => onOpen(event)}>查看解读</button>
          <button type="button" className="secondary-button" onClick={() => onResearch?.(event.suggestedResearchQuestion)}>围绕此事研究</button>
        </div>
      </div>
    </article>
  );
}

function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--:--';
}
