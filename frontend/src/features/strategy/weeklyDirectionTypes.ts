import type { OvernightMode } from './overnightTypes';

export interface UpDetectionMetrics {
  sampleCount: number; dayCount: number; predictedUpCount: number; actualUpCount: number;
  trueUpCount: number; missedUpCount: number; falseUpCount: number;
  upPrecision: number | null; upRecall: number | null; predictedUpRate: number;
  accuracy: number; alwaysNotUpAccuracy: number;
}

export interface UpDetection {
  metrics: UpDetectionMetrics | null; noUpDays: number; pairedDays: number;
  baselineOnlyCount?: number;
  daily: Array<UpDetectionMetrics & { signalDate: string }>;
  comparisons: Record<string, UpDetectionMetrics | null>;
  missedExampleTotal: number;
  missedExamples: Array<{ signalDate: string; instrumentCode: string; probability: number;
    rawProbability: number | null; calibratedProbability: number | null;
    trainedAt: string | null; modelFitThrough?: string | null; indexReturn: number | null }>;
}

export interface WeeklyDirectionAudit {
  protocol: string; target: string; periodStart: string; periodEnd: string; computedAt: string;
  archives: Array<{ mode: OvernightMode; recordCount: number; pendingCount: number }>;
  windows: Array<{ signalDate: string; cutoff: string; status: string; reason?: string | null;
    diagnostic?: { scanStatus?: string; gapSeconds?: number | null; queueDelaySeconds?: number | null } | null }>;
  groups: Array<{ key: string; mode: OvernightMode; cutoff: string; completeDays: number;
    scheduledCount: number; settledCount: number; missing: Record<string, number>;
    upDetection: UpDetection }>;
}
