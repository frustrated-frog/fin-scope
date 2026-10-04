import type { MarketPulseWorkspace, SectorRotation } from './marketPulseTypes';
import { confirmationLabels, eventSector } from './sectorObservation';
import { finite } from './panoramaModel';
import { usePanoramaCatalysts } from './usePanoramaCatalysts';

type Props = {
  workspace: MarketPulseWorkspace; businessDate: string; hasSnapshot: boolean;
  sectors: SectorRotation[]; selectedCode: string; onSelect: (code: string) => void;
};

export function PanoramaCatalysts({ workspace, businessDate, hasSnapshot, sectors, selectedCode, onSelect }: Props) {
  const { status, events, retry } = usePanoramaCatalysts(workspace, businessDate, hasSnapshot);
  const selected = sectors.find(sector => sector.sectorCode === selectedCode);
  const rows = events.map(event => ({ event, sector: eventSector(event, sectors) }))
    .filter(row => !selectedCode || row.sector?.sectorCode === selectedCode);
  const renderRows = (items: typeof rows) => items.map(({ event, sector }, index) => <article className="mpa-catalyst-row" key={`${event.radarEventId}-${event.sectorCode ?? event.sectorName}-${index}`}>
    <div className="mpa-catalyst-industry">{sector
      ? <button type="button" aria-label={`联动行业${sector.sectorName}`} aria-pressed={selectedCode === sector.sectorCode} onClick={() => onSelect(sector.sectorCode)}>{sector.sectorName}<span aria-hidden="true">↗</span></button>
      : <span>{event.sectorName ?? '未关联行业'}</span>}</div>
    <div className="mpa-catalyst-content"><h4>{event.title}</h4><p>{event.evidence?.[0] ?? '等待更多事件信息'}</p>
      {(event.evidence?.length ?? 0) > 1 && <details><summary>查看行情响应</summary><ul>{event.evidence!.slice(1).map((item, i) => <li key={i}>{item}</li>)}</ul></details>}
    </div>
    <div className="mpa-catalyst-response"><span data-state={event.confirmationState}>{confirmationLabels[event.confirmationState ?? ''] ?? '待观察'}</span>
      <small>事件 {finite(event.eventScore) ? event.eventScore : '—'}<i />行情 {finite(event.marketReactionScore) ? event.marketReactionScore : '—'}</small>
    </div>
  </article>);
  return <section className="mpa-panel mpa-catalysts" aria-label="行业催化">
    <header className="mpa-section-head"><div><span className="mpa-eyebrow">事件与行情</span><h3>行业催化 <small>{selected ? `聚焦 ${selected.sectorName}` : selectedCode ? '所选行业' : '全市场'}</small></h3></div>
      <div className="mpa-catalyst-scope"><span>观察截面 <time>{businessDate}</time></span>{selectedCode && <button type="button" onClick={() => onSelect('')}>查看全部行业</button>}</div>
    </header>
    {status === 'loading' && <p className="mpa-note" role="status">正在读取当日催化…</p>}
    {status === 'error' && <div className="mpa-catalyst-error" role="status">当日催化加载失败。<button type="button" onClick={retry}>重试行业催化</button></div>}
    {status === 'unavailable' && <p className="mpa-note">这一天尚未保存行业催化截面。</p>}
    {status === 'ready' && (rows.length ? <div className="mpa-catalyst-list">
      {renderRows(rows.slice(0, 3))}
      {rows.length > 3 && <details className="mpa-catalyst-more" key={`${businessDate}-${selectedCode}`}><summary>展开其余 {rows.length - 3} 条催化</summary>{renderRows(rows.slice(3))}</details>}
    </div> : <p className="mpa-note">{selectedCode ? '该行业在所选截面中暂无关联催化，可继续观察它的行情与轨迹。' : '所选截面暂无行业催化。'}</p>)}
  </section>;
}
