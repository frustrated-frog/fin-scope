import { KnowledgeTopic } from '../knowledgeTypes';

export type KnowledgeClassification = 'RECOGNITION' | 'MATERIAL';

export function classifyKnowledgeTopic(topic: KnowledgeTopic, entryCount = 0): KnowledgeClassification {
  const sourceCount = (topic.articleCount || 0) + (topic.briefCount || 0);
  return entryCount > 0 || topic.masteryStatus !== 'EXPLORING' || sourceCount >= 2
    ? 'RECOGNITION'
    : 'MATERIAL';
}
