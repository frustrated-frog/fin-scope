import { describe, expect, test } from 'vitest';

import { EventCluster, EvidenceItem } from '../../../shared/types';
import { projectFactCandidates } from './factProjection';

const events: EventCluster[] = [
  { id: 1, canonicalTitle: '公司披露季度经营数据', themeCode: 'COMPANY', summary: '季度收入保持增长。' },
  { id: 2, canonicalTitle: '行业需求可能回暖', themeCode: 'INDUSTRY' },
  { id: 3, canonicalTitle: '政策发布进入执行期', themeCode: 'POLICY' }
];

function evidence(input: Partial<EvidenceItem> & Pick<EvidenceItem, 'id' | 'eventId'>): EvidenceItem {
  return {
    sourceTier: 'MEDIA',
    evidenceType: 'IMPACT',
    claim: `材料 ${input.id}`,
    confidence: 60,
    ...input
  };
}

describe('projectFactCandidates', () => {
  test('marks a candidate substantial only when direct and primary evidence are both present', () => {
    const result = projectFactCandidates(events, [
      evidence({ id: 11, eventId: 1, sourceTier: 'COMPANY', evidenceType: 'FACT', confidence: 82 }),
      evidence({ id: 21, eventId: 2, sourceTier: 'MEDIA', evidenceType: 'IMPACT', confidence: 91 }),
      evidence({ id: 31, eventId: 3, sourceTier: 'MEDIA', evidenceType: 'TIMELINE', confidence: 73 })
    ]);

    expect(result.map((item) => [item.event.id, item.verificationState])).toEqual([
      [1, 'SUBSTANTIAL'],
      [2, 'UNVERIFIED'],
      [3, 'NEEDS_CORROBORATION']
    ]);
    expect(result[0]).toMatchObject({ directEvidenceCount: 1, primaryEvidenceCount: 1, maxConfidence: 82 });
  });

  test('sorts supporting material by source quality before confidence', () => {
    const result = projectFactCandidates([events[0]], [
      evidence({ id: 12, eventId: 1, sourceTier: 'MEDIA', evidenceType: 'FACT', confidence: 98 }),
      evidence({ id: 13, eventId: 1, sourceTier: 'OFFICIAL', evidenceType: 'TIMELINE', confidence: 65 }),
      evidence({ id: 14, eventId: 1, sourceTier: 'COMPANY', evidenceType: 'FACT', confidence: 80 })
    ]);

    expect(result[0].evidence.map((item) => item.id)).toEqual([13, 14, 12]);
  });

  test('describes the missing verification coverage without treating the projection as persisted truth', () => {
    const [candidate] = projectFactCandidates([events[1]], [
      evidence({ id: 22, eventId: 2, sourceTier: 'MEDIA', evidenceType: 'IMPACT' })
    ]);

    expect(candidate.gaps).toEqual(['缺少可直接引用的事实或时间线材料', '缺少监管、官方或公司一手来源']);
  });
});
