import type { DailyBarPoint } from './KlineChart';

/** 兼容旧日线响应；未提供复权口径的历史会明确提示。 */
export type StockMarketBar = DailyBarPoint & {
  adjustment?: string;
  isComplete?: boolean;
  sourceCode?: string;
  asOf?: string;
};

export type ValidatedStockBar = StockMarketBar & {
  tradeDate: string;
  open: number;
  high: number;
  low: number;
  close: number;
};

export type StockHorizon = 5 | 20 | 60 | 120 | 252;

export type StockPerformance = {
  sessions: StockHorizon;
  label: string;
  sampleCount: number;
  requiredSampleCount: number;
  startDate?: string;
  endDate?: string;
  returnPct?: number;
  /** 正数表示从先前峰值到后续谷值的最大跌幅。 */
  maxDrawdownPct?: number;
  reason?: string;
};

export type StockYtdPerformance = {
  year?: number;
  sampleCount: number;
  startDate?: string;
  endDate?: string;
  returnPct?: number;
  reason?: string;
};

export type StockMovingAverage = {
  sessions: 20 | 60;
  sampleCount: number;
  value?: number;
  distancePct?: number;
  reason?: string;
};

export type StockVolumeObservation = {
  priorSampleCount: number;
  priorMean?: number;
  latest?: number;
  ratio?: number;
  reason?: string;
};

export type StockPriceLevel = {
  sessions: 20 | 60;
  sampleCount: number;
  high?: number;
  low?: number;
  /** (最新收盘 / 对应高低点 - 1) × 100，可为负数。 */
  distanceToHighPct?: number;
  distanceToLowPct?: number;
  /** (最新收盘 - 区间低点) / (区间高点 - 区间低点) × 100；突破后可超出 0–100。 */
  locationPct?: number;
  reason?: string;
};

export type StockEventCode = 'BREAKOUT_20_HIGH' | 'BREAKDOWN_20_LOW'
  | 'MA20_CROSS_UP' | 'MA20_CROSS_DOWN' | 'VOLUME_SURGE';

export type StockMarketEvent = {
  date: string;
  code: StockEventCode;
  label: string;
  price: number;
  /** 高低点价格、当日 MA20 或此前 20 日均量，取决于 code。 */
  referenceValue: number;
  previousPrice?: number;
  previousReferenceValue?: number;
  volume?: number;
  ratio?: number;
};

export type StockHistorySummary = {
  bars: ValidatedStockBar[];
  warnings: string[];
  excludedIncompleteCount: number;
  performance: StockPerformance[];
  ytd: StockYtdPerformance;
  movingAverages: StockMovingAverage[];
  volume: StockVolumeObservation;
  streak: { direction: 'UP' | 'DOWN' | 'FLAT'; days: number };
  levels: StockPriceLevel[];
  events: StockMarketEvent[];
};

export type StockBenchmarkComparison = {
  points: { date: string; stockReturnPct: number; benchmarkReturnPct: number }[];
  dates: string[];
  sampleCount: number;
  startDate?: string;
  endDate?: string;
  stockReturnPct?: number;
  benchmarkReturnPct?: number;
  excessReturnPp?: number;
  warnings: string[];
  reason?: string;
};

type ValidatedHistory = {
  bars: ValidatedStockBar[];
  warnings: string[];
  excludedIncompleteCount: number;
  reason?: string;
};

const PERFORMANCE_HORIZONS: { sessions: StockHorizon; label: string }[] = [
  { sessions: 5, label: '近 5 日' },
  { sessions: 20, label: '近 20 日' },
  { sessions: 60, label: '近 60 日' },
  { sessions: 120, label: '近 120 日' },
  { sessions: 252, label: '近 252 日' }
];
const TECHNICAL_WINDOWS = [20, 60] as const;
const EVENT_LOOKBACK_SESSIONS = 20;
const CORRUPT_HISTORY_REASON = '日线存在无效或重复记录，已暂停计算';

/**
 * 所有统计仅使用已完成日线。完整记录的价格、日期或复权口径异常时拒绝整段历史，
 * 防止删除坏点后把跨缺口收益误报成连续 N 个交易日收益；不补价格或成交量。
 */
export function summarizeStockHistory(bars: readonly StockMarketBar[] | null | undefined): StockHistorySummary {
  const history = validateStockHistory(bars);
  const values = history.bars;
  const unavailableReason = history.reason ?? '暂无已完成日线';
  return {
    bars: values,
    warnings: history.warnings,
    excludedIncompleteCount: history.excludedIncompleteCount,
    performance: PERFORMANCE_HORIZONS.map(({ sessions, label }) =>
      calculatePerformance(values, sessions, label, history.reason)),
    ytd: calculateYtd(values, unavailableReason),
    movingAverages: TECHNICAL_WINDOWS.map((sessions) => calculateMovingAverage(values, sessions, history.reason)),
    volume: calculateVolume(values, values.length - 1, history.reason),
    streak: calculateStreak(values),
    levels: TECHNICAL_WINDOWS.map((sessions) => calculatePriceLevel(values, sessions, history.reason)),
    events: collectEvents(values)
  };
}

/** 只对齐双方真实存在的相同交易日期；基日和末日严格相同，不做前向填充。 */
export function compareStockBenchmark(
  stockBars: readonly StockMarketBar[] | null | undefined,
  benchmarkBars: readonly StockMarketBar[] | null | undefined
): StockBenchmarkComparison {
  const stock = validateStockHistory(stockBars);
  const benchmark = validateStockHistory(benchmarkBars);
  const warnings = [
    ...stock.warnings.map((warning) => `个股：${warning}`),
    ...benchmark.warnings.map((warning) => `基准：${warning}`)
  ];
  const benchmarkByDate = new Map(benchmark.bars.map((bar) => [bar.tradeDate, bar]));
  const overlap = stock.bars.filter((bar) => benchmarkByDate.has(bar.tradeDate));
  const dates = overlap.map((bar) => bar.tradeDate);
  const latestStockDate = stock.bars[stock.bars.length - 1]?.tradeDate;
  const latestBenchmarkDate = benchmark.bars[benchmark.bars.length - 1]?.tradeDate;
  const endDate = dates[dates.length - 1];
  if (latestStockDate && latestStockDate !== endDate) {
    warnings.push(`个股最新日 ${latestStockDate} 未与基准末日对齐，比较截至 ${endDate ?? '暂无共同交易日'}`);
  }
  if (latestBenchmarkDate && latestBenchmarkDate !== endDate) {
    warnings.push(`基准最新日 ${latestBenchmarkDate} 未与个股末日对齐，比较截至 ${endDate ?? '暂无共同交易日'}`);
  }
  if (stock.reason || benchmark.reason || overlap.length < 2) {
    return {
      points: [], dates, sampleCount: dates.length, startDate: dates[0], endDate, warnings,
      reason: stock.reason ?? benchmark.reason ?? '至少需要 2 个共同交易日'
    };
  }
  const stockBase = overlap[0].close;
  const benchmarkBase = benchmarkByDate.get(dates[0])!.close;
  const points: StockBenchmarkComparison['points'] = [];
  for (const bar of overlap) {
    const stockReturnPct = percentageChange(bar.close, stockBase);
    const benchmarkReturnPct = percentageChange(benchmarkByDate.get(bar.tradeDate)!.close, benchmarkBase);
    if (stockReturnPct === undefined || benchmarkReturnPct === undefined) {
      return { points: [], dates, sampleCount: dates.length, warnings, reason: '收益计算超出有效数值范围' };
    }
    points.push({ date: bar.tradeDate, stockReturnPct, benchmarkReturnPct });
  }
  const latest = points[points.length - 1];
  const excessReturnPp = latest.stockReturnPct - latest.benchmarkReturnPct;
  return {
    points, dates, sampleCount: points.length, startDate: dates[0], endDate, warnings,
    stockReturnPct: latest.stockReturnPct,
    benchmarkReturnPct: latest.benchmarkReturnPct,
    excessReturnPp: Number.isFinite(excessReturnPp) ? excessReturnPp : undefined
  };
}

function validateStockHistory(input: readonly StockMarketBar[] | null | undefined): ValidatedHistory {
  const warnings: string[] = [];
  const bars: ValidatedStockBar[] = [];
  const dates = new Set<string>();
  const adjustments = new Set<string>();
  let excludedIncompleteCount = 0;
  let hasMissingAdjustment = false;
  let hasMissingVolume = false;
  let hasCorruption = false;
  for (const bar of input ?? []) {
    if (bar?.isComplete === false) {
      excludedIncompleteCount += 1;
      continue;
    }
    if (!bar || !isValidDate(bar.tradeDate) || !hasValidPrices(bar) || !hasValidVolume(bar.volume)) {
      warnings.push(`无效日线：${bar?.tradeDate ?? '日期缺失'}`);
      hasCorruption = true;
      continue;
    }
    if (dates.has(bar.tradeDate)) {
      warnings.push(`重复交易日：${bar.tradeDate}`);
      hasCorruption = true;
    }
    dates.add(bar.tradeDate);
    if (bar.adjustment == null) {
      hasMissingAdjustment = true;
    } else {
      adjustments.add(typeof bar.adjustment === 'string' ? bar.adjustment.trim().toUpperCase() : 'INVALID');
    }
    hasMissingVolume ||= bar.volume == null;
    bars.push({ ...bar, tradeDate: bar.tradeDate, open: bar.open!, high: bar.high!, low: bar.low!, close: bar.close! });
  }
  if (excludedIncompleteCount > 0) {
    warnings.push(`已排除 ${excludedIncompleteCount} 根未完成日线`);
  }
  if (hasMissingVolume) {
    warnings.push('部分成交量缺失，相应量比与放量事件不计算');
  }
  if (hasCorruption) {
    return { bars: [], warnings, excludedIncompleteCount, reason: CORRUPT_HISTORY_REASON };
  }
  if (adjustments.size > 1 || (adjustments.size === 1 && (!adjustments.has('QFQ') || hasMissingAdjustment))) {
    const reason = '日线复权口径不一致或不支持，仅支持统一前复权日线';
    warnings.push(reason);
    return { bars: [], warnings, excludedIncompleteCount, reason };
  }
  if (hasMissingAdjustment) {
    warnings.push('复权口径未提供');
  }
  bars.sort((left, right) => left.tradeDate.localeCompare(right.tradeDate));
  return { bars, warnings, excludedIncompleteCount };
}

function isValidDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const timestamp = Date.parse(`${value}T00:00:00Z`);
  return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value;
}

function hasValidPrices(bar: StockMarketBar): boolean {
  const prices = [bar.open, bar.high, bar.low, bar.close];
  if (!prices.every((price) => typeof price === 'number' && Number.isFinite(price) && price > 0)) {
    return false;
  }
  return bar.high! >= Math.max(bar.open!, bar.close!, bar.low!)
    && bar.low! <= Math.min(bar.open!, bar.close!);
}

function hasValidVolume(volume: unknown): boolean {
  return volume == null || (typeof volume === 'number' && Number.isFinite(volume) && volume >= 0);
}

function percentageChange(value: number, baseline: number): number | undefined {
  const percentage = (value / baseline - 1) * 100;
  return Number.isFinite(percentage) ? percentage : undefined;
}

function mean(values: number[]): number {
  return values.reduce((total, value) => total + value / values.length, 0);
}

function comparePriceToAverage(price: number, average: number, sampleCount: number): -1 | 0 | 1 {
  // 均值的逐项浮点累加会产生舍入误差；只在随样本数及价格量级变化的机器精度内视作贴线。
  const tolerance = Number.EPSILON * sampleCount * Math.max(Math.abs(price), Math.abs(average));
  const difference = price - average;
  if (Math.abs(difference) <= tolerance) {
    return 0;
  }
  return difference < 0 ? -1 : 1;
}

function calculatePerformance(
  bars: ValidatedStockBar[], sessions: StockHorizon, label: string, unavailableReason?: string
): StockPerformance {
  const window = bars.slice(-(sessions + 1));
  const result: StockPerformance = { sessions, label, sampleCount: window.length, requiredSampleCount: sessions + 1 };
  if (window.length < sessions + 1) {
    return { ...result, reason: unavailableReason ?? `需要 ${sessions + 1} 根已完成日线，当前 ${window.length} 根` };
  }
  let peak = window[0].close;
  let maxDrawdownPct = 0;
  for (const bar of window) {
    peak = Math.max(peak, bar.close);
    maxDrawdownPct = Math.max(maxDrawdownPct, (peak - bar.close) / peak * 100);
  }
  const returnPct = percentageChange(window[window.length - 1].close, window[0].close);
  return {
    ...result, startDate: window[0].tradeDate, endDate: window[window.length - 1].tradeDate,
    returnPct, maxDrawdownPct, reason: returnPct === undefined ? '收益计算超出有效数值范围' : undefined
  };
}

function calculateYtd(bars: ValidatedStockBar[], unavailableReason: string): StockYtdPerformance {
  const latest = bars[bars.length - 1];
  if (!latest) {
    return { sampleCount: 0, reason: unavailableReason };
  }
  const year = Number(latest.tradeDate.slice(0, 4));
  const previousYearBars = bars.filter((bar) => Number(bar.tradeDate.slice(0, 4)) === year - 1);
  const baseline = previousYearBars[previousYearBars.length - 1];
  const currentYearCount = bars.filter((bar) => Number(bar.tradeDate.slice(0, 4)) === year).length;
  if (!baseline) {
    return { year, sampleCount: currentYearCount, endDate: latest.tradeDate, reason: '缺少上一年末收盘基点' };
  }
  const returnPct = percentageChange(latest.close, baseline.close);
  return {
    year, sampleCount: currentYearCount + 1, startDate: baseline.tradeDate, endDate: latest.tradeDate,
    returnPct, reason: returnPct === undefined ? '收益计算超出有效数值范围' : undefined
  };
}

function calculateMovingAverage(
  bars: ValidatedStockBar[], sessions: 20 | 60, unavailableReason?: string
): StockMovingAverage {
  const window = bars.slice(-sessions);
  if (window.length < sessions) {
    return { sessions, sampleCount: window.length, reason: unavailableReason ?? `需要 ${sessions} 根已完成日线` };
  }
  const value = mean(window.map((bar) => bar.close));
  const distancePct = percentageChange(window[window.length - 1].close, value);
  return { sessions, sampleCount: window.length, value, distancePct };
}

function calculateVolume(bars: ValidatedStockBar[], index: number, unavailableReason?: string): StockVolumeObservation {
  const priorBars = bars.slice(Math.max(0, index - 20), Math.max(0, index));
  const volumes = priorBars.flatMap((bar) => bar.volume == null ? [] : [bar.volume]);
  const latest = bars[index]?.volume ?? undefined;
  if (volumes.length < 20) {
    return { priorSampleCount: volumes.length, latest, reason: unavailableReason ?? '需要此前 20 个交易日的有效成交量' };
  }
  const priorMean = mean(volumes);
  if (latest === undefined || priorMean === 0) {
    return {
      priorSampleCount: volumes.length, latest, priorMean,
      reason: latest === undefined ? '最新成交量缺失' : '此前 20 日均量为零'
    };
  }
  const ratio = latest / priorMean;
  return {
    priorSampleCount: volumes.length, latest, priorMean,
    ratio: Number.isFinite(ratio) ? ratio : undefined,
    reason: Number.isFinite(ratio) ? undefined : '量比计算超出有效数值范围'
  };
}

function calculateStreak(bars: ValidatedStockBar[]): StockHistorySummary['streak'] {
  let days = 0;
  let direction: StockHistorySummary['streak']['direction'] = 'FLAT';
  for (let index = bars.length - 1; index > 0; index -= 1) {
    const change = Math.sign(bars[index].close - bars[index - 1].close);
    const currentDirection = change > 0 ? 'UP' : change < 0 ? 'DOWN' : 'FLAT';
    if (currentDirection === 'FLAT' || (days > 0 && currentDirection !== direction)) {
      break;
    }
    direction = currentDirection;
    days += 1;
  }
  return { direction, days };
}

function calculatePriceLevel(
  bars: ValidatedStockBar[], sessions: 20 | 60, unavailableReason?: string
): StockPriceLevel {
  const latest = bars[bars.length - 1];
  const priorBars = bars.slice(Math.max(0, bars.length - 1 - sessions), Math.max(0, bars.length - 1));
  if (!latest || priorBars.length < sessions) {
    return { sessions, sampleCount: priorBars.length, reason: unavailableReason ?? `需要此前 ${sessions} 根已完成日线` };
  }
  const high = Math.max(...priorBars.map((bar) => bar.high));
  const low = Math.min(...priorBars.map((bar) => bar.low));
  const location = high > low ? (latest.close - low) / (high - low) * 100 : undefined;
  return {
    sessions, sampleCount: priorBars.length, high, low,
    distanceToHighPct: percentageChange(latest.close, high),
    distanceToLowPct: percentageChange(latest.close, low),
    locationPct: location !== undefined && Number.isFinite(location) ? location : undefined,
    reason: high === low ? '区间高低点相同，无法计算区间位置' : undefined
  };
}

function collectEvents(bars: ValidatedStockBar[]): StockMarketEvent[] {
  const events: StockMarketEvent[] = [];
  for (let index = Math.max(20, bars.length - EVENT_LOOKBACK_SESSIONS); index < bars.length; index += 1) {
    const current = bars[index];
    const priorBars = bars.slice(index - 20, index);
    const high = Math.max(...priorBars.map((bar) => bar.high));
    const low = Math.min(...priorBars.map((bar) => bar.low));
    const common = { date: current.tradeDate, price: current.close };
    if (current.close > high) {
      events.push({ ...common, code: 'BREAKOUT_20_HIGH', label: '收盘突破此前 20 日最高价', referenceValue: high });
    }
    if (current.close < low) {
      events.push({ ...common, code: 'BREAKDOWN_20_LOW', label: '收盘跌破此前 20 日最低价', referenceValue: low });
    }
    const previous = bars[index - 1];
    const previousMa = mean(priorBars.map((bar) => bar.close));
    const currentMa = mean(bars.slice(index - 19, index + 1).map((bar) => bar.close));
    const crossing = { ...common, referenceValue: currentMa, previousPrice: previous.close, previousReferenceValue: previousMa };
    const previousSide = comparePriceToAverage(previous.close, previousMa, 20);
    const currentSide = comparePriceToAverage(current.close, currentMa, 20);
    if (previousSide <= 0 && currentSide > 0) {
      events.push({ ...crossing, code: 'MA20_CROSS_UP', label: '收盘上穿 MA20' });
    }
    if (previousSide >= 0 && currentSide < 0) {
      events.push({ ...crossing, code: 'MA20_CROSS_DOWN', label: '收盘下穿 MA20' });
    }
    const volume = calculateVolume(bars, index);
    if (volume.ratio !== undefined && volume.ratio >= 2) {
      events.push({
        ...common, code: 'VOLUME_SURGE', label: '成交量达到此前 20 日均量的 2 倍',
        referenceValue: volume.priorMean!, volume: volume.latest, ratio: volume.ratio
      });
    }
  }
  return events;
}
