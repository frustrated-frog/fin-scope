import { FormEvent, useEffect, useState } from 'react';

import { KnowledgeTopic } from '../knowledgeTypes';
import { TopicCard } from './TopicCard';

export function TopicLibrary({
  topics,
  totalCount,
  loading,
  onSearch,
  onOpenTopic,
  onCreate
}: {
  topics: KnowledgeTopic[];
  totalCount: number;
  loading: boolean;
  onSearch: (query: string) => Promise<void>;
  onOpenTopic: (topicId: number) => void;
  onCreate?: (input: { name: string; description: string }) => Promise<void>;
}) {
  const [query, setQuery] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

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
          <p className="knowledge-kicker">Research archive</p>
          <h2>主题档案</h2>
          <p>按长期问题组织证据、答案和判断，而不是堆叠零散文章。</p>
        </div>
        <button className="knowledge-primary-button" type="button" onClick={() => setDialogOpen(true)}>新建主题</button>
      </div>

      <div className="topic-library-toolbar">
        <label>
          <span>搜索主题</span>
          <input
            type="search"
            aria-label="搜索主题"
            placeholder="名称、描述或关键词"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <span>{loading ? '正在检索…' : `${totalCount} 个主题`}</span>
      </div>

      <div className="knowledge-topic-grid">
        {topics.length > 0 ? topics.map((topic) => (
          <TopicCard key={topic.id} topic={topic} onOpen={() => onOpenTopic(topic.id)} />
        )) : (
          <div className="knowledge-library-empty">
            <strong>{query ? '没有匹配的主题' : '从第一个长期问题开始'}</strong>
            <p>{query ? '换一个关键词，或清除筛选查看全部档案。' : '例如：利率下行如何影响不同资产，或 Agent 产品的长期护城河是什么。'}</p>
          </div>
        )}
      </div>

      {dialogOpen && (
        <div className="knowledge-dialog-backdrop" role="presentation">
          <form className="knowledge-dialog" role="dialog" aria-modal="true" aria-labelledby="new-topic-title" onSubmit={submit}>
            <div>
              <p className="knowledge-kicker">New research file</p>
              <h3 id="new-topic-title">新建研究主题</h3>
              <p>主题应该描述一个需要持续更新的判断，不是一篇文章的标题。</p>
            </div>
            <label>主题名称<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
            <label>研究范围<textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
            <div className="knowledge-dialog-actions">
              <button type="button" onClick={() => setDialogOpen(false)}>取消</button>
              <button className="knowledge-primary-button" type="submit" disabled={!onCreate}>建立档案</button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
