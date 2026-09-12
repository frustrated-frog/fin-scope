import { useMemo, useState } from 'react';
import { buildResearchContext, filterSectors, pct, ratio, sectorRules } from './marketResearch';
import type { SectorFilter } from './marketResearchTypes';
import type { SectorRotation, StockDiscoveryMarketContext } from './marketPulseTypes';

type Props = { sectors: SectorRotation[]; businessDate: string; onOpenStockDiscovery?: (context: StockDiscoveryMarketContext) => void };
export function SectorResearchPanel({ sectors, businessDate, onOpenStockDiscovery }: Props) {
  const [filter, setFilter] = useState<SectorFilter>({ kind: 'ALL', search: '' });
  const [selected, setSelected] = useState<string[]>([]);
  const results = useMemo(() => filterSectors(sectors, filter), [sectors, filter]);
  const compared = results.filter(item => selected.includes(item.sectorCode));
  function toggle(code: string) {
    setSelected(current => current.includes(code) ? current.filter(item => item !== code) : [...current.filter(item => results.some(result => result.sectorCode === item)).slice(0, 2), code]);
  }
  return <section className="mp-research-section" aria-label="行业机会筛选器">
    <header className="mp-research-heading"><div><span>从条件发现方向</span><h3>行业机会筛选器</h3></div><strong>{results.length} / {sectors.length} 个行业</strong></header>
    <div className="mp-research-filters">
      <label>机会形态<select value={filter.kind} onChange={event => setFilter({ ...filter, kind: event.target.value as SectorFilter['kind'] })}>
        <option value="ALL">全部行业</option><option value="EMERGING">初步转强</option><option value="PULLBACK">中期强势回落</option><option value="REPAIR">弱势修复</option>
      </select></label>
      <label>行业名称<input value={filter.search} onChange={event => setFilter({ ...filter, search: event.target.value })} placeholder="搜索行业" /></label>
      <label>最低上涨比例<select value={filter.minBreadth ?? ''} onChange={event => setFilter({ ...filter, minBreadth: event.target.value ? Number(event.target.value) : undefined })}>
        <option value="">不限</option><option value="50">50%</option><option value="60">60%</option><option value="80">80%</option>
      </select></label>
      <label>最高5日涨幅<select value={filter.maxReturn5d ?? ''} onChange={event => setFilter({ ...filter, maxReturn5d: event.target.value ? Number(event.target.value) : undefined })}>
        <option value="">不限</option><option value="5">5%</option><option value="10">10%</option><option value="20">20%</option>
      </select></label>
    </div>
    <p className="mp-research-note">{sectorRules[filter.kind]} 缺少筛选所需指标的行业不参与结果。数据日：{businessDate}。</p>
    <div className="mp-research-table-wrap"><table><caption>行业指标与比较选择</caption><thead><tr><th>比较</th><th>行业</th><th>今日</th><th>5日</th><th>20日</th><th>5日超额</th><th>上涨比例</th><th>继续研究</th></tr></thead><tbody>
      {results.map(item => <tr key={item.sectorCode}><td><input type="checkbox" aria-label={`比较${item.sectorName}`} checked={selected.includes(item.sectorCode)} onChange={() => toggle(item.sectorCode)} /></td><th scope="row">{item.sectorName}</th><td>{pct(item.return1d)}</td><td>{pct(item.return5d)}</td><td>{pct(item.return20d)}</td><td>{pct(item.excessReturn5d)}</td><td>{ratio(item.breadthRatio)}</td><td><button type="button" disabled={!onOpenStockDiscovery} onClick={() => onOpenStockDiscovery?.(buildResearchContext([item], businessDate))}>研究行业</button></td></tr>)}
    </tbody></table></div>
    {!results.length && <p className="mp-research-note">暂无行业满足条件。可放宽筛选，或补刷新行业行情后再试。</p>}
    <div className="mp-research-compare"><span>最多比较3个：{compared.map(item => item.sectorName).join(' / ') || '勾选行业开始比较'}</span><button type="button" disabled={!compared.length || !onOpenStockDiscovery} onClick={() => onOpenStockDiscovery?.(buildResearchContext(compared, businessDate))}>研究已选行业</button></div>
    {!!compared.length && <div className="mp-research-comparison" aria-label="行业对比">{compared.map(item => <article key={item.sectorCode}><h4>{item.sectorName}</h4><dl><div><dt>5日超额</dt><dd>{pct(item.excessReturn5d)}</dd></div><div><dt>20日表现</dt><dd>{pct(item.return20d)}</dd></div><div><dt>上涨比例</dt><dd>{ratio(item.breadthRatio)}</dd></div></dl></article>)}</div>}
  </section>;
}
