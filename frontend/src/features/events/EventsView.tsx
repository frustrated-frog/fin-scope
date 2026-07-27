import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { themeLabel } from '../../shared/brief/markdown';
import {
  ContentIdea,
  EventArticleLink,
  EventCluster,
  EvidenceItem,
  LearningTask, PageResponse
} from '../../shared/types';

const eventStatuses = ['ACTIVE', 'COOLING', 'ARCHIVED'];
const contentStatuses = ['IDEA', 'DRAFTING', 'READY', 'PUBLISHED', 'ARCHIVED'];

export function EventsView({
  events,
  initialEventId,
  mode = 'both',
  onOpenEvent,
  onBack,
  learningTasks,
  contentIdeas,
  onContentIdeaStatusChange,
  onEventStatusChange,
  onMergeEvent,
  onMoveEventArticle,
  onChanged,
  addToast
}: {
  events: EventCluster[];
  initialEventId?: number | null;
  mode?: 'queue' | 'detail' | 'both';
  onOpenEvent?: (eventId: number) => void;
  onBack?: () => void;
  learningTasks: LearningTask[];
  contentIdeas: ContentIdea[];
  onContentIdeaStatusChange: (ideaId: number, status: string) => Promise<void>;
  onEventStatusChange: (eventId: number, status: string) => Promise<void>;
  onMergeEvent: (sourceEventId: number, targetEventId: number) => Promise<void>;
  onMoveEventArticle: (
    sourceEventId: number,
    articleId: number,
    input: { targetEventId?: number; createNewEvent?: boolean }
  ) => Promise<void>;
  onChanged: () => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [eventArticles, setEventArticles] = useState<EventArticleLink[]>([]);
  const [eventEvidence, setEventEvidence] = useState<EvidenceItem[]>([]);
  const [sourceTierFilter, setSourceTierFilter] = useState('ALL');
  const [themeFilter, setThemeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [noveltyFilter, setNoveltyFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const pageSize = 20;
  const [serverPage, setServerPage] = useState<PageResponse<EventCluster> | null>(null);
  const [eventStatusDrafts, setEventStatusDrafts] = useState<Record<number, string>>({});
  const [mergeTargetId, setMergeTargetId] = useState('');
  const [articleMoveTargets, setArticleMoveTargets] = useState<Record<number, string>>({});
  const [ideaStatusDrafts, setIdeaStatusDrafts] = useState<Record<number, string>>({});
  const [mutationPending, setMutationPending] = useState(false);
  const detailRequestGeneration = useRef(0);
  const mutationLockRef = useRef(false);
  const selectedEventIdRef = useRef<number | null>(null);

  useEffect(() => {
    selectedEventIdRef.current = selectedEventId;
  }, [selectedEventId]);

  const themeOptions = useMemo(
    () => Array.from(new Set(events.map((event) => event.themeCode).filter(Boolean))),
    [events]
  );
  const noveltyOptions = useMemo(
    () => Array.from(new Set(events.map((event) => event.noveltyState || 'UNKNOWN'))),
    [events]
  );
  const filteredEvents = useMemo(() => {
    return events.filter((event) => {
      const matchesTheme = themeFilter === 'ALL' || event.themeCode === themeFilter;
      const matchesStatus = statusFilter === 'ALL' || (event.status || 'ACTIVE') === statusFilter;
      const matchesNovelty = noveltyFilter === 'ALL' || (event.noveltyState || 'UNKNOWN') === noveltyFilter;
      return matchesTheme && matchesStatus && matchesNovelty;
    });
  }, [events, themeFilter, statusFilter, noveltyFilter]);
  const noveltySummary = useMemo(
    () => distribution(events.map((event) => event.noveltyState || 'UNKNOWN')),
    [events]
  );
  const totalPages = Math.ceil(filteredEvents.length / pageSize);
  const currentPage = Math.min(page, Math.max(totalPages - 1, 0));
  const pagedEvents = serverPage?.items ?? filteredEvents.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

  useEffect(() => {
    const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (themeFilter !== 'ALL') query.set('themeCode', themeFilter);
    if (statusFilter !== 'ALL') query.set('status', statusFilter);
    if (noveltyFilter !== 'ALL') query.set('noveltyState', noveltyFilter);
    api<PageResponse<EventCluster>>(`/api/events/paged?${query}`).then(setServerPage).catch(() => setServerPage(null));
  }, [page, themeFilter, statusFilter, noveltyFilter]);

  useEffect(() => {
    setPage(0);
  }, [themeFilter, statusFilter, noveltyFilter]);

  useEffect(() => {
    if (!filteredEvents.length) {
      setSelectedEventId(null);
      return;
    }
    if (initialEventId && filteredEvents.some((event) => event.id === initialEventId)) {
      setSelectedEventId(initialEventId);
      return;
    }
    setSelectedEventId((current) => {
      if (current && filteredEvents.some((event) => event.id === current)) {
        return current;
      }
      return filteredEvents[0].id;
    });
  }, [events, filteredEvents, initialEventId]);

  useEffect(() => {
    if (!selectedEventId) {
      setEventArticles([]);
      setEventEvidence([]);
      return;
    }
    const requestGeneration = ++detailRequestGeneration.current;
    Promise.all([
      api<EventArticleLink[]>(`/api/events/${selectedEventId}/articles`),
      api<EvidenceItem[]>(`/api/events/${selectedEventId}/evidence`)
    ])
      .then(([articles, evidence]) => {
        if (requestGeneration !== detailRequestGeneration.current) return;
        setEventArticles(articles);
        setEventEvidence(evidence);
      })
      .catch(() => {
        if (requestGeneration !== detailRequestGeneration.current) return;
        setEventArticles([]);
        setEventEvidence([]);
      });
  }, [selectedEventId]);

  const selectedEvent = events.find((event) => event.id === selectedEventId) ?? null;
  const selectedTasks = selectedEvent
    ? learningTasks.filter((task) => task.eventId === selectedEvent.id)
    : [];
  const selectedIdeas = selectedEvent
    ? contentIdeas.filter((idea) => idea.eventId === selectedEvent.id)
    : [];
  const availableMergeTargets = selectedEvent
    ? events.filter((event) => event.id !== selectedEvent.id && (event.status || 'ACTIVE') !== 'ARCHIVED')
    : [];
  const sourceTierOptions = Array.from(new Set(eventEvidence.map((item) => item.sourceTier).filter(Boolean)));
  const sortedEvidence = [...eventEvidence].sort((left, right) => {
    const tierDelta = trustedTierRank(right.sourceTier) - trustedTierRank(left.sourceTier);
    if (tierDelta !== 0) {
      return tierDelta;
    }
    return (right.confidence || 0) - (left.confidence || 0);
  });
  const visibleEvidence = sourceTierFilter === 'ALL'
    ? sortedEvidence
    : sortedEvidence.filter((item) => item.sourceTier === sourceTierFilter);
  const topEvidence = sortedEvidence[0];
  const tierDistribution = distribution(eventEvidence.map((item) => item.sourceTier || 'UNKNOWN'));
  const typeDistribution = distribution(eventEvidence.map((item) => item.evidenceType || 'UNKNOWN'));

  async function saveEventStatus() {
    if (!selectedEvent) {
      return;
    }
    const status = eventStatusDrafts[selectedEvent.id] || selectedEvent.status || 'ACTIVE';
    if (!confirmAction(`确认把事件状态更新为 ${status}？`)) {
      return;
    }
    await runGovernanceAction(() => onEventStatusChange(selectedEvent.id, status));
  }

  async function mergeSelectedEvent() {
    if (!selectedEvent || !mergeTargetId) {
      return;
    }
    const targetId = Number(mergeTargetId);
    if (!confirmAction('确认合并事件？这会迁移文章、证据、学习任务和内容选题，并归档当前事件。')) {
      return;
    }
    await runGovernanceAction(async () => {
      await onMergeEvent(selectedEvent.id, targetId);
      setMergeTargetId('');
      setSelectedEventId(targetId);
      await reloadSelectedEvent(targetId);
    });
  }

  async function moveArticle(article: EventArticleLink) {
    if (!selectedEvent) {
      return;
    }
    const target = articleMoveTargets[article.articleId] || 'NEW_EVENT';
    if (!confirmAction('确认调整这篇文章的事件归属？')) {
      return;
    }
    if (target === 'NEW_EVENT') {
      await runGovernanceAction(() => onMoveEventArticle(selectedEvent.id, article.articleId, { createNewEvent: true }));
    } else {
      await runGovernanceAction(() => onMoveEventArticle(selectedEvent.id, article.articleId, { targetEventId: Number(target) }));
    }
  }

  async function saveIdeaStatus(idea: ContentIdea) {
    await runGovernanceAction(() => onContentIdeaStatusChange(idea.id, ideaStatusDrafts[idea.id] || idea.status || 'IDEA'));
  }

  async function runGovernanceAction(action: () => Promise<void>) {
    if (mutationLockRef.current) return;
    mutationLockRef.current = true;
    setMutationPending(true);
    try {
      await action();
      await reloadSelectedEvent();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '治理操作失败，请重试', 'error');
    } finally {
      mutationLockRef.current = false;
      setMutationPending(false);
    }
  }

  async function reloadSelectedEvent(eventId = selectedEvent?.id) {
    if (!eventId) {
      return;
    }
    const requestGeneration = ++detailRequestGeneration.current;
    await onChanged();
    if (requestGeneration !== detailRequestGeneration.current || selectedEventIdRef.current !== eventId) {
      return;
    }
    const [articles, evidence] = await Promise.all([
      api<EventArticleLink[]>(`/api/events/${eventId}/articles`),
      api<EvidenceItem[]>(`/api/events/${eventId}/evidence`)
    ]);
    if (requestGeneration === detailRequestGeneration.current && selectedEventIdRef.current === eventId) {
      setEventArticles(articles);
      setEventEvidence(evidence);
    }
  }

  return (
    <section className={mode === 'detail' ? 'event-archive-page' : mode === 'queue' ? 'events-queue-page' : 'events-workbench'}>
      {mode !== 'detail' && <section className="panel events-queue-panel">
        <div className="panel-heading events-heading">
          <div>
            <h3>事件研究台</h3>
            <p className="muted">追踪事件生命周期和证据链。</p>
          </div>
          <div className="event-hero-badges">
            <span className="subtle-badge">事件记忆</span>
            <span className="subtle-badge">{events.length} events</span>
          </div>
        </div>

        <div className="events-filter-grid" aria-label="事件队列筛选">
          <label className="inline-select">
            <span>主题</span>
            <select aria-label="事件主题筛选" value={themeFilter} onChange={(event) => setThemeFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              {themeOptions.map((themeCode) => (
                <option key={themeCode} value={themeCode}>{themeLabel(themeCode)}</option>
              ))}
            </select>
          </label>
          <label className="inline-select">
            <span>状态</span>
            <select aria-label="事件状态筛选" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              {eventStatuses.map((status) => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
          </label>
          <label className="inline-select">
            <span>新意</span>
            <select aria-label="事件新意筛选" value={noveltyFilter} onChange={(event) => setNoveltyFilter(event.target.value)}>
              <option value="ALL">ALL</option>
              {noveltyOptions.map((state) => (
                <option key={state} value={state}>{state}</option>
              ))}
            </select>
          </label>
        </div>

        <div className="event-summary-strip" aria-label="事件新意分布">
          {Object.entries(noveltySummary).map(([state, count]) => (
            <span key={state} className="subtle-badge">{state} {count}</span>
          ))}
        </div>

        <div className="item-list events-queue-list">
          {pagedEvents.length ? pagedEvents.map((event) => (
            <button
              key={event.id}
              type="button"
              className={selectedEventId === event.id ? 'event-card event-card-active' : 'event-card'}
              onClick={() => {
                setSelectedEventId(event.id);
                onOpenEvent?.(event.id);
              }}
            >
              <div className="event-card-signal" aria-hidden="true" />
              <div className="event-card-main">
                <div className="event-card-titleblock">
                  <span className="event-card-kicker">{themeLabel(event.themeCode)} · {event.noveltyState ?? 'NEW'}</span>
                  <strong>{event.canonicalTitle}</strong>
                </div>
                <span className={`event-status-pill ${eventStatusTone(event.status)}`}>{event.status ?? 'ACTIVE'}</span>
              </div>
              <p>{event.summary || '暂无摘要'}</p>
              <div className="event-card-bottom">
                <div className="event-card-metrics" aria-label="事件指标">
                  <span><small>文章</small><strong>{event.articleCount ?? 0}</strong><em>文章 {event.articleCount ?? 0}</em></span>
                  <span><small>证据</small><strong>{event.evidenceCount ?? 0}</strong><em>证据 {event.evidenceCount ?? 0}</em></span>
                  <span><small>重要性</small><strong>{event.importanceScore ?? 0}</strong><em>重要性 {event.importanceScore ?? 0}</em></span>
                </div>
                <span className="event-card-updated">最近更新 {formatDate(event.lastSeenAt || event.updatedAt)}</span>
              </div>
            </button>
          )) : (
            <p className="muted">暂无匹配事件。</p>
          )}
        </div>
        {totalPages > 1 && (
          <div className="pagination-controls events-pagination">
            <button className="secondary-button" type="button" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>上一页</button>
            <span>第 {currentPage + 1} / {totalPages} 页 · 共 {filteredEvents.length} 个事件</span>
            <button className="secondary-button" type="button" disabled={currentPage >= totalPages - 1} onClick={() => setPage(currentPage + 1)}>下一页</button>
          </div>
        )}
      </section>}

      {mode !== 'queue' && <section className="panel detail-panel events-detail-panel" role="region" aria-label="事件详情">
        {!selectedEvent ? (
          <p className="muted">选择一个事件后查看时间线、归并依据、证据强度和治理动作。</p>
        ) : (
          <>
            <header className="event-detail-hero">
              <div className="event-detail-topbar">
                {onBack && (
                  <button className="secondary-button event-archive-back" type="button" onClick={onBack}>
                    <span className="event-archive-back-icon" aria-hidden="true">←</span>
                    <span>返回事件队列</span>
                  </button>
                )}
                <div className="event-hero-badges">
                  <span className={`event-status-pill ${eventStatusTone(selectedEvent.status)}`}>{selectedEvent.status ?? 'ACTIVE'}</span>
                  <span className={`subtle-badge event-novelty-pill ${eventNoveltyTone(selectedEvent.noveltyState)}`}>
                    {selectedEvent.noveltyState ?? 'NEW'}
                  </span>
                </div>
              </div>
              <div className="event-detail-title">
                <span className="section-kicker">{themeLabel(selectedEvent.themeCode)}</span>
                <h3>{selectedEvent.canonicalTitle}</h3>
                <p>{selectedEvent.summary || '暂无摘要'}</p>
              </div>
            </header>

            <div className="event-detail-metrics" aria-label="事件指标">
              <div>
                <span>主题</span>
                <strong>{themeLabel(selectedEvent.themeCode)}</strong>
              </div>
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
            </div>

            <div className="events-detail-grid">
              <section className="event-detail-section event-timeline-section">
                <div className="event-detail-section-head">
                  <strong>事件时间线</strong>
                  <span>{eventArticles.length} 篇文章</span>
                </div>
                {eventArticles.length ? (
                  <ol className="event-timeline-list">
                    {[...eventArticles].sort(compareArticleLinks).map((article) => (
                      <li key={`${article.eventId}-${article.articleId}`}>
                        <span className={`badge event-timeline-kind ${eventNoveltyTone(article.noveltyType)}`}>
                          {article.noveltyType ?? 'NEW'}
                        </span>
                        <div>
                          <strong>{article.articleTitle || `Article ${article.articleId}`}</strong>
                          <p>{formatDate(article.createdAt)} · {article.articleUrl ? <a href={article.articleUrl} target="_blank" rel="noopener noreferrer">查看原文</a> : '无链接'}</p>
                        </div>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="muted">暂无关联文章，事件仍可作为待补证据的研究入口。</p>
                )}
              </section>

              <section className="event-detail-section event-merge-section">
                <div className="event-detail-section-head">
                  <strong>归并依据</strong>
                  <span>relation / score / reason</span>
                </div>
                {eventArticles.length ? (
                  <ul className="event-detail-list merge-basis-list">
                    {eventArticles.map((article) => (
                      <li key={`basis-${article.articleId}`}>
                        <div>
                          <strong>{article.articleTitle || `Article ${article.articleId}`}</strong>
                          <p>{article.noveltyReason || '暂无归并原因'}</p>
                        </div>
                        <div className="merge-basis-tags">
                          <span className="subtle-badge">{article.relationType || 'PRIMARY'}</span>
                          <span className="subtle-badge">匹配 {formatPercent(article.matchScore)}</span>
                          <span className={`badge event-novelty-pill ${eventNoveltyTone(article.noveltyType)}`}>
                            {article.noveltyType || 'NEW'}
                          </span>
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="muted">暂无文章归并依据。</p>
                )}
              </section>

              <section className="event-detail-section evidence-strength-section">
                <div className="event-detail-section-head">
                  <strong>证据强度</strong>
                  <span>按可信度排序</span>
                </div>
                {topEvidence ? (
                  <div className="top-evidence">
                    <span className="section-kicker">最高可信证据</span>
                    <strong>当前最高：{topEvidence.claim}</strong>
                    <p>
                      {topEvidence.sourceTier} · {topEvidence.evidenceType} · 置信度 {topEvidence.confidence}
                      {isTrustedTier(topEvidence.sourceTier) ? ' · 高可信来源' : ''}
                    </p>
                  </div>
                ) : (
                  <p className="muted">暂无证据。可以先从关联文章补证据，再判断事件强度。</p>
                )}
                <div className="evidence-distribution">
                  <div>
                    <strong>source tier 分布</strong>
                    {renderDistribution(tierDistribution)}
                  </div>
                  <div>
                    <strong>evidence type 分布</strong>
                    {renderDistribution(typeDistribution)}
                  </div>
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
                  <ul className="event-detail-list evidence-list">
                    {visibleEvidence.map((item) => (
                      <li key={item.id}>
                        <span className={isTrustedTier(item.sourceTier) ? 'badge trusted-tier' : 'subtle-badge'}>
                          {item.sourceTier}
                        </span>
                        <div>
                          <strong>{item.evidenceType} · {item.confidence}</strong>
                          <p>{item.claim}{item.articleUrl && <> · <a href={item.articleUrl} target="_blank" rel="noopener noreferrer">原文</a></>}</p>
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="muted">暂无匹配证据。</p>
                )}
              </section>

              <section className="event-detail-section event-learning-section">
                <div className="event-detail-section-head">
                  <strong>学习任务</strong>
                  <span>{selectedTasks.length} tasks</span>
                </div>
                {selectedTasks.length ? selectedTasks.map((task) => (
                  <article className="event-output-card event-output-card-task" key={task.id}>
                    <span className="event-output-format">{task.status}</span>
                    <strong>{task.question}</strong>
                    {task.whyNeeded && <p>{task.whyNeeded}</p>}
                    <p className="muted">请在知识工作台中接受、作答或完成此任务。</p>
                  </article>
                )) : (
                  <p className="muted">暂无学习任务。事件仍可先用于观察证据和新变量。</p>
                )}
              </section>

              <section className="event-detail-section event-idea-section">
                <div className="event-detail-section-head">
                  <strong>内容选题</strong>
                  <span>{selectedIdeas.length} ideas</span>
                </div>
                {selectedIdeas.length ? selectedIdeas.map((idea) => (
                  <article className="event-output-card event-output-card-idea" key={idea.id}>
                    <span className="event-output-format">{idea.format}</span>
                    <strong>{idea.title}</strong>
                    {idea.angle && <p>{idea.angle}</p>}
                    <div className="task-status-row event-output-status">
                      <label className="inline-select">
                        <span>选题状态</span>
                        <select
                          aria-label={`内容选题状态-${idea.id}`}
                          value={ideaStatusDrafts[idea.id] || idea.status || 'IDEA'}
                          onChange={(event) => setIdeaStatusDrafts((current) => ({
                            ...current,
                            [idea.id]: event.target.value
                          }))}
                        >
                          {contentStatuses.map((status) => (
                            <option key={status} value={status}>{status}</option>
                          ))}
                        </select>
                      </label>
                      <button className="compact-button event-output-save" type="button" disabled={mutationPending} onClick={() => saveIdeaStatus(idea)}>
                        保存状态
                      </button>
                    </div>
                  </article>
                )) : (
                  <p className="muted">暂无内容选题。证据链稳定后再生成内容更稳。</p>
                )}
              </section>

              <section className="event-detail-section governance-section">
                <div className="event-detail-section-head">
                  <strong>人工治理</strong>
                  <span>高影响操作需确认</span>
                </div>
                <div className="governance-controls">
                  <label className="inline-select">
                    <span>事件状态</span>
                    <select
                      aria-label="事件状态"
                      value={eventStatusDrafts[selectedEvent.id] || selectedEvent.status || 'ACTIVE'}
                      onChange={(event) => setEventStatusDrafts((current) => ({
                        ...current,
                        [selectedEvent.id]: event.target.value
                      }))}
                    >
                      {eventStatuses.map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                  </label>
                  <button className="compact-button" type="button" disabled={mutationPending} onClick={saveEventStatus}>保存事件状态</button>

                  <label className="inline-select">
                    <span>合并到事件</span>
                    <select
                      aria-label="合并到事件"
                      value={mergeTargetId}
                      onChange={(event) => setMergeTargetId(event.target.value)}
                    >
                      <option value="">选择目标事件</option>
                      {availableMergeTargets.map((event) => (
                        <option key={event.id} value={event.id}>{event.canonicalTitle}</option>
                      ))}
                    </select>
                  </label>
                  <button className="compact-button danger-button" type="button" disabled={mutationPending} onClick={mergeSelectedEvent}>
                    合并事件
                  </button>
                </div>

                {eventArticles.length ? (
                  <div className="article-move-list">
                    {eventArticles.map((article) => (
                      <div className="article-move-row" key={`move-${article.articleId}`}>
                        <div>
                          <strong>{article.articleTitle || `Article ${article.articleId}`}</strong>
                          <p>{article.noveltyType || 'NEW'} · {article.noveltyReason || '暂无原因'}</p>
                        </div>
                        <label className="inline-select">
                          <span>{`移动文章-${article.articleId}`}</span>
                          <select
                            aria-label={`移动文章目标-${article.articleId}`}
                          value={articleMoveTargets[article.articleId] || 'NEW_EVENT'}
                          onChange={(event) => setArticleMoveTargets((current) => ({
                            ...current,
                            [article.articleId]: event.target.value
                          }))}
                        >
                            <option value="NEW_EVENT">拆成新事件</option>
                            {availableMergeTargets.map((event) => (
                              <option key={event.id} value={event.id}>{event.canonicalTitle}</option>
                            ))}
                          </select>
                        </label>
                        <button className="compact-button" type="button" disabled={mutationPending} onClick={() => moveArticle(article)}>
                          {`移动文章-${article.articleId}`}
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="muted">暂无文章可移动。</p>
                )}
              </section>
            </div>
          </>
        )}
      </section>}
    </section>
  );
}

function trustedTierRank(tier?: string) {
  if (tier === 'REGULATOR') {
    return 3;
  }
  if (tier === 'OFFICIAL') {
    return 2;
  }
  if (tier === 'COMPANY') {
    return 1;
  }
  return 0;
}

function eventStatusTone(status?: string) {
  if (status === 'ARCHIVED') return 'archived';
  if (status === 'COOLING') return 'cooling';
  return 'active';
}

function eventNoveltyTone(novelty?: string) {
  return (novelty || 'NEW').toLowerCase().replace(/[^a-z0-9_-]/g, '-');
}

function isTrustedTier(tier?: string) {
  return tier === 'REGULATOR' || tier === 'OFFICIAL';
}

function distribution(values: string[]) {
  return values.reduce<Record<string, number>>((result, value) => {
    result[value] = (result[value] || 0) + 1;
    return result;
  }, {});
}

function renderDistribution(items: Record<string, number>) {
  const entries = Object.entries(items);
  if (!entries.length) {
    return <p className="muted">暂无</p>;
  }
  return (
    <div className="distribution-tags">
      {entries.map(([key, count]) => (
        <span className="subtle-badge" key={key}>{key} {count}</span>
      ))}
    </div>
  );
}

function compareArticleLinks(left: EventArticleLink, right: EventArticleLink) {
  return Date.parse(left.createdAt || '') - Date.parse(right.createdAt || '');
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '--';
}

function formatPercent(value?: number) {
  if (value == null || Number.isNaN(value)) {
    return '--';
  }
  return `${Math.round(value * 100)}%`;
}

function confirmAction(message: string) {
  if (typeof window === 'undefined' || typeof window.confirm !== 'function') {
    return true;
  }
  try {
    return window.confirm(message) !== false;
  } catch {
    return true;
  }
}
