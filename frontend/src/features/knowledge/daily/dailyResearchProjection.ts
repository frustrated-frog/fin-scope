import { RadarEvent, RadarNewsItem, ResearchRadarSnapshot } from '../../news/researchRadarTypes';

export type DailyResearchProjection = {
  changes: RadarEvent[];
  flashes: RadarNewsItem[];
  warnings: string[];
  refreshedAt?: string;
};

export function projectDailyResearch(snapshot?: ResearchRadarSnapshot | null): DailyResearchProjection {
  if (!snapshot) return { changes: [], flashes: [], warnings: [] };

  const events = Array.isArray(snapshot.events) ? snapshot.events : [];
  const liveItems = Array.isArray(snapshot.liveItems) ? snapshot.liveItems : [];
  const warnings = Array.isArray(snapshot.warnings) ? snapshot.warnings : [];
  const changes = [...events]
    .filter((event) => event.priorityScore >= 55 || event.watchlistRelated)
    .sort((left, right) => right.priorityScore - left.priorityScore || timestamp(right.lastSeenAt) - timestamp(left.lastSeenAt))
    .slice(0, 5);
  const seen = new Set<string>();
  const flashes = [...liveItems]
    .filter((item) => item.kind === 'FLASH')
    .sort((left, right) => timestamp(right.publishedAt) - timestamp(left.publishedAt))
    .filter((item) => {
      const key = normalizeHeadline(item.title);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 12);

  return { changes, flashes, warnings, refreshedAt: snapshot.refreshedAt };
}

export function describeVerificationGap(event: RadarEvent) {
  const uncertainty = event.uncertainty?.trim();
  if ((event.evidenceCount || 0) > 0 || event.evidenceStatus === 'CONFIRMED') {
    return uncertainty || '已有补充材料，仍需持续观察后续变化';
  }
  if (!uncertainty || uncertainty.includes('暂未发现明显信息缺口')) {
    return '尚未核对公告、监管或公司一手材料';
  }
  return `${uncertainty}；尚未核对一手材料`;
}

function normalizeHeadline(value: string) {
  return value.toLowerCase().replace(/[\s\p{P}\p{S}]+/gu, '');
}

function timestamp(value?: string) {
  return value ? Date.parse(value) || 0 : 0;
}
