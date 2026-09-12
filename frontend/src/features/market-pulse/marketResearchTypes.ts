export type ResearchGroupCode = 'STRONG' | 'TREND' | 'BREAKOUT';
export type ResearchStock = {
  instrumentCode: string;
  return1d?: number | null;
  return5d?: number | null;
  return20d?: number | null;
  amount?: number | null;
  groupCodes: ResearchGroupCode[];
};
export type ResearchGroup = {
  code: ResearchGroupCode;
  label: string;
  definition: string;
  eligibleCount: number;
  memberCount: number;
  validCount: number;
  advanceRatio?: number | null;
  medianReturn?: number | null;
  members: string[];
};
export type DailyResearch = {
  businessDate: string;
  selectionDate?: string;
  sourceCode: string;
  qualityStatus: 'PARTIAL' | 'UNAVAILABLE';
  sampleCount: number;
  stocks: ResearchStock[];
  groups: ResearchGroup[];
  warnings: string[];
};
export type ThemeMember = { instrumentCode: string; name: string; segment: string; evidence: string };
export type ResearchTheme = { id: string; name: string; effectiveDate: string; members: ThemeMember[] };
export type ResearchPeriod = 1 | 5 | 20;
export type SectorFilter = {
  kind: 'ALL' | 'EMERGING' | 'PULLBACK' | 'REPAIR';
  search: string;
  minBreadth?: number;
  maxReturn5d?: number;
};
