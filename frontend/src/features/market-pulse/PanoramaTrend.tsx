import { useState } from 'react';
import { amount, chartPath, finite, indexColors, indexSeries, pct, ratio, type PanoramaFrame } from './panoramaModel';

type Props = { frames: PanoramaFrame[]; selectedDate: string; benchmark: string; onSelect: (date: string) => void };
export function PanoramaTrend({ frames, selectedDate, benchmark, onSelect }: Props) {
  const [hoveredDate, setHoveredDate] = useState<string>();
  const series = indexSeries(frames, benchmark);
  const hoverIndex = frames.findIndex(frame => frame.businessDate === hoveredDate);
  const active = hoverIndex >= 0 ? hoverIndex : Math.max(0, frames.findIndex(frame => frame.businessDate === selectedDate));
  const point = frames[active];
  const extent = Math.max(2, ...series.flatMap(line => line.values.filter(finite).map(Math.abs))) * 1.15;
  const maxAmount = Math.max(1, ...frames.map(frame => frame.totalAmount ?? 0));
  const x = (i: number) => 58 + i / Math.max(1, frames.length - 1) * 860;
  const y = (value: number) => 126 - value / extent * 94;
  return <section className="mpa-panel mpa-trend" aria-label="市场趋势主图">
    <header className="mpa-section-head"><div><span className="mpa-eyebrow">市场方向</span><h3>价格与参与度，同步观察</h3></div><span className="mpa-caption">{benchmark ? '共同基日的收益差 · 百分点' : '共同基日的累计涨跌 · %'}</span></header>
    <div className="mpa-index-legend">{series.map((line, i) => <div key={line.code}><i style={{ background: indexColors[i % indexColors.length] }} /><span>{line.name}</span><strong>{benchmark ? pct(line.values[active]).replace('%', 'pp') : pct(line.values[active])}</strong></div>)}</div>
    {series.length === 0 && <p className="mpa-note">指数历史尚不足两个观测日；每日快照积累后显示走势，下方仍可观察市场参与度。</p>}
    <div className="mpa-chart-wrap" onMouseLeave={() => setHoveredDate(undefined)}>
      <svg viewBox="0 0 960 398" role="img" aria-label="指数累计涨跌、成交额和市场宽度的共同时间轴">
        {[-1, -.5, 0, .5, 1].map(tick => <g key={tick}><line className="mpa-gridline" x1="58" x2="918" y1={y(tick * extent)} y2={y(tick * extent)} /><text className="mpa-axis" x="48" y={y(tick * extent) + 4} textAnchor="end">{(tick * extent).toFixed(1)}{benchmark ? 'pp' : '%'}</text></g>)}
        {series.map((line, i) => <path key={line.code} d={chartPath(line.values, y, 860, 58)} fill="none" stroke={indexColors[i % indexColors.length]} strokeWidth="2.5" strokeLinejoin="round" />)}
        <text className="mpa-axis" x="58" y="244">成交额</text>
        {frames.map((frame, i) => finite(frame.totalAmount) && <rect key={frame.businessDate} className={`mpa-volume ${frame.businessDate === selectedDate ? 'is-current' : ''}`} x={x(i) - Math.min(9, 310 / frames.length)} y={294 - frame.totalAmount / maxAmount * 39} width={Math.min(18, 620 / frames.length)} height={frame.totalAmount / maxAmount * 39} rx="2" />)}
        <line className="mpa-gridline" x1="58" x2="918" y1="294" y2="294" />
        <text className="mpa-axis" x="48" y="320" textAnchor="end">100%</text><text className="mpa-axis" x="48" y="345" textAnchor="end">50%</text><text className="mpa-axis" x="48" y="370" textAnchor="end">0%</text>
        <line className="mpa-gridline" x1="58" x2="918" y1="341" y2="341" strokeDasharray="3 4" />
        <path d={chartPath(frames.map(frame => frame.advanceRatio), value => 367 - value * 52, 860, 58)} className="mpa-breadth-line" />
        <path d={chartPath(frames.map(frame => frame.ma20Ratio), value => 367 - value * 52, 860, 58)} className="mpa-ma-line" />
        {[0, Math.floor((frames.length - 1) / 2), frames.length - 1].filter((v, i, a) => a.indexOf(v) === i && v >= 0).map(i => <text key={i} className="mpa-axis" x={x(i)} y="390" textAnchor="middle">{frames[i]?.businessDate.slice(5)}</text>)}
        <line className="mpa-crosshair" x1={x(active)} x2={x(active)} y1="25" y2="370" />
        {series.map((line, i) => finite(line.values[active]) && <circle key={line.code} cx={x(active)} cy={y(line.values[active]!)} r="4" fill={indexColors[i % indexColors.length]} />)}
        {frames.map((frame, i) => <rect key={frame.businessDate} x={x(i) - 430 / Math.max(1, frames.length - 1)} y="20" width={860 / Math.max(1, frames.length - 1)} height="350" fill="transparent" className="mpa-chart-hit" onMouseEnter={() => setHoveredDate(frame.businessDate)} onClick={() => onSelect(frame.businessDate)}><title>{frame.businessDate} · 点击查看当日全景</title></rect>)}
      </svg>
    </div>
    <div className="mpa-chart-readout"><time>{point?.businessDate ?? '—'}</time><span><i className="mpa-key breadth" />上涨占比 <b>{ratio(point?.advanceRatio)}</b></span><span><i className="mpa-key ma" />站上20日均线 <b>{ratio(point?.ma20Ratio)}</b></span><span>成交额 <b>{amount(point?.totalAmount)}</b></span></div>
  </section>;
}
