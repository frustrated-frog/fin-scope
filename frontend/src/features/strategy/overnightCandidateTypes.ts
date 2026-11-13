export const candidateProtocol = 'overnight-candidate-rolling-v2';

export interface CandidateReadiness {
  computedAt: string; status?: string; reason?: string;
  completeDays?: number; cohortDays?: number;
  reasonCounts?: Record<string, number>;
  support?: { protocol: string; status: string; dayCount: number; poolRows: number; candidateRows: number;
    outsidePoolRows: number; unpairedDays: number;
    features: Array<{ code: string; poolMedian: number; candidateMedian: number; outsideCentralRangeRate: number }> };
  days?: Array<{ signalDate: string; expectedCount: number; availableCount: number;
    complete: boolean; missing: Record<string, number> }>;
}

export interface CandidateSelectionAudit {
  reason: string; selectionDays: number; plannedDays: number; selectionRows?: number;
  expectedRows?: number; candidateCoverage: number; selectionThrough: string | null;
}

export interface CandidateForwardGroup {
  mode: string; cutoff: string; scheduledCount: number; pairedCount: number; dayCount: number;
  ranking: { topK: number; dayCount: number; noCandidateDays: number;
    forced: { selectedDays: number; precision: Record<string, number | null> };
    matched: { selectedDays: number; precision: Record<string, number | null> } };
}
