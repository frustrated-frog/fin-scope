import { useEffect, useState } from 'react';

import { api } from '../../shared/api/client';
import { themeLabel } from '../../shared/brief/markdown';
import { EventArticleLink, EventCluster, EvidenceItem } from '../../shared/types';

export function EventsView({
  events,
  initialEventId
}: {
  events: EventCluster[];
  initialEventId?: number | null;
}) {
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [eventArticles, setEventArticles] = useState<EventArticleLink[]>([]);
  const [eventEvidence, setEventEvidence] = useState<EvidenceItem[]>([]);
  const [sourceTierFilter, setSourceTierFilter] = useState('ALL');

  useEffect(() => {
    if (!events.length) {
      setSelectedEventId(null);
      return;
    }
    if (initialEventId && events.some((event) => event.id === initialEventId)) {
      setSelectedEventId(initialEventId);
      return;
    }
    setSelectedEventId((current) => current ?? events[0].id);
  }, [events, initialEventId]);

  useEffect(() => {
    if (!selectedEventId) {
      setEventArticles([]);
      setEventEvidence([]);
      return;
    }
    Promise.all([
      api<EventArticleLink[]>(`/api/events/${selectedEventId}/articles`),
      api<EvidenceItem[]>(`/api/events/${selectedEventId}/evidence`)
    ])
      .then(([articles, evidence]) => {
        setEventArticles(articles);
        setEventEvidence(evidence);
      })
      .catch(() => {
        setEventArticles([]);
        setEventEvidence([]);
      });
  }, [selectedEventId]);

  const selectedEvent = events.find((event) => event.id === selectedEventId) ?? null;
  const noveltySummary = events.reduce<Record<string, number>>((summary, event) => {
    const key = event.noveltyState ?? 'UNKNOWN';
    summary[key] = (summary[key] || 0) + 1;
    return summary;
  }, {});
  const sourceTierOptions = Array.from(new Set(eventEvidence.map((item) => item.sourceTier).filter(Boolean)));
  const visibleEvidence = sourceTierFilter === 'ALL'
    ? eventEvidence
    : eventEvidence.filter((item) => item.sourceTier === sourceTierFilter);

  return (
    <section className="research-grid">
      <section className="panel">
        <div className="panel-heading">
          <h3>事件记忆</h3>
          <span className="subtle-badge">{events.length} events</span>
        </div>
        <div className="event-summary-strip">
          {Object.entries(noveltySummary).map(([state, count]) => (
            <span key={state} className="subtle-badge">{state} {count}</span>
          ))}
        </div>
        <div className="item-list">
          {events.map((event) => (
            <button
              key={event.id}
              type="button"
              className={selectedEventId === event.id ? 'event-card event-card-active' : 'event-card'}
              onClick={() => setSelectedEventId(event.id)}
            >
              <div className="event-card-top">
                <strong>{event.canonicalTitle}</strong>
                <span className="badge">{event.noveltyState ?? 'NEW'}</span>
              </div>
              <p>{event.summary || '暂无摘要'}</p>
              <div className="event-card-meta">
                <span>{themeLabel(event.themeCode)}</span>
                <span>证据 {event.evidenceCount ?? 0}</span>
                <span>文章 {event.articleCount ?? 0}</span>
              </div>
            </button>
          ))}
        </div>
      </section>

      <section className="panel detail-panel">
        {!selectedEvent ? (
          <p className="muted">选择一个事件查看文章与证据。</p>
        ) : (
          <>
            <div className="panel-heading">
              <div>
                <h3>{selectedEvent.canonicalTitle}</h3>
                <p className="muted">{themeLabel(selectedEvent.themeCode)} · 重要性 {selectedEvent.importanceScore ?? 0}</p>
              </div>
              <span className="badge">{selectedEvent.status ?? 'ACTIVE'}</span>
            </div>
            <div className="event-summary-strip">
              <span className="subtle-badge">首次出现 {selectedEvent.firstSeenAt?.slice(0, 10) || '--'}</span>
              <span className="subtle-badge">最近更新 {selectedEvent.lastSeenAt?.slice(0, 10) || '--'}</span>
              <span className="subtle-badge">新意 {selectedEvent.noveltyState ?? 'NEW'}</span>
            </div>
            <div className="topic-links">
              <div>
                <strong>关联文章</strong>
                <ul>
                  {eventArticles.map((article) => (
                    <li key={`${article.eventId}-${article.articleId}`}>{article.articleTitle || `Article ${article.articleId}`}</li>
                  ))}
                </ul>
              </div>
              <div>
                <div className="event-filter-bar">
                  <strong>事件证据</strong>
                  <label className="inline-select">
                    <span>证据来源层级</span>
                    <select
                      aria-label="证据来源层级"
                      value={sourceTierFilter}
                      onChange={(event) => setSourceTierFilter(event.target.value)}
                    >
                      <option value="ALL">ALL</option>
                      {sourceTierOptions.map((tier) => (
                        <option key={tier} value={tier}>{tier}</option>
                      ))}
                    </select>
                  </label>
                </div>
                <ul>
                  {visibleEvidence.map((item) => (
                    <li key={item.id}>[{item.sourceTier}] {item.claim}</li>
                  ))}
                </ul>
              </div>
            </div>
          </>
        )}
      </section>
    </section>
  );
}
