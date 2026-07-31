import { KnowledgeTopic } from '../knowledgeTypes';

export type KnowledgeClassification = 'RECOGNITION' | 'MATERIAL';

export function classifyKnowledgeTopic(topic: KnowledgeTopic, entryCount = 0): KnowledgeClassification {
  const reviewed = topic.masteryStatus === 'REVIEWING' || topic.masteryStatus === 'MATURE';
  const deliberatelyDeveloped = topic.masteryStatus === 'BUILDING' && topic.revision > 0;
  return entryCount > 0 || reviewed || deliberatelyDeveloped
    ? 'RECOGNITION'
    : 'MATERIAL';
}

export function isFormalInvestmentRecognition(
  topic: KnowledgeTopic,
  acceptedTopicIds: ReadonlySet<number> = new Set<number>()
): boolean {
  if (acceptedTopicIds.has(topic.id)) return true;
  const hasArticleProvenance = (topic.articleCount || 0) > 0 || (topic.briefCount || 0) > 0;
  return !hasArticleProvenance && classifyKnowledgeTopic(topic) === 'RECOGNITION';
}
