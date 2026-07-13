import { KnowledgeTopic } from '../knowledgeTypes';

export function ReviewQueue({ topics, onOpen }: { topics: KnowledgeTopic[]; onOpen: (id: number) => void }) {
  return <section className="review-queue"><div className="knowledge-section-heading"><div><p className="knowledge-kicker">Due reviews</p><h2>到期复习</h2><p>复习不是重读笔记，而是用新证据检查旧判断。</p></div></div><div className="review-queue-list">{topics.length ? topics.map((topic) => <button key={topic.id} type="button" onClick={() => onOpen(topic.id)}><span><strong>{topic.name}</strong><small>{topic.description || '检查当前判断是否仍然成立'}</small></span><span>开始复习 →</span></button>) : <div className="knowledge-library-empty"><strong>今天没有到期主题</strong><p>现有判断都在复习周期内。</p></div>}</div></section>;
}
