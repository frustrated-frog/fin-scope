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
  mainNetInflow?: number;
  flowRank?: number;
  persistenceDays?: number;
  crowdingScore?: number;
  rotationScore: number;
  stage?: string;
  explanations?: string[];
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
