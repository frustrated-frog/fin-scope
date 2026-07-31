import { FormEvent, useEffect, useState } from 'react';

import { KnowledgeTopic } from '../knowledgeTypes';
import { TopicCard } from './TopicCard';
import { classifyKnowledgeTopic } from './knowledgeClassification';

export function TopicLibrary({
  topics,
  loading,
  onSearch,
  onOpenTopic,
  onCreate
}: {
  topics: KnowledgeTopic[];
  loading: boolean;
  onSearch: (query: string) => Promise<void>;
  onOpenTopic: (topicId: number) => void;
  onCreate?: (input: { name: string; description: string }) => Promise<void>;
}) {
  const [query, setQuery] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const recognitions = topics.filter((topic) => classifyKnowledgeTopic(topic) === 'RECOGNITION');

  useEffect(() => {
    const timer = window.setTimeout(() => {
      onSearch(query.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query, onSearch]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!onCreate) return;
    await onCreate({ name: name.trim(), description: description.trim() });
    setName('');
    setDescription('');
    setDialogOpen(false);
  }

  return (
    <section className="topic-library">
      <div className="knowledge-section-heading topic-library-heading">
        <div>
          <p className="knowledge-kicker">长期判断，而不是文章收藏</p>
          <h2>投资认识</h2>
          <p>只有能够被新事实保持、修正或推翻的独立结论，才进入这里。</p>
        </div>
        <button className="knowledge-primary-button" type="button" onClick={() => setDialogOpen(true)}>新建投资问题</button>
      </div>

      <div className="topic-library-toolbar">
        <label>
          <span>搜索投资认识</span>
          <input
            type="search"
            aria-label="搜索投资认识"
            placeholder="名称、描述或关键词"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <span>{loading ? '正在检索…' : `共 ${recognitions.length} 个认识档案`}</span>
      </div>

      <div className="knowledge-topic-grid">
        {recognitions.length > 0 ? recognitions.map((topic) => (
          <TopicCard key={topic.id} topic={topic} classification="RECOGNITION" onOpen={() => onOpenTopic(topic.id)} />
        )) : (
          <div className="knowledge-library-empty">
            <strong>{query ? '没有匹配的认识' : '还没有形成投资认识'}</strong>
            <p>{query ? '换一个关键词，或清除筛选查看全部认识。' : '运行投资 Agent，或从一个能持续被事实检验的投资问题开始。'}</p>
          </div>
        )}
      </div>

      {dialogOpen && (
        <div className="knowledge-dialog-backdrop" role="presentation">
          <form className="knowledge-dialog" role="dialog" aria-modal="true" aria-labelledby="new-topic-title" onSubmit={submit}>
            <div>
              <p className="knowledge-kicker">New research file</p>
              <h3 id="new-topic-title">新建投资问题</h3>
              <p>写一个需要持续更新的判断问题，不要粘贴文章标题。</p>
            </div>
            <label>投资问题<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
            <label>判断范围<textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
            <div className="knowledge-dialog-actions">
              <button type="button" onClick={() => setDialogOpen(false)}>取消</button>
              <button className="knowledge-primary-button" type="submit" disabled={!onCreate}>建立认识档案</button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
