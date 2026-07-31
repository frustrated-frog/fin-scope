import { useState } from 'react';

import { api } from '../../shared/api/client';
import type { RadarEvent, RadarEventDetail } from './researchRadarTypes';

export function RadarEventCard({ event, onResearch }: { event: RadarEvent; onResearch?: (question: string) => void }) {
  const [expanded, setExpanded] = useState(false);
  const [detail, setDetail] = useState<RadarEventDetail>();
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(false);

  async function toggleEvidence() {
    if (expanded) { setExpanded(false); return; }
    setExpanded(true);
    if (detail || loading) return;
    setLoading(true);
    try {
      setDetail(await api<RadarEventDetail>(`/api/research-radar/events/${event.id}`));
    } catch (error) {
      setDetailError(error instanceof Error ? error.message : '依据加载失败');
    } finally {
      setLoading(false);
    }
  }

  const reasons = event.reasons.filter((reason) => reason !== event.watchlistExplanation);

  return (
    <article className={`radar-event-card ${event.watchlistRelated ? 'is-related' : ''}`}>
      <div className="radar-event-score" aria-label={`研究优先级 ${event.priorityScore} 分`}>
        <strong>{event.priorityScore}</strong><span>优先级</span>
      </div>
      <div className="radar-event-body">
        <div className="radar-event-meta">
          <span className="radar-recommendation">{event.recommendation}</span>
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
          <button type="button" className="ghost-button" onClick={() => void toggleEvidence()}>{expanded ? '收起依据' : '查看依据'}</button>
          <button type="button" className="secondary-button" onClick={() => onResearch?.(event.suggestedResearchQuestion)}>围绕此事研究</button>
        </div>
        {expanded ? (
          <div className="radar-evidence" aria-live="polite">
            <strong>{event.sourceCount} 个独立来源共同报道</strong>
            <p><b>尚待确认：</b>{event.uncertainty}</p>
            <p><b>下一步观察：</b>{event.nextObservation}</p>
            {loading ? <span>正在读取原始来源…</span> : null}
            {detailError ? <span role="alert">{detailError}</span> : null}
            {detail?.signals.map((signal) => (
              <article className="radar-signal" key={signal.id}>
                <div><span>{signal.sourceName}</span><time dateTime={signal.publishedAt}>{formatDateTime(signal.publishedAt)}</time></div>
                {signal.url ? <a href={signal.url} target="_blank" rel="noreferrer">{signal.title}</a> : <strong>{signal.title}</strong>}
                <small>{signal.matchReason}</small>
              </article>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--:--';
}
function formatDateTime(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : '--';
}
