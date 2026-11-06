import type { OvernightMode } from './overnightTypes';

export const selectionProtocol = 'overnight-same-day-selection-v1';
export type SelectionArm = 'INDEX' | 'STOCK_ONLY' | 'INDEX_ONLY';

export interface SelectionMeasure {
  selectedDays: number;
  selectionCountPerArm: number;
  dayCoverage: number;
  precision: Record<SelectionArm, number | null>;
}

export interface SameDaySelection {
  protocol: string;
  dayCount: number;
  sampleCount: number;
  noCandidateDays: number;
  forcedTopK: SelectionMeasure;
  matchedCandidates: SelectionMeasure;
}

export interface SelectionForwardGroup {
  mode: OvernightMode;
  cutoff: string;
  dayCount: number;
  pairedCount: number;
  scheduledCount: number;
  scheduledDays: number;
  sameDaySelection: SameDaySelection;
}
