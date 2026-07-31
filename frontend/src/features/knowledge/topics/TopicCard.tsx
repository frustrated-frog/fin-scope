import { KnowledgeTopic } from '../knowledgeTypes';
import { KnowledgeClassification } from './knowledgeClassification';

const masteryLabels: Record<string, string> = {
  EXPLORING: '探索中', BUILDING: '构建中', REVIEWING: '复习中', MATURE: '已成熟'
};

export function TopicCard({ topic, classification, onOpen }: { topic: KnowledgeTopic; classification: KnowledgeClassification; onOpen: () => void }) {
  return (
    <article className="knowledge-topic-card">
      <div className="knowledge-topic-card-head">
        <span className={`knowledge-state state-${topic.masteryStatus.toLowerCase()}`}>
          {classification === 'MATERIAL' ? '待提炼材料' : masteryLabels[topic.masteryStatus]}
        </span>
        <span className="knowledge-topic-count">{topic.articleCount || 0} 篇资料</span>
      </div>
      <h3>{topic.name}</h3>
      <p>{topic.description || (classification === 'MATERIAL' ? '这还是来源材料，尚未提炼成可验证的投资判断。' : '尚未定义认识边界和需要验证的核心问题。')}</p>
      <dl>
        <div><dt>关联简报</dt><dd>{topic.briefCount || 0}</dd></div>
        <div><dt>修订版本</dt><dd>v{topic.revision}</dd></div>
      </dl>
      <button type="button" onClick={onOpen}>{classification === 'MATERIAL' ? '打开并提炼' : '打开认识档案'} <span aria-hidden="true">→</span></button>
    </article>
  );
}
