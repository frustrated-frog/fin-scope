import { useRef, useState } from 'react';
import type { KeyboardEvent, PointerEvent } from 'react';

import { AM_CLOSE, CLOSE, minuteLabel, OPEN, PM_OPEN, replayMoney, tradingSlot } from './capitalReplaySeries';
import type { MinuteRange, ReplayPoint, ReplayWindow } from './capitalReplaySeries';

const WIDTH = 720;
const LEFT = 44;
const RIGHT = 710;
const AMOUNT_BASE = 225;
const FLOW_BASE = 272;

type Props = {
  points: ReplayPoint[];
  span: MinuteRange;
  selection: MinuteRange;
  windows: ReplayWindow[];
  onSelect: (range: MinuteRange) => void;
};

export function CapitalReplayChart({ points, span, selection, windows, onSelect }: Props) {
  const [cursor, setCursor] = useState<number | null>(null);
  const dragStart = useRef<number | null>(null);
  const slotSpan = Math.max(1, tradingSlot(span.to) - tradingSlot(span.from));
  const x = (minute: number) => LEFT + (tradingSlot(minute) - tradingSlot(span.from)) * (RIGHT - LEFT) / slotSpan;
  const priceValues = points.flatMap((point) => point.price === null ? [] : [point.price]);
  const minPrice = Math.min(...priceValues);
  const maxPrice = Math.max(...priceValues);
  const padding = priceValues.length ? Math.max((maxPrice - minPrice) * 0.1, maxPrice * 0.001, 0.01) : 0;
  const bottomPrice = minPrice - padding;
  const topPrice = maxPrice + padding;
  const y = (price: number) => 160 - (price - bottomPrice) / (topPrice - bottomPrice) * 142;
  const maxAmount = Math.max(1, ...points.map((point) => point.tradeAmount ?? 0));
  const maxFlow = Math.max(1, ...points.map((point) => Math.abs(point.net ?? 0)));
  const barWidth = Math.max(1, Math.min(10, 490 / slotSpan));
  const chartCursor = cursor === null ? null : points.find((point) => point.minute === cursor) ?? null;
  const inspecting = chartCursor ?? points.find((point) => point.minute === selection.to);
  let lastMinute: number | null = null;
  const pricePath = points.map((point) => {
    if (point.price === null) {
      lastMinute = null;
      return '';
    }
    const command = lastMinute === point.minute - 1 ? 'L' : 'M';
    lastMinute = point.minute;
    return `${command} ${x(point.minute)} ${y(point.price)}`;
  }).join(' ');
  const ticks = span.from === OPEN && span.to === CLOSE
    ? [OPEN, OPEN + 60, AM_CLOSE, PM_OPEN + 60, CLOSE]
    : [span.from, Math.floor((span.from + span.to) / 2), span.to];

  function findPointerMinute(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const target = (event.clientX - bounds.left) / bounds.width * WIDTH;
    return points.reduce((nearest, point) =>
      Math.abs(x(point.minute) - target) < Math.abs(x(nearest.minute) - target) ? point : nearest
    ).minute;
  }

  function moveSelection(event: KeyboardEvent<SVGSVGElement>) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const selectedIndex = points.findIndex((point) => point.minute >= selection.to);
    const index = selectedIndex < 0 ? points.length : selectedIndex;
    const next = Math.max(0, Math.min(points.length - 1, index + (event.key === 'ArrowLeft' ? -1 : 1)));
    const minute = points[next].minute;
    setCursor(minute);
    onSelect({ from: minute, to: minute });
  }

  if (!points.length) {
    return <p className="capital-replay-empty">这一时段没有分时记录。</p>;
  }

  const brush = dragStart.current === null || cursor === null ? selection : {
    from: Math.min(dragStart.current, cursor), to: Math.max(dragStart.current, cursor)
  };

  return (
    <div className="capital-replay-chart">
      <div className="capital-replay-chart-legend">
        <span className="price">分时价</span><span className="volume">成交额</span><span className="net">大额净额</span>
        <small>拖动选区 · 方向键逐分钟查看</small>
      </div>
      <svg className="capital-replay-plot" viewBox={`0 0 ${WIDTH} 302`} preserveAspectRatio="none"
        role="group" aria-label="分时价格、成交额和大额净额图" tabIndex={0}
        onKeyDown={moveSelection}
        onPointerDown={(event) => {
          if (event.button !== 0) return;
          const minute = findPointerMinute(event);
          dragStart.current = minute;
          setCursor(minute);
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => setCursor(findPointerMinute(event))}
        onPointerLeave={() => { if (dragStart.current === null) setCursor(null); }}
        onPointerCancel={() => { dragStart.current = null; setCursor(null); }}
        onPointerUp={(event) => {
          if (dragStart.current === null) return;
          const end = findPointerMinute(event);
          const start = dragStart.current;
          dragStart.current = null;
          setCursor(end);
          onSelect({ from: Math.min(start, end), to: Math.max(start, end) });
        }}
      >
        {span.from <= AM_CLOSE && span.to >= PM_OPEN && (
          <rect className="lunch" x={x(AM_CLOSE)} y={8} width={x(PM_OPEN) - x(AM_CLOSE)} height={288} />
        )}
        {priceValues.length > 0 && [topPrice, (topPrice + bottomPrice) / 2, bottomPrice].map((price, index) => (
          <g key={index}>
            <line className="gridline" x1={LEFT} x2={RIGHT} y1={y(price)} y2={y(price)} />
            <text className="price-axis" x={3} y={y(price) + 4}>{price.toFixed(2)}</text>
          </g>
        ))}
        <line className="gridline" x1={LEFT} x2={RIGHT} y1={AMOUNT_BASE} y2={AMOUNT_BASE} />
        <line className="gridline" x1={LEFT} x2={RIGHT} y1={FLOW_BASE} y2={FLOW_BASE} />
        <rect className="selection" x={x(brush.from) - 1} y={8}
          width={Math.max(2, x(brush.to) - x(brush.from) + 2)} height={288} />
        {points.map((point) => {
          const position = x(point.minute) - barWidth / 2;
          const amountHeight = (point.tradeAmount ?? 0) / maxAmount * 47;
          const flowHeight = Math.abs(point.net ?? 0) / maxFlow * 24;
          return (
            <g key={point.minute}>
              {amountHeight > 0 && <rect className="amount-bar" x={position} y={AMOUNT_BASE - amountHeight}
                width={barWidth} height={amountHeight} />}
              {point.net !== null && <rect className={point.net > 0 ? 'in' : point.net < 0 ? 'out' : 'zero'}
                x={position} y={point.net > 0 ? FLOW_BASE - flowHeight : FLOW_BASE}
                width={barWidth} height={Math.max(1, flowHeight)} />}
            </g>
          );
        })}
        {pricePath && <path className="price-line" d={pricePath} />}
        {windows.filter((window) => window.to >= span.from && window.to <= span.to).map((window) => {
          const price = points.find((point) => point.minute === window.to)?.price;
          return price == null ? null : <circle className={window.net > 0 ? 'event in' : 'event out'}
            key={window.to} cx={x(window.to)} cy={y(price)} r={4} />;
        })}
        {chartCursor && (
          <line className="cursor" x1={x(chartCursor.minute)} x2={x(chartCursor.minute)} y1={8} y2={296} />
        )}
      </svg>
      <div className="capital-replay-time-axis" aria-hidden="true">
        {ticks.map((minute) => <time key={minute} style={{ left: `${x(minute) / WIDTH * 100}%` }}>{minuteLabel(minute)}</time>)}
      </div>
      {inspecting && <div className="capital-replay-cursor-info">
        <time>{minuteLabel(inspecting.minute)}</time>
        <span>价格 <b>{inspecting.price?.toFixed(2) ?? '—'}</b></span>
        <span>成交额 <b>{replayMoney(inspecting.tradeAmount)}</b></span>
        <span>大额净额 <b>{replayMoney(inspecting.net, true)}</b></span>
      </div>}
    </div>
  );
}
