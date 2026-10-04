import type { MarketEventConfirmation, SectorRotation } from './marketPulseTypes';
import { finite, isoDate } from './panoramaModel';

export const sectorStageLabels: Record<string, string> = {
  EMERGING: '萌芽', ACCELERATING: '加速', PERSISTENT: '持续', OVERHEATED: '过热',
  FADING: '退潮', REVERSING: '反转试探', WEAK: '弱势', INSUFFICIENT_DATA: '数据不足',
};
export const confirmationLabels: Record<string, string> = {
  CONFIRMED: '同向确认', UNCONFIRMED: '事件未获确认', MARKET_LEADING: '行情先行', QUIET: '低响应',
};

export function sectorTrail(sector: SectorRotation, businessDate: string) {
  const dated = new Map((sector.rotationTrail ?? []).filter(point => {
    const date = isoDate(point.businessDate);
    return /^\d{4}-\d{2}-\d{2}$/.test(date) && date <= businessDate
      && finite(point.relativeStrength) && finite(point.relativeMomentum);
  }).map(point => [isoDate(point.businessDate), point]));
  return [...dated].sort(([left], [right]) => left.localeCompare(right)).map(([, point]) => point);
}

export function rotationSummary(trail: ReturnType<typeof sectorTrail>) {
  const latest = trail[trail.length - 1];
  if (!latest) {
    return undefined;
  }
  const strength = latest.relativeStrength!;
  const momentum = latest.relativeMomentum!;
  const quadrant = strength >= 0 ? (momentum >= 0 ? '领先' : '减弱') : (momentum >= 0 ? '改善' : '落后');
  const previous = trail[trail.length - 2];
  const pace = previous
    ? Math.hypot(strength - previous.relativeStrength!, momentum - previous.relativeMomentum!) >= .75 ? '轮动加速' : '轮动平稳'
    : '等待更多轨迹';
  return { quadrant, pace, latest };
}

export function eventSector(event: MarketEventConfirmation, sectors: SectorRotation[]) {
  if (event.sectorCode) {
    return sectors.find(sector => sector.sectorCode === event.sectorCode);
  }
  const matches = sectors.filter(sector => sector.sectorName === event.sectorName);
  return matches.length === 1 ? matches[0] : undefined;
}
