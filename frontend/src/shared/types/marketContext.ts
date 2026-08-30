export type StockDiscoveryMarketContext = {
  businessDate?: string;
  transitionCode:
    | 'REPAIR_EXPANSION'
    | 'NARROWING_DIVERGENCE'
    | 'RISK_RELEASE'
    | 'RAPID_ROTATION'
    | 'RANGE_BALANCE'
    | 'INSUFFICIENT_DATA';
  transitionLabel: string;
  riskPosture: 'OFFENSIVE' | 'BALANCED' | 'DEFENSIVE';
  preferredSectors: string[];
  avoidSectors: string[];
  chasePolicy: 'CONFIRMATION_ALLOWED' | 'PULLBACK_ONLY' | 'NO_CHASING';
  summary: string;
};
