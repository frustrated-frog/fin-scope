import { describe, expect, test } from 'vitest';

import { replayEvents } from './capitalReplayEvents';
import { minuteLabel } from './capitalReplaySeries';
import type { ReplayPoint } from './capitalReplaySeries';

function series(prices: Array<number | null>, from = 9 * 60 + 31): ReplayPoint[] {
  return prices.map((price, index) => ({
    minute: from + index, time: `${minuteLabel(from + index)}:00`, price,
    tradeAmount: 100_000, largeNet: null, superLargeNet: null, net: null
  }));
}

describe('replay turning points', () => {
  test('includes a move exactly at the threshold without including a smaller move', () => {
    expect(replayEvents(series([100, 100.3, 100.6]))).toHaveLength(1);
    expect(replayEvents(series([100, 100.3, 100.59]))).toEqual([]);
  });

  test('keeps variable-length legs and their relationship without requiring fund-flow data', () => {
    const events = replayEvents(series([100, 100.2, 100.8, 101.2, 102, 101.5, 101, 100, ...Array(12).fill(100)]));
    expect(events).toHaveLength(2);
    expect(events[0]).toMatchObject({ from: 571, to: 575, label: '快速上涨', net: null, flowCount: 0 });
    expect(events[1]).toMatchObject({ from: 575, to: 578, context: '上涨后回落' });
    expect(events[0].outcome).toMatchObject({ status: 'AVAILABLE', label: '涨幅全部回吐', through: 585 });
    expect(events[0].outcome.change).toBeCloseTo(100 / 102 - 1);
  });

  test('describes falling prices with net inflow without assigning a trader identity', () => {
    const points = series([100, 99.6, 99.2, 98.8]);
    points.forEach((point) => { point.net = 30_000; });
    expect(replayEvents(points)[0]).toMatchObject({ label: '下跌伴大额净流入', direction: 'down', net: 120_000 });
  });

  test('retains more than four turns in chronological order', () => {
    const events = replayEvents(series([100, 101, 102, 101, 100, 101, 102, 101, 100, 101, 102, 101, 100]));
    expect(events).toHaveLength(6);
    expect(events.map((event) => event.from)).toEqual([571, 573, 575, 577, 579, 581]);
  });

  test('starts a move at the end of a flat period and compares equal-length prior turnover', () => {
    const points = series([...Array(10).fill(100), 100.5, 101, 101.5, 102]);
    points.forEach((point, index) => { point.tradeAmount = index >= 9 ? 300_000 : 100_000; });
    expect(replayEvents(points)[0]).toMatchObject({ from: 580, to: 584, label: '放量上涨', volumeRatio: 3 });
  });

  test('does not invent moves across missing prices, missing minutes or lunch', () => {
    const missingPrice = series([100, null, 105]);
    const missingMinute = [series([100])[0], ...series([105, 105], 574)];
    const lunch = [...series([100, 100], 689), ...series([105, 105], 781)];
    expect(replayEvents(missingPrice)).toEqual([]);
    expect(replayEvents(missingMinute)).toEqual([]);
    expect(replayEvents(lunch)).toEqual([]);
  });

  test('does not present partially covered net flow as a complete event metric', () => {
    const points = series([100, 100.5, 101, 101.5]);
    points[1].net = -30_000;
    expect(replayEvents(points)[0]).toMatchObject({ net: null, flowCount: 1, minuteCount: 4 });
  });

  test('detects isolated flow concentrations throughout the day without a four-event cap', () => {
    const points = series(Array(100).fill(100));
    points.forEach((point, index) => { point.net = index % 16 >= 5 && index % 16 < 10 ? -40_000 : 0; });
    const events = replayEvents(points);
    expect(events.length).toBeGreaterThan(4);
    expect(events.every((event) => event.label === '大额净流出集中')).toBe(true);
    expect(events.every((event, index) => index === 0 || event.from > events[index - 1].to)).toBe(true);
  });

  test('does not interpret zero or incomplete turnover as a concentration or volume expansion', () => {
    const flat = series(Array(10).fill(100));
    flat.forEach((point) => { point.net = -40_000; point.tradeAmount = 0; });
    expect(replayEvents(flat)).toEqual([]);
    const rising = series([...Array(5).fill(100), 101, 102]);
    rising[2].tradeAmount = null;
    expect(replayEvents(rising)[0].volumeRatio).toBeNull();
  });

  test('does not mutate the source order and handles an empty or single-point series', () => {
    const points = series([100, 101, 102]).reverse();
    const original = [...points];
    expect(replayEvents(points)[0].from).toBe(571);
    expect(points).toEqual(original);
    expect(replayEvents([])).toEqual([]);
    expect(replayEvents(series([100]))).toEqual([]);
  });
});

describe('subsequent performance', () => {
  test('marks an unfinished ten-minute observation as pending', () => {
    expect(replayEvents(series([100, 101, 102, 102]))[0].outcome).toMatchObject({
      status: 'PENDING', label: '待观察', change: null
    });
  });

  test('does not substitute a later quote for a missing follow-up minute', () => {
    const points = series([100, 101, 102, ...Array(15).fill(102)]);
    points.splice(7, 1);
    expect(replayEvents(points)[0].outcome).toMatchObject({ status: 'INCOMPLETE', change: null });
  });

  test('counts trading minutes across lunch rather than treating the break as a data gap', () => {
    const points = [...series([100, 101, 102, 102], 687), ...series(Array(10).fill(101), 781)];
    expect(replayEvents(points)[0].outcome).toMatchObject({
      status: 'AVAILABLE', through: 789, horizon: '后续 10 个交易分钟', label: '涨幅回吐过半'
    });
  });

  test('uses the close for a late event and reports when there is no subsequent interval', () => {
    const points = series([100, 101, 102, ...Array(8).fill(102)], 890);
    expect(replayEvents(points)[0].outcome).toMatchObject({ status: 'AVAILABLE', through: 900, horizon: '至收盘' });
    expect(replayEvents(series([100, 101, 102], 898))[0].outcome).toMatchObject({
      status: 'PENDING', label: '已收盘，无后续区间', change: null
    });
  });

  test('recognizes recovery after a decline and continuation after a rise', () => {
    const recovery = replayEvents(series([100, 99, 98, ...Array(10).fill(100)]));
    expect(recovery[0].outcome.label).toBe('跌幅全部修复');
    const continuation = replayEvents(series([100, 101, 102, 101.5, ...Array(10).fill(103)]));
    expect(continuation[0].outcome.label).toBe('继续走高');
  });
});
