import { describe, expect, test } from 'vitest';

import { ResearchRadarSnapshot } from '../../news/researchRadarTypes';
import { projectDailyResearch } from './dailyResearchProjection';

const snapshot: ResearchRadarSnapshot = {
  overview: { eventCount: 3, highPriorityCount: 1, watchlistRelatedCount: 1, sourceCount: 6 },
  events: [
    { id: 1, title: '较早变化', summary: '摘要', priorityScore: 62, recommendation: '值得浏览', reasons: [], watchlistRelated: false, watchlistExplanation: '', sourceCount: 1, signalCount: 1, uncertainty: '仍需确认', nextObservation: '跟踪公告', suggestedResearchQuestion: '', lastSeenAt: '2026-08-01T08:00:00' },
    { id: 2, title: '高优先级变化', summary: '摘要', priorityScore: 88, recommendation: '重点关注', reasons: [], watchlistRelated: true, watchlistExplanation: '与持仓相关', sourceCount: 3, signalCount: 3, uncertainty: '订单持续性未知', nextObservation: '等待经营数据', suggestedResearchQuestion: '', lastSeenAt: '2026-08-01T09:00:00' },
    { id: 3, title: '同分但更新', summary: '摘要', priorityScore: 62, recommendation: '值得浏览', reasons: [], watchlistRelated: false, watchlistExplanation: '', sourceCount: 2, signalCount: 2, uncertainty: '影响范围未知', nextObservation: '观察价格', suggestedResearchQuestion: '', lastSeenAt: '2026-08-01T10:00:00' }
  ],
  liveItems: [
    { id: 'a', kind: 'FLASH', title: '公司发布新产品', content: '公司发布新产品。', publishedAt: '2026-08-01T09:00:00', providerCode: 'CLS', sourceName: '财联社', sourceTier: 'MEDIA' },
    { id: 'b', kind: 'FLASH', title: '公司发布新产品！', content: '重复快讯', publishedAt: '2026-08-01T09:05:00', providerCode: 'THS', sourceName: '同花顺', sourceTier: 'MEDIA' },
    { id: 'c', kind: 'ARTICLE', title: '深度文章', content: '不是快讯', publishedAt: '2026-08-01T10:00:00', providerCode: 'WEB', sourceName: '媒体', sourceTier: 'MEDIA' },
    { id: 'd', kind: 'FLASH', title: '政策发布', content: '政策内容', publishedAt: '2026-08-01T11:00:00', providerCode: 'OFFICIAL', sourceName: '官方', sourceTier: 'OFFICIAL' }
  ],
  warnings: ['一个来源暂不可用'],
  refreshedAt: '2026-08-01T11:01:00'
};

describe('projectDailyResearch', () => {
  test('orders market changes by research value and recency', () => {
    const result = projectDailyResearch(snapshot);

    expect(result.changes.map((item) => item.id)).toEqual([2, 3, 1]);
  });

  test('keeps only deduplicated flashes and orders them newest first', () => {
    const result = projectDailyResearch(snapshot);

    expect(result.flashes.map((item) => item.id)).toEqual(['d', 'b']);
    expect(result.warnings).toEqual(['一个来源暂不可用']);
  });
});
