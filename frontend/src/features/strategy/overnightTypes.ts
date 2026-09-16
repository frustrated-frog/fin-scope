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
