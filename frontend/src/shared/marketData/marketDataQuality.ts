import { MarketDataQuality, MarketDataQualityStatus } from '../types';

export type AggregatedMarketDataQuality = {
  status: MarketDataQualityStatus;
  sourceCode?: string;
  staleAgeSeconds?: number;
  warning?: string;
  observedCount?: number;
  degradedCount?: number;
};

const freshStatuses = new Set<MarketDataQualityStatus>(['FRESH_PRIMARY', 'FRESH_FALLBACK']);

export function aggregateMarketDataQuality(values: Array<Partial<MarketDataQuality>>): AggregatedMarketDataQuality {
  const observed = values.filter((value) => value.qualityStatus);
  if (!observed.length) return { status: 'FRESH_PRIMARY', observedCount: 0, degradedCount: 0 };

  const statuses = observed.map((value) => value.qualityStatus as MarketDataQualityStatus);
  const freshCount = statuses.filter((status) => freshStatuses.has(status)).length;
  const staleCount = statuses.filter((status) => status === 'STALE_FALLBACK').length;
  const unavailableCount = statuses.filter((status) => status === 'UNAVAILABLE').length;
  const partialCount = statuses.filter((status) => status === 'PARTIAL_FRESH').length;
  let status: MarketDataQualityStatus;
  if (unavailableCount === observed.length) status = 'UNAVAILABLE';
  else if (staleCount === observed.length) status = 'STALE_FALLBACK';
  else if (partialCount > 0 || unavailableCount > 0 || (freshCount > 0 && staleCount > 0)) status = 'PARTIAL_FRESH';
  else if (staleCount > 0) status = 'PARTIAL_FRESH';
  else if (statuses.some((value) => value === 'FRESH_FALLBACK')) status = 'FRESH_FALLBACK';
  else status = 'FRESH_PRIMARY';

  const sources = unique(observed.map((value) => value.sourceCode));
  const warnings = unique(observed.map((value) => value.warning));
  const ages = observed.map((value) => value.staleAgeSeconds).filter((value): value is number => value != null);
  return {
    status,
    sourceCode: sources.length ? sources.join(',') : undefined,
    staleAgeSeconds: ages.length ? Math.max(...ages) : undefined,
    warning: warnings.length ? warnings.join('；') : undefined,
    observedCount: observed.length,
    degradedCount: staleCount + unavailableCount + partialCount
  };
}

function unique(values: Array<string | undefined>) {
  return Array.from(new Set(values.filter((value): value is string => Boolean(value?.trim()))));
}
