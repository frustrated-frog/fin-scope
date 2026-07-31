import { KnowledgeTopic } from '../knowledgeTypes';

export type KnowledgeClassification = 'RECOGNITION' | 'MATERIAL';

export function classifyKnowledgeTopic(topic: KnowledgeTopic, entryCount = 0): KnowledgeClassification {
  const reviewed = topic.masteryStatus === 'REVIEWING' || topic.masteryStatus === 'MATURE';
  const deliberatelyDeveloped = topic.masteryStatus === 'BUILDING' && topic.revision > 0;
  return entryCount > 0 || reviewed || deliberatelyDeveloped
    ? 'RECOGNITION'
    : 'MATERIAL';
}
