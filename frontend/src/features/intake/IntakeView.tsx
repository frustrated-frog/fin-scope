import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { AsyncTask, FetchBatch, IntakeCandidate } from '../../shared/types';
import { createIngestTaskChannel, IngestTaskChannel } from '../articles/ingestTaskChannel';

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待打标' },
  { value: 'SAVED_FOR_LATER', label: '稍后看' },
  { value: 'SKIPPED', label: '已跳过' },
  { value: 'PROMOTED', label: '已入库' },
  { value: 'REJECTED', label: '已拒绝' }
];

export function IntakeView({
  batches,
  candidates,
  status,
  onStatusChange,
  onChanged,
  addToast
}: {
  batches: FetchBatch[];
  candidates: IntakeCandidate[];
  status: string;
  onStatusChange: (status: string) => Promise<IntakeCandidate[]>;
  onChanged: () => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [busyCandidateId, setBusyCandidateId] = useState<number | null>(null);
  const [promoteResult, setPromoteResult] = useState<{
    candidateId: number;
    articleText: string;
    fullText: string;
  } | null>(null);
  const [promoteTask, setPromoteTask] = useState<AsyncTask | null>(null);
  const promoteChannelRef = useRef<IngestTaskChannel | null>(null);
  useEffect(() => () => promoteChannelRef.current?.dispose(), []);

  async function promote(candidateId: number) {
    setBusyCandidateId(candidateId);
    setPromoteTask(null);
    try {
      const task = await api<AsyncTask>(`/api/intake/candidates/${candidateId}/promote-async`, {
        method: 'POST'
      });
      setPromoteTask(task);
      promoteChannelRef.current?.dispose();
      const channel = createIngestTaskChannel(task, {
        fetchTask: () => api<AsyncTask>(`/api/tasks/${task.taskId}`),
        timeoutMs: 5 * 60 * 1000,
        onProgress: setPromoteTask
      });
      promoteChannelRef.current = channel;
      const completed = await channel.completion;
      setPromoteTask(completed);
      if (completed.status === 'FAILED') throw new Error(completed.errorMessage || completed.message || '入文章库失败');
      const articleText = `已入文章库 #${completed.articleId}`;
      setPromoteResult({ candidateId, articleText, fullText: completed.message || articleText });
      addToast(completed.message || articleText, 'success');
      await onChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '入文章库失败', 'error');
    } finally {
      promoteChannelRef.current = null;
      setBusyCandidateId(null);
    }
  }

  async function updateStatus(candidateId: number, humanStatus: string) {
    setBusyCandidateId(candidateId);
    try {
      await api<IntakeCandidate>(`/api/intake/candidates/${candidateId}/status`, {
        method: 'POST',
        body: JSON.stringify({ humanStatus })
      });
      addToast(`候选项已标记为 ${humanStatus}`, 'success');
      await onChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '状态更新失败', 'error');
    } finally {
      setBusyCandidateId(null);
    }
  }

  return (
    <section className="intake-workspace">
      <section className="panel intake-summary-panel">
        <div className="panel-heading">
          <h3>候选池</h3>
          <span className="subtle-badge">{candidates.length} candidates</span>
        </div>
        {promoteResult && (
          <div className="intake-promote-result" data-candidate-id={promoteResult.candidateId}>
            <span>{promoteResult.articleText}</span>
            <p>{promoteResult.fullText}</p>
          </div>
        )}
        {promoteTask && busyCandidateId !== null && (
          <div className="intake-promote-result" role="status">
            <span>正在入文章库</span>
            <p>{promoteTask.message || promoteTask.phase || '等待开始'}</p>
          </div>
        )}
        <div className="intake-status-tabs" role="group" aria-label="候选状态筛选">
          {STATUS_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={status === option.value ? 'compact-button intake-status-tab active' : 'ghost-button intake-status-tab'}
              onClick={() => onStatusChange(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
        <div className="intake-batch-list" aria-label="最近摄入批次">
          {batches.slice(0, 4).map((batch) => (
            <article key={batch.id} className="intake-batch-row">
              <div>
                <strong>{batch.sourceName || `Source #${batch.sourceId}`}</strong>
                <p>{batch.batchSummaryText || batch.errorMessage || '暂无批次总结'}</p>
              </div>
              <span className="subtle-badge">{batch.status}</span>
            </article>
          ))}
          {batches.length === 0 && <p className="muted">暂无摄入批次</p>}
        </div>
      </section>

      <section className="intake-candidate-list" aria-label="候选内容列表">
        {candidates.length === 0 ? (
          <div className="panel empty-state">
            <p className="empty-state-text">当前状态下暂无候选项</p>
          </div>
        ) : (
          candidates.map((candidate) => (
            <article
              key={candidate.id}
              className={`panel intake-candidate-card${busyCandidateId === candidate.id ? ' is-promoting' : ''}`}
            >
              <div className="intake-candidate-top">
                <div className="intake-score" aria-label={`Agent 分数 ${candidate.agentScore ?? 0}`}>
                  <strong>{candidate.agentScore ?? 0}</strong>
                  <span>score</span>
                </div>
                <div className="intake-candidate-title">
                  <div className="intake-badges">
                    <span className="badge">{candidate.agentRecommendation || 'NEED_REVIEW'}</span>
                    <span className="subtle-badge">{candidate.agentStatus || 'PENDING'}</span>
                    <span className="subtle-badge">{candidate.sourceName || '未知来源'}</span>
                  </div>
                  <h3>{candidate.chineseTitle || candidate.originalTitle || '未命名候选'}</h3>
                  {candidate.originalTitle && candidate.originalTitle !== candidate.chineseTitle && (
                    <p className="muted">{candidate.originalTitle}</p>
                  )}
                </div>
              </div>

              <p className="intake-decision">{candidate.decisionSummary || '暂无决策摘要'}</p>

              <div className="intake-detail-grid">
                <div>
                  <h4>关键事实</h4>
                  <ul>
                    {parseJsonList(candidate.keyFactsJson).map((fact) => (
                      <li key={fact}>{fact}</li>
                    ))}
                    {parseJsonList(candidate.keyFactsJson).length === 0 && <li>暂无关键事实</li>}
                  </ul>
                </div>
                <div>
                  <h4>为什么重要</h4>
                  <p>{candidate.whyItMatters || '暂无说明'}</p>
                </div>
                <div>
                  <h4>新颖性判断</h4>
                  <p>{candidate.noveltyJudgment || '暂无判断'}</p>
                </div>
                <div>
                  <h4>风险提示</h4>
                  <ul>
                    {parseJsonList(candidate.riskFlagsJson).map((flag) => (
                      <li key={flag}>{flag}</li>
                    ))}
                    {parseJsonList(candidate.riskFlagsJson).length === 0 && <li>暂无风险提示</li>}
                  </ul>
                </div>
              </div>

              <div className="intake-candidate-actions">
                <a className="ghost-button intake-source-link" href={candidate.originalUrl} target="_blank" rel="noreferrer">
                  原文
                </a>
                <button
                  type="button"
                  className="secondary-button"
                  aria-label={`稍后看-${candidate.id}`}
                  disabled={busyCandidateId === candidate.id || isTerminalStatus(candidate.humanStatus)}
                  onClick={() => updateStatus(candidate.id, 'SAVED_FOR_LATER')}
                >
                  稍后看
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={busyCandidateId === candidate.id || isTerminalStatus(candidate.humanStatus)}
                  onClick={() => updateStatus(candidate.id, 'SKIPPED')}
                >
                  跳过
                </button>
                {(candidate.humanStatus === 'SKIPPED' || candidate.humanStatus === 'REJECTED') && (
                  <button
                    type="button"
                    className="ghost-button"
                    disabled={busyCandidateId === candidate.id}
                    onClick={() => updateStatus(candidate.id, 'PENDING')}
                  >
                    恢复待处理
                  </button>
                )}
                <button
                  type="button"
                  className="danger-button"
                  disabled={busyCandidateId === candidate.id || isTerminalStatus(candidate.humanStatus)}
                  onClick={() => updateStatus(candidate.id, 'REJECTED')}
                >
                  拒绝
                </button>
                <button
                  type="button"
                  className="compact-button"
                  aria-label={`入文章库-${candidate.id}`}
                  disabled={busyCandidateId === candidate.id || (candidate.humanStatus === 'PROMOTED' && !candidate.promotedArticleId)}
                  onClick={() => promote(candidate.id)}
                >
                  {busyCandidateId === candidate.id
                    ? '正在入库…'
                    : candidate.humanStatus === 'PROMOTED' ? '重试工作包' : '入文章库'}
                </button>
              </div>
              {busyCandidateId === candidate.id && (
                <div className="intake-candidate-progress" role="status" aria-live="polite">
                  正在入文章库 · {promoteTask?.message || promoteTask?.phase || '正在提交入库任务'}
                </div>
              )}
            </article>
          ))
        )}
      </section>
    </section>
  );
}

const TERMINAL_STATUSES = new Set(['PROMOTED', 'REJECTED', 'SKIPPED']);

function isTerminalStatus(status?: string) {
  return TERMINAL_STATUSES.has(status || '');
}

function parseJsonList(value?: string) {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map((item) => String(item)).filter(Boolean) : [];
  } catch {
    return value.split(/\n|;/).map((item) => item.trim()).filter(Boolean);
  }
}
