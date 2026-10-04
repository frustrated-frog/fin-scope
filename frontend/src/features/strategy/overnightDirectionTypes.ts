export interface OvernightDirectionMetrics {
  sampleCount: number; dayCount: number; accuracy: number; balancedAccuracy?: number | null;
  brierScore: number; predictedUpRate?: number;
  comparisons: Record<string, { accuracy: number; brierScore: number }>;
}

export interface OvernightCloseDirection {
  protocol: string; target: string; status: string; reason: string;
  upProbability?: number; notUpProbability?: number; direction?: 'UP' | 'NOT_UP';
  rawUpProbability?: number; calibratedUpProbability?: number; probabilitySource?: string;
  selectedModel?: string; baselineProbability?: number; historical?: OvernightDirectionMetrics;
  forwardDays?: number; forwardStatus?: string; validated?: boolean; artifactId?: string;
}

export interface OvernightDirectionOutcome {
  status: string; actualReturn?: number; actualUp?: boolean; correct?: boolean;
  signalClose?: number; targetClose?: number;
}

export interface OvernightDirectionForward {
  groups: Array<{ key: string; mode: string; cutoff: string; status: string; eligible: boolean;
    dayCount: number; requiredDays: number; coverage: number; metrics?: OvernightDirectionMetrics }>;
}
