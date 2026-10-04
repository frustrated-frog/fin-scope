import type { OvernightJointPrediction, OvernightJointState } from './overnightJointTypes';
import type { OvernightCloseDirection, OvernightDirectionOutcome } from './overnightDirectionTypes';

export type OvernightMode = 'TAIL_ENTRY' | 'AFTER_CLOSE_HOLDING';
export interface OvernightTarget {
  target: 'OPEN' | '10:00' | '14:30' | 'CLOSE'; status: string; sampleCount: number;
  upProbability?: number; expectedNetReturn?: number; lowerNetReturn?: number | null; upperNetReturn?: number | null;
  probabilitySource?: 'CALIBRATED_MODEL' | 'HISTORICAL_BASELINE' | 'JOINT_MODEL'; selectionReason?: string;
  joint?: OvernightJointPrediction;
  modelUpProbability?: number; modelExpectedNetReturn?: number;
  validationCount?: number; brierScore?: number; baselineBrier?: number; directionAccuracy?: number;
  trainingThrough?: string; costBasisReturn?: number;
  minimumSamples?: number; missingSamples?: number; missingValidationSamples?: number;
  rawUpProbability?: number; baselineProbability?: number; trainingCount?: number; calibrationCount?: number;
  calibrationStart?: string; calibrationThrough?: string; calibrationStatus?: string; calibrationReason?: string;
  reliability?: {
    status: string; count: number; from?: string; through?: string;
    brierSkill?: number | null; recentCount?: number; recentBrierSkill?: number | null;
    rawBrier?: number; baselineAccuracy?: number; intervalCoverage?: number; nominalCoverage?: number;
    expectedReturnMae?: number;
  };
}
export interface OvernightReport {
  id?: string; mode: OvernightMode; instrumentCode: string; signalDate: string; cutoff: string;
  status: string; generatedAt: string; dataThrough: string; targetDate?: string;
  referencePrice?: number; costBps: number; costBasis?: number; quantity?: number;
  evidenceKind: 'FORWARD' | 'RETROSPECTIVE'; inputFingerprint: string; modelVersion: string;
  targets: OvernightTarget[]; warnings: string[];
  jointResearch?: { protocol?: string; status: string; reason: string; symbolCount?: number; trainedAt?: string };
  closeDirection?: OvernightCloseDirection;
  outcome?: { status: string; warnings?: string[]; closeDirection?: OvernightDirectionOutcome;
    targets: Array<{ target: string; actualNetReturn: number; brierScore?: number }> };
}
export interface OvernightPosition {
  instrumentCode: string; instrumentName: string; averageCost: number; quantity: number; openedOn?: string;
}

export interface OvernightAutomationJob {
  key: string; signalDate: string; cutoff: string; mode: OvernightMode; phase: 'DISCOVER' | 'PREDICT';
  status: string; reason?: string; startedAt: string; finishedAt?: string; attempts: number;
  instrumentCode?: string; instrumentName?: string; snapshotAt?: string; scope?: string;
  observedCount?: number; freshCount?: number;
  candidates?: Array<{ instrumentCode: string; instrumentName: string; changePct?: number; volumeRatio?: number; selectionLane?: string }>;
  ranking?: { status: string; target: string; opportunityStatus: string; evaluatedCount: number;
    candidates: Array<OvernightJointPrediction & { instrumentCode: string; reportId: string }> };
  results?: Array<{ instrumentCode: string; status: string; reportId?: string; reason?: string;
    evidenceKind?: string; warnings?: string[] }>;
}
export interface OvernightAutomationState {
  enabled: boolean; candidateLimit: number; serverTime: string; calendarAvailable: boolean; tradingDay: boolean;
  nextTailAt?: string; ledgerReceivedAt?: string; ledgerFresh: boolean; positionCount: number;
  holdingStatus: string; heartbeat?: { lastTickAt: string; error?: string };
  jobs: OvernightAutomationJob[];
  joint?: OvernightJointState;
  history?: {
    desiredDays: number;
    coverage: Array<{ instrumentCode: string; firstDate: string; lastDate: string; completeDays: number; barCount: number }>;
    jobs: Array<{ key: string; instrumentCode: string; status: string; reason?: string; addedDays?: number; finishedAt?: string }>;
  };
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
