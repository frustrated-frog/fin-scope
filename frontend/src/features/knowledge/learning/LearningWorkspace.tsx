import { useState } from 'react';

import { KnowledgeEntry, KnowledgeEntryInput, KnowledgeEvidence, KnowledgeTask, KnowledgeTopic } from '../knowledgeTypes';
import { LearningAnswerEditor } from './LearningAnswerEditor';
import { LearningTaskCard } from './LearningTaskCard';
import { SuggestionInbox } from './SuggestionInbox';

export function LearningWorkspace({
  tasks,
  topics,
  selectedTaskId,
  evidence,
  draft,
  onSelectTask,
  onAccept,
  onStart,
  onSaveDraft,
  onComplete,
  onDismiss,
  onOpenEvent
}: {
  tasks: KnowledgeTask[];
  topics: KnowledgeTopic[];
  selectedTaskId?: number;
  evidence: KnowledgeEvidence[];
  draft?: KnowledgeEntry;
  onSelectTask: (taskId: number) => void;
  onAccept: (taskId: number, topicId: number, revision: number) => Promise<void>;
  onStart: (taskId: number, revision: number) => Promise<void>;
  onSaveDraft: (taskId: number, input: KnowledgeEntryInput) => Promise<KnowledgeEntry | void>;
  onComplete: (taskId: number, input: KnowledgeEntryInput) => Promise<KnowledgeEntry | void>;
  onDismiss: (taskId: number, reason: string, revision: number) => Promise<void>;
  onOpenEvent: (eventId: number) => void;
}) {
  const activeTask = tasks.find((task) => task.id === selectedTaskId) || tasks[0];
  const [dismissReason, setDismissReason] = useState('');

  return (
    <section className="learning-workspace">
      <div className="learning-queue-panel">
        <div className="knowledge-section-heading">
          <div><p className="knowledge-kicker">Learning queue</p><h2>学习队列</h2><p>每个问题都要归入主题，并最终形成可复用的答案。</p></div>
        </div>
        <div className="learning-task-list">
          {tasks.length > 0 ? tasks.map((task) => (
            <LearningTaskCard key={task.id} task={task} active={activeTask?.id === task.id} onSelect={() => onSelectTask(task.id)} />
          )) : <div className="knowledge-library-empty"><strong>学习队列已经清空</strong><p>可以去建议收件箱确认新的问题，或回到主题档案继续整理已有结论。</p></div>}
        </div>
      </div>

      <section className="learning-task-workspace">
        {!activeTask ? <div className="learning-no-selection"><strong>选择一个问题开始</strong></div> : (
          <>
            <header>
              <div>
                <p className="knowledge-kicker">Question #{activeTask.id}</p>
                <h2>{activeTask.question}</h2>
                {activeTask.whyNeeded && <p>{activeTask.whyNeeded}</p>}
              </div>
              {activeTask.eventId && <button type="button" onClick={() => onOpenEvent(activeTask.eventId as number)}>查看来源事件</button>}
            </header>
            {activeTask.status === 'SUGGESTED' && (
              <SuggestionInbox task={activeTask} topics={topics} onAccept={onAccept} onDismiss={onDismiss} />
            )}
            {activeTask.status === 'TODO' && (
              <div className="learning-start-panel">
                <p>开始后，这个问题会进入进行中状态，工作台会优先提醒你继续完成。</p>
                <button className="knowledge-primary-button" type="button" onClick={() => onStart(activeTask.id, activeTask.revision)}>开始回答</button>
              </div>
            )}
            {activeTask.status === 'IN_PROGRESS' && (
              <>
                <LearningAnswerEditor task={activeTask} draft={draft} evidence={evidence} onSaveDraft={onSaveDraft} onComplete={onComplete} />
                <details className="learning-dismiss">
                  <summary>暂时放弃这个问题</summary>
                  <label>说明原因<textarea rows={2} value={dismissReason} onChange={(event) => setDismissReason(event.target.value)} /></label>
                  <button type="button" disabled={!dismissReason.trim()} onClick={() => onDismiss(activeTask.id, dismissReason, activeTask.revision)}>放弃任务</button>
                </details>
              </>
            )}
            {activeTask.status === 'DONE' && <div className="knowledge-callout"><strong>答案已经沉淀</strong><p>它已进入主题知识记录，并等待下一次复习或新证据触发更新。</p></div>}
          </>
        )}
      </section>
    </section>
  );
}
