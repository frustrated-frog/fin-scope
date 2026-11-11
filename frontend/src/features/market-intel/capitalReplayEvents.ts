import { AM_CLOSE, CLOSE, OPEN, PM_OPEN } from './capitalReplaySeries';
import type { MinuteRange, ReplayPoint } from './capitalReplaySeries';

export type ReplayOutcome = {
  status: 'AVAILABLE' | 'PENDING' | 'INCOMPLETE';
  through: number;
  horizon: string;
  label: string;
  change: number | null;
};

export type ReplayEvent = MinuteRange & {
  label: string;
  direction: 'up' | 'down' | 'flat';
  change: number;
  amount: number | null;
  net: number | null;
  flowCount: number;
  minuteCount: number;
  volumeRatio: number | null;
  context: string;
  outcome: ReplayOutcome;
};

// These descriptive thresholds are local to the replay view, not trading signals.
const MIN_MOVE = 0.006;
const REVERSAL = 0.004;
const RAPID_MOVE = 0.01;
const RAPID_MINUTES = 10;
const VOLUME_EXPANSION = 1.5;
const FLOW_SHARE = 0.15;
const FLOW_MINUTES = 5;
const FOLLOWUP_MINUTES = 10;
const NUMERIC_TOLERANCE = 1e-10;

export function replayPercent(value: number) {
  return `${value > 0 ? '+' : value < 0 ? '−' : ''}${(Math.abs(value) * 100).toFixed(2)}%`;
}

export function replayPhase(minute: number) {
  return minute <= AM_CLOSE ? '上午' : minute < 14 * 60 + 30 ? '午后' : '尾盘';
}

function sessionIndex(minute: number) {
  return minute <= AM_CLOSE ? minute - OPEN : AM_CLOSE - OPEN + minute - PM_OPEN;
}

function sessionMinute(index: number) {
  return index <= AM_CLOSE - OPEN ? OPEN + index : PM_OPEN + index - (AM_CLOSE - OPEN);
}

function completeSum(points: ReplayPoint[], read: (point: ReplayPoint) => number | null) {
  const values = points.map(read);
  return values.length && values.every((value) => value !== null && Number.isFinite(value))
    ? values.reduce<number>((total, value) => total + value!, 0) : null;
}

function continuousPriceRuns(points: ReplayPoint[]) {
  const runs: ReplayPoint[][] = [];
  let run: ReplayPoint[] = [];
  for (const point of points) {
    if (point.price === null || point.price <= 0 || !Number.isFinite(point.price)) {
      run = [];
      continue;
    }
    if (!run.length || point.minute !== run[run.length - 1].minute + 1) {
      run = [];
      runs.push(run);
    }
    run.push(point);
  }
  return runs;
}

function priceLegs(run: ReplayPoint[]): MinuteRange[] {
  const legs: MinuteRange[] = [];
  let anchor = run[0];
  let extreme = anchor;
  let low = anchor;
  let high = anchor;
  let direction = 0;
  const append = () => {
    if (Math.abs(extreme.price! / anchor.price! - 1) >= MIN_MOVE - NUMERIC_TOLERANCE) {
      legs.push({ from: anchor.minute, to: extreme.minute });
    }
  };
  for (const point of run.slice(1)) {
    if (direction === 0) {
      if (point.price! <= low.price!) {
        low = point;
      }
      if (point.price! >= high.price!) {
        high = point;
      }
      if (point.price! / low.price! - 1 >= MIN_MOVE - NUMERIC_TOLERANCE) {
        anchor = low;
        direction = 1;
        extreme = point;
      } else if (point.price! / high.price! - 1 <= -MIN_MOVE + NUMERIC_TOLERANCE) {
        anchor = high;
        direction = -1;
        extreme = point;
      }
      continue;
    }
    if (direction * (point.price! - extreme.price!) > 0) {
      extreme = point;
    } else if (direction * (point.price! / extreme.price! - 1) <= -REVERSAL + NUMERIC_TOLERANCE) {
      append();
      anchor = extreme;
      extreme = point;
      direction *= -1;
    }
  }
  append();
  return legs;
}

function followup(points: ReplayPoint[], range: MinuteRange, change: number): ReplayOutcome {
  const endIndex = sessionIndex(range.to);
  const targetIndex = Math.min(endIndex + FOLLOWUP_MINUTES, sessionIndex(CLOSE));
  const through = sessionMinute(targetIndex);
  const horizon = targetIndex - endIndex < FOLLOWUP_MINUTES ? '至收盘' : '后续 10 个交易分钟';
  const base = { through, horizon, change: null };
  if (targetIndex === endIndex) {
    return { ...base, status: 'PENDING', label: '已收盘，无后续区间' };
  }
  if (Math.max(...points.map((point) => point.minute)) < through) {
    return { ...base, status: 'PENDING', label: '待观察' };
  }
  // 13:00 is an optional snapshot at the lunch boundary, not an extra trading minute.
  const prices = new Map(points.filter((point) => point.minute !== PM_OPEN)
    .map((point) => [sessionIndex(point.minute), point.price]));
  const startPrice = points.find((point) => point.minute === range.to)?.price;
  for (let index = endIndex + 1; index <= targetIndex; index += 1) {
    const price = prices.get(index);
    if (price == null || price <= 0 || !Number.isFinite(price)) {
      return { ...base, status: 'INCOMPLETE', label: '后续分钟缺失，暂不判断' };
    }
  }
  const afterChange = prices.get(targetIndex)! / startPrice! - 1;
  const move = Math.abs(change);
  const retraced = change === 0 ? 0 : -Math.sign(change) * afterChange * (1 + change) / move;
  let label = Math.abs(afterChange) < 0.002 ? '变化不大' : afterChange > 0 ? '随后走高' : '随后走低';
  if (move >= MIN_MOVE - NUMERIC_TOLERANCE && Math.abs(afterChange) >= 0.002) {
    label = Math.sign(afterChange) === Math.sign(change)
      ? change > 0 ? '继续走高' : '继续走低'
      : change > 0 ? '小幅回落' : '小幅修复';
  }
  if (move >= MIN_MOVE - NUMERIC_TOLERANCE && retraced >= 1 - NUMERIC_TOLERANCE) {
    label = change > 0 ? '涨幅全部回吐' : '跌幅全部修复';
  } else if (move >= MIN_MOVE - NUMERIC_TOLERANCE && retraced >= 0.5 - NUMERIC_TOLERANCE) {
    label = change > 0 ? '涨幅回吐过半' : '跌幅修复过半';
  }
  return { through, horizon, status: 'AVAILABLE', label, change: afterChange };
}

function describeEvent(points: ReplayPoint[], run: ReplayPoint[], range: MinuteRange, flowOnly = false): ReplayEvent {
  const sample = run.filter((point) => point.minute >= range.from && point.minute <= range.to);
  const first = sample[0];
  const last = sample[sample.length - 1];
  const change = last.price! / first.price! - 1;
  const amount = completeSum(sample, (point) => point.tradeAmount);
  const net = completeSum(sample, (point) => point.net);
  const prior = run.filter((point) => point.minute < range.from).slice(-sample.length);
  const priorAmount = prior.length === sample.length ? completeSum(prior, (point) => point.tradeAmount) : null;
  const volumeRatio = amount !== null && priorAmount !== null && priorAmount > 0 ? amount / priorAmount : null;
  const up = change > 0;
  let label = up ? '上涨' : '下跌';
  if (flowOnly) {
    label = net! > 0 ? '大额净流入集中' : '大额净流出集中';
  } else if (net !== null && amount !== null && amount > 0
    && Math.abs(net) / amount >= FLOW_SHARE && Math.sign(net) !== Math.sign(change)) {
    label = up ? '上涨伴大额净流出' : '下跌伴大额净流入';
  } else if (volumeRatio !== null && volumeRatio >= VOLUME_EXPANSION) {
    label = up ? '放量上涨' : '放量下跌';
  } else if (range.to - range.from <= RAPID_MINUTES && Math.abs(change) >= RAPID_MOVE - NUMERIC_TOLERANCE) {
    label = up ? '快速上涨' : '快速下跌';
  } else {
    label = up ? '持续走高' : '持续走低';
  }
  return {
    ...range, label, direction: flowOnly ? 'flat' : up ? 'up' : 'down', change, amount, net, volumeRatio,
    flowCount: sample.filter((point) => point.net !== null).length, minuteCount: sample.length,
    context: replayPhase(range.from), outcome: followup(points, range, change)
  };
}

/** Derive chronological, non-overlapping legs; gaps and lunch always split detection. */
export function replayEvents(points: ReplayPoint[]): ReplayEvent[] {
  const ordered = [...points].sort((first, second) => first.minute - second.minute);
  const runs = continuousPriceRuns(ordered);
  const events = runs.flatMap((run) => priceLegs(run).map((range) => describeEvent(ordered, run, range)));
  const flowCandidates: ReplayEvent[] = [];
  const magnitudes: number[] = [];
  for (const run of runs) {
    for (let start = 0; start <= run.length - FLOW_MINUTES; start += 1) {
      const sample = run.slice(start, start + FLOW_MINUTES);
      const event = describeEvent(ordered, run, { from: sample[0].minute, to: sample[FLOW_MINUTES - 1].minute }, true);
      if (event.net !== null && event.amount !== null && event.amount > 0) {
        magnitudes.push(Math.abs(event.net));
        if (Math.abs(event.net) / event.amount >= FLOW_SHARE) {
          flowCandidates.push(event);
        }
      }
    }
  }
  magnitudes.sort((first, second) => first - second);
  const threshold = (magnitudes[Math.floor(magnitudes.length / 2)] ?? 0) * 1.5;
  for (const event of flowCandidates.sort((first, second) => Math.abs(second.net!) - Math.abs(first.net!))) {
    if (Math.abs(event.net!) <= threshold || events.some((other) => event.from <= other.to && event.to >= other.from)) {
      continue;
    }
    events.push(event);
  }
  events.sort((first, second) => first.from - second.from);
  for (let index = 1; index < events.length; index += 1) {
    const previous = events[index - 1];
    const event = events[index];
    if (previous.to === event.from && previous.direction !== 'flat' && event.direction !== 'flat') {
      event.context = event.direction === 'down' ? '上涨后回落' : '下跌后修复';
    }
  }
  return events;
}
