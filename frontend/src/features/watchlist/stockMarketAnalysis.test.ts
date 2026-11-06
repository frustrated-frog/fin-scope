import { describe, expect, test } from 'vitest';

import { compareStockBenchmark, summarizeStockHistory } from './stockMarketAnalysis';
import type { StockMarketBar } from './stockMarketAnalysis';

function makeBars(closes: number[], startDate = '2026-01-02'): StockMarketBar[] {
  const date = new Date(`${startDate}T00:00:00Z`);
  return closes.map((close) => {
    while (date.getUTCDay() === 0 || date.getUTCDay() === 6) {
      date.setUTCDate(date.getUTCDate() + 1);
    }
    const tradeDate = date.toISOString().slice(0, 10);
    date.setUTCDate(date.getUTCDate() + 1);
    return { tradeDate, open: close, high: close, low: close, close, volume: 100, adjustment: 'QFQ', isComplete: true };
  });
}

describe('summarizeStockHistory performance', () => {
  test.each([5, 20, 60, 120, 252])('uses exactly %i close-to-close intervals and N+1 observations', (sessions) => {
    const bars = makeBars(Array.from({ length: 260 }, (_, index) => 100 + index), '2025-01-02');
    const result = summarizeStockHistory(bars).performance.find((performance) => performance.sessions === sessions)!;

    expect(result.sampleCount).toBe(sessions + 1);
    expect(result.requiredSampleCount).toBe(sessions + 1);
    expect(result.startDate).toBe(bars[259 - sessions].tradeDate);
    expect(result.endDate).toBe(bars[259].tradeDate);
    expect(result.returnPct).toBeCloseTo((359 / (359 - sessions) - 1) * 100);
    expect(result.maxDrawdownPct).toBe(0);
  });

  test('reports insufficient observations rather than shortening the horizon', () => {
    const result = summarizeStockHistory(makeBars([100, 102, 104, 106, 108])).performance[0];

    expect(result.returnPct).toBeUndefined();
    expect(result.maxDrawdownPct).toBeUndefined();
    expect(result).toMatchObject({ sampleCount: 5, requiredSampleCount: 6 });
    expect(result.reason).toContain('需要 6 根');
  });

  test('counts only peaks before each trough inside the same horizon', () => {
    const result = summarizeStockHistory(makeBars([100, 50, 200, 180, 160, 150])).performance[0];

    expect(result.returnPct).toBeCloseTo(50);
    expect(result.maxDrawdownPct).toBeCloseTo(50);
  });

  test('excludes an older drawdown outside the requested window', () => {
    const result = summarizeStockHistory(makeBars([200, 10, 100, 110, 120, 130, 140, 150])).performance[0];

    expect(result.maxDrawdownPct).toBe(0);
    expect(result.returnPct).toBeCloseTo(50);
  });

  test('uses the last supplied closing observation of the immediately previous year for YTD', () => {
    const bars = makeBars([90, 100, 105, 110]);
    bars[0].tradeDate = '2025-12-30';
    bars[1].tradeDate = '2025-12-31';
    bars[2].tradeDate = '2026-01-02';
    bars[3].tradeDate = '2026-01-05';

    const result = summarizeStockHistory(bars).ytd;

    expect(result).toMatchObject({ year: 2026, sampleCount: 3, startDate: '2025-12-31', endDate: '2026-01-05' });
    expect(result.returnPct).toBeCloseTo(10);
  });

  test('does not turn the first current-year price or a two-year-old price into the YTD baseline', () => {
    const bars = makeBars([50, 100, 110]);
    bars[0].tradeDate = '2024-12-31';

    const result = summarizeStockHistory(bars).ytd;

    expect(result.returnPct).toBeUndefined();
    expect(result.sampleCount).toBe(2);
    expect(result.reason).toContain('缺少上一年末');
  });
});

describe('summarizeStockHistory technical observations', () => {
  test('calculates MA20 and MA60 including the latest completed close', () => {
    const result = summarizeStockHistory(makeBars(Array.from({ length: 60 }, (_, index) => index + 1)));

    expect(result.movingAverages[0].value).toBeCloseTo(50.5);
    expect(result.movingAverages[0].distancePct).toBeCloseTo((60 / 50.5 - 1) * 100);
    expect(result.movingAverages[1].value).toBeCloseTo(30.5);
    expect(result.movingAverages[1].sampleCount).toBe(60);
    expect(result.levels[1].high).toBeUndefined();
    expect(result.levels[1].sampleCount).toBe(59);
  });

  test('compares the latest volume only against the previous 20 sessions', () => {
    const bars = makeBars(Array.from({ length: 21 }, () => 100));
    bars[20].volume = 200;

    const result = summarizeStockHistory(bars);

    expect(result.volume).toMatchObject({ priorSampleCount: 20, priorMean: 100, latest: 200, ratio: 2 });
    expect(result.events.find((event) => event.code === 'VOLUME_SURGE')).toMatchObject({
      date: bars[20].tradeDate, price: 100, referenceValue: 100, volume: 200, ratio: 2
    });
    expect(summarizeStockHistory(bars.slice(0, 20)).volume.ratio).toBeUndefined();
  });

  test('does not search older sessions to replace missing volume within the prior 20-day window', () => {
    const bars = makeBars(Array.from({ length: 23 }, () => 100));
    bars[15].volume = undefined;
    bars[22].volume = 300;

    const result = summarizeStockHistory(bars);

    expect(result.volume).toMatchObject({ priorSampleCount: 19 });
    expect(result.volume.ratio).toBeUndefined();
    expect(result.events.filter((event) => event.code === 'VOLUME_SURGE')).toEqual([]);
    expect(result.performance[0].returnPct).toBe(0);
  });

  test('accepts zero volume without inventing a ratio against a zero mean', () => {
    const bars = makeBars(Array.from({ length: 21 }, () => 100)).map((bar) => ({ ...bar, volume: 0 }));
    bars[20].volume = 100;

    const result = summarizeStockHistory(bars);

    expect(result.bars).toHaveLength(21);
    expect(result.volume.priorMean).toBe(0);
    expect(result.volume.ratio).toBeUndefined();
    expect(result.volume.reason).toContain('均量为零');
    expect(result.events.some((event) => event.code === 'VOLUME_SURGE')).toBe(false);
  });

  test('keeps price calculations with absent latest volume and suppresses its ratio', () => {
    const bars = makeBars(Array.from({ length: 21 }, () => 100));
    bars[20].volume = null as unknown as number;

    const result = summarizeStockHistory(bars);

    expect(result.volume.priorMean).toBe(100);
    expect(result.volume.ratio).toBeUndefined();
    expect(result.volume.reason).toBe('最新成交量缺失');
    expect(result.performance[0].returnPct).toBe(0);
  });

  test.each([
    { closes: [100, 101, 102, 103], direction: 'UP', days: 3 },
    { closes: [103, 102, 101, 100], direction: 'DOWN', days: 3 },
    { closes: [100, 101, 101, 102, 103], direction: 'UP', days: 2 },
    { closes: [100, 101, 102, 102], direction: 'FLAT', days: 0 }
  ])('counts consecutive close changes and resets at flats: $closes', ({ closes, direction, days }) => {
    expect(summarizeStockHistory(makeBars(closes)).streak).toEqual({ direction, days });
  });

  test('uses strictly previous sessions for levels, including their wicks', () => {
    const bars = makeBars(Array.from({ length: 61 }, () => 100));
    bars[0].high = 130;
    bars[0].low = 70;
    bars[40].high = 110;
    bars[40].low = 90;
    bars[60] = { ...bars[60], open: 120, close: 120, high: 200, low: 50 };

    const levels = summarizeStockHistory(bars).levels;

    expect(levels[0]).toMatchObject({ high: 110, low: 90, locationPct: 150, sampleCount: 20 });
    expect(levels[0].distanceToHighPct).toBeCloseTo((120 / 110 - 1) * 100);
    expect(levels[0].distanceToLowPct).toBeCloseTo((120 / 90 - 1) * 100);
    expect(levels[1]).toMatchObject({ high: 130, low: 70, sampleCount: 60 });
  });

  test('leaves range location unavailable for a flat prior price range', () => {
    const level = summarizeStockHistory(makeBars(Array.from({ length: 21 }, () => 100))).levels[0];

    expect(level).toMatchObject({ high: 100, low: 100, distanceToHighPct: 0, distanceToLowPct: 0 });
    expect(level.locationPct).toBeUndefined();
    expect(level.reason).toContain('高低点相同');
  });
});

describe('summarizeStockHistory events', () => {
  test.each([
    { baseline: 100.1, latest: 101.1, code: 'MA20_CROSS_UP' },
    { baseline: 3.65, latest: 2.65, code: 'MA20_CROSS_DOWN' }
  ])('detects $code from a decimal flat baseline despite mean rounding', ({ baseline, latest, code }) => {
    const bars = makeBars([...Array.from({ length: 20 }, () => baseline), latest]);

    const crossings = summarizeStockHistory(bars).events.filter((event) => event.code.startsWith('MA20_CROSS_'));

    expect(crossings).toHaveLength(1);
    expect(crossings[0]).toMatchObject({ code, date: bars[20].tradeDate, previousPrice: baseline, price: latest });
    expect(crossings[0].previousReferenceValue).toBeCloseTo(baseline, 12);
  });

  test.each([
    { baseline: 100.1, latest: 100.10000001, code: 'MA20_CROSS_UP' },
    { baseline: 3.65, latest: 3.64999999, code: 'MA20_CROSS_DOWN' }
  ])('preserves small genuine $code moves above machine precision', ({ baseline, latest, code }) => {
    const bars = makeBars([...Array.from({ length: 20 }, () => baseline), latest]);

    expect(summarizeStockHistory(bars).events.filter((event) => event.code.startsWith('MA20_CROSS_')))
      .toMatchObject([{ code, date: bars[20].tradeDate }]);
  });

  test.each([
    { baseline: 100.1, previous: 100.2, latest: 100.3 },
    { baseline: 3.65, previous: 3.64, latest: 3.63 },
    { baseline: 100.1, previous: 100.1, latest: 100.1 }
  ])('does not invent a decimal crossing while remaining on the same side or equal: $latest', ({ baseline, previous, latest }) => {
    const bars = makeBars([...Array.from({ length: 19 }, () => baseline), previous, latest]);

    expect(summarizeStockHistory(bars).events.filter((event) => event.code.startsWith('MA20_CROSS_'))).toEqual([]);
  });

  test('records close breakouts and an actual MA20 upward crossing with factual reference values', () => {
    const bars = makeBars([...Array.from({ length: 20 }, () => 100), 110, 111]);
    bars[5].high = 105;

    const result = summarizeStockHistory(bars);
    const crossings = result.events.filter((event) => event.code === 'MA20_CROSS_UP');

    expect(crossings).toHaveLength(1);
    expect(crossings[0]).toMatchObject({
      date: bars[20].tradeDate, price: 110, referenceValue: 100.5, previousPrice: 100, previousReferenceValue: 100
    });
    expect(result.events.find((event) => event.code === 'BREAKOUT_20_HIGH')).toMatchObject({
      date: bars[20].tradeDate, price: 110, referenceValue: 105
    });
  });

  test('records downward crossings and closing breaks below the prior low', () => {
    const bars = makeBars([...Array.from({ length: 20 }, () => 100), 90, 89]);
    bars[5].low = 95;

    const result = summarizeStockHistory(bars);
    const crossings = result.events.filter((event) => event.code === 'MA20_CROSS_DOWN');

    expect(crossings).toHaveLength(1);
    expect(crossings[0]).toMatchObject({ date: bars[20].tradeDate, price: 90, referenceValue: 99.5 });
    expect(result.events.find((event) => event.code === 'BREAKDOWN_20_LOW')).toMatchObject({ referenceValue: 95 });
  });

  test('does not call being above MA20 a crossover without the previous MA observation', () => {
    const bars = makeBars(Array.from({ length: 20 }, (_, index) => index + 100));

    expect(summarizeStockHistory(bars).events).toEqual([]);
    expect(summarizeStockHistory([...bars, ...makeBars([120], '2026-02-02')]).events
      .filter((event) => event.code === 'MA20_CROSS_UP')).toEqual([]);
  });

  test('requires a closing break rather than just an intraday wick or equality', () => {
    const bars = makeBars(Array.from({ length: 21 }, () => 100));
    bars[20].high = 120;
    bars[20].low = 80;

    expect(summarizeStockHistory(bars).events).toEqual([]);
  });

  test('bounds event dates to the latest 20 completed sessions', () => {
    const bars = makeBars(Array.from({ length: 80 }, (_, index) => 100 + index));
    const events = summarizeStockHistory(bars).events;

    expect(events.length).toBeGreaterThan(0);
    expect(events.every((event) => event.date >= bars[60].tradeDate!)).toBe(true);
    expect(events[events.length - 1].date).toBe(bars[79].tradeDate);
  });
});

describe('summarizeStockHistory validation', () => {
  test('excludes incomplete bars from every analytic before validating their contents', () => {
    const bars = makeBars(Array.from({ length: 62 }, (_, index) => 100 + index));
    const valid = summarizeStockHistory(bars);
    const result = summarizeStockHistory([...bars, { ...bars[61], close: 0, isComplete: false }]);

    expect(result.excludedIncompleteCount).toBe(1);
    expect(result.bars).toEqual(valid.bars);
    expect(result.performance).toEqual(valid.performance);
    expect(result.ytd).toEqual(valid.ytd);
    expect(result.movingAverages).toEqual(valid.movingAverages);
    expect(result.levels).toEqual(valid.levels);
    expect(result.volume).toEqual(valid.volume);
    expect(result.streak).toEqual(valid.streak);
    expect(result.events).toEqual(valid.events);
  });

  test.each([
    { close: 0 }, { close: -1 }, { close: NaN }, { close: Infinity },
    { close: undefined }, { close: null }, { open: 0 }, { high: 99 }, { low: 101 },
    { high: 90, low: 110 }, { volume: -1 }, { volume: Infinity }, { volume: NaN },
    { tradeDate: '2026-02-30' }, { tradeDate: '2026-1-02' }, { tradeDate: undefined }, { tradeDate: '' }
  ])('rejects the whole suspect series instead of skipping a corrupt complete observation: %j', (patch) => {
    const bars = makeBars(Array.from({ length: 25 }, () => 100));
    bars[10] = { ...bars[10], ...patch } as StockMarketBar;

    const result = summarizeStockHistory(bars);

    expect(result.bars).toEqual([]);
    expect(result.performance.every((metric) => metric.returnPct === undefined)).toBe(true);
    expect(result.movingAverages.every((metric) => metric.value === undefined)).toBe(true);
    expect(result.events).toEqual([]);
    expect(result.warnings.some((warning) => warning.includes('无效日线'))).toBe(true);
  });

  test('rejects identical duplicates too, rather than counting one day twice', () => {
    const bars = makeBars(Array.from({ length: 6 }, () => 100));

    const result = summarizeStockHistory([...bars, { ...bars[2] }]);

    expect(result.bars).toEqual([]);
    expect(result.performance[0].returnPct).toBeUndefined();
    expect(result.warnings.some((warning) => warning.includes('重复交易日'))).toBe(true);
  });

  test('sorts dates chronologically without changing the supplied objects or array', () => {
    const bars = makeBars([100, 105, 110]);
    const reversed = Object.freeze([...bars].reverse());

    const result = summarizeStockHistory(reversed);

    expect(result.bars.map((bar) => bar.close)).toEqual([100, 105, 110]);
    expect(reversed.map((bar) => bar.close)).toEqual([110, 105, 100]);
    expect(result.bars[0]).not.toBe(bars[0]);
  });

  test.each(['NONE', 'HFQ'])('rejects explicitly unsupported %s adjustment', (adjustment) => {
    const bars = makeBars(Array.from({ length: 25 }, () => 100)).map((bar) => ({ ...bar, adjustment }));

    const result = summarizeStockHistory(bars);

    expect(result.bars).toEqual([]);
    expect(result.performance[0].reason).toContain('复权口径');
  });

  test('rejects mixed adjustment modes, including partial missing metadata', () => {
    const bars = makeBars(Array.from({ length: 25 }, () => 100));
    bars[0].adjustment = undefined;

    expect(summarizeStockHistory(bars).bars).toEqual([]);
    bars[0].adjustment = 'NONE';
    expect(summarizeStockHistory(bars).bars).toEqual([]);
  });

  test('allows legacy histories with no adjustment metadata and warns explicitly', () => {
    const bars = makeBars(Array.from({ length: 6 }, () => 100)).map((bar) => ({ ...bar, adjustment: undefined }));

    const result = summarizeStockHistory(bars);

    expect(result.performance[0].returnPct).toBe(0);
    expect(result.warnings).toContain('复权口径未提供');
  });

  test('rejects explicit UNKNOWN adjustment rather than treating it as absent legacy metadata', () => {
    const bars = makeBars(Array.from({ length: 6 }, () => 100)).map((bar) => ({ ...bar, adjustment: 'UNKNOWN' }));

    const result = summarizeStockHistory(bars);

    expect(result.bars).toEqual([]);
    expect(result.performance[0].returnPct).toBeUndefined();
    expect(result.performance[0].reason).toContain('复权口径');
  });

  test('rejects explicit UNKNOWN mixed with absent adjustment metadata', () => {
    const bars = makeBars(Array.from({ length: 6 }, () => 100)).map((bar) => ({ ...bar, adjustment: undefined as string | undefined }));
    bars[0].adjustment = 'UNKNOWN';

    const result = summarizeStockHistory(bars);

    expect(result.bars).toEqual([]);
    expect(result.performance[0].returnPct).toBeUndefined();
    expect(result.performance[0].reason).toContain('复权口径');
  });

  test('returns explicit unavailable states for no data and one observation', () => {
    const empty = summarizeStockHistory(null);

    expect(empty.bars).toEqual([]);
    expect(empty.performance.every((metric) => metric.returnPct === undefined && metric.sampleCount === 0)).toBe(true);
    expect(empty.ytd.reason).toBe('暂无已完成日线');
    expect(empty.streak).toEqual({ direction: 'FLAT', days: 0 });
    expect(empty.events).toEqual([]);
    expect(summarizeStockHistory(makeBars([100])).streak).toEqual({ direction: 'FLAT', days: 0 });
  });
});

describe('compareStockBenchmark', () => {
  test('warns when the benchmark has a newer tail while keeping identical common endpoints', () => {
    const stock = makeBars([100, 110, 120]);
    const benchmark = makeBars([200, 210, 220, 240]);

    const result = compareStockBenchmark(stock, benchmark);

    expect(result.endDate).toBe(stock[2].tradeDate);
    expect(result.dates).toEqual(stock.map((bar) => bar.tradeDate));
    expect(result.stockReturnPct).toBeCloseTo(20);
    expect(result.benchmarkReturnPct).toBeCloseTo(10);
    expect(result.excessReturnPp).toBeCloseTo(10);
    expect(result.warnings).toContain(`基准最新日 ${benchmark[3].tradeDate} 未与个股末日对齐，比较截至 ${stock[2].tradeDate}`);
  });

  test('normalizes to identical common endpoints without forward-filling unequal histories', () => {
    const stock = makeBars([90, 100, 120, 130, 140, 150]);
    const benchmark = makeBars([200, 220, 210, 250]);
    benchmark[0].tradeDate = stock[1].tradeDate;
    benchmark[1].tradeDate = stock[3].tradeDate;
    benchmark[2].tradeDate = stock[4].tradeDate;
    benchmark[3].tradeDate = '2026-01-15';

    const result = compareStockBenchmark(stock, benchmark);

    expect(result.dates).toEqual([stock[1].tradeDate, stock[3].tradeDate, stock[4].tradeDate]);
    expect(result.points[0]).toEqual({ date: stock[1].tradeDate, stockReturnPct: 0, benchmarkReturnPct: 0 });
    expect(result.points[1].stockReturnPct).toBeCloseTo(30);
    expect(result.points[1].benchmarkReturnPct).toBeCloseTo(10);
    expect(result).toMatchObject({ startDate: stock[1].tradeDate, endDate: stock[4].tradeDate, sampleCount: 3 });
    expect(result.stockReturnPct).toBeCloseTo(40);
    expect(result.benchmarkReturnPct).toBeCloseTo(5);
    expect(result.excessReturnPp).toBeCloseTo(35);
    expect(result.warnings.some((warning) => warning.includes('未与基准末日对齐'))).toBe(true);
  });

  test('excludes incomplete observations on either side before determining endpoints', () => {
    const stock = makeBars([100, 110, 120, 130]);
    const benchmark = makeBars([200, 210, 220, 230]);
    stock[3].isComplete = false;
    benchmark[2].isComplete = false;

    const result = compareStockBenchmark(stock, benchmark);

    expect(result.dates).toEqual([stock[0].tradeDate, stock[1].tradeDate]);
    expect(result.stockReturnPct).toBeCloseTo(10);
    expect(result.benchmarkReturnPct).toBeCloseTo(5);
    expect(result.excessReturnPp).toBeCloseTo(5);
  });

  test('rejects corrupt histories instead of bridging over duplicate or zero prices', () => {
    const stock = makeBars([100, 110, 120]);
    const benchmark = makeBars([200, 210, 220]);
    benchmark[1].close = 0;

    expect(compareStockBenchmark(stock, benchmark).stockReturnPct).toBeUndefined();
    expect(compareStockBenchmark([...stock, stock[0]], makeBars([200, 210, 220])).points).toEqual([]);
  });

  test('requires two common dates, including when both histories are empty or disjoint', () => {
    expect(compareStockBenchmark([], []).reason).toContain('至少需要 2 个共同交易日');
    expect(compareStockBenchmark(makeBars([100]), makeBars([200])).stockReturnPct).toBeUndefined();
    expect(compareStockBenchmark(makeBars([100]), makeBars([200], '2026-02-02')).dates).toEqual([]);
  });
});
