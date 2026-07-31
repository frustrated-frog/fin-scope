import { describe, expect, test } from 'vitest';

import { KnowledgeEvidence, KnowledgeTopicWorkspace } from '../knowledgeTypes';
import { projectVerificationQueue } from './verificationQueueProjection';

function workspace(evidence: KnowledgeEvidence[], overrides: Partial<KnowledgeTopicWorkspace> = {}): KnowledgeTopicWorkspace {
  return {
    topic: {
      id: 7,
      name: '先进封装供需可能进入上行周期',
      description: '持续检验产能利用率、价格和资本开支。',
      lifecycleStatus: 'ACTIVE',
      masteryStatus: 'REVIEWING',
      revision: 2,
      articleCount: 3
    },
    events: [{
      id: 31,
      canonicalTitle: '公司发布季度经营更新',
      importanceScore: 82,
      lastMeaningfulUpdateAt: '2026-07-31T08:00:00'
    }],
    evidence,
    tasks: [],
    entries: [{
      id: 71,
      topicId: 7,
      entryType: 'THESIS',
      entryStatus: 'FINAL',
      contentMarkdown: '先进封装需求正在改善。',
      confidence: 'MEDIUM',
      revision: 1
    }],
    ...overrides
  };
}

function evidence(input: Partial<KnowledgeEvidence> & Pick<KnowledgeEvidence, 'id' | 'claim'>): KnowledgeEvidence {
  return {
    eventId: 31,
    sourceTier: 'MEDIA',
    evidenceType: 'FACT',
    confidence: 70,
    articleTitle: `材料 ${input.id}`,
    articleUrl: `https://news.example.com/${input.id}`,
    ...input
  };
}

describe('projectVerificationQueue', () => {
  test('creates atomic propositions only from FACT or TIMELINE evidence linked to a recognition', () => {
    const result = projectVerificationQueue([workspace([
      evidence({ id: 1, claim: '公司披露二季度先进封装收入同比增长 28%。' }),
      evidence({ id: 2, claim: '公司预计新产线将在九月投产。', evidenceType: 'TIMELINE' }),
      evidence({ id: 3, claim: '先进封装会成为下一个超级周期。', evidenceType: 'IMPACT' })
    ])]);

    expect(result.map((item) => item.proposition)).toEqual([
      '公司披露二季度先进封装收入同比增长 28%。',
      '公司预计新产线将在九月投产。'
    ]);
    expect(result[0]).toMatchObject({
      recognitionId: 7,
      recognitionName: '先进封装供需可能进入上行周期',
      eventId: 31,
      status: 'NEEDS_PRIMARY'
    });
  });

  test('rejects article-shaped content and evidence without a traceable source', () => {
    const longClaim = `这是一整段文章摘要，${'包含大量没有必要进入核验队列的内容。'.repeat(18)}`;
    const result = projectVerificationQueue([workspace([
      evidence({ id: 4, claim: '[某公司季度更新](https://news.example.com/story)' }),
      evidence({ id: 5, claim: 'arXiv:2601.00001 Abstract: We propose a new method.' }),
      evidence({ id: 6, claim: '行业未来是否会进入上行周期？' }),
      evidence({ id: 7, claim: longClaim }),
      evidence({ id: 8, claim: '公司披露季度收入同比增长 18%。', articleUrl: undefined })
    ])]);

    expect(result).toEqual([]);
  });

  test('does not trust a regulator label without a regulator host', () => {
    const [arxiv, regulator, company] = projectVerificationQueue([workspace([
      evidence({
        id: 9,
        claim: '论文报告样本准确率达到 91%。',
        sourceTier: 'REGULATOR',
        articleUrl: 'https://arxiv.org/abs/2601.00001'
      }),
      evidence({
        id: 10,
        claim: '交易所公告公司股票将于八月恢复交易。',
        sourceTier: 'REGULATOR',
        articleUrl: 'https://www.sse.com.cn/disclosure/notice/10'
      }),
      evidence({
        id: 11,
        claim: '公司公告新产线已完成设备搬入。',
        sourceTier: 'COMPANY',
        articleUrl: 'https://investor.example-corp.com/releases/11'
      })
    ])]);

    expect(arxiv.status).toBe('NEEDS_PRIMARY');
    expect(regulator.status).toBe('RECORDED');
    expect(company.status).toBe('RECORDED');
  });

  test('deduplicates the same proposition inside one recognition and keeps its source records', () => {
    const result = projectVerificationQueue([workspace([
      evidence({ id: 12, claim: '公司披露二季度先进封装收入同比增长 28%。' }),
      evidence({ id: 13, claim: ' 公司披露二季度先进封装收入同比增长 28%。 ' })
    ])]);

    expect(result).toHaveLength(1);
    expect(result[0].materials.map((item) => item.id)).toEqual([12, 13]);
  });
});
