import { KnowledgeTopicWorkspace } from '../knowledgeTypes';
import { KnowledgeMarkdown } from '../KnowledgeMarkdown';

export function TopicTimeline({ workspace }: { workspace: KnowledgeTopicWorkspace }) {
  const latest = workspace.entries[0];
  const steps = [
    workspace.events.length > 0 && { label: '来源事件', values: workspace.events.map((item) => item.canonicalTitle) },
    workspace.evidence.length > 0 && { label: '代表证据', values: workspace.evidence.map((item) => item.claim) },
    workspace.tasks.length > 0 && { label: '待回答问题', values: workspace.tasks.filter((item) => item.status !== 'DONE').map((item) => item.question) },
    workspace.entries.length > 0 && { label: '我的回答', values: workspace.entries.map((item) => item.contentMarkdown) },
    latest && { label: '当前结论 / 下一次复习', values: [latest.contentMarkdown] }
  ].filter(Boolean) as Array<{ label: string; values: string[] }>;

  return (
    <ol className="knowledge-thread" aria-label="知识脉络">
      {steps.filter((step) => step.values.length > 0).map((step) => (
        <li key={step.label}>
          <span aria-hidden="true" />
          <div><h3>{step.label}</h3>{step.values.slice(0, 4).map((value, index) => <KnowledgeMarkdown key={`${value}-${index}`} value={value} />)}</div>
        </li>
      ))}
    </ol>
  );
}
