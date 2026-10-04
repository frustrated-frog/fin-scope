import { expect, test } from 'vitest';
import { eventSector, rotationSummary, sectorTrail } from './sectorObservation';
import type { MarketEventConfirmation, SectorRotation } from './marketPulseTypes';

test('summarizes only valid dated observations through the selected day in chronological order', () => {
  const sector: SectorRotation = { sectorCode: 'A', sectorName: '行业', rotationScore: 60, rotationTrail: [
    { businessDate: '2026-09-29', relativeStrength: 1, relativeMomentum: 1 },
    { businessDate: '2026-09-28', relativeStrength: -.5, relativeMomentum: .2 },
    { businessDate: '2026-09-30', relativeStrength: 8, relativeMomentum: -8 },
    { relativeStrength: 7, relativeMomentum: 7 },
    { businessDate: '2026-09-27', relativeStrength: NaN, relativeMomentum: 1 },
  ] };
  const trail = sectorTrail(sector, '2026-09-29');
  expect(trail.map(point => point.businessDate)).toEqual(['2026-09-28', '2026-09-29']);
  expect(rotationSummary(trail)).toMatchObject({ quadrant: '领先', pace: '轮动加速' });
  expect(rotationSummary(sectorTrail(sector, '2026-09-28'))).toMatchObject({ quadrant: '改善', pace: '等待更多轨迹' });
  expect(rotationSummary(sectorTrail(sector, '2026-09-26'))).toBeUndefined();
});

test('matches catalysts by industry code first, with an unambiguous exact-name fallback for old snapshots', () => {
  const sectors = [{ sectorCode: 'A', sectorName: '行业甲', rotationScore: 60 }, { sectorCode: 'B', sectorName: '行业乙', rotationScore: 40 }];
  const event: MarketEventConfirmation = { title: '事件', eventScore: 80, marketReactionScore: 60, sectorCode: 'A', sectorName: '行业乙' };
  expect(eventSector(event, sectors)?.sectorCode).toBe('A');
  expect(eventSector({ ...event, sectorCode: undefined }, sectors)?.sectorCode).toBe('B');
  expect(eventSector({ ...event, sectorCode: 'missing' }, sectors)).toBeUndefined();
  expect(eventSector({ ...event, sectorCode: undefined }, [...sectors, { sectorCode: 'C', sectorName: '行业乙', rotationScore: 0 }])).toBeUndefined();
});
