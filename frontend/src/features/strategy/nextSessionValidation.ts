export interface NextSessionValidation {
  recordCount: number;
  groups: Array<{
    version: string; loaded: number; duplicates: number; pending: number; unavailable: number;
    count: number; days: number; accuracy: number | null; brier: number | null;
    bins: Array<{ lower: number; upper: number; count: number; days: number; forecast: number | null; actual: number | null }>;
  }>;
}
