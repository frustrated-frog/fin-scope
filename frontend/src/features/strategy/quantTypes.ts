export interface QuantDataset {
  id: number; name: string; market: string; dataKind: 'REAL' | 'LEARNING_SAMPLE';
  startDate?: string; endDate?: string; status: string; fingerprint?: string; qualitySummary?: string;
  datasetLevel?: 'RESEARCH' | 'LEARNING'; asOfTime?: string; fingerprintVersion?: string;
}

export interface QuantDatasetQuality {
  datasetId: number;
  status: string;
  summary?: string;
  fingerprint?: string;
  availableFactors: string[];
}

export interface QuantDataSyncRun {
  id: number; datasetId: number; triggerType: 'MANUAL' | 'SCHEDULED';
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  requestedInstruments: number; succeededInstruments: number; failedInstruments: number;
  insertedRows: number; degradedInstruments: number; sourceSummary?: string;
  warningSummary?: string; startedAt: string; finishedAt?: string;
}

export interface QuantFactorAnalysis {
  datasetId: number; datasetFingerprint: string; factorCode: string; sampleCount: number;
  icMean: number; icStd: number; icIr: number; positiveIcRatio: number;
  negativeIcRatio?: number; zeroIcRatio?: number; icMeanCiLower?: number; icMeanCiUpper?: number;
  evaluationMode?: 'CROSS_SECTIONAL_FACTOR_STUDY';
  researchDirection?: FactorResearchDirection;
  directionAdjustedIcMean?: number;
  favorableIcRatio?: number;
  directionAdjustedCiLower?: number; directionAdjustedCiUpper?: number;
  totalEligibleDays?: number; minCrossSectionSize?: number; coverageRatio?: number;
  quantileSampleDays?: number; quantileSpreadMean?: number; favorableQuantileSpreadRatio?: number;
  quantileMonotonicityMean?: number; directionAdjustedQuantileSpread?: number; directionAdjustedMonotonicity?: number;
  validationEligible?: boolean; evaluationPolicyVersion?: string; blockingReasons?: string[];
  sampleEvidence?: 'INSUFFICIENT_SAMPLE' | 'DIRECTIONALLY_ALIGNED' | 'OPPOSED' | 'UNSTABLE';
  conclusion?: 'SUPPORTED' | 'REFUTED' | 'INCONCLUSIVE';
  caveats?: string[];
  horizons?: FactorHorizonAnalysis[];
  robustness?: FactorRobustnessReport;
}

export interface FactorHorizonAnalysis {
  horizonDays: number; sampleCount: number; totalEligibleDays: number; minCrossSectionSize: number;
  coverageRatio: number; icMean: number; icStd: number; icIr: number;
  positiveIcRatio?: number; negativeIcRatio?: number; icMeanCiLower?: number; icMeanCiUpper?: number;
  favorableIcRatio: number;
  directionAdjustedIcMean: number; directionAdjustedCiLower: number; directionAdjustedCiUpper: number;
  directionAdjustedQuantileSpread: number; directionAdjustedMonotonicity: number;
}

export interface FactorRobustnessReport {
  protocolVersion: string; inSampleCount: number; outOfSampleCount: number;
  inSampleIcMean: number; outOfSampleIcMean: number;
  directionAdjustedInSampleIcMean: number; directionAdjustedOutOfSampleIcMean: number;
  outOfSampleDirectionAligned: boolean; rankTurnoverProxy: number;
  netQuantileSpreadAt10Bps: number; netQuantileSpreadAt30Bps: number; costModel: string;
}

export type FactorLifecycleStatus =
  | 'CANDIDATE' | 'DEFINITION_REVIEWED' | 'IMPLEMENTED' | 'CALCULATION_VERIFIED'
  | 'EXPLORATORY' | 'VALIDATED' | 'PRODUCTION_ELIGIBLE' | 'INVALIDATED' | 'RETIRED';

export type FactorResearchDirection = 'POSITIVE_HYPOTHESIS' | 'NEGATIVE_HYPOTHESIS';

export interface ResearchFactorDefinition {
  identity: { namespace: string; code: string; version: string };
  name: string;
  category: string;
  frequency: string;
  expectedDirection: FactorResearchDirection;
  plainMeaning: string;
  hypothesis: string;
  economicRationale: string;
  interpretationBoundary: string;
  requiredFields: string[];
  availableAtRule: string;
  missingPolicy: string;
  calculationKey: string;
  calculationVersion: string;
  sourceType: string;
  sourceRef: string;
  evaluationPolicyCode: string;
  evaluationPolicyVersion: string;
  status: FactorLifecycleStatus;
  validationEvidenceRef?: string;
}

export interface ResearchDraft {
  id: number;
  sourceType: 'CAPITAL_BEHAVIOR';
  instrumentCode: string;
  instrumentName: string;
  observedAt: string;
  signalCode: string;
  factor: { namespace: string; code: string; version: string };
  snapshotId: number;
  snapshotFingerprint: string;
  evidenceRefs: string[];
  objectiveTags: string[];
  evaluationMode: 'CROSS_SECTIONAL_FACTOR_STUDY';
  status: 'DRAFT';
  requiredNextSteps: string[];
  createdAt: string;
}

export interface FactorResearchAgentTrace {
  id: number; nodeName: string; status: string; input: string; output: string;
  budgetSnapshot?: string; createdAt?: string;
}

export interface FactorResearchAgentRun {
  id: number; datasetId: number; datasetFingerprint: string;
  factor: { namespace: string; code: string; version: string };
  researchDraftId?: number; question: string;
  status: 'AWAITING_APPROVAL' | 'APPROVED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'BUDGET_EXHAUSTED';
  plan: string[]; allowedTools: string[];
  maxToolCalls: number; toolCallsUsed: number; maxLlmCalls: number; llmCallsUsed: number; maxRunSeconds: number;
  evidenceJson: string; evidenceHash: string; findingJson: string; stopReason: string;
  trace: FactorResearchAgentTrace[];
}

export interface QuantResearchEntryIntent {
  factorCode: string;
  draftId?: number;
  sourceLabel?: string;
}

export interface QuantStrategySpec {
  name: string; datasetId: number; benchmark: string; investmentHypothesis: string; riskBoundary: string;
  startDate?: string; endDate?: string;
  factors: Array<{ code: string; weight: number; direction: string }>;
  portfolio: { topN: number; rebalanceEvery: number; weighting: string };
  execution: { signalPrice: string; fillPrice: string; slippageBps: number };
  cost: { buyCommission: number; sellCommission: number; stampDuty: number; minimumCommission: number };
}

export interface QuantStrategyDraft {
  id: number; datasetId: number; status: string; model: string; spec: QuantStrategySpec; validationIssues: string[];
}

export interface QuantStrategyVersion {
  id: number; name: string; datasetId: number; version: number; specJson: string;
  strategyFingerprint: string; datasetFingerprint: string; engineVersion: string; source: string;
}

export type QuantStrategyCompatibilityStatus = 'ADAPTABLE' | 'NEEDS_FACTOR' | 'UNSUPPORTED';

export interface QuantStrategyCatalogSource {
  code: string; repositoryUrl?: string; branch?: string; commitSha: string;
  status: string; lastSyncedAt: string; errorMessage?: string;
}

export interface QuantStrategyCandidate {
  id: number; title: string; assetClass: 'EQUITY'; sourceCommitSha: string;
  reportedSharpe?: number; reportedVolatility?: number; rebalanceCadence?: string;
  implementationUrl?: string; paperUrl?: string;
  compatibilityStatus: QuantStrategyCompatibilityStatus; adaptationNote: string;
  mappedFactors: string[]; missingFactors: string[]; archived: boolean;
}

export interface QuantStrategyCatalogSyncResult {
  sourceCode: string; commitSha: string; importedCount: number; activeCount: number; syncedAt: string;
}

export interface BacktestMetrics {
  totalReturn: number; annualizedReturn: number; annualizedVolatility: number; maxDrawdown: number;
  sharpeRatio: number; calmarRatio: number; winRate: number; turnover: number; tradeCount: number;
  benchmarkReturn: number; excessReturn: number;
}

export interface QuantExperiment {
  id: number; strategyVersionId: number; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  datasetId?: number; datasetName?: string; dataKind?: 'REAL' | 'LEARNING_SAMPLE';
  datasetFingerprint: string; engineVersion: string; errorMessage?: string; createdAt?: string; completedAt?: string;
  result?: { metrics: BacktestMetrics; equityCurve: Array<{ tradeDate: string; portfolioNav: number; benchmarkNav: number; drawdown: number }>;
    annualPerformance: Array<{ year: number; portfolioReturn: number; benchmarkReturn: number; excessReturn: number; maxDrawdown: number }>;
    trades: Array<{ signalDate: string; tradeDate: string; instrumentCode: string; side: string; quantity: number; price: number; fee: number }>;
    positions: Array<{ tradeDate: string; instrumentCode: string; quantity: number; price: number; marketValue: number; weight: number }>;
    warnings: string[] };
  interpretation?: string;
}

export type SingleStockForecastStatus =
  | 'INSUFFICIENT_DATA' | 'ROBUST' | 'CONDITIONAL' | 'NO_CLEAR_EDGE';

export interface ForecastPerformanceSummary {
  totalReturn: number; annualizedReturn: number; annualizedVolatility: number;
  sharpeRatio: number; dailyWinRate: number; maxDrawdown: number;
  maxDrawdownStartDate: string; maxDrawdownTroughDate: string;
  maxDrawdownRecoveryDate?: string; maxDrawdownDurationDays: number;
}

export interface SingleStockForecastPerformance {
  benchmarkLabel: string;
  strategy: ForecastPerformanceSummary;
  benchmark: ForecastPerformanceSummary;
  excessReturn: number; tradeCount: number; profitableTradeRate: number;
  turnover: number; totalCost: number; holdingTimeRatio: number;
  averageHoldingDays: number;
  trades: Array<{ signalDate: string; entryDate: string; exitDate: string;
    probability: number; netReturn: number; cost: number; holdingDays: number }>;
}

export interface ForecastConfidenceInterval {
  status: 'AVAILABLE' | 'UNAVAILABLE'; lower?: number; upper?: number;
  confidenceLevel: number; method: string; validIterations: number;
  reason?: string; limitation?: string;
}

export interface ForecastProbabilityMetrics {
  sampleCount: number; accuracy: number; brierScore: number; baselineBrierScore: number;
  brierSkillScore: number; logLoss: number; expectedCalibrationError: number;
}

export interface ForecastSplitSlice {
  startDate: string; endDate: string; sampleCount: number;
  independentSampleCount: number; positiveCount: number; purgedCount: number;
}

export interface ForecastQualification {
  status: 'QUALIFIED' | 'CONDITIONAL' | 'FAILED' | 'INSUFFICIENT_DATA';
  reason?: string;
  trial: { trialId: string; featureVersion: string; labelVersion: string; splitVersion: string;
    calibrationVersion: string; bootstrapVersion: string; randomSeed: number; modelVersion: string };
  splitAudit: { development: ForecastSplitSlice; calibration: ForecastSplitSlice;
    lockedTest: ForecastSplitSlice; labelHorizonDays: number; independentStrideDays: number; rule: string };
  calibration: { status: 'FITTED' | 'NOT_FITTED'; method: string; sampleCount: number;
    positiveCount: number; slope: number; intercept: number; rawLogLoss: number;
    calibratedLogLoss: number; reason?: string };
  lockedTest: { baselineProbability: number; rawMetrics: ForecastProbabilityMetrics;
    calibratedMetrics: ForecastProbabilityMetrics; baselineMetrics: ForecastProbabilityMetrics;
    reliabilityBins: Array<{ lowerBound: number; upperBound: number; count: number;
      meanProbability?: number; observedUpRate?: number; calibrationError?: number }> };
  confidenceIntervals: { brierSkillScore: ForecastConfidenceInterval;
    accuracy: ForecastConfidenceInterval; excessReturn: ForecastConfidenceInterval;
    sharpeRatio: ForecastConfidenceInterval };
}

export interface SingleStockForecast {
  reportSchemaVersion: string; modelVersion: string;
  instrumentCode: string; asOfDate: string; horizonDays: number;
  status: SingleStockForecastStatus; conclusion: string;
  decision?: 'UP' | 'DOWN' | 'ABSTAIN'; modelDecision?: 'UP' | 'DOWN' | 'ABSTAIN';
  decisionReason?: string;
  barCount: number; labeledSampleCount?: number;
  upProbability?: number; expectedNetReturn?: number; lowerNetReturn?: number; upperNetReturn?: number;
  rawProbability?: number; probabilityInterval?: ForecastConfidenceInterval;
  dataFingerprint: string; sourceCode: string; sourceFamily: string; qualityStatus: string;
  lastClose: number;
  strategyPolicy: { signalThreshold: number; holdingDays: number; entryRule: string;
    exitRule: string; overlapPolicy: string; roundTripCostRate: number; benchmark: string };
  validation?: {
    outOfSampleCount: number; independentSampleCount: number; accuracy: number;
    brierScore: number; baselineBrierScore: number; observedUpRate: number;
  };
  recentObservations: Array<{
    signalDate: string; probability: number; actualNetReturn: number; correct: boolean;
  }>;
  factorExplanations: Array<{ code: string; name: string; category: string; formula: string;
    window: string; currentValue: number; historicalPercentile: number;
    standardizedValue: number; coefficient: number; contribution: number; direction: string;
    economicMeaning: string; boundary: string }>;
  performance?: SingleStockForecastPerformance;
  equityCurve: Array<{ tradeDate: string; strategyNav: number; benchmarkNav: number;
    drawdown: number; invested: boolean }>;
  annualPerformance: Array<{ year: number; strategyReturn: number; benchmarkReturn: number;
    excessReturn: number; maxDrawdown: number; tradeCount: number }>;
  regimePerformance: Array<{ regime: string; label: string; sampleDays: number;
    strategyReturn: number; benchmarkReturn: number; excessReturn: number;
    sharpeRatio: number; maxDrawdown: number; tradeCount: number; holdingTimeRatio: number }>;
  inSample?: { sampleCount: number; accuracy: number; brierScore: number;
    baselineBrierScore?: number; evidenceRole: string };
  outOfSample?: { sampleCount: number; accuracy: number; brierScore: number;
    baselineBrierScore?: number; evidenceRole: string };
  parameterStability?: { positiveExcessRatio: number; worstExcessReturn: number;
    worstSharpeRatio: number; scenarios: Array<{ holdingDays: number; threshold: number;
      primary: boolean; annualizedReturn: number; excessReturn: number; sharpeRatio: number;
      maxDrawdown: number; tradeCount: number }> };
  qualification?: ForecastQualification;
  selectiveValidation?: { lowerThreshold: number; upperThreshold: number;
    sampleCount: number; coveredCount: number; coverage: number;
    coveredAccuracy: number; abstainRate: number };
  context?: { market: { code?: string; label: string; status: string; coverage: number;
      regime?: string; reason?: string };
    industry: { code?: string; label: string; status: string; coverage: number;
      regime?: string; reason?: string };
    featureCodes: string[]; alignmentRule: string };
  modelCompetition?: { selectedModel: string; selectionEndDate: string;
    calibrationStartDate: string; selectionRule: string;
    candidates: Array<{ code: string; name: string; selected: boolean;
      selectionSampleCount: number; accuracy: number; brierScore: number;
      logLoss: number; baselineBrierScore: number; reason: string }> };
  leakageAudit?: { status: string; checkedSampleCount: number; checks: string[] };
  qlibReference?: { status: string; role: string; runtimeDependency: boolean };
  warnings: string[];
}

export interface SingleStockForecastRun {
  id: number; instrumentCode: string; asOfDate: string; status: SingleStockForecastStatus;
  horizonDays?: number; maturityStatus?: 'PENDING' | 'MATURED' | 'UNAVAILABLE';
  upProbability?: number; dataFingerprint: string; modelVersion: string;
  reportSchemaVersion: string; sameDataAsPrevious: boolean; createdAt: string;
  report?: SingleStockForecast;
  outcome?: { entryDate?: string; exitDate?: string; entryOpen?: number; exitOpen?: number;
    actualNetReturn?: number; actualDirection?: 'UP' | 'DOWN'; correct?: boolean | null;
    settledAt?: string; sourceCode?: string; note?: string };
  modelHealth?: ForecastModelHealth;
  holdingSnapshot?: { held: boolean; instrumentCode: string; instrumentName?: string; role?: string;
    targetWeight?: number; currentWeight?: number; quantity?: number; averageCost?: number;
    lastClose?: number; estimatedMarketValue?: number; unrealizedReturn?: number;
    note?: string; interpretation: string };
}

export interface ForecastModelHealth {
  instrumentCode: string; horizonDays: number; modelVersion: string;
  status: 'INSUFFICIENT_EVIDENCE' | 'HEALTHY' | 'WATCH' | 'PAUSED';
  directionOutputPaused: boolean; sampleCount: number; coveredCount: number;
  abstainedCount: number; coverage: number; coveredAccuracy?: number;
  brierScore?: number; baselineBrierScore?: number; logLoss?: number;
  observedUpRate?: number; firstAsOfDate?: string; lastAsOfDate?: string;
  conclusion: string;
}
