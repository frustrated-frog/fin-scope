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

export type MarketPulseWorkspace = {
  businessDate?: string;
  regime?: MarketRegime;
  recentRegimes?: MarketRegime[];
  sectors?: SectorRotation[];
  eventConfirmations?: MarketEventConfirmation[];
  candidates?: MarketPulseCandidate[];
  qualityStatus: MarketPulseQuality;
  warnings?: string[];
  generatedAt?: string;
};
