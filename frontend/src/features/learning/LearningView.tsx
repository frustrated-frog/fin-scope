import { FormEvent, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { splitLines, themeLabel } from '../../shared/brief/markdown';
import { LearningTask, Topic, TopicDetail } from '../../shared/types';

const taskStatuses = ['TODO', 'LEARNING', 'REVIEWING', 'DONE'];

export function LearningView({
  topics,
  learningTasks,
  topicDetail,
  onOpenTopic,
  onOpenEvent,
  onChanged,
  onTaskStatusChange,
  setMessage,
  addToast
}: {
  topics: Topic[];
  learningTasks: LearningTask[];
  topicDetail: TopicDetail | null;
  onOpenTopic: (topicId: number) => Promise<void>;
  onOpenEvent: (eventId: number) => void;
  onChanged: () => Promise<void>;
  onTaskStatusChange: (taskId: number, status: string) => Promise<void>;
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [status, setStatus] = useState('LEARNING');
  const [note, setNote] = useState('');
  const [themeFilter, setThemeFilter] = useState('ALL');
  const [taskStatusDrafts, setTaskStatusDrafts] = useState<Record<number, string>>({});
  const activeTopic = topicDetail?.topic;

  const visibleTaskStatuses = useMemo(() => {
    const current = { ...taskStatusDrafts };
    learningTasks.forEach((task) => {
      if (!current[task.id]) {
        current[task.id] = task.status;
      }
    });
    return current;
  }, [learningTasks, taskStatusDrafts]);
  const taskThemeOptions = useMemo(
    () => Array.from(new Set(learningTasks.map((task) => task.themeCode))).filter(Boolean),
    [learningTasks]
  );
  const visibleTasks = useMemo(
    () => themeFilter === 'ALL' ? learningTasks : learningTasks.filter((task) => task.themeCode === themeFilter),
    [learningTasks, themeFilter]
  );

  async function appendNote(event: FormEvent) {
    event.preventDefault();
    if (!activeTopic) {
      return;
    }
    await api(`/api/topics/${activeTopic.id}/notes`, {
      method: 'POST',
      body: JSON.stringify({ status, note })
    });
    setNote('');
    setMessage('个人理解已写入主题 Markdown');
    await onChanged();
    await onOpenTopic(activeTopic.id);
  }

  async function updateTaskStatus(task: LearningTask) {
    const nextStatus = visibleTaskStatuses[task.id] || task.status;
    await onTaskStatusChange(task.id, nextStatus);
    addToast('学习任务状态已更新', 'success');
  }

  return (
    <section className="learning-grid">
      <section className="panel learning-queue">
        <div className="panel-heading">
          <h3>学习队列</h3>
          <label className="inline-select">
            <span>学习主题筛选</span>
            <select
              aria-label="学习主题筛选"
              value={themeFilter}
              onChange={(event) => setThemeFilter(event.target.value)}
            >
              <option value="ALL">ALL</option>
              {taskThemeOptions.map((themeCode) => (
                <option key={themeCode} value={themeCode}>{themeLabel(themeCode)}</option>
              ))}
            </select>
          </label>
        </div>
        {visibleTasks.length > 0 && (
          <div className="research-task-stack">
            {visibleTasks.map((task) => (
              <article className="research-task-card" key={task.id}>
                <div className="research-task-head">
                  <span className="badge">{task.status}</span>
                  <span>{themeLabel(task.themeCode)}</span>
                </div>
                <strong>{task.question}</strong>
                {task.concepts && <p>{task.concepts}</p>}
                <div className="task-status-row">
                  <label className="inline-select">
                    <span>任务状态</span>
                    <select
                      aria-label={`学习任务状态-${task.id}`}
                      value={visibleTaskStatuses[task.id] || task.status}
                      onChange={(event) => setTaskStatusDrafts((current) => ({
                        ...current,
                        [task.id]: event.target.value
                      }))}
                    >
                      {taskStatuses.map((item) => (
                        <option key={item} value={item}>{item}</option>
                      ))}
                    </select>
                  </label>
                  <button
                    aria-label={`更新任务状态-${task.id}`}
                    className="compact-button"
                    type="button"
                    onClick={() => updateTaskStatus(task)}
                  >
                    更新任务状态
                  </button>
                  {task.eventId ? (
                    <button
                      aria-label={`查看关联事件-${task.eventId}`}
                      className="compact-button"
                      type="button"
                      onClick={() => onOpenEvent(task.eventId)}
                    >
                      查看关联事件
                    </button>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
        )}
        <div className="item-list">
          {topics.map((topic) => (
            <article className="list-item" key={topic.id}>
              <div>
                <strong>{topic.name}</strong>
                <p>{topic.description || '暂无描述'}</p>
                <ul className="question-list">
                  {splitLines(topic.learningQuestions).slice(0, 2).map((question) => (
                    <li key={question}>{question}</li>
                  ))}
                </ul>
              </div>
              <button className="compact-button learning-action-button" onClick={() => onOpenTopic(topic.id)}>记录理解</button>
            </article>
          ))}
        </div>
      </section>

      <section className="panel detail-panel">
        {!activeTopic ? (
          <p className="muted">选择一个主题后记录自己的理解。</p>
        ) : (
          <>
            <div className="panel-heading">
              <div>
                <h3>{activeTopic.name}</h3>
                <p className="muted">{activeTopic.markdownPath}</p>
              </div>
              <span className="badge">{activeTopic.status}</span>
            </div>
            <p>{activeTopic.description}</p>
            <div className="topic-links">
              <div>
                <strong>关联文章</strong>
                <ul>
                  {topicDetail.linkedArticles.map((article) => (
                    <li key={article.id}>{article.title}</li>
                  ))}
                </ul>
              </div>
              <div>
                <strong>关联简报</strong>
                <ul>
                  {topicDetail.linkedBriefs.map((brief) => (
                    <li key={brief.id}>{brief.title}</li>
                  ))}
                </ul>
              </div>
            </div>
            <form className="note-form" onSubmit={appendNote}>
              <label>
                学习状态
                <select value={status} onChange={(event) => setStatus(event.target.value)}>
                  <option value="LEARNING">学习中</option>
                  <option value="REVIEWING">复盘中</option>
                  <option value="MATURE">可输出</option>
                </select>
              </label>
              <label>
                个人理解
                <textarea value={note} onChange={(event) => setNote(event.target.value)} required rows={5} />
              </label>
              <button className="primary-button" type="submit">保存理解</button>
            </form>
            <pre className="markdown-preview">{topicDetail.markdown}</pre>
          </>
        )}
      </section>
    </section>
  );
}
