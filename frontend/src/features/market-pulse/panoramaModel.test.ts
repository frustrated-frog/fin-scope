import { expect, test } from 'vitest';
import { buildFrames, chartPath, indexSeries, sectorMembers, turnoverTiles, type PanoramaFrame } from './panoramaModel';

const day = (date: string, a?: number, b?: number): PanoramaFrame => ({ businessDate: date, sectors: [], indices: [
  { code: 'A', name: '大盘', close: a }, { code: 'B', name: '小盘', close: b },
] });
test('uses a common starting date and preserves gaps instead of interpolating prices', () => {
  const frames = [day('2026-09-01', 90), day('2026-09-02', 100, 200), day('2026-09-03', undefined, 210), day('2026-09-04', 110, 220)];
  const series = indexSeries(frames);
  expect(series[0].values[0]).toBeUndefined();
  expect(series[0].values[1]).toBe(0);
  expect(series[0].values[2]).toBeUndefined();
  expect(series[0].values[3]).toBeCloseTo(10);
  expect(indexSeries(frames, 'A')[1].values[2]).toBeUndefined();
  expect(indexSeries(frames, 'A')[1].values[3]).toBeCloseTo(0);
  expect(chartPath([0, undefined, 10], v => v, 100)).toBe('M0.00,0.00  M100.00,10.00');
});
test('merges actual history without future dates or copying today returns into historical bars', () => {
  const frames = buildFrames([{ ...day('2026-09-01', 100), indices: [{ code: 'A', name: '大盘', close: 100, return1d: 1 }] }, day('2026-09-04', 500)], {
    businessDate: '2026-09-03', qualityStatus: 'PARTIAL', breadth: { indices: [{ code: 'A', name: '大盘', close: 110, return1d: 5,
      history: [{ businessDate: [2026, 9, 1], close: 100 }, { businessDate: '2026-09-02', close: 105 }, { businessDate: '2026-09-04', close: 500 }] }] },
  });
  expect(frames.map(frame => frame.businessDate)).toEqual(['2026-09-01', '2026-09-02', '2026-09-03']);
  expect(frames[0].indices[0].return1d).toBe(1);
  expect(frames[1].indices[0].return1d).toBeUndefined();
  expect(frames[2].indices[0].return1d).toBe(5);
  expect(frames[1].sectors).toEqual([]);
});
test('associates constituents by industry code instead of similar names', () => {
  expect(sectorMembers([{ instrumentCode: '600000.SH', groupCodes: [], sectorCodes: ['881100'] }, { instrumentCode: '600001.SH', groupCodes: [], sectorNames: ['银行'] }], { sectorCode: '881100', sectorName: '银行', rotationScore: 70 })).toHaveLength(1);
});

test('turnover map areas preserve actual amounts and omit missing weights', () => {
  const tiles = turnoverTiles([{ instrumentCode: 'A', amount: 75, groupCodes: [] }, { instrumentCode: 'B', amount: 25, groupCodes: [] }, { instrumentCode: 'C', groupCodes: [] }]);
  expect(tiles).toHaveLength(2);
  expect(tiles[0].width * tiles[0].height).toBeCloseTo(7500);
  expect(tiles[1].width * tiles[1].height).toBeCloseTo(2500);
});
