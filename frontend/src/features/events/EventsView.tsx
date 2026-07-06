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

      <section className="panel detail-panel event-detail-panel" role="region" aria-label="事件详情">
        {!selectedEvent ? (
          <p className="muted">选择一个事件查看文章与证据。</p>
        ) : (
          <>
            <header className="event-detail-hero">
              <div className="event-detail-title">
                <span className="section-kicker">{themeLabel(selectedEvent.themeCode)}</span>
                <h3>{selectedEvent.canonicalTitle}</h3>
                <p>{selectedEvent.summary || '暂无摘要'}</p>
              </div>
              <span className="badge">{selectedEvent.status ?? 'ACTIVE'}</span>
            </header>

            <div className="event-detail-metrics" aria-label="事件指标">
              <div>
                <span>重要性</span>
                <strong>{selectedEvent.importanceScore ?? 0}</strong>
              </div>
              <div>
                <span>关联文章</span>
                <strong>{selectedEvent.articleCount ?? eventArticles.length} 篇</strong>
              </div>
              <div>
                <span>事件证据</span>
                <strong>{selectedEvent.evidenceCount ?? eventEvidence.length} 条</strong>
              </div>
              <div>
                <span>新意</span>
                <strong>{selectedEvent.noveltyState ?? 'NEW'}</strong>
              </div>
            </div>

            <div className="event-detail-timeline" aria-label="事件时间">
              <span>首次出现 {selectedEvent.firstSeenAt?.slice(0, 10) || '--'}</span>
              <span>最近更新 {selectedEvent.lastSeenAt?.slice(0, 10) || '--'}</span>
            </div>

            <div className="event-detail-content">
              <section className="event-detail-section">
                <div className="event-detail-section-head">
                  <strong>关联文章</strong>
                  <span>共 {eventArticles.length} 篇</span>
                </div>
                {eventArticles.length ? (
                  <ul className="event-detail-list">
                    {eventArticles.map((article) => (
                      <li key={`${article.eventId}-${article.articleId}`}>{article.articleTitle || `Article ${article.articleId}`}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="muted">暂无关联文章。</p>
                )}
              </section>

              <section className="event-detail-section">
                <div className="event-detail-section-head">
                  <strong>事件证据</strong>
                  <span>共 {visibleEvidence.length} 条</span>
                </div>
                <label className="inline-select event-detail-filter">
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
                {visibleEvidence.length ? (
                  <ul className="event-detail-list">
                    {visibleEvidence.map((item) => (
                      <li key={item.id}>
                        <span>{item.sourceTier}</span>
                        {item.claim}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="muted">暂无匹配证据。</p>
                )}
              </section>
            </div>
          </>
        )}
      </section>
    </section>
  );
}
