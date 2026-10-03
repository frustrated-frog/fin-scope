import type { MarketIndexPerformance, MarketPulseWorkspace, SectorRotation } from './marketPulseTypes';
import type { ResearchStock } from './marketResearchTypes';

export type PanoramaFrame = {
  businessDate: string;
  headline?: string;
  advanceRatio?: number;
  ma20Ratio?: number;
  totalAmount?: number;
  indices: MarketIndexPerformance[];
  sectors: SectorRotation[];
};
export type PanoramaMetric = 'return1d' | 'return5d' | 'return20d' | 'breadthRatio';
export type IndexSeries = { code: string; name: string; values: (number | undefined)[] };
export const metricLabels: Record<PanoramaMetric, string> = {
  return1d: '当日涨跌', return5d: '5日涨跌', return20d: '20日涨跌', breadthRatio: '上涨占比',
};
export const indexColors = ['#489cbe', '#a889dc', '#dfa34e', '#df7e9c', '#55b8a5'];
export function isoDate(value?: string | number[]) {
  return Array.isArray(value) ? value.map((part, i) => i ? String(part).padStart(2, '0') : String(part)).join('-') : value ?? '';
}
export function finite(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
export function pct(value: unknown, digits = 2) {
  return finite(value) ? `${value > 0 ? '+' : ''}${value.toFixed(digits)}%` : '—';
}
export function ratio(value: unknown) {
  return finite(value) ? `${(value * 100).toFixed(0)}%` : '—';
}
export function amount(value: unknown) {
  if (!finite(value)) {
    return '—';
  }
  return value >= 1e12 ? `${(value / 1e12).toFixed(2)} 万亿` : `${(value / 1e8).toFixed(0)} 亿`;
}
export function heatLevel(value: unknown, metric: PanoramaMetric): string {
  if (!finite(value)) {
    return 'missing';
  }
  const centered = metric === 'breadthRatio' ? (value - .5) * 10 : value / (metric === 'return20d' ? 4 : metric === 'return5d' ? 2 : 1);
  if (Math.abs(centered) < .15) {
    return 'flat';
  }
  return `${centered > 0 ? 'up' : 'down'}-${Math.min(3, Math.max(1, Math.ceil(Math.abs(centered))))}`;
}

/** Merge real dated observations, leaving unavailable sectors and metrics empty. */
export function buildFrames(history: PanoramaFrame[], workspace: MarketPulseWorkspace): PanoramaFrame[] {
  const maximum = workspace.businessDate ?? '';
  const frames = new Map<string, PanoramaFrame>();
  const ensure = (date: string) => {
    if (!frames.has(date)) {
      frames.set(date, { businessDate: date, indices: [], sectors: [] });
    }
    return frames.get(date)!;
  };
  for (const point of workspace.breadth?.history ?? []) {
    const date = isoDate(point.businessDate);
    if (date && date <= maximum) {
      Object.assign(ensure(date), { advanceRatio: point.advanceRatio, ma20Ratio: point.ma20Ratio, totalAmount: point.totalAmount });
    }
  }
  const current: PanoramaFrame = { businessDate: maximum, headline: workspace.dailyReview?.headline,
    indices: workspace.breadth?.indices ?? [], sectors: workspace.sectors ?? [],
    advanceRatio: workspace.breadth?.advanceRatio, ma20Ratio: workspace.breadth?.trendBreadth?.ma20Ratio,
    totalAmount: workspace.breadth?.totalAmount };
  const snapshots = [...history, current].filter(frame => frame.businessDate && frame.businessDate <= maximum)
    .sort((a, b) => a.businessDate.localeCompare(b.businessDate));
  for (const frame of snapshots) {
    const target = ensure(frame.businessDate);
    for (const key of ['advanceRatio', 'ma20Ratio', 'totalAmount'] as const) {
      if (finite(frame[key])) {
        target[key] = frame[key];
      }
    }
    target.headline = frame.headline;
    target.sectors = frame.sectors ?? [];
    for (const index of frame.indices ?? []) {
      for (const point of [...(index.history ?? []), { businessDate: index.businessDate ?? frame.businessDate, close: index.close }]) {
        const date = isoDate(point.businessDate);
        if (!date || date > frame.businessDate || !finite(point.close) || point.close <= 0) {
          continue;
        }
        const row = ensure(date);
        const existing = row.indices.find(item => item.code === index.code);
        const observation = { ...index, history: undefined, businessDate: date, close: point.close };
        // Historical bars contain close only; do not attach the snapshot's end-date returns.
        if (date !== frame.businessDate) {
          observation.return1d = undefined;
          observation.return5d = undefined;
          observation.return20d = undefined;
        }
        if (existing) {
          if (date === frame.businessDate) {
            Object.assign(existing, observation);
          }
        } else {
          row.indices.push(observation);
        }
      }
    }
  }
  return [...frames.values()].sort((a, b) => a.businessDate.localeCompare(b.businessDate)).slice(-60);
}

/** All lines start on the same date, so their relative positions remain comparable. */
export function indexSeries(frames: PanoramaFrame[], benchmark = ''): IndexSeries[] {
  const definitions = new Map<string, string>();
  frames.forEach(frame => frame.indices.forEach(index => definitions.set(index.code, index.name)));
  const eligible = [...definitions.keys()].filter(code => frames.filter(frame => finite(frame.indices.find(i => i.code === code)?.close)).length >= 2);
  const base = frames.find(frame => eligible.length > 0 && eligible.every(code => finite(frame.indices.find(i => i.code === code)?.close)));
  if (!base) {
    return [];
  }
  const series = eligible.map(code => {
    const start = base.indices.find(i => i.code === code)!.close!;
    return { code, name: definitions.get(code)!, values: frames.map(frame => {
      const close = frame.indices.find(i => i.code === code)?.close;
      return frame.businessDate >= base.businessDate && finite(close) ? (close / start - 1) * 100 : undefined;
    }) };
  });
  const reference = series.find(line => line.code === benchmark);
  return series.map(line => ({ ...line, values: line.values.map((value, i) => {
    if (!reference) {
      return value;
    }
    const baseValue = reference.values[i];
    return finite(value) && finite(baseValue) ? value - baseValue : undefined;
  }) }));
}
export function chartPath(values: (number | undefined)[], y: (value: number) => number, width: number, left = 0) {
  let connected = false;
  return values.map((value, i) => {
    if (!finite(value)) {
      connected = false;
      return '';
    }
    const command = connected ? 'L' : 'M';
    connected = true;
    return `${command}${(left + i / Math.max(1, values.length - 1) * width).toFixed(2)},${y(value).toFixed(2)}`;
  }).join(' ');
}
export function sectorMembers(stocks: ResearchStock[], sector: SectorRotation) {
  return stocks.filter(stock => stock.sectorCodes?.includes(sector.sectorCode));
}

export type WeightedTile = { code: string; x: number; y: number; width: number; height: number; weight: number };
/** Binary treemap: each tile area is proportional to its actual positive turnover. */
export function turnoverTiles(stocks: ResearchStock[]): WeightedTile[] {
  const items = stocks.filter(stock => finite(stock.amount) && stock.amount > 0)
    .map(stock => ({ code: stock.instrumentCode, weight: stock.amount! })).sort((a, b) => b.weight - a.weight);
  const tiles: WeightedTile[] = [];
  const layout = (rows: typeof items, x: number, y: number, width: number, height: number) => {
    if (!rows.length) {
      return;
    }
    if (rows.length === 1) {
      tiles.push({ ...rows[0], x, y, width, height });
      return;
    }
    const total = rows.reduce((sum, row) => sum + row.weight, 0);
    let split = 1;
    let subtotal = rows[0].weight;
    while (split < rows.length - 1 && subtotal + rows[split].weight / 2 < total / 2) {
      subtotal += rows[split].weight;
      split++;
    }
    const fraction = subtotal / total;
    if (width >= height) {
      layout(rows.slice(0, split), x, y, width * fraction, height);
      layout(rows.slice(split), x + width * fraction, y, width * (1 - fraction), height);
    } else {
      layout(rows.slice(0, split), x, y, width, height * fraction);
      layout(rows.slice(split), x, y + height * fraction, width, height * (1 - fraction));
    }
  };
  layout(items, 0, 0, 100, 100);
  return tiles;
}
