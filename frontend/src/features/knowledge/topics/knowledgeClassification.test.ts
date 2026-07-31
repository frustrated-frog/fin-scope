import { describe, expect, test } from 'vitest';

import { KnowledgeTopic } from '../knowledgeTypes';
import { classifyKnowledgeTopic } from './knowledgeClassification';

function topic(input: Partial<KnowledgeTopic> = {}): KnowledgeTopic {
  return {
    id: 1,
    name: '某篇文章的自动摘要',
    lifecycleStatus: 'ACTIVE',
    masteryStatus: 'EXPLORING',
    revision: 1,
    articleCount: 1,
    briefCount: 0,
    ...input
  };
}

describe('classifyKnowledgeTopic', () => {
  test('treats a single-source exploring item as material instead of investment recognition', () => {
    expect(classifyKnowledgeTopic(topic())).toBe('MATERIAL');
  });

  test('does not confuse multiple source articles with a formed recognition', () => {
    expect(classifyKnowledgeTopic(topic({ articleCount: 2, briefCount: 1 }))).toBe('MATERIAL');
  });

  test('recognizes actively developed files or files with a conclusion entry', () => {
    expect(classifyKnowledgeTopic(topic({ masteryStatus: 'BUILDING', revision: 2 }))).toBe('RECOGNITION');
    expect(classifyKnowledgeTopic(topic(), 1)).toBe('RECOGNITION');
  });

  test('keeps an automatically marked building file as material until it is revised', () => {
    expect(classifyKnowledgeTopic(topic({ masteryStatus: 'BUILDING', revision: 0 }))).toBe('MATERIAL');
  });
});
