import { FormEvent, useState } from 'react';

import { api } from '../../shared/api/client';
import { splitLines } from '../../shared/brief/markdown';
import { Topic } from '../../shared/types';

export function TopicsView({
  topics,
  onChanged,
  onOpenTopic
}: {
  topics: Topic[];
  onChanged: () => Promise<void>;
  onOpenTopic: (topicId: number) => Promise<void>;
}) {
  const [name, setName] = useState('');

  async function createTopic(event: FormEvent) {
    event.preventDefault();
    await api('/api/topics', {
      method: 'POST',
      body: JSON.stringify({ name, description: '手动创建的学习主题', status: 'LEARNING' })
    });
    setName('');
    await onChanged();
  }

  return (
    <section className="split">
      <form className="panel form-panel" onSubmit={createTopic}>
        <div className="panel-heading">
          <h3>新增主题</h3>
          <span className="subtle-badge">Topic</span>
        </div>
        <label>
          主题名
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <button className="primary-button" type="submit">保存主题</button>
      </form>
      <section className="panel">
        <div className="panel-heading">
          <h3>主题库</h3>
          <span className="subtle-badge">{topics.length} topics</span>
        </div>
        <div className="item-list">
          {topics.map((topic) => (
            <article className="list-item" key={topic.id}>
              <div>
                <strong>{topic.name}</strong>
                <p>{topic.description || '暂无描述'}</p>
                <p className="topic-meta">
                  <span>关联文章 {topic.articleCount ?? 0}</span>
                  <span>关联简报 {topic.briefCount ?? 0}</span>
                </p>
                {topic.terms && <p className="topic-terms">{topic.terms}</p>}
                {topic.markdownPath && <p className="vault-path">{topic.markdownPath}</p>}
                <ul className="question-list">
                  {splitLines(topic.learningQuestions).slice(0, 2).map((question) => (
                    <li key={question}>{question}</li>
                  ))}
                </ul>
              </div>
              <div className="item-actions">
                <button className="compact-button" onClick={() => onOpenTopic(topic.id)}>查看详情</button>
                <span className="badge">{topic.status}</span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}
