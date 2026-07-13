import { KnowledgeTask } from '../knowledgeTypes';

const statusLabels: Record<string, string> = {
  SUGGESTED: '待确认建议', TODO: '待开始', IN_PROGRESS: '进行中', DONE: '已沉淀', DISMISSED: '已放弃'
};

export function LearningTaskCard({
  task,
  active,
  onSelect
}: {
  task: KnowledgeTask;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={active ? 'learning-task-card active' : 'learning-task-card'}
      aria-pressed={active}
      onClick={onSelect}
    >
      <span className={`knowledge-task-status status-${task.status.toLowerCase()}`}>
        {statusLabels[task.status]}
      </span>
      <strong>{task.question}</strong>
      <small>{task.whyNeeded || '打开任务查看研究背景和引用证据。'}</small>
      <span className="learning-task-meta">
        <span>{task.origin === 'AGENT' ? 'Agent 建议' : '人工任务'}</span>
        {task.priority !== undefined && <span>P{task.priority}</span>}
      </span>
    </button>
  );
}
