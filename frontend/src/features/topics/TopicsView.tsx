import { FormEvent, useState } from 'react';

import { api } from '../../shared/api/client';
import { splitLines } from '../../shared/brief/markdown';
import { Topic } from '../../shared/types';

export function TopicsView({
  topics,
  onChanged,
  onOpenTopicReader,
  onDeleteTopic
}: {
  topics: Topic[];
  onChanged: () => Promise<void>;
  onOpenTopicReader: (topicId: number) => Promise<void>;
  onDeleteTopic: (topic: Topic) => void;
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
        <div className="item-list topic-list">
          {topics.map((topic) => (
            <article className="list-item topic-list-item" key={topic.id}>
              <div className="topic-card-body">
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
              <div className="topic-card-actions" role="group" aria-label="主题操作">
                <button className="compact-button" type="button" onClick={() => onOpenTopicReader(topic.id)}>查看详情</button>
                <button className="compact-button topic-delete-button" type="button" onClick={() => onDeleteTopic(topic)}>删除主题</button>
                <span className="badge">{topic.status}</span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}
