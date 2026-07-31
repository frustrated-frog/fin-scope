import { KnowledgeTopic } from '../knowledgeTypes';

export type KnowledgeClassification = 'RECOGNITION' | 'MATERIAL';

export function classifyKnowledgeTopic(topic: KnowledgeTopic, entryCount = 0): KnowledgeClassification {
  const sourceCount = (topic.articleCount || 0) + (topic.briefCount || 0);
  const reviewed = topic.masteryStatus === 'REVIEWING' || topic.masteryStatus === 'MATURE';
  const deliberatelyDeveloped = topic.masteryStatus === 'BUILDING' && topic.revision > 0;
  return entryCount > 0 || sourceCount >= 2 || reviewed || deliberatelyDeveloped
    ? 'RECOGNITION'
    : 'MATERIAL';
}
