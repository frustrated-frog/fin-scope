import type { OvernightCloseDirection } from './overnightDirectionTypes';

export function isBaselineDirection(direction?: OvernightCloseDirection) {
  return direction?.assessmentStatus === 'BASELINE_ONLY' || direction?.selectedModel === 'PRIOR';
}
