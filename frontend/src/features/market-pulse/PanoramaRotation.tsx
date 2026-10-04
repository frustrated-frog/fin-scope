import type { SectorRotation } from './marketPulseTypes';
import { isoDate, pct } from './panoramaModel';
import { rotationSummary, sectorTrail } from './sectorObservation';

type Props = { sectors: SectorRotation[]; selectedCode: string; onSelect: (code: string) => void; businessDate: string };
export function PanoramaRotation({ sectors, selectedCode, onSelect, businessDate }: Props) {
  const rows = sectors.map(sector => ({ sector, trail: sectorTrail(sector, businessDate) })).filter(row => row.trail.length);
  const extentX = Math.max(1, ...rows.flatMap(row => row.trail.map(point => Math.abs(point.relativeStrength!))));
  const extentY = Math.max(1, ...rows.flatMap(row => row.trail.map(point => Math.abs(point.relativeMomentum!))));
  const selected = rows.find(row => row.sector.sectorCode === selectedCode);
  const latest = selected?.trail[selected.trail.length - 1];
  const summary = rotationSummary(selected?.trail ?? []);
  const selectedSector = sectors.find(sector => sector.sectorCode === selectedCode);
  return <section className="mpa-panel mpa-rotation" aria-label="联动行业轮动图">
    <header className="mpa-section-head"><div><span className="mpa-eyebrow">主线迁移</span><h3>行业轮动轨迹</h3></div><span className="mpa-caption">相对行业均值</span></header>
    <div className="mpa-rotation-stage">
      <svg viewBox="0 0 400 330" role="img" aria-label="行业相对强度与动量轮动尾迹">
        <rect x="38" y="25" width="162" height="135" className="mpa-quadrant improving" /><rect x="200" y="25" width="162" height="135" className="mpa-quadrant leading" />
        <rect x="38" y="160" width="162" height="135" className="mpa-quadrant lagging" /><rect x="200" y="160" width="162" height="135" className="mpa-quadrant weakening" />
        <line x1="200" y1="25" x2="200" y2="295" className="mpa-gridline" /><line x1="38" y1="160" x2="362" y2="160" className="mpa-gridline" />
        <text x="48" y="45" className="mpa-axis">改善</text><text x="330" y="45" className="mpa-axis">领先</text><text x="48" y="283" className="mpa-axis">落后</text><text x="330" y="283" className="mpa-axis">减弱</text>
        <text x="200" y="320" className="mpa-axis" textAnchor="middle">相对强度 →</text><text x="16" y="160" className="mpa-axis" transform="rotate(-90 16 160)" textAnchor="middle">强度动量 →</text>
        {rows.map(({ sector, trail }) => {
          const last = trail[trail.length - 1];
          const chosen = sector.sectorCode === selectedCode;
          const points = trail.map(p => `${200 + p.relativeStrength! / extentX * 145},${160 - p.relativeMomentum! / extentY * 116}`).join(' ');
          return <g key={sector.sectorCode} className={`mpa-rotation-point ${chosen ? 'is-selected' : ''}`} onClick={() => onSelect(sector.sectorCode)} role="button" tabIndex={0} aria-label={`轮动选择${sector.sectorName}`} aria-pressed={chosen} onKeyDown={event => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onSelect(sector.sectorCode);
            }
          }}><title>{sector.sectorName} · 相对强度 {pct(last.relativeStrength)} · 动量 {pct(last.relativeMomentum)}</title>
            <polyline points={points} fill="none" />
            <circle cx={200 + last.relativeStrength! / extentX * 145} cy={160 - last.relativeMomentum! / extentY * 116} r={chosen ? 6 : 3.5} />
            {chosen && <text x={200 + last.relativeStrength! / extentX * 145} y={146 - last.relativeMomentum! / extentY * 116} textAnchor="middle">{sector.sectorName}</text>}
          </g>;
        })}
      </svg>
      {!rows.length && <p className="mpa-note">当前日期暂无行业轮动尾迹。</p>}
    </div>
    <div className="mpa-rotation-caption" aria-label="行业轮动摘要">
      <strong>{selectedSector?.sectorName ?? '点选行业，查看它的移动轨迹'}{summary && <b className="mpa-quadrant-tag">{summary.quadrant} · {summary.pace}</b>}</strong>
      <span>{latest ? `相对强度 ${pct(latest.relativeStrength)} · 动量 ${pct(latest.relativeMomentum)}` : selectedCode ? '所选行业在此截面暂无有效轨迹' : '点为当日位置，线为此前的变化路径'}</span>
      {selected && latest && <small>{selected.trail.length} 个观测日 · {isoDate(selected.trail[0].businessDate)} — {isoDate(latest.businessDate)}</small>}
    </div>
  </section>;
}
