import { useState } from 'react';

import { KnowledgeTask, KnowledgeTopic } from '../knowledgeTypes';

export function SuggestionInbox({
  task,
  topics,
  onAccept,
  onDismiss
}: {
  task: KnowledgeTask;
  topics: KnowledgeTopic[];
  onAccept: (taskId: number, topicId: number, revision: number) => Promise<void>;
  onDismiss: (taskId: number, reason: string, revision: number) => Promise<void>;
}) {
  const [topicId, setTopicId] = useState('');
  const [dismissReason, setDismissReason] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(command: () => Promise<void>) {
    setBusy(true);
    try { await command(); } finally { setBusy(false); }
  }

  return (
    <div className="suggestion-decision">
      <div className="knowledge-callout">
        <strong>这是一条建议，不是你的承诺</strong>
        <p>Agent 发现它可能补齐当前知识缺口。确认价值并选择归属主题后，它才会进入学习队列。</p>
      </div>
      <label>归入主题
        <select aria-label="归入主题" value={topicId} onChange={(event) => setTopicId(event.target.value)}>
          <option value="">选择一个主题</option>
          {topics.map((topic) => <option key={topic.id} value={topic.id}>{topic.name}</option>)}
        </select>
      </label>
      <button
        className="knowledge-primary-button"
        type="button"
        disabled={!topicId || busy}
        onClick={() => run(() => onAccept(task.id, Number(topicId), task.revision))}
      >接受到学习队列</button>
      <details>
        <summary>这次不处理</summary>
        <label>原因（可选）<textarea value={dismissReason} onChange={(event) => setDismissReason(event.target.value)} rows={2} /></label>
        <button type="button" disabled={busy} onClick={() => run(() => onDismiss(task.id, dismissReason, task.revision))}>忽略建议</button>
      </details>
    </div>
  );
}
