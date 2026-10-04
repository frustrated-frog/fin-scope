import type { OvernightMode } from './overnightTypes';

export interface OvernightJointPrediction {
  status: string; adopted: boolean; qualified: boolean; artifactId: string;
  upProbability: number; expectedNetReturn: number; downsideProbability: number;
  lowerNetReturn: number; upperNetReturn: number; rankScore: number;
  forwardStatus: string; forwardDays: number;
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
      historical: { dayCount: number; accuracy: number; brierScore: number } }> }>;
  forward?: { requiredDays: number; computedAt: string; groups: OvernightJointGroup[] };
  jobs: Array<{ key: string; mode: OvernightMode; status: string; reason?: string }>;
}
