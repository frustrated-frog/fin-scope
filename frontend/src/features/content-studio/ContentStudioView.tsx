import { useMemo, useState } from 'react';

import { themeLabel } from '../../shared/brief/markdown';
import { ContentIdea, PageResponse } from '../../shared/types';

const ideaStatuses = ['IDEA', 'DRAFTING', 'READY', 'PUBLISHED', 'ARCHIVED'];

const formatLabels: Record<string, string> = {
  LONG_ARTICLE: '长文章',
  X_THREAD: 'X 长帖',
  SHORT_POST: '短内容',
  VIDEO_SCRIPT: '视频脚本',
  NEWSLETTER: '邮件简报',
  PODCAST_SCRIPT: '播客脚本'
};

const statusLabels: Record<string, string> = {
  IDEA: '想法',
  DRAFTING: '撰写中',
  READY: '待发布',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档'
};

function formatLabel(format: string) {
  return formatLabels[format] || format.replace(/_/g, ' ');
}

function statusLabel(status: string) {
  return statusLabels[status] || status.replace(/_/g, ' ');
}

export function ContentStudioView({
  contentIdeas,
  pagination,
  onPageChange,
  onIdeaStatusChange,
  addToast
}: {
  contentIdeas: ContentIdea[];
  pagination?: PageResponse<ContentIdea> | null;
  onPageChange?: (page: number) => void;
  onIdeaStatusChange: (ideaId: number, status: string) => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [draftStatuses, setDraftStatuses] = useState<Record<number, string>>({});
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortMode, setSortMode] = useState('SCORE');

  const visibleStatuses = useMemo(() => {
    const current = { ...draftStatuses };
    contentIdeas.forEach((idea) => {
      if (!current[idea.id]) {
        current[idea.id] = idea.status || 'IDEA';
      }
    });
    return current;
  }, [contentIdeas, draftStatuses]);
  const statusCounts = useMemo(
    () => countBy(contentIdeas.map((idea) => visibleStatuses[idea.id] || idea.status || 'IDEA')),
    [contentIdeas, visibleStatuses]
  );
  const formatCounts = useMemo(
    () => countBy(contentIdeas.map((idea) => idea.format || 'UNKNOWN')),
    [contentIdeas]
  );
  const filteredIdeas = useMemo(() => {
    const visible = statusFilter === 'ALL'
      ? [...contentIdeas]
      : contentIdeas.filter((idea) => (visibleStatuses[idea.id] || idea.status || 'IDEA') === statusFilter);
    return visible.sort((left, right) => {
      if (sortMode === 'NEWEST') return right.id - left.id;
      if (sortMode === 'FORMAT') return left.format.localeCompare(right.format) || right.score - left.score;
      if (sortMode === 'STATUS') {
        const statusDelta = ideaStatuses.indexOf(visibleStatuses[left.id] || left.status || 'IDEA')
          - ideaStatuses.indexOf(visibleStatuses[right.id] || right.status || 'IDEA');
        return statusDelta || right.score - left.score;
      }
      return right.score - left.score;
    });
  }, [contentIdeas, sortMode, statusFilter, visibleStatuses]);
  const leadIdea = filteredIdeas[0] || contentIdeas[0];
  const averageScore = contentIdeas.length
    ? Math.round(contentIdeas.reduce((total, idea) => total + (idea.score || 0), 0) / contentIdeas.length)
    : 0;
  const highScoreCount = contentIdeas.filter((idea) => (idea.score || 0) >= 90).length;
  const primaryFormat = Object.entries(formatCounts).sort((left, right) => right[1] - left[1])[0];
  const totalIdeas = pagination?.totalCount ?? contentIdeas.length;
  const totalPages = Math.max(1, pagination?.totalPages ?? 1);
  const currentPage = Math.min(totalPages, (pagination?.page ?? 0) + 1);
  const pageStart = totalIdeas === 0 ? 0 : (pagination?.page ?? 0) * (pagination?.pageSize ?? contentIdeas.length) + 1;
  const pageEnd = contentIdeas.length === 0 ? pageStart : pagination
    ? Math.min(totalIdeas, pageStart + contentIdeas.length - 1)
    : contentIdeas.length;

  async function saveIdeaStatus(idea: ContentIdea) {
    await onIdeaStatusChange(idea.id, visibleStatuses[idea.id] || idea.status || 'IDEA');
    addToast('选题状态已更新', 'success');
  }

  return (
    <section className="content-studio-workbench">
      <header className="studio-command-hero">
        <div>
          <p className="eyebrow">Content signal desk</p>
          <h3>选题生产台</h3>
          <p>把研究事件、证据强度和受众定位收束成可推进的内容队列，优先处理高分、有明确角度的选题。</p>
        </div>
        <div className="studio-hero-metrics" aria-label="选题总览">
          <span><small>选题总数</small><strong>{totalIdeas}</strong></span>
          <span><small>平均分</small><strong>{averageScore}分</strong></span>
          <span><small>高分候选</small><strong>{highScoreCount}</strong></span>
        </div>
      </header>

      <div className="studio-layout">
        <aside className="studio-control-panel" aria-label="选题控制台">
          <div className="studio-control-head">
            <span>Pipeline</span>
            <strong>{filteredIdeas.length} / {totalIdeas}</strong>
          </div>
          <div className="studio-status-stack">
            <button
              className={statusFilter === 'ALL' ? 'studio-status-filter active' : 'studio-status-filter'}
              type="button"
              onClick={() => setStatusFilter('ALL')}
            >
              <span>ALL</span><strong>{contentIdeas.length}</strong>
            </button>
            {ideaStatuses.map((status) => (
              <button
                className={statusFilter === status ? 'studio-status-filter active' : 'studio-status-filter'}
                type="button"
                key={status}
                onClick={() => setStatusFilter(status)}
              >
                <span>{status}</span><strong>{statusCounts[status] || 0}</strong>
              </button>
            ))}
          </div>
          <label className="inline-select studio-sort-control">
            <span>队列排序</span>
            <select aria-label="选题排序" value={sortMode} onChange={(event) => setSortMode(event.target.value)}>
              <option value="SCORE">按评分优先</option>
              <option value="NEWEST">按最新生成</option>
              <option value="FORMAT">按内容形态</option>
              <option value="STATUS">按推进状态</option>
            </select>
          </label>
          <div className="studio-format-map">
            <span>主力形态</span>
            <strong>{primaryFormat ? `${primaryFormat[0]} · ${primaryFormat[1]}` : '暂无'}</strong>
            <div>
              {Object.entries(formatCounts).slice(0, 5).map(([format, count]) => (
                <small key={format}>{format} {count}</small>
              ))}
            </div>
          </div>
          {leadIdea && (
            <div className="studio-lead-card">
              <span>当前优先处理</span>
              <strong>{leadIdea.score} 分高优先级选题</strong>
              <small>{themeLabel(leadIdea.themeCode)} · {leadIdea.format}</small>
            </div>
          )}
        </aside>

        <main className="studio-stream">
          <div className="studio-stream-head">
            <div>
              <p className="eyebrow">Idea stream</p>
              <h3>内容选题队列</h3>
            </div>
            <span className="subtle-badge">第 {currentPage} / {totalPages} 页</span>
          </div>
          <div className="studio-grid">
            {filteredIdeas.length ? filteredIdeas.map((idea) => {
              const status = visibleStatuses[idea.id] || idea.status || 'IDEA';
              return (
                <article className="studio-card content-studio-card" key={idea.id}>
                  <div className="studio-card-top">
                    <span className="studio-score">{idea.score}</span>
                    <span className="studio-card-tags" aria-label="内容标签">
                      <span
                        className="studio-format-pill"
                        aria-label={`内容形态：${formatLabel(idea.format)}`}
                      >
                        <i aria-hidden="true" />
                        {formatLabel(idea.format)}
                      </span>
                      <span
                        className={`studio-status-pill ${statusTone(status)}`}
                        aria-label={`推进状态：${statusLabel(status)}`}
                      >
                        {statusLabel(status)}
                      </span>
                    </span>
                  </div>
                  <div className="studio-card-body">
                    <span>{themeLabel(idea.themeCode)}</span>
                    <strong>{idea.title}</strong>
                    {idea.angle && <p>{idea.angle}</p>}
                  </div>
                  {(idea.scoreReason || idea.audience) && (
                    <div className="studio-signal-box">
                      {idea.scoreReason && <p>{idea.scoreReason}</p>}
                      {idea.audience && <small>{idea.audience}</small>}
                    </div>
                  )}
                  {idea.outline && (
                    <ol className="studio-outline">
                      {idea.outline.split('\n').filter(Boolean).map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ol>
                  )}
                  <div className="task-status-row">
                    <label className="inline-select">
                      <span>选题状态</span>
                      <select
                        aria-label={`内容选题状态-${idea.id}`}
                        value={status}
                        onChange={(event) => setDraftStatuses((current) => ({
                          ...current,
                          [idea.id]: event.target.value
                        }))}
                      >
                        {ideaStatuses.map((item) => (
                          <option key={item} value={item}>{item}</option>
                        ))}
                      </select>
                    </label>
                    <button className="compact-button" type="button" onClick={() => saveIdeaStatus(idea)}>
                      保存选题状态
                    </button>
                  </div>
                </article>
              );
            }) : (
              <p className="empty-state">当前筛选下暂无选题。</p>
            )}
          </div>
          <div className="studio-pagination" aria-label="选题分页">
            <div>
              <strong>共 {totalIdeas} 个选题</strong>
              <span>当前显示 {pageStart}-{pageEnd}</span>
            </div>
            <div className="studio-page-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={!pagination || currentPage <= 1}
                onClick={() => onPageChange?.((pagination?.page ?? 0) - 1)}
              >
                上一页
              </button>
              <button
                type="button"
                className="compact-button"
                disabled={!pagination || currentPage >= totalPages}
                onClick={() => onPageChange?.((pagination?.page ?? 0) + 1)}
              >
                下一页
              </button>
            </div>
          </div>
        </main>
      </div>
    </section>
  );
}

function countBy(values: string[]) {
  return values.reduce<Record<string, number>>((result, value) => {
    result[value] = (result[value] || 0) + 1;
    return result;
  }, {});
}

function statusTone(status: string) {
  if (status === 'READY' || status === 'PUBLISHED') return 'ready';
  if (status === 'DRAFTING') return 'drafting';
  if (status === 'ARCHIVED') return 'archived';
  return 'idea';
}
