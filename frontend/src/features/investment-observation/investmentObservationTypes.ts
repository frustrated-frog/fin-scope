export type InvestmentObservationStage = 'FOCUS' | 'TRACKING' | 'LEARNING' | 'ARCHIVED';
export type InvestmentObservationDisposition = 'ACTIVE' | 'LATER' | 'IGNORED';

export type InvestmentObservationScoreDimension = {
  code: string;
  label: string;
  score: number;
  maxScore: number;
  explanation: string;
};

export type InvestmentObservation = {
  id: number;
  sourceType: 'RADAR_EVENT';
  sourceId: number;
  title: string;
  summary?: string;
  subjectType: 'EVENT' | 'COMPANY' | 'INDUSTRY';
  subjectName?: string;
  stage: InvestmentObservationStage;
  changeType: 'ORDER' | 'PRICE' | 'POLICY' | 'EARNINGS' | 'COMPETITION' | 'CAPITAL' | 'OTHER';
  score: number;
  scoreDimensions: InvestmentObservationScoreDimension[];
  whyItMatters?: string;
  uncertainty?: string;
  nextValidation?: string;
  supportingEvidenceCount: number;
  opposingEvidenceCount: number;
  independentSourceCount: number;
  firstObservedAt?: string;
  lastChangedAt?: string;
  disposition: InvestmentObservationDisposition;
  revision: number;
  evidenceInsufficient: boolean;
  sourceAvailable: boolean;
  updatedAt?: string;
};

export type InvestmentObservationTransition = {
  id: number;
  observationId: number;
  fromStage?: InvestmentObservationStage;
  toStage: InvestmentObservationStage;
  reason: string;
  occurredAt: string;
};

export type InvestmentObservationWorkspace = {
  focus: InvestmentObservation[];
  tracking: InvestmentObservation[];
  learning: InvestmentObservation[];
  archived: InvestmentObservation[];
  transitions: InvestmentObservationTransition[];
  activeCount: number;
  changedTodayCount: number;
  waitingValidationCount: number;
  archivedCount: number;
  warning?: string;
  refreshedAt?: string;
};

export type InvestmentObservationRefreshResult = {
  scannedCount: number;
  updatedCount: number;
  preservedCount: number;
  focusCount: number;
  trackingCount: number;
  learningCount: number;
  refreshedAt: string;
};
