import { RadarEvent, RadarNewsItem, ResearchRadarSnapshot } from '../../news/researchRadarTypes';

export type DailyResearchProjection = {
  changes: RadarEvent[];
  flashes: RadarNewsItem[];
  warnings: string[];
  refreshedAt?: string;
};

export function projectDailyResearch(snapshot?: ResearchRadarSnapshot | null): DailyResearchProjection {
  if (!snapshot) return { changes: [], flashes: [], warnings: [] };

  const changes = [...snapshot.events]
    .sort((left, right) => right.priorityScore - left.priorityScore || timestamp(right.lastSeenAt) - timestamp(left.lastSeenAt))
    .slice(0, 8);
  const seen = new Set<string>();
  const flashes = [...snapshot.liveItems]
    .filter((item) => item.kind === 'FLASH')
    .sort((left, right) => timestamp(right.publishedAt) - timestamp(left.publishedAt))
    .filter((item) => {
      const key = normalizeHeadline(item.title);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 12);

  return { changes, flashes, warnings: snapshot.warnings || [], refreshedAt: snapshot.refreshedAt };
}

function normalizeHeadline(value: string) {
  return value.toLowerCase().replace(/[\s\p{P}\p{S}]+/gu, '');
}

function timestamp(value?: string) {
  return value ? Date.parse(value) || 0 : 0;
}
