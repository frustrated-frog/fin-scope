export type ReplayMinute = {
  time: string;
  price: number | null;
  tradeAmount: number | null;
  largeNet: number | null;
  superLargeNet: number | null;
};

export type ReplaySession = {
  date: string;
  priceProvider: string | null;
  flowProvider: string | null;
  minutes: ReplayMinute[];
};

export type CapitalReplay = {
  sessions: ReplaySession[];
  warnings: string[];
};

export type MinuteRange = { from: number; to: number };
export type ReplayPoint = ReplayMinute & { minute: number; net: number | null };
export type ReplayWindow = MinuteRange & { label: string; net: number };

export const OPEN = 9 * 60 + 30;
export const AM_CLOSE = 11 * 60 + 30;
export const PM_OPEN = 13 * 60;
export const CLOSE = 15 * 60;
const CLOSE_AUCTION = 14 * 60 + 57;
const WINDOW_MINUTES = 5;

export function minuteLabel(minute: number) {
  return [Math.floor(minute / 60), minute % 60].map((value) => String(value).padStart(2, '0')).join(':');
}

export function tradingSlot(minute: number) {
  return minute <= AM_CLOSE ? minute - OPEN : minute - PM_OPEN + (AM_CLOSE - OPEN) + 8;
}

export function replayPoints(session: ReplaySession): ReplayPoint[] {
  return session.minutes.map((point) => ({
    ...point,
    minute: Number(point.time.slice(0, 2)) * 60 + Number(point.time.slice(3, 5)),
    net: point.largeNet === null || point.superLargeNet === null ? null : point.largeNet + point.superLargeNet
  }));
}

function summarizeField(points: ReplayPoint[], read: (point: ReplayPoint) => number | null) {
  const values = points.map(read).filter((value): value is number => value !== null);
  return { value: values.length ? values.reduce((total, value) => total + value, 0) : null, count: values.length };
}

export function summarizeRange(points: ReplayPoint[], range: MinuteRange) {
  const selected = points.filter((point) => point.minute >= range.from && point.minute <= range.to);
  const expectedPrice = Math.max(0, Math.min(range.to, AM_CLOSE) - Math.max(range.from, OPEN) + 1)
    + Math.max(0, Math.min(range.to, CLOSE) - Math.max(range.from, PM_OPEN + 1) + 1)
    + Number(selected.some((point) => point.minute === PM_OPEN));
  const expectedFlow = expectedPrice
    - Number(range.from <= OPEN && range.to >= OPEN)
    - Number(selected.some((point) => point.minute === PM_OPEN));
  return {
    selected,
    amount: summarizeField(selected, (point) => point.tradeAmount),
    net: summarizeField(selected, (point) => point.net),
    large: summarizeField(selected, (point) => point.largeNet),
    superLarge: summarizeField(selected, (point) => point.superLargeNet),
    startPrice: selected.find((point) => point.minute === range.from)?.price ?? null,
    endPrice: selected.find((point) => point.minute === range.to)?.price ?? null,
    expectedPrice,
    expectedFlow
  };
}

export function flowWindows(points: ReplayPoint[]): ReplayWindow[] {
  const candidates = [];
  for (let start = 0; start <= points.length - WINDOW_MINUTES; start += 1) {
    const sample = points.slice(start, start + WINDOW_MINUTES);
    const first = sample[0];
    const last = sample[sample.length - 1];
    if (first.minute <= OPEN || first.minute === PM_OPEN || last.minute >= CLOSE_AUCTION
      || last.minute - first.minute !== WINDOW_MINUTES - 1
      || sample.some((point) => point.net === null || point.tradeAmount === null || point.price === null)) {
      continue;
    }
    const net = sample.reduce((total, point) => total + point.net!, 0);
    const amount = sample.reduce((total, point) => total + point.tradeAmount!, 0);
    const change = last.price! / first.price! - 1;
    const prices = sample.map((point) => point.price!);
    const narrowRange = (Math.max(...prices) - Math.min(...prices)) / first.price! <= 0.004;
    const label = net > 0
      ? change >= 0.004 ? '上涨伴大额净流入' : '大额净流入集中'
      : change >= 0.004 ? '上涨伴大额净流出'
        : narrowRange ? '价稳伴大额净流出' : '大额净流出集中';
    candidates.push({ from: first.minute, to: last.minute, net, amount, label });
  }
  const magnitudes = candidates.map((value) => Math.abs(value.net)).sort((a, b) => a - b);
  const threshold = (magnitudes[Math.floor(magnitudes.length / 2)] ?? 0) * 1.5;
  const windows: ReplayWindow[] = [];
  for (const candidate of candidates.sort((a, b) => Math.abs(b.net) - Math.abs(a.net))) {
    if (!candidate.amount || Math.abs(candidate.net) <= threshold || Math.abs(candidate.net) < candidate.amount * 0.15) {
      continue;
    }
    if (windows.some((window) => candidate.from <= window.to && candidate.to >= window.from)) {
      continue;
    }
    windows.push(candidate);
    if (windows.length === 4) {
      break;
    }
  }
  return windows;
}

export function replayMoney(value: number | null, signed = false) {
  if (value === null) {
    return '—';
  }
  const sign = value < 0 ? '−' : signed && value > 0 ? '+' : '';
  const amount = Math.abs(value);
  if (amount >= 1e8) {
    return `${sign}${(amount / 1e8).toFixed(2)} 亿`;
  }
  if (amount >= 1e4) {
    return `${sign}${(amount / 1e4).toFixed(2)} 万`;
  }
  return `${sign}${Math.round(amount).toLocaleString('zh-CN')} 元`;
}
