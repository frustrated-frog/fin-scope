import { describe, expect, test } from 'vitest';

import { replayOutcome } from './capitalReplayOutcome';
import { minuteLabel } from './capitalReplaySeries';
import type { ReplayPoint } from './capitalReplaySeries';

function series(prices: Array<number | null>, from = 571): ReplayPoint[] {
  return prices.map((price, index) => ({ minute: from + index, time: `${minuteLabel(from + index)}:00`,
    price, tradeAmount: null, largeNet: null, superLargeNet: null, net: null }));
}

describe('subsequent price path', () => {
  test('distinguishes a temporary surge from an unchanged ten-minute endpoint', () => {
    const points = series([100, 101, 102, 103, 105, 104, ...Array(7).fill(102.1)]);
    const result = replayOutcome(points, { from: 571, to: 573 });
    expect(result.status).toBe('AVAILABLE');
    expect(result.label).toBe('冲高后回落');
    expect(result.maxRise).toBeCloseTo(105 / 102 - 1);
    expect(result.maxDrawdown).toBeCloseTo(102.1 / 105 - 1);
    expect(result.returnedToStart).toBe(false);
  });

  test('records a temporary decline and subsequent recovery', () => {
    const points = series([100, 99, 98, 96, ...Array(9).fill(98)]);
    const result = replayOutcome(points, { from: 571, to: 573 });
    expect(result.label).toBe('下探后修复');
    expect(result.maxRise).toBe(0);
    expect(result.maxDrawdown).toBeCloseTo(96 / 98 - 1);
  });

  test('detects a return to the original starting price even if the endpoint rises later', () => {
    const points = series([100, 101, 102, 100, ...Array(9).fill(103)]);
    const result = replayOutcome(points, { from: 571, to: 573 });
    expect(result.label).toBe('继续走高');
    expect(result.returnedToStart).toBe(true);
    expect(result.maxDrawdown).toBeCloseTo(100 / 102 - 1);
  });

  test('uses a running peak for drawdown, rather than the lowest return from the interval end', () => {
    const result = replayOutcome(series([100, 101, 102, 110, 105, ...Array(8).fill(108)]), { from: 571, to: 573 });
    expect(result.maxDrawdown).toBeCloseTo(105 / 110 - 1);
    expect(result.maxRise).toBeCloseTo(110 / 102 - 1);
  });

  test('withholds path statistics when an intermediate minute is missing', () => {
    const points = series([100, 101, 102, 104, null, ...Array(8).fill(103)]);
    points.push(...series([101], 900));
    const result = replayOutcome(points, { from: 571, to: 573 });
    expect(result).toMatchObject({ status: 'INCOMPLETE', maxRise: null, maxDrawdown: null, returnedToStart: null });
    expect(result.close.status).toBe('AVAILABLE');
    expect(result.close.retainedMove).toBeCloseTo(0.5);
  });

  test('withholds future statistics and never substitutes the latest quote for the closing price', () => {
    const result = replayOutcome(series([100, 101, 102, 104]), { from: 571, to: 573 });
    expect(result).toMatchObject({ status: 'PENDING', maxRise: null, maxDrawdown: null });
    expect(result.close).toMatchObject({ status: 'PENDING', change: null, retainedMove: null });
  });

  test('calculates the remaining ten trading minutes across lunch', () => {
    const points = [...series([100, 101, 102, 102], 687), ...series(Array(9).fill(101), 781)];
    const result = replayOutcome(points, { from: 687, to: 689 });
    expect(result).toMatchObject({ status: 'AVAILABLE', through: 789 });
    expect(result.maxDrawdown).toBeCloseTo(101 / 102 - 1);
  });

  test('only observes through the actual close for late intervals', () => {
    const result = replayOutcome(series([100, 101, 102, 103, 101, 101], 895), { from: 895, to: 897 });
    expect(result).toMatchObject({ status: 'AVAILABLE', horizon: '至收盘', through: 900, returnedToStart: false });
    expect(result.close.retainedMove).toBeCloseTo(0.5);
  });
});

describe('closing performance', () => {
  test('measures retained movement from the original starting price', () => {
    const points = [...series([100, 101, 102]), ...series([100.5], 900)];
    const result = replayOutcome(points, { from: 571, to: 573 });
    expect(result.close.retainedMove).toBeCloseTo(0.25);
    expect(result.close.change).toBeCloseTo(100.5 / 102 - 1);
  });

  test('keeps continuation above 100% and a complete reversal below 0% explicit', () => {
    const base = series([100, 99, 98]);
    expect(replayOutcome([...base, ...series([97], 900)], { from: 571, to: 573 }).close.retainedMove).toBeCloseTo(1.5);
    expect(replayOutcome([...base, ...series([101], 900)], { from: 571, to: 573 }).close.retainedMove).toBeCloseTo(-0.5);
  });

  test('does not divide a flat or tiny original move into a misleading retained percentage', () => {
    const points = [...series([100, 100.1]), ...series([101], 900)];
    const result = replayOutcome(points, { from: 571, to: 572 });
    expect(result.close.status).toBe('AVAILABLE');
    expect(result.close.retainedMove).toBeNull();
  });
});
