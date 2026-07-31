import { KnowledgeEvidence, KnowledgeTopicWorkspace } from '../knowledgeTypes';

export type VerificationStatus = 'NEEDS_PRIMARY' | 'RECORDED';

export type VerificationQueueItem = {
  id: string;
  recognitionId: number;
  recognitionName: string;
  recognitionDescription?: string;
  eventId: number;
  eventTitle: string;
  proposition: string;
  status: VerificationStatus;
  materials: KnowledgeEvidence[];
  maxConfidence: number;
  updatedAt?: string;
};

const propositionTypes = new Set(['FACT', 'TIMELINE']);
const officialTiers = new Set(['REGULATOR', 'OFFICIAL', 'COMPANY']);
const regulatorHosts = [
  'sec.gov',
  'sse.com.cn',
  'szse.cn',
  'bse.cn',
  'hkexnews.hk',
  'pbc.gov.cn',
  'csrc.gov.cn'
];
const excludedOfficialHosts = [
  'arxiv.org',
  'news.google.com',
  'sina.com.cn',
  'qq.com',
  '163.com',
  'sohu.com',
  'bloomberg.com',
  'reuters.com',
  'wsj.com',
  'ft.com',
  'techcrunch.com',
  'reddit.com',
  'x.com',
  'twitter.com',
  'youtube.com'
];

export function projectVerificationQueue(workspaces: KnowledgeTopicWorkspace[]): VerificationQueueItem[] {
  const projected: VerificationQueueItem[] = [];

  workspaces.forEach((workspace) => {
    const events = new Map(workspace.events.map((event) => [event.id, event]));
    const grouped = new Map<string, KnowledgeEvidence[]>();

    workspace.evidence.forEach((item) => {
      if (!isEligibleProposition(item)) return;
      const key = normalizeProposition(item.claim);
      grouped.set(key, [...(grouped.get(key) || []), item]);
    });

    grouped.forEach((materials, key) => {
      const first = materials[0];
      const event = events.get(first.eventId);
      if (!event) return;
      projected.push({
        id: `${workspace.topic.id}:${first.eventId}:${key}`,
        recognitionId: workspace.topic.id,
        recognitionName: workspace.topic.name,
        recognitionDescription: workspace.topic.description,
        eventId: first.eventId,
        eventTitle: event.canonicalTitle,
        proposition: first.claim.trim(),
        status: materials.some(isVerifiedPrimarySource) ? 'RECORDED' : 'NEEDS_PRIMARY',
        materials,
        maxConfidence: materials.reduce((maximum, item) => Math.max(maximum, item.confidence || 0), 0),
        updatedAt: event.lastMeaningfulUpdateAt
      });
    });
  });

  return projected.sort((left, right) => {
    const statusDifference = statusRank(left.status) - statusRank(right.status);
    if (statusDifference !== 0) return statusDifference;
    const leftTime = Date.parse(left.updatedAt || '') || 0;
    const rightTime = Date.parse(right.updatedAt || '') || 0;
    return rightTime - leftTime;
  });
}

function isEligibleProposition(item: KnowledgeEvidence) {
  const claim = item.claim?.trim();
  if (!claim || !item.articleUrl || !propositionTypes.has(item.evidenceType || '')) return false;
  if (claim.length > 240 || /[?？]$/.test(claim)) return false;
  if (/^\s*\[[^\]]+\]\(https?:\/\//i.test(claim)) return false;
  if (/\barxiv\s*:|\babstract\s*:/i.test(claim)) return false;
  return true;
}

function normalizeProposition(claim: string) {
  return claim.trim().replace(/\s+/g, ' ').toLowerCase();
}

function isVerifiedPrimarySource(item: KnowledgeEvidence) {
  if (!officialTiers.has(item.sourceTier) || !item.articleUrl) return false;
  let hostname: string;
  try {
    hostname = new URL(item.articleUrl).hostname.toLowerCase().replace(/^www\./, '');
  } catch {
    return false;
  }
  if (item.sourceTier === 'REGULATOR') {
    return hostname.endsWith('.gov')
      || hostname.includes('.gov.')
      || regulatorHosts.some((host) => hostname === host || hostname.endsWith(`.${host}`));
  }
  return !excludedOfficialHosts.some((host) => hostname === host || hostname.endsWith(`.${host}`));
}

function statusRank(status: VerificationStatus) {
  return status === 'NEEDS_PRIMARY' ? 0 : 1;
}
