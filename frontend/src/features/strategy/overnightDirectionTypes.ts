export interface OvernightDirectionMetrics {
  sampleCount: number; dayCount: number; accuracy: number; balancedAccuracy?: number | null;
  brierScore: number; predictedUpRate?: number;
  comparisons: Record<string, { accuracy: number; brierScore: number }>;
  upCandidates?: { topK: number; selectedDays: number; dayCount: number; selectionCount: number;
    precision: number | null; dayCoverage: number; poolUpRate: number | null; lift: number | null };
}

export interface OvernightCloseDirection {
  referencePrice?: number; referenceAt?: string;
  protocol: string; target: string; status: string; reason: string;
  upProbability?: number; notUpProbability?: number; direction?: 'UP' | 'NOT_UP';
  rawUpProbability?: number; calibratedUpProbability?: number; probabilitySource?: string;
  selectedModel?: string; baselineProbability?: number; historical?: OvernightDirectionMetrics;
  forwardDays?: number; forwardStatus?: string; validated?: boolean; artifactId?: string;
  challenger?: OvernightContextDirection;
  activeSource?: string;
  upSelection?: Omit<OvernightCloseDirection, 'upSelection' | 'challenger'>;
}

export interface OvernightContextDirection extends Omit<OvernightCloseDirection, 'challenger'> {
  incumbentProbability?: number; contextAt?: string; contextReceivedAt?: string;
  contextSymbols?: number; industryAvailable?: boolean; indexCount?: number;
  historyWindows?: number[]; featureCount?: number;
}

export interface OvernightDirectionOutcome {
  status: string; actualReturn?: number; actualUp?: boolean; correct?: boolean;
  signalClose?: number; targetClose?: number;
}

export interface OvernightDirectionForward {
  protocol?: string; target?: string;
  groups: Array<{ key: string; mode: string; cutoff: string; status: string; eligible: boolean;
    dayCount: number; requiredDays: number; coverage: number; metrics?: OvernightDirectionMetrics }>;
}
