import type { WatchFocus } from '../watchlist/watchFocusTypes';
import type { SectorRotation } from './marketPulseTypes';
import type { StockDiscoveryReport } from '../strategy/quantTypes';

export type PersonalChange = {
  id: string;
  category: 'COMPANY' | 'TRACKING' | 'INDUSTRY';
  code: string;
  name?: string;
  title: string;
  summary?: string;
  occurredAt: string;
  reason?: string;
  nextWatch?: string;
  eventKey?: string;
  sampleId?: number;
  reportId?: number;
  sectorName?: string;
};
export function stockCode(code: string) {
  return code.replace(/^(SH|SZ|BJ):/, '').replace(/\.(SH|SZ|BJ)$/, '');
}
export function sectorFollows(
  sector: SectorRotation,
  focuses: WatchFocus[],
  report?: StockDiscoveryReport,
) {
  const members = new Set(
    report?.candidates
      .filter((item) => item.sector_names.includes(sector.sectorName))
      .map((item) => stockCode(item.code)) ?? [],
  );
  return focuses.filter(
    (item) =>
      item.type === 'STOCK' &&
      (item.sectorCode === sector.sectorCode ||
        item.direction?.trim() === sector.sectorName ||
        members.has(stockCode(item.code))),
  );
}
export function industryChanges(
  sectors: SectorRotation[],
  focuses: WatchFocus[],
  date: string,
  report?: StockDiscoveryReport,
): PersonalChange[] {
  return sectors.flatMap((sector) => {
    const related = sectorFollows(sector, focuses, report);
    if (
      !related.length ||
      sector.return1d == null ||
      !Number.isFinite(sector.return1d) ||
      Math.abs(sector.return1d) < 1
    ) {
      return [];
    }
    return [
      {
        id: `sector:${sector.sectorCode}`,
        category: 'INDUSTRY' as const,
        code: related[0].code,
        name: sector.sectorName,
        title: `${sector.sectorName}当日${sector.return1d >= 0 ? '上涨' : '下跌'} ${Math.abs(sector.return1d).toFixed(2)}%`,
        summary: `关联自选：${related.map((item) => item.name || item.code).join('、')}`,
        occurredAt: date,
        sectorName: sector.sectorName,
      },
    ];
  });
}
export function opportunitySectors(
  sectors: SectorRotation[],
  focuses: WatchFocus[],
  report?: StockDiscoveryReport,
) {
  const sorted = sectors
    .filter((s) => Number.isFinite(s.return1d) && (s.return1d ?? 0) > 0)
    .slice()
    .sort(
      (a, b) =>
        (b.rotationScore ?? 0) - (a.rotationScore ?? 0) ||
        (b.return1d ?? 0) - (a.return1d ?? 0),
    );
  const related = sorted.filter(
    (s) => sectorFollows(s, focuses, report).length,
  );
  const other = sorted.filter((s) => !sectorFollows(s, focuses, report).length);
  return [...related.slice(0, 2), ...other.slice(0, 2)].slice(0, 4);
}

export function relativeChanges(
  research: import('./marketResearchTypes').DailyResearch | undefined,
  sectors: SectorRotation[],
  focuses: WatchFocus[],
  date: string,
  report?: StockDiscoveryReport,
): PersonalChange[] {
  if (!research || research.businessDate !== date) {
    return [];
  }
  return focuses
    .filter((focus) => focus.type === 'STOCK')
    .flatMap((focus) => {
      const stock = research.stocks.find(
        (item) => stockCode(item.instrumentCode) === stockCode(focus.code),
      );
      const sector = sectors.find(
        (item) => sectorFollows(item, [focus], report).length,
      );
      if (
        stock?.return1d == null ||
        sector?.return1d == null ||
        !Number.isFinite(stock.return1d) ||
        !Number.isFinite(sector.return1d)
      ) {
        return [];
      }
      const excess = stock.return1d - sector.return1d;
      if (Math.abs(excess) < 2) {
        return [];
      }
      return [
        {
          id: `relative:${focus.watchlistId}`,
          category: 'COMPANY' as const,
          code: focus.code,
          name: focus.name,
          title: `当日表现${excess > 0 ? '强于' : '弱于'}${sector.sectorName} ${Math.abs(excess).toFixed(2)} 个百分点`,
          summary: `个股 ${stock.return1d > 0 ? '+' : ''}${stock.return1d.toFixed(2)}%，行业 ${sector.return1d > 0 ? '+' : ''}${sector.return1d.toFixed(2)}%。`,
          occurredAt: date,
          reason: focus.reason,
          nextWatch: focus.nextWatch,
        },
      ];
    });
}
