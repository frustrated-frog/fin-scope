import { continuousPriceRuns, describeReplayRange } from './capitalReplayEvents';
import type { ReplayEvent } from './capitalReplayEvents';
import { AM_CLOSE, CLOSE, OPEN, PM_OPEN } from './capitalReplaySeries';
import type { MinuteRange, ReplayPoint } from './capitalReplaySeries';

export type ReplayStage = ReplayEvent & { events: ReplayEvent[] };

const STAGE_REVERSAL = 0.012;
const MIN_STAGE_MINUTES = 5;
const LARGE_MOVE = 0.025;
const EPSILON = 1e-10;
const PERIODS = [
  { label: '早盘', from: OPEN, to: OPEN + 60 },
  { label: '上午', from: OPEN + 60, to: AM_CLOSE },
  { label: '午后', from: PM_OPEN, to: 14 * 60 + 30 },
  { label: '尾盘', from: 14 * 60 + 30, to: CLOSE }
];

function stageRanges(points: ReplayPoint[]): MinuteRange[] {
  const ranges: MinuteRange[] = [];
  let start = points[0];
  let extreme = start;
  let low = start;
  let high = start;
  let direction = 0;
  for (const point of points.slice(1)) {
    if (direction === 0) {
      low = point.price! < low.price! ? point : low;
      high = point.price! > high.price! ? point : high;
      if (point.price! / low.price! - 1 >= STAGE_REVERSAL - EPSILON) {
        direction = 1;
        extreme = point;
      } else if (point.price! / high.price! - 1 <= -STAGE_REVERSAL + EPSILON) {
        direction = -1;
        extreme = point;
      }
      continue;
    }
    if (direction * (point.price! - extreme.price!) > 0) {
      extreme = point;
    } else if (direction * (point.price! / extreme.price! - 1) <= -STAGE_REVERSAL + EPSILON
      && (extreme.minute - start.minute >= MIN_STAGE_MINUTES
        || Math.abs(extreme.price! / start.price! - 1) >= LARGE_MOVE - EPSILON
        || Math.abs(point.price! / extreme.price! - 1) >= LARGE_MOVE - EPSILON)) {
      ranges.push({ from: start.minute, to: extreme.minute });
      start = extreme;
      extreme = point;
      direction *= -1;
    }
  }
  const last = points[points.length - 1];
  if (last.minute > start.minute) {
    ranges.push({ from: start.minute, to: last.minute });
  }
  return ranges;
}

function stageLabel(points: ReplayPoint[], range: MinuteRange) {
  const sample = points.filter((point) => point.minute >= range.from && point.minute <= range.to);
  const first = sample[0].price!;
  const last = sample[sample.length - 1].price!;
  const high = Math.max(...sample.map((point) => point.price!));
  const low = Math.min(...sample.map((point) => point.price!));
  const peakGain = high / first - 1;
  const lowChange = low / first - 1;
  const change = last / first - 1;
  if (peakGain >= 0.006 - EPSILON && last <= first + (high - first) * 0.5 && last / high - 1 <= -0.006 + EPSILON) {
    return '冲高回落';
  }
  if (lowChange <= -0.006 + EPSILON && last >= first - (first - low) * 0.5 && last / low - 1 >= 0.006 - EPSILON) {
    return '下探后修复';
  }
  if (change >= 0.006 - EPSILON) {
    return '持续走高';
  }
  if (change <= -0.006 + EPSILON) {
    return '持续走低';
  }
  return high / low - 1 >= 0.006 - EPSILON ? '区间震荡' : '窄幅整理';
}

/** Coarse price stages cover complete runs; all original events remain attached exactly once. */
export function replayStages(points: ReplayPoint[], events: ReplayEvent[]): ReplayStage[] {
  const ordered = [...points].sort((first, second) => first.minute - second.minute);
  const stages: ReplayStage[] = [];
  for (const run of continuousPriceRuns(ordered)) {
    for (const period of PERIODS) {
      const sample = run.filter((point) => point.minute >= period.from && point.minute <= period.to);
      if (sample.length < 2) {
        continue;
      }
      for (const range of stageRanges(sample)) {
        const event = describeReplayRange(ordered, run, range);
        stages.push({ ...event, label: stageLabel(run, range), context: period.label,
          direction: Math.abs(event.change) < 0.002 ? 'flat' : event.direction, events: [] });
      }
    }
  }
  stages.sort((first, second) => first.from - second.from);
  for (const event of events) {
    const stage = stages.find((candidate) => event.to > candidate.from && event.to <= candidate.to);
    if (stage) {
      stage.events.push(event);
    }
  }
  return stages;
}
