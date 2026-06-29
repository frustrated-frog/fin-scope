import { api } from '../../shared/api/client';
import { Brief } from '../../shared/types';

export function BriefsView({
  briefs,
  onChanged,
  setMessage,
  onOpenBrief,
  onAfterCompound
}: {
  briefs: Brief[];
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
  onOpenBrief: (date: string) => Promise<void>;
  onAfterCompound: () => void;
}) {
  async function generateBrief() {
    await api('/api/briefs/generate', { method: 'POST' });
    setMessage('今日简报已生成');
    await onChanged();
  }

  async function compoundBrief(date: string) {
    await api(`/api/topics/from-brief/${date}`, { method: 'POST' });
    setMessage('简报已沉淀到主题库');
    await onChanged();
    onAfterCompound();
  }

  return (
    <section className="panel wide">
      <div className="panel-heading">
        <h3>每日简报</h3>
        <button className="primary-button" onClick={generateBrief}>生成今日简报</button>
      </div>
      <div className="item-list">
        {briefs.map((brief) => (
          <article className="list-item" key={brief.id}>
            <div>
              <strong>{brief.title}</strong>
              <p>{brief.markdownPath}</p>
            </div>
            <div className="item-actions">
              <button className="ghost-button" onClick={() => onOpenBrief(brief.briefDate)}>查看简报</button>
              <button className="compact-button" onClick={() => compoundBrief(brief.briefDate)}>沉淀主题</button>
              <span className="badge">{brief.briefDate}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
