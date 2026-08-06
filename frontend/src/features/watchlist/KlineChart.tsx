import { useMemo } from 'react';

/** 后端 daily-bars 返回的单根日 K 记录。 */
export type DailyBarPoint = {
  code?: string;
  market?: string;
  tradeDate?: string;
  open?: number;
  high?: number;
  low?: number;
  close?: number;
  volume?: number;
  amount?: number;
  amplitude?: number;
  changePct?: number;
  turnoverRate?: number;
};

const VIEW_W = 1_024;
const PRICE_TOP = 22;
const PRICE_BOTTOM = 356;
const VOLUME_TOP = 382;
const VOLUME_BOTTOM = 464;
const PLOT_LEFT = 18;
const PLOT_RIGHT = 950;
const LABEL_X = 1_010;

/**
 * 手写 SVG 日 K 图。以宽坐标系与自适应柱宽绘制，避免密集日线互相覆盖而显得模糊。
 * A 股配色沿用红涨绿跌，价格与成交量使用独立绘图区。
 */
export function KlineChart({ bars }: { bars: DailyBarPoint[] }) {
  const geometry = useMemo(() => layout(bars), [bars]);
  if (!geometry) {
    return <p className="muted watchlist-kline-empty">暂无日线数据</p>;
  }

  const { candles, priceMin, priceMax, ticks, dateTicks } = geometry;

  return (
    <svg
      className="watchlist-kline-svg"
      viewBox={`0 0 ${VIEW_W} 500`}
      preserveAspectRatio="xMidYMid meet"
      role="img"
      aria-label={`最近 ${candles.length} 个交易日日 K 线`}
      shapeRendering="geometricPrecision"
    >
      <title>{`最近 ${candles.length} 个交易日日 K 线`}</title>
      <rect className="watchlist-kline-plot-bg" x={PLOT_LEFT} y={PRICE_TOP} width={PLOT_RIGHT - PLOT_LEFT} height={PRICE_BOTTOM - PRICE_TOP} rx="12" />
      <rect className="watchlist-kline-volume-bg" x={PLOT_LEFT} y={VOLUME_TOP} width={PLOT_RIGHT - PLOT_LEFT} height={VOLUME_BOTTOM - VOLUME_TOP} rx="8" />

      {ticks.map((tick) => {
        const y = priceY(tick.value, priceMin, priceMax);
        return (
          <g key={tick.label} className="watchlist-kline-gridline">
            <line x1={PLOT_LEFT} x2={PLOT_RIGHT} y1={y} y2={y} />
            <text x={LABEL_X} y={y + 4} textAnchor="end">{tick.label}</text>
          </g>
        );
      })}

      {dateTicks.map((tick) => (
        <text key={tick.index} className="watchlist-kline-date" x={tick.x} y="489" textAnchor={tick.anchor}>{tick.label}</text>
      ))}

      {candles.map((c) => (
        <rect
          key={`vol-${c.index}`}
          className={`watchlist-kline-volume ${c.up ? 'watchlist-up' : 'watchlist-down'}`}
          x={c.x - c.bodyWidth / 2}
          y={VOLUME_BOTTOM - c.volumeRatio * (VOLUME_BOTTOM - VOLUME_TOP)}
          width={c.bodyWidth}
          height={Math.max(1, c.volumeRatio * (VOLUME_BOTTOM - VOLUME_TOP))}
        />
      ))}

      {candles.map((c) => (
        <g key={c.index} className={`watchlist-kline-candle ${c.up ? 'watchlist-up' : 'watchlist-down'}`}>
          <line x1={c.x} x2={c.x} y1={c.wickTop} y2={c.wickBottom} />
          <rect x={c.x - c.bodyWidth / 2} y={c.bodyTop} width={c.bodyWidth} height={Math.max(1.5, c.bodyHeight)} />
        </g>
      ))}
    </svg>
  );
}

function layout(bars: DailyBarPoint[]) {
  if (!bars || bars.length === 0) return null;
  const values = bars.filter((bar) => bar.high != null && bar.low != null && bar.open != null && bar.close != null);
  if (values.length === 0) return null;

  const rawMax = Math.max(...values.map((bar) => bar.high as number));
  const rawMin = Math.min(...values.map((bar) => bar.low as number));
  const rawSpan = rawMax - rawMin || Math.max(rawMax * 0.02, 1);
  const padding = Math.max(rawSpan * 0.07, rawMax * 0.002, 0.01);
  const priceMax = rawMax + padding;
  const priceMin = Math.max(0, rawMin - padding);
  const volumeMax = Math.max(1, ...values.map((bar) => bar.volume ?? 0));
  const n = values.length;
  const plotWidth = PLOT_RIGHT - PLOT_LEFT;
  const step = n > 1 ? plotWidth / (n - 1) : plotWidth;
  const bodyWidth = Math.max(1.35, Math.min(10, step * 0.68));
  const candleX = (index: number) => n === 1 ? PLOT_LEFT + plotWidth / 2 : PLOT_LEFT + index * step;

  const candles = values.map((bar, index) => {
    const x = candleX(index);
    const open = bar.open as number;
    const close = bar.close as number;
    const bodyTop = priceY(Math.max(open, close), priceMin, priceMax);
    const bodyBottom = priceY(Math.min(open, close), priceMin, priceMax);
    return {
      index,
      x,
      bodyWidth,
      up: close >= open,
      wickTop: priceY(bar.high as number, priceMin, priceMax),
      wickBottom: priceY(bar.low as number, priceMin, priceMax),
      bodyTop,
      bodyHeight: Math.abs(bodyBottom - bodyTop),
      volumeRatio: Math.min(1, (bar.volume ?? 0) / volumeMax),
      date: bar.tradeDate ?? ''
    };
  });

  const ticks = Array.from({ length: 5 }, (_, index) => {
    const value = priceMin + ((priceMax - priceMin) * index) / 4;
    return { value, label: value.toFixed(2) };
  });
  const datePositions = [0, Math.floor((n - 1) / 3), Math.floor((n - 1) * 2 / 3), n - 1]
    .filter((index, position, all) => all.indexOf(index) === position);
  const dateTicks = datePositions.map((index) => ({
    index,
    x: candleX(index),
    label: compactDate(candles[index].date),
    anchor: index === 0 ? 'start' : index === n - 1 ? 'end' : 'middle'
  }));

  return { candles, priceMin, priceMax, ticks, dateTicks };
}

function priceY(price: number, priceMin: number, priceMax: number) {
  const span = priceMax - priceMin || 1;
  return PRICE_TOP + (1 - (price - priceMin) / span) * (PRICE_BOTTOM - PRICE_TOP);
}

function compactDate(value: string) {
  const match = value.match(/^\d{4}-(\d{2})-(\d{2})$/);
  return match ? `${match[1]}-${match[2]}` : value;
}
