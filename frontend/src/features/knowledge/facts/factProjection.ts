import { EventCluster, EvidenceItem } from '../../../shared/types';

export type FactVerificationState = 'SUBSTANTIAL' | 'NEEDS_CORROBORATION' | 'UNVERIFIED';

export type FactCandidate = {
  event: EventCluster;
  evidence: EvidenceItem[];
  verificationState: FactVerificationState;
  directEvidenceCount: number;
  primaryEvidenceCount: number;
  maxConfidence: number;
  gaps: string[];
};

const directTypes = new Set(['FACT', 'TIMELINE']);
const primaryTiers = new Set(['REGULATOR', 'OFFICIAL', 'COMPANY']);
const tierRanks: Record<string, number> = {
  REGULATOR: 4,
  OFFICIAL: 3,
  COMPANY: 2,
  RESEARCH: 1,
  MEDIA: 0
};

export function projectFactCandidates(events: EventCluster[], evidenceItems: EvidenceItem[]): FactCandidate[] {
  const evidenceByEvent = new Map<number, EvidenceItem[]>();
  evidenceItems.forEach((item) => {
    evidenceByEvent.set(item.eventId, [...(evidenceByEvent.get(item.eventId) || []), item]);
  });

  return events.map((event) => {
    const evidence = [...(evidenceByEvent.get(event.id) || [])].sort(compareEvidence);
    const directEvidenceCount = evidence.filter((item) => directTypes.has(item.evidenceType)).length;
    const primaryEvidenceCount = evidence.filter((item) => primaryTiers.has(item.sourceTier)).length;
    const gaps: string[] = [];
    if (directEvidenceCount === 0) gaps.push('缺少可直接引用的事实或时间线材料');
    if (primaryEvidenceCount === 0) gaps.push('缺少监管、官方或公司一手来源');

    return {
      event,
      evidence,
      verificationState: directEvidenceCount > 0 && primaryEvidenceCount > 0
        ? 'SUBSTANTIAL'
        : directEvidenceCount > 0 || primaryEvidenceCount > 0
          ? 'NEEDS_CORROBORATION'
          : 'UNVERIFIED',
      directEvidenceCount,
      primaryEvidenceCount,
      maxConfidence: evidence.reduce((maximum, item) => Math.max(maximum, item.confidence || 0), 0),
      gaps
    };
  });
}

function compareEvidence(left: EvidenceItem, right: EvidenceItem) {
  const tierDifference = (tierRanks[right.sourceTier] ?? -1) - (tierRanks[left.sourceTier] ?? -1);
  return tierDifference || (right.confidence || 0) - (left.confidence || 0);
}
