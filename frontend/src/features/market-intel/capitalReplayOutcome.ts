import { AM_CLOSE, CLOSE, OPEN, PM_OPEN } from './capitalReplaySeries';
import type { MinuteRange, ReplayPoint } from './capitalReplaySeries';

type OutcomeStatus = 'AVAILABLE' | 'PENDING' | 'INCOMPLETE';

export type ReplayClosePerformance = {
  status: OutcomeStatus;
  change: number | null;
  retainedMove: number | null;
};

export type ReplayOutcome = {
  status: OutcomeStatus;
  through: number;
  horizon: string;
  label: string;
  change: number | null;
  maxRise: number | null;
  maxDrawdown: number | null;
  returnedToStart: boolean | null;
  direction: 'up' | 'down' | 'flat';
  close: ReplayClosePerformance;
};

const MIN_MOVE = 0.006;
const EPSILON = 1e-10;
const FOLLOWUP_MINUTES = 10;

function sessionIndex(minute: number) {
  return minute <= AM_CLOSE ? minute - OPEN : AM_CLOSE - OPEN + minute - PM_OPEN;
}

function sessionMinute(index: number) {
  return index <= AM_CLOSE - OPEN ? OPEN + index : PM_OPEN + index - (AM_CLOSE - OPEN);
}

function validPrice(value: number | null | undefined): value is number {
  return value != null && Number.isFinite(value) && value > 0;
}

function closePerformance(points: ReplayPoint[], start: number, end: number): ReplayClosePerformance {
  const closing = points.find((point) => point.minute === CLOSE)?.price;
  if (!validPrice(closing)) {
    return { status: 'PENDING', change: null, retainedMove: null };
  }
  return {
    status: 'AVAILABLE', change: closing / end - 1,
    retainedMove: Math.abs(end / start - 1) >= MIN_MOVE - EPSILON ? (closing - start) / (end - start) : null
  };
}

function endpointLabel(change: number, afterChange: number) {
  const move = Math.abs(change);
  const retraced = change === 0 ? 0 : -Math.sign(change) * afterChange * (1 + change) / move;
  let label = Math.abs(afterChange) < 0.002 ? '变化不大' : afterChange > 0 ? '随后走高' : '随后走低';
  if (move >= MIN_MOVE - EPSILON && Math.abs(afterChange) >= 0.002) {
    label = Math.sign(afterChange) === Math.sign(change)
      ? change > 0 ? '继续走高' : '继续走低'
      : change > 0 ? '小幅回落' : '小幅修复';
  }
  if (move >= MIN_MOVE - EPSILON && retraced >= 1 - EPSILON) {
    label = change > 0 ? '涨幅全部回吐' : '跌幅全部修复';
  } else if (move >= MIN_MOVE - EPSILON && retraced >= 0.5 - EPSILON) {
    label = change > 0 ? '涨幅回吐过半' : '跌幅修复过半';
  }
  return label;
}

function pathPerformance(prices: number[], start: number, end: number, direction: ReplayOutcome['direction']) {
  let high = end;
  let maxDrawdown = 0;
  for (const price of prices) {
    high = Math.max(high, price);
    maxDrawdown = Math.min(maxDrawdown, price / high - 1);
  }
  return {
    maxRise: Math.max(0, ...prices.map((price) => price / end - 1)), maxDrawdown,
    returnedToStart: direction === 'flat' ? null : prices.some((price) => direction === 'up'
      ? price <= start * (1 + EPSILON) : price >= start * (1 - EPSILON))
  };
}

/** Path metrics require every subsequent trading minute; the closing return needs its exact endpoint only. */
export function replayOutcome(points: ReplayPoint[], range: MinuteRange): ReplayOutcome {
  const endIndex = sessionIndex(range.to);
  const targetIndex = Math.min(endIndex + FOLLOWUP_MINUTES, sessionIndex(CLOSE));
  const through = sessionMinute(targetIndex);
  const start = points.find((point) => point.minute === range.from)?.price;
  const end = points.find((point) => point.minute === range.to)?.price;
  const change = validPrice(start) && validPrice(end) ? end / start - 1 : 0;
  const direction = Math.abs(change) < MIN_MOVE - EPSILON ? 'flat' : change > 0 ? 'up' : 'down';
  const base: ReplayOutcome = {
    status: 'PENDING', through, horizon: targetIndex - endIndex < FOLLOWUP_MINUTES ? '至收盘' : '后续 10 个交易分钟',
    label: '待观察', change: null, maxRise: null, maxDrawdown: null, returnedToStart: null, direction,
    close: validPrice(start) && validPrice(end) ? closePerformance(points, start, end)
      : { status: 'INCOMPLETE', change: null, retainedMove: null }
  };
  if (!validPrice(start) || !validPrice(end)) {
    return { ...base, status: 'INCOMPLETE', label: '区间价格缺失，暂不判断' };
  }
  if (targetIndex === endIndex) {
    return { ...base, label: '已收盘，无后续区间' };
  }
  if (Math.max(...points.map((point) => point.minute)) < through) {
    return base;
  }
  // The optional 13:00 snapshot does not add a trading minute to the lunch boundary.
  const byIndex = new Map(points.filter((point) => point.minute !== PM_OPEN)
    .map((point) => [sessionIndex(point.minute), point.price]));
  const prices: number[] = [];
  for (let index = endIndex + 1; index <= targetIndex; index += 1) {
    const price = byIndex.get(index);
    if (!validPrice(price)) {
      return { ...base, status: 'INCOMPLETE', label: '后续分钟缺失，暂不判断' };
    }
    prices.push(price);
  }
  const afterChange = prices[prices.length - 1] / end - 1;
  const path = pathPerformance(prices, start, end, direction);
  let label = endpointLabel(change, afterChange);
  if (label === '变化不大') {
    if (Math.max(...prices) / end - 1 >= MIN_MOVE - EPSILON) {
      label = '冲高后回落';
    } else if (Math.min(...prices) / end - 1 <= -MIN_MOVE + EPSILON) {
      label = '下探后修复';
    }
  }
  return { ...base, ...path, status: 'AVAILABLE', change: afterChange, label };
}
