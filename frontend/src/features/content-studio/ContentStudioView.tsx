import { useMemo, useState } from 'react';

import { ContentIdea } from '../../shared/types';

const ideaStatuses = ['IDEA', 'DRAFTING', 'READY', 'PUBLISHED', 'ARCHIVED'];

export function ContentStudioView({
  contentIdeas,
  onIdeaStatusChange,
  addToast
}: {
  contentIdeas: ContentIdea[];
  onIdeaStatusChange: (ideaId: number, status: string) => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [draftStatuses, setDraftStatuses] = useState<Record<number, string>>({});

  const visibleStatuses = useMemo(() => {
    const current = { ...draftStatuses };
    contentIdeas.forEach((idea) => {
      if (!current[idea.id]) {
        current[idea.id] = idea.status || 'IDEA';
      }
    });
    return current;
  }, [contentIdeas, draftStatuses]);

  async function saveIdeaStatus(idea: ContentIdea) {
    await onIdeaStatusChange(idea.id, visibleStatuses[idea.id] || idea.status || 'IDEA');
    addToast('选题状态已更新', 'success');
  }

  return (
    <section className="panel wide content-studio-panel">
      <div className="panel-heading">
        <h3>Content Studio</h3>
        <span className="subtle-badge">{contentIdeas.length} ideas</span>
      </div>
      <div className="studio-grid">
        {contentIdeas.map((idea) => (
          <article className="studio-card content-studio-card" key={idea.id}>
            <div className="studio-card-top">
              <span className="studio-score">{idea.score}</span>
              <span className="badge">{idea.format}</span>
            </div>
            <strong>{idea.title}</strong>
            {idea.angle && <p>{idea.angle}</p>}
            {idea.scoreReason && <p className="muted">{idea.scoreReason}</p>}
            {idea.audience && <p className="muted">{idea.audience}</p>}
            {idea.outline && <pre className="studio-outline">{idea.outline}</pre>}
            <div className="task-status-row">
              <label className="inline-select">
                <span>选题状态</span>
                <select
                  aria-label={`内容选题状态-${idea.id}`}
                  value={visibleStatuses[idea.id] || idea.status || 'IDEA'}
                  onChange={(event) => setDraftStatuses((current) => ({
                    ...current,
                    [idea.id]: event.target.value
                  }))}
                >
                  {ideaStatuses.map((status) => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
              </label>
              <button className="compact-button" type="button" onClick={() => saveIdeaStatus(idea)}>
                保存选题状态
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
