export const candidateProtocol = 'overnight-candidate-rolling-v3';

export interface CandidateTraining {
  status: string; reason: string; trainingDays: number; trainingRows: number; outsidePoolRows: number;
  trainingThrough: string | null; excludedRows: number; unverifiedActionRows: number; minimumDays: number; minimumRows: number;
}

interface CandidateTrainingMetrics {
  dayCount: number; sampleCount: number;
  arms: Record<string, { accuracy: number; brierScore: number;
    topK: { precision: number | null }; calls: { precision: number | null; dayCoverage: number; selectedDays: number; selectionCount: number } }>;
  momentum: { precision: number | null } | null;
}

export interface CandidateTrainingComparison extends CandidateTrainingMetrics {
  plannedDays: number; warmupDays: number; firstDate: string | null; lastDate: string | null;
  lastFiveDates: CandidateTrainingMetrics; evidenceKind: string; eligible: boolean;
}

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
