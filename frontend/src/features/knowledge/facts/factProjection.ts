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
    const verificationState: FactVerificationState = directEvidenceCount > 0 && primaryEvidenceCount > 0
      ? 'SUBSTANTIAL'
      : directEvidenceCount > 0 || primaryEvidenceCount > 0
        ? 'NEEDS_CORROBORATION'
        : 'UNVERIFIED';

    return {
      event,
      evidence,
      verificationState,
      directEvidenceCount,
      primaryEvidenceCount,
      maxConfidence: evidence.reduce((maximum, item) => Math.max(maximum, item.confidence || 0), 0),
      gaps
    };
  }).sort(compareCandidates);
}

function compareEvidence(left: EvidenceItem, right: EvidenceItem) {
  const tierDifference = (tierRanks[right.sourceTier] ?? -1) - (tierRanks[left.sourceTier] ?? -1);
  return tierDifference || (right.confidence || 0) - (left.confidence || 0);
}

function compareCandidates(left: FactCandidate, right: FactCandidate) {
  const rankDifference = actionabilityRank(left) - actionabilityRank(right);
  if (rankDifference !== 0) return rankDifference;
  const leftTime = Date.parse(left.event.lastMeaningfulUpdateAt || left.event.updatedAt || left.event.lastSeenAt || '') || 0;
  const rightTime = Date.parse(right.event.lastMeaningfulUpdateAt || right.event.updatedAt || right.event.lastSeenAt || '') || 0;
  return rightTime - leftTime || right.event.id - left.event.id;
}

function actionabilityRank(candidate: FactCandidate) {
  if (candidate.verificationState === 'NEEDS_CORROBORATION') return 0;
  if (candidate.verificationState === 'UNVERIFIED' && candidate.evidence.length > 0) return 1;
  if (candidate.verificationState === 'SUBSTANTIAL') return 2;
  return 3;
}
