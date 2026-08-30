export type MarketPulseQuality = 'READY' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE';

export type MarketRegimeFeatures = {
  return1d?: number;
  return5d?: number;
  return20d?: number;
  priceVsMa20?: number;
  priceVsMa60?: number;
  volatility20?: number;
  maxDrawdown20?: number;
  amountRatio5To20?: number;
  marketBreadth?: number;
  sectorDispersion?: number;
};

export type MarketRegime = {
  businessDate?: string | number[];
  trendState?: string;
  liquidityState?: string;
  riskAppetiteState?: string;
  rotationState?: string;
  marketStage?: string;
  confidenceScore?: number;
  explanation?: string;
  evidence?: string[];
  qualityStatus?: MarketPulseQuality;
  features?: MarketRegimeFeatures;
};

export type SectorRotation = {
  sectorCode: string;
  sectorName: string;
  return1d?: number;
  return5d?: number;
  return20d?: number;
  excessReturn5d?: number;
  mainNetInflow?: number;
  flowRank?: number;
  previousFlowRank?: number;
  breadthRatio?: number;
  persistenceDays?: number;
  crowdingScore?: number;
  rotationScore: number;
  stage?: string;
  explanations?: string[];
  rotationTrail?: SectorRotationPoint[];
};

export type SectorRotationPoint = {
  businessDate?: string;
  relativeStrength?: number;
  relativeMomentum?: number;
};

export type MarketEventConfirmation = {
  radarEventId?: number;
  title: string;
  sectorName?: string;
  eventScore: number;
  marketReactionScore: number;
  confirmationState?: string;
  eligibleForRanking?: boolean;
  evidence?: string[];
};

export type MarketPulseCandidate = {
  instrumentCode: string;
  name: string;
  researchRank?: number;
  calibratedProbability?: number;
  healthStatus?: string;
  sectorName?: string;
  sectorStage?: string;
  whyNow?: string;
  reasons?: string[];
  risks?: string[];
  invalidationConditions?: string[];
};

export type MarketIndexPerformance = {
  code: string;
  name: string;
  businessDate?: string;
  close?: number;
  return1d?: number;
  return5d?: number;
  return20d?: number;
  sourceCode?: string;
  qualityStatus?: string;
};

export type MarketReturnDistributionBucket = {
  code: string;
  label: string;
  lowerBound?: number;
  upperBound?: number;
  count: number;
  ratio: number;
};

export type MarketTrendBreadth = {
  ma20Ratio?: number;
  ma20ValidCount?: number;
  ma60Ratio?: number;
  ma60ValidCount?: number;
  ma120Ratio?: number;
  ma120ValidCount?: number;
  ma250Ratio?: number;
  ma250ValidCount?: number;
};

export type MarketNewHighLow = {
  high20Count?: number;
  low20Count?: number;
  valid20Count?: number;
  high60Count?: number;
  low60Count?: number;
  valid60Count?: number;
  high250Count?: number;
  low250Count?: number;
  valid250Count?: number;
};

export type MarketInternalHistoryPoint = {
  businessDate?: string;
  advanceCount?: number;
  declineCount?: number;
  flatCount?: number;
  validCount?: number;
  advanceRatio?: number;
  totalAmount?: number;
  medianChangePct?: number;
  ma20Ratio?: number;
  ma60Ratio?: number;
  ma120Ratio?: number;
  ma250Ratio?: number;
  newHigh20Count?: number;
  newLow20Count?: number;
  newHigh60Count?: number;
  newLow60Count?: number;
  newHigh250Count?: number;
  newLow250Count?: number;
  netAdvances?: number;
  advanceDeclineLine?: number;
  advanceAmountRatio?: number;
  netAdvancingAmount?: number;
  mcclellanOscillator?: number;
  breadthThrustRatio?: number;
};

export type MarketBreadthChangeSummary = {
  previousBusinessDate?: string;
  headline?: string;
  advanceRatioChange?: number;
  medianChangePctChange?: number;
  totalAmountChangeRatio?: number;
  ma20RatioChange?: number;
  newHighLowBalanceChange?: number;
  netAdvancesChange?: number;
  advanceAmountRatioChange?: number;
  mcclellanOscillatorChange?: number;
  changes?: string[];
};

export type MarketVolumePressure = {
  advanceAmount?: number;
  declineAmount?: number;
  flatAmount?: number;
  advanceAmountRatio?: number;
  netAdvancingAmount?: number;
  trin?: number;
};

export type MarketBreadthMomentum = {
  mcclellanOscillator?: number;
  breadthThrustRatio?: number;
  status?: string;
};

export type MarketBreadth = {
  businessDate?: string;
  sourceCode?: string;
  sourceFamily?: string;
  qualityStatus?: string;
  retrievedAt?: string;
  advanceCount?: number;
  declineCount?: number;
  flatCount?: number;
  validCount?: number;
  advanceRatio?: number;
  totalAmount?: number;
  limitUpCount?: number;
  limitDownCount?: number;
  medianChangePct?: number;
  returnDistribution?: MarketReturnDistributionBucket[];
  trendBreadth?: MarketTrendBreadth;
  newHighLow?: MarketNewHighLow;
  netAdvances?: number;
  advanceDeclineLine?: number;
  volumePressure?: MarketVolumePressure;
  breadthMomentum?: MarketBreadthMomentum;
  history?: MarketInternalHistoryPoint[];
  changeSummary?: MarketBreadthChangeSummary;
  indices?: MarketIndexPerformance[];
  interpretation?: string;
  warnings?: string[];
};

export type DailyMarketReview = {
  businessDate?: string;
  headline?: string;
  indexOverview?: string;
  breadthConclusion?: string;
  leadingSectors?: string[];
  weakeningSectors?: string[];
  confirmedEvents?: string[];
  riskSignals?: string[];
  nextSessionWatchlist?: string[];
  evidence?: string[];
  qualityStatus?: MarketPulseQuality;
  sourceFingerprint?: string;
  generatedAt?: string;
};

export type MarketPulseHistoryPoint = {
  businessDate?: string;
  marketStage?: string;
  confidenceScore?: number;
  advanceRatio?: number;
  totalAmount?: number;
  medianChangePct?: number;
  leadingSectorName?: string;
  leadingSectorScore?: number;
  headline?: string;
  qualityStatus?: MarketPulseQuality;
};

export type MarketPulseWorkspace = {
  businessDate?: string;
  regime?: MarketRegime;
  breadth?: MarketBreadth;
  dailyReview?: DailyMarketReview;
  recentRegimes?: MarketRegime[];
  historyPoints?: MarketPulseHistoryPoint[];
  sectors?: SectorRotation[];
  eventConfirmations?: MarketEventConfirmation[];
  candidates?: MarketPulseCandidate[];
  qualityStatus: MarketPulseQuality;
  warnings?: string[];
  generatedAt?: string;
};

export type MarketPulseBackfillResult = {
  startDate?: string;
  endDate?: string;
  status: 'SUCCEEDED' | 'PARTIAL' | 'FAILED';
  results: Array<{
    businessDate: string;
    status: string;
    qualityStatus?: MarketPulseQuality;
  }>;
  failures?: Record<string, string>;
};
