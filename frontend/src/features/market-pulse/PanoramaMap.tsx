import { useState } from 'react';
import type { ResearchStock } from './marketResearchTypes';
import type { SectorRotation, StockDiscoveryMarketContext } from './marketPulseTypes';
import { buildResearchContext } from './marketResearch';
import { amount, finite, heatLevel, metricLabels, pct, ratio, sectorMembers, turnoverTiles, type PanoramaMetric } from './panoramaModel';

type Props = {
  sectors: SectorRotation[]; selectedCode: string; onSelect: (code: string) => void;
  metric: PanoramaMetric; onMetric: (metric: PanoramaMetric) => void;
  stocks: ResearchStock[]; stocksLoading: boolean; stocksError: boolean; businessDate: string;
  watchedCodes: Set<string>; onOpenStock?: (code: string) => void;
  onOpenStockDiscovery?: (context?: StockDiscoveryMarketContext) => void;
};
export function PanoramaMap({ sectors, selectedCode, onSelect, metric, onMetric, stocks, stocksLoading, stocksError, businessDate, watchedCodes, onOpenStock, onOpenStockDiscovery }: Props) {
  const [query, setQuery] = useState('');
  const [drillCode, setDrillCode] = useState('');
  const [sizing, setSizing] = useState<'equal' | 'amount'>('equal');
  const selected = sectors.find(sector => sector.sectorCode === selectedCode);
  const drill = drillCode === selectedCode && selected;
  const matches = [...sectors].sort((a, b) => a.sectorCode.localeCompare(b.sectorCode))
    .filter(sector => !query || sector.sectorName.includes(query) || sector.sectorCode.includes(query));
  const members = selected ? sectorMembers(stocks, selected) : [];
  const memberMetric = metric === 'breadthRatio' ? 'return1d' : metric;
  const sortedMembers = [...members].sort((a, b) => (b[memberMetric] ?? -Infinity) - (a[memberMetric] ?? -Infinity));
  const totalAmount = members.some(stock => finite(stock.amount))
    ? members.reduce((sum, stock) => sum + (stock.amount ?? 0), 0) : undefined;
  const tiles = turnoverTiles(members);
  const leaders = [...sectors].filter(sector => finite(sector[metric])).sort((a, b) => (b[metric] ?? 0) - (a[metric] ?? 0));
  return <section className="mpa-panel mpa-map" aria-label="全市场分层地图">
    <header className="mpa-section-head"><div><span className="mpa-eyebrow">市场结构 · {businessDate}</span><h3>{drill ? drill.sectorName : '全行业强弱地图'} <small>{drill ? `${members.length} 只样本` : `${sectors.length} 个行业`}</small></h3></div><label className="mpa-select">颜色<select aria-label="地图颜色指标" value={metric} onChange={event => onMetric(event.target.value as PanoramaMetric)}>{Object.entries(metricLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></header>
    <div className="mpa-map-toolbar">
      <div className="mpa-breadcrumb"><button type="button" onClick={() => setDrillCode('')}>全市场</button>{drill && <><span>/</span><strong>{drill.sectorName}</strong></>}</div>
      {drill ? <label className="mpa-select">面积<select aria-label="个股面积" value={sizing} onChange={event => setSizing(event.target.value as 'equal' | 'amount')}><option value="equal">等面积</option><option value="amount">成交额</option></select></label> : <input className="mpa-search" aria-label="查找行业" placeholder="查找行业…" value={query} onChange={event => setQuery(event.target.value)} />}
      <div className="mpa-color-key"><span>{metric === 'breadthRatio' && !drill ? '低于50%' : '下跌'}</span><i /><span>{metric === 'breadthRatio' && !drill ? '高于50%' : '上涨'}</span></div>
    </div>
    <div className="mpa-map-body">
      <div className="mpa-field">
        {!drill && <div className="mpa-sector-grid">{matches.map(sector => <button type="button" key={sector.sectorCode} className={`mpa-tile heat-${heatLevel(sector[metric], metric)}`} aria-label={`选择行业${sector.sectorName}`} aria-pressed={sector.sectorCode === selectedCode} onClick={() => onSelect(sector.sectorCode)}>
          <span>{sector.sectorName}</span><strong>{metric === 'breadthRatio' ? ratio(sector[metric]) : pct(sector[metric])}</strong><small>上涨占比 {ratio(sector.breadthRatio)}</small>
        </button>)}</div>}
        {!drill && !matches.length && <p className="mpa-empty">{sectors.length ? '没有匹配的行业。' : '这一天没有行业截面，可从时间轴选择其他日期。'}</p>}
        {drill && <>
          <p className="mpa-note">收益截至 {businessDate}，按当前目录行业归属展示；★ 为自选。面积{ sizing === 'amount' ? '与成交额成比例' : '相同'}，颜色为{metricLabels[memberMetric]}。{sizing === 'amount' && `成交额可用 ${tiles.length}/${members.length} 只，面积仅在这些样本中比较。`}</p>
          {stocksLoading ? <p role="status" className="mpa-empty">正在读取当日个股表现…</p> : sizing === 'amount' ? <div className={`mpa-turnover-map${tiles.length < 4 ? ' is-sparse' : ''}`} aria-label="个股成交额面积地图">{tiles.map(tile => {
            const stock = members.find(member => member.instrumentCode === tile.code)!;
            return <button key={tile.code} type="button" disabled={!onOpenStock} onClick={() => onOpenStock?.(tile.code)}
              className={`mpa-turnover-tile heat-${heatLevel(stock[memberMetric], memberMetric)}`}
              style={{ left: `${tile.x}%`, top: `${tile.y}%`, width: `${tile.width}%`, height: `${tile.height}%` }}
              aria-label={`${stock.instrumentName ?? tile.code} 成交额 ${amount(stock.amount)}`}
              title={`${stock.instrumentName ?? tile.code} · ${pct(stock[memberMetric])} · 成交额 ${amount(stock.amount)}`}>
              {tile.width > 9 && tile.height > 10 && <><span>{watchedCodes.has(tile.code.split('.')[0]) ? '★ ' : ''}{stock.instrumentName ?? tile.code}</span><strong>{pct(stock[memberMetric])}</strong></>}
            </button>;
          })}{!tiles.length && <p className="mpa-empty">当日样本暂无正成交额，请切换等面积查看个股。</p>}</div> : <div className="mpa-stock-grid">{sortedMembers.map(stock => <button type="button" key={stock.instrumentCode} disabled={!onOpenStock} onClick={() => onOpenStock?.(stock.instrumentCode)} className={`mpa-tile heat-${heatLevel(stock[memberMetric], memberMetric)}`} title={`${stock.instrumentName ?? stock.instrumentCode} · 成交额 ${amount(stock.amount)}`}>
            <span>{watchedCodes.has(stock.instrumentCode.split('.')[0]) ? '★ ' : ''}{stock.instrumentName ?? stock.instrumentCode}</span><strong>{pct(stock[memberMetric])}</strong><small>{stock.instrumentCode}</small>
          </button>)}</div>}
          {sizing === 'amount' && members.length > 0 && <details className="mpa-member-list"><summary>查看全部 {members.length} 只样本（含无成交额个股）</summary>{sortedMembers.map(stock => <button type="button" key={stock.instrumentCode} onClick={() => onOpenStock?.(stock.instrumentCode)} disabled={!onOpenStock}><span>{stock.instrumentName ?? stock.instrumentCode}</span><strong>{pct(stock[memberMetric])}</strong><small>{amount(stock.amount)}</small></button>)}</details>}
          {!stocksLoading && !members.length && <p className="mpa-empty">{stocksError ? '当日个股数据未加载成功，请使用顶部重试。' : '本地样本中暂无该行业的当日行情。'}</p>}
        </>}
      </div>
      <aside className="mpa-sector-inspector" aria-label="选中行业详情">
        {selected ? <>
          <span className="mpa-eyebrow">选中行业</span><h4>{selected.sectorName}</h4><div className={`mpa-inspector-return heat-text-${heatLevel(selected[metric], metric)}`}>{metric === 'breadthRatio' ? ratio(selected[metric]) : pct(selected[metric])}<small>{metricLabels[metric]}</small></div>
          <dl><div><dt>当日</dt><dd>{pct(selected.return1d)}</dd></div><div><dt>近5日</dt><dd>{pct(selected.return5d)}</dd></div><div><dt>近20日</dt><dd>{pct(selected.return20d)}</dd></div><div><dt>上涨占比</dt><dd>{ratio(selected.breadthRatio)}</dd></div><div><dt>本地成员样本</dt><dd>{stocksLoading ? '读取中' : members.length}</dd></div><div><dt>样本成交额</dt><dd>{members.length ? amount(totalAmount) : '—'}</dd></div></dl>
          <p>{selected.explanations?.[0] ?? '结合历史矩阵观察强弱是否持续。'}</p>
          <button className="mpa-primary" type="button" onClick={() => setDrillCode(drill ? '' : selectedCode)}>{drill ? '返回全行业地图' : '展开行业个股'} <span>↗</span></button>
          {onOpenStockDiscovery && <button className="mpa-secondary" type="button" onClick={() => onOpenStockDiscovery(buildResearchContext([selected], businessDate))}>进入行业研究 →</button>}
        </> : <>
          <span className="mpa-eyebrow">结构速览</span><h4>强弱分布</h4><p>点击任一行业，同步查看它的历史表现与轮动轨迹。</p>
          <div className="mpa-distribution"><strong>{sectors.filter(s => (s.return1d ?? 0) > 0).length}<small>上涨行业</small></strong><strong>{sectors.filter(s => finite(s.return1d) && s.return1d < 0).length}<small>下跌行业</small></strong></div>
          <span className="mpa-eyebrow">{metricLabels[metric]}领先</span>{leaders.slice(0, 4).map(sector => <button className="mpa-rank" type="button" key={sector.sectorCode} onClick={() => onSelect(sector.sectorCode)}><span>{sector.sectorName}</span><strong>{metric === 'breadthRatio' ? ratio(sector[metric]) : pct(sector[metric])}</strong></button>)}
        </>}
      </aside>
    </div>
    <footer className="mpa-panel-foot">{drill ? '个股为本地已采集样本，点击进入个股详情。' : '行业等面积展示 · 灰色表示暂无数据 · 点击行业联动下方历史矩阵'}</footer>
  </section>;
}
