import type { OvernightMode } from './overnightTypes';
import type { OvernightDirectionForward, OvernightDirectionMetrics } from './overnightDirectionTypes';
import type { ErrorAudit } from './overnightErrorTypes';
import type { SameDaySelection, SelectionForwardGroup } from './overnightSelectionTypes';

export interface OvernightJointPrediction {
  status: string; adopted: boolean; qualified: boolean; artifactId: string;
  upProbability: number; expectedNetReturn: number; downsideProbability: number;
  lowerNetReturn: number; upperNetReturn: number; rankScore: number;
  forwardStatus: string; forwardDays: number;
  downsideCalibrationStatus?: string;
  incumbentProbability?: number; incumbentExpectedNetReturn?: number;
}

export interface OvernightJointGroup {
  key: string; mode: OvernightMode; cutoff: string; costBps: number; target: string;
  status: string; eligible: boolean; dayCount: number; requiredDays: number; pairedCount: number; coverage: number;
  metrics?: { accuracy: number; brierScore: number; comparisons: Record<string, { brierScore: number; accuracy: number }> };
  selection?: { meanNetReturn: number; selectedDays: number; netReturnLower?: number | null };
}

export interface OvernightJointState {
  protocol: string; poolSize: number; targetSize: number; readySymbols: number; minimumSymbols: number;
  coverageAt?: string; scope?: string; limitation?: string;
  models: Array<{ id: string; createdAt: string; labelsThrough: string;
    profile: { key: string; mode: OvernightMode; cutoff: string; costBps: number };
    data: { symbolCount: number; dayCount: number; rows: number };
    targets: Array<{ target: string; trainingDays: number; calibrationDays: number;
      historical: { dayCount: number; accuracy: number; brierScore: number } }>;
    closeDirection?: { target?: string; data: { symbolCount: number }; audit: { historical: OvernightDirectionMetrics } } | null;
    freshResearch?: { protocol: string; status: string;
      stockSelection?: { protocol?: string; status: string; historical?: SameDaySelection };
      indexAudit?: { status?: string; dayCount?: number; nonConstantFeatures?: number };
      models: Record<string, { audit: { trainingThrough: string; legacyTrainingThrough: string } }> } | null }>;
  forward?: { requiredDays: number; computedAt: string; groups: OvernightJointGroup[] };
  closeDirectionForward?: OvernightDirectionForward;
  contextDirectionForward?: OvernightDirectionForward;
  selectionForward?: { protocol: string; groups: SelectionForwardGroup[] };
  freshForward?: { protocol: string; groups: Array<{ mode: OvernightMode; cutoff: string; group: string;
    dayCount: number; pairedCount: number; scheduledCount: number;
    metrics?: { accuracy: number; comparisons: Record<string, { accuracy: number }> } | null }> };
  errorAudit?: ErrorAudit;
  validationWorker?: { status: string; lastTickAt: string; lastCompletedAt?: string | null; intervalSeconds: number };
  marketAblation?: Array<{ cutoff: string; groups: Array<{ group: string; status: string; dayCount: number; requiredDays: number }> }>;
  marketForward?: { groups: Array<{ mode: OvernightMode; cutoff: string; group: string;
    dayCount: number; requiredDays: number; pairedCount: number; scheduledCount: number;
    metrics?: { accuracy: number; comparisons: Record<string, { accuracy: number }> } | null }> };
  contextSnapshots?: Array<{ signalDate: string; cutoff: string; observedAt: string;
    receivedAt: string; capturedSymbols: number; expectedSymbols: number;
    relativeStrength?: { protocol: string; expectedIndices: number; capturedIndices: number; pairedSymbols: number };
    market?: { quality: string; capturedSymbols?: number; expectedSymbols?: number; reason?: string } }>;
  jobs: Array<{ key: string; mode: OvernightMode; status: string; reason?: string }>;
}
