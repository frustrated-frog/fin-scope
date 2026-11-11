import { expect, test } from 'vitest';

import { replayEvents } from './capitalReplayEvents';
import { replayStages } from './capitalReplayStages';
import { minuteLabel } from './capitalReplaySeries';
import type { ReplayPoint } from './capitalReplaySeries';

function series(prices: Array<number | null>, from = 570): ReplayPoint[] {
  return prices.map((price, index) => ({ minute: from + index, time: `${minuteLabel(from + index)}:00`,
    price, tradeAmount: 100_000, largeNet: null, superLargeNet: null, net: null }));
}

test('groups small reversals into a stage while retaining every original interval exactly once', () => {
  const points = series([100, 100.8, 100, 100.9, 100, 100.7, 100, ...Array(20).fill(100)]);
  const events = replayEvents(points);
  const stages = replayStages(points, events);
  expect(events.length).toBeGreaterThan(4);
  expect(stages).toHaveLength(1);
  expect(stages[0]).toMatchObject({ from: 570, to: 596, context: '早盘', label: '冲高回落', net: null });
  expect(stages.flatMap((stage) => stage.events)).toEqual(events);
});

test('splits significant reversals without imposing a fixed number of stages', () => {
  const points = series([100, 101, 102, 103, 104, 105, 104, 102, 101, 100, 99, 98, 100, 102, 104]);
  const stages = replayStages(points, replayEvents(points));
  expect(stages).toHaveLength(3);
  expect(stages.map((stage) => stage.direction)).toEqual(['up', 'down', 'up']);
  expect(stages[0].to).toBe(stages[1].from);
});

test('preserves morning, afternoon and tail context instead of ranking away quieter periods', () => {
  const points = [...series(Array(121).fill(100)), ...series(Array(121).fill(100), 780)];
  const stages = replayStages(points, []);
  expect(stages.map((stage) => stage.context)).toEqual(['早盘', '上午', '午后', '尾盘']);
  expect(stages.every((stage) => stage.label === '窄幅整理' && stage.direction === 'flat')).toBe(true);
});

test('does not bridge missing minute prices, time gaps or the lunch break', () => {
  const points = [...series([100, 101, null, 103, 104]), ...series([105, 106], 600),
    ...series([110, 111], 689), ...series([112, 113], 781)];
  const stages = replayStages(points, replayEvents(points));
  expect(stages.map(({ from, to }) => [from, to])).toEqual([[570, 571], [573, 574], [600, 601], [689, 690], [781, 782]]);
});

test('assigns an interval crossing a phase boundary to one stage and keeps its full evidence range', () => {
  const points = series([100, 100.2, 100.5, 101], 628);
  const events = replayEvents(points);
  const stages = replayStages(points, events);
  expect(stages).toHaveLength(2);
  expect(stages.flatMap((stage) => stage.events)).toHaveLength(events.length);
  expect(stages[1].events[0]).toMatchObject({ from: 628, to: 631 });
});

test('retains flow concentration details even when the price stage is flat', () => {
  const points = series(Array(40).fill(100));
  points.forEach((point, index) => { point.net = index >= 5 && index <= 9 ? 40_000 : 0; });
  const events = replayEvents(points);
  const stages = replayStages(points, events);
  expect(events.length).toBeGreaterThan(0);
  expect(stages[0].events).toEqual(events);
  expect(stages[0].net).toBe(200_000);
});

test('does not turn an empty or single-price session into a fabricated stage', () => {
  expect(replayStages([], [])).toEqual([]);
  expect(replayStages(series([100]), [])).toEqual([]);
});

test('does not treat the opening auction snapshot as a missing flow minute', () => {
  const points = series([100, 100.5, 101, 101.5]);
  points.forEach((point, index) => { point.net = index === 0 ? null : 10_000; });
  const stage = replayStages(points, replayEvents(points))[0];
  expect(stage).toMatchObject({ net: 30_000, flowCount: 3, minuteCount: 3 });
});
