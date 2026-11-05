import type { OvernightMode } from './overnightTypes';

export interface ErrorScore {
  sampleCount: number; dayCount: number; accuracy: number; falseUpCount: number; missedUpCount: number;
  status: string;
}
interface Candidates { precision: number | null; selectedDays: number; dayCount: number }
export interface ErrorAudit {
  target: string; computedAt: string;
  groups: Array<{ key: string; mode: OvernightMode; cutoff: string; scheduledCount: number; settledCount: number;
    coverage: number; completeDays: number; missing: Record<string, number>; metrics: ErrorScore | null;
    upCandidates: Candidates; slices: Array<ErrorScore & { dimension: string; value: string }>;
    selectionComparison: { pairedDays: number; incumbent: Candidates; challenger: Candidates } }>;
}
