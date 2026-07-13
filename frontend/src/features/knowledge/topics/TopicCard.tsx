import { KnowledgeTopic } from '../knowledgeTypes';

const masteryLabels: Record<string, string> = {
  EXPLORING: '探索中', BUILDING: '构建中', STABLE: '已稳定', MASTERED: '已掌握'
};

export function TopicCard({ topic, onOpen }: { topic: KnowledgeTopic; onOpen: () => void }) {
  return (
    <article className="knowledge-topic-card">
      <div className="knowledge-topic-card-head">
        <span className={`knowledge-state state-${topic.masteryStatus.toLowerCase()}`}>
          {masteryLabels[topic.masteryStatus]}
        </span>
        <span className="knowledge-topic-count">{topic.articleCount || 0} 篇资料</span>
      </div>
      <h3>{topic.name}</h3>
      <p>{topic.description || '尚未定义主题边界和需要验证的核心问题。'}</p>
      <dl>
        <div><dt>关联简报</dt><dd>{topic.briefCount || 0}</dd></div>
        <div><dt>修订版本</dt><dd>v{topic.revision}</dd></div>
      </dl>
      <button type="button" onClick={onOpen}>打开主题档案 <span aria-hidden="true">→</span></button>
    </article>
  );
}
