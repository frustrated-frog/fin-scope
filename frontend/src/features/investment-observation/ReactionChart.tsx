import { useId } from 'react';
import type { ReactionPoint } from './reactionTypes';
import { signed } from './reactionTypes';

type Metric = 'stockReturnPct' | 'benchmarkReturnPct' | 'relativeReturnPp';
export interface ReactionSeries { label: string; points: ReactionPoint[]; metric: Metric; color: string; }

/** Gaps remain gaps: never connect across unavailable or unclosed sessions. */
export function reactionPath(points: ReactionPoint[], metric: Metric, x: (n: number) => number, y: (n: number) => number) {
  let connected = false;
  return points.map(point => {
    const value = point[metric];
    if (value == null || point.status === 'NOT_DUE' || !Number.isFinite(value)) {
      connected = false;
      return '';
    }
    const command = connected ? 'L' : 'M';
    connected = true;
    return `${command}${x(point.session).toFixed(2)},${y(value).toFixed(2)}`;
  }).join(' ');
}

export function ReactionChart({ series, relative = false }: { series: ReactionSeries[]; relative?: boolean }) {
  const labelId = useId();
  const values = series.flatMap(line => line.points.flatMap(point => {
    const value = point[line.metric];
    return point.status !== 'NOT_DUE' && value != null && Number.isFinite(value) ? [value] : [];
  }));
  const min = Math.min(-1, ...values);
  const max = Math.max(1, ...values);
  const padding = (max - min) * .15;
  const lower = min - padding;
  const upper = max + padding;
  const x = (session: number) => 58 + (session + 5) * 63;
  const y = (value: number) => 218 - (value - lower) / (upper - lower) * 186;
  return (
    <figure className="reaction-chart">
      <figcaption id={labelId}>{relative ? '相对沪深300的累计表现 · 百分点' : '累计收益 · %'}<span>以首个反应交易日前一日收盘为 0</span></figcaption>
      {values.length === 0 ? <p className="reaction-empty">还没有可绘制的已收盘行情。更新样本后查看路径。</p> : (
        <svg viewBox="0 0 720 254" role="img" aria-labelledby={labelId}>
          <rect x={x(0)} y="24" width={x(5) - x(0)} height="202" fill="var(--reaction-band)" />
          {[min, 0, max].map((value, index) => <g key={index}>
            <line x1="58" x2="688" y1={y(value)} y2={y(value)} stroke="var(--reaction-line)" strokeDasharray={value === 0 ? undefined : '3 5'} />
            <text x="48" y={y(value) + 4} textAnchor="end">{signed(value)}</text>
          </g>)}
          <line x1={x(0)} x2={x(0)} y1="24" y2="226" stroke="var(--reaction-muted)" strokeDasharray="4 4" />
          {[-5, -3, 0, 1, 3, 5].map(value => <text key={value} x={x(value)} y="246" textAnchor="middle">{value === 0 ? '基准日' : `${value > 0 ? '+' : ''}${value}日`}</text>)}
          {series.map(line => <g key={line.label}>
            <path d={reactionPath(line.points, line.metric, x, y)} stroke={line.color} fill="none" strokeWidth="2.3" />
            {line.points.map(point => {
              const value = point[line.metric];
              if (value == null || point.status === 'NOT_DUE' || !Number.isFinite(value)) {
                return null;
              }
              return <circle key={point.session} cx={x(point.session)} cy={y(value)} r="3" fill={line.color}>
                <title>{line.label} · {point.tradeDate} · {signed(value, relative ? ' pp' : '%')}</title>
              </circle>;
            })}
          </g>)}
        </svg>
      )}
      <div className="reaction-legend">{series.map(line => <span key={line.label}><i style={{ background: line.color }} />{line.label}</span>)}</div>
    </figure>
  );
}
