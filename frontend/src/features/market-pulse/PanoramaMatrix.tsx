import { useEffect, useMemo, useRef } from 'react';
import { heatLevel, metricLabels, pct, ratio, type PanoramaFrame, type PanoramaMetric } from './panoramaModel';

type Props = { frames: PanoramaFrame[]; selectedDate: string; selectedCode: string; metric: PanoramaMetric; onSelect: (date: string, code: string) => void };
export function PanoramaMatrix({ frames, selectedDate, selectedCode, metric, onSelect }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const sectors = useMemo(() => {
    const names = new Map<string, string>();
    frames.forEach(frame => frame.sectors.forEach(sector => names.set(sector.sectorCode, sector.sectorName)));
    return [...names].sort((a, b) => a[0].localeCompare(b[0]));
  }, [frames]);
  useEffect(() => {
    const container = scrollRef.current;
    const cell = container?.querySelector<HTMLElement>('td.is-date');
    if (container && cell) {
      container.scrollLeft += cell.getBoundingClientRect().left - container.getBoundingClientRect().left - container.clientWidth * .7;
    }
  }, [selectedDate, frames.length]);
  useEffect(() => {
    const container = scrollRef.current;
    const row = container?.querySelector<HTMLElement>('tr.is-sector');
    if (container && row) {
      container.scrollTop += row.getBoundingClientRect().top - container.getBoundingClientRect().top - container.clientHeight / 2;
    }
  }, [selectedCode]);
  return <section className="mpa-panel mpa-matrix" aria-label="行业时间矩阵">
    <header className="mpa-section-head"><div><span className="mpa-eyebrow">持续与扩散</span><h3>主线如何演变</h3></div><span className="mpa-caption">{metricLabels[metric]} · {frames.length} 个观测日</span></header>
    <p className="mpa-note">横向看持续性，纵向看扩散面。点击色块，整页切换到对应日期与行业。</p>
    <div ref={scrollRef} className="mpa-matrix-scroll" tabIndex={0} role="region" aria-label="可横向滚动的行业历史表">
      <table style={{ width: 124 + frames.length * 46 }}><colgroup><col style={{ width: 116 }} />{frames.map(frame => <col key={frame.businessDate} style={{ width: 46 }} />)}</colgroup><thead><tr><th scope="col">行业 / 日期</th>{frames.map(frame => <th key={frame.businessDate} scope="col" className={frame.businessDate === selectedDate ? 'is-date' : ''}>{frame.businessDate.slice(5)}</th>)}</tr></thead>
        <tbody>{sectors.map(([code, name], rowIndex) => <tr key={code} className={code === selectedCode ? 'is-sector' : ''}><th scope="row"><button type="button" onClick={() => onSelect(selectedDate, code)}>{name}</button></th>{frames.map((frame, columnIndex) => {
          const sector = frame.sectors.find(item => item.sectorCode === code);
          const value = sector?.[metric];
          const display = metric === 'breadthRatio' ? ratio(value) : pct(value);
          return <td key={frame.businessDate} className={frame.businessDate === selectedDate ? 'is-date' : ''}><button type="button" className={`mpa-matrix-cell heat-${heatLevel(value, metric)}`} aria-label={`${name} ${frame.businessDate} ${metricLabels[metric]} ${display}`} data-cell="true" tabIndex={(code === selectedCode || (!selectedCode && rowIndex === 0)) && frame.businessDate === selectedDate ? 0 : -1}
            onKeyDown={event => {
              const moves: Record<string, [number, number]> = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] };
              const move = moves[event.key];
              if (move) {
                event.preventDefault();
                const nextRow = Math.max(0, Math.min(sectors.length - 1, rowIndex + move[0]));
                const nextColumn = Math.max(0, Math.min(frames.length - 1, columnIndex + move[1]));
                onSelect(frames[nextColumn].businessDate, sectors[nextRow][0]);
                scrollRef.current?.querySelectorAll<HTMLButtonElement>('[data-cell]')[nextRow * frames.length + nextColumn]?.focus();
              }
            }} aria-pressed={code === selectedCode && frame.businessDate === selectedDate} onClick={() => onSelect(frame.businessDate, code)} title={`${name} · ${frame.businessDate}\n${metricLabels[metric]} ${display}`}><span>{display}</span></button></td>;
        })}</tr>)}</tbody>
      </table>
      {!sectors.length && <p className="mpa-empty">该区间暂无行业历史截面。</p>}
    </div>
  </section>;
}
