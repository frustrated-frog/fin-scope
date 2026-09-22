export type OvernightMode = 'TAIL_ENTRY' | 'AFTER_CLOSE_HOLDING';
export interface OvernightTarget {
  target: 'OPEN' | '10:00' | '14:30' | 'CLOSE'; status: string; sampleCount: number;
  upProbability?: number; expectedNetReturn?: number; lowerNetReturn?: number; upperNetReturn?: number;
  validationCount?: number; brierScore?: number; baselineBrier?: number; directionAccuracy?: number;
  trainingThrough?: string; costBasisReturn?: number;
}
export interface OvernightReport {
  id?: string; mode: OvernightMode; instrumentCode: string; signalDate: string; cutoff: string;
  status: string; generatedAt: string; dataThrough: string; targetDate?: string;
  referencePrice?: number; costBps: number; costBasis?: number; quantity?: number;
  evidenceKind: 'FORWARD' | 'RETROSPECTIVE'; inputFingerprint: string; modelVersion: string;
  targets: OvernightTarget[]; warnings: string[];
  outcome?: { status: string; warnings?: string[]; targets: Array<{ target: string; actualNetReturn: number; brierScore?: number }> };
}
export interface OvernightPosition {
  instrumentCode: string; instrumentName: string; averageCost: number; quantity: number; openedOn?: string;
}

export interface CapturePlan {
  enabled: boolean; instrumentCodes: string[]; costBps: number; enabledSince?: string; updatedAt?: string;
}
export interface CaptureState {
  plan: CapturePlan; slots: string[]; serverTime: string; calendarAvailable: boolean;
  runs: Array<{ signalDate: string; cutoff: string; status: string; reason?: string;
    instrumentCodes: string[]; costBps: number;
    results: Array<{ instrumentCode: string; status: string; evidenceKind?: string; reason?: string; warnings?: string[] }> }>;
}
export interface OvernightValidation {
  recordCount: number; scope: string; limitations: string[];
  groups: Array<{ mode: OvernightMode; modelVersion: string; cutoff: string; costBps: number;
    evidenceKind: string; recordCount: number; statuses: Record<string, number>; missingReasons: Record<string, number>;
    targets: Array<{ target: string; count: number; days: number; accuracy: number; brier: number;
      baselineCount: number; pairedBrier: number | null; baselineBrier: number | null;
      meanNetReturn: number; selectedCount: number; selectedNetReturn: number;
      bins: Array<{ lower: number; upper: number; count: number; days: number; predicted: number | null; actual: number | null }> }> }>;
}
