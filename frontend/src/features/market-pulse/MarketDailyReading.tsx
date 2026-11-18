import { useMarketReading } from './useMarketReading';
import type { ReadingFact, ReadingPoint } from './useMarketReading';
import type { MarketPulseWorkspace } from './marketPulseTypes';
import './MarketDailyReading.css';

export type { MarketReading } from './useMarketReading';

export function MarketDailyReading({ workspace, dates, onLoad, onOpenPanorama, onOpenBreadth, onRefresh, refreshing }: {
  workspace: MarketPulseWorkspace; dates: string[]; onLoad: (date: string) => void;
  onOpenPanorama: () => void; onOpenBreadth: () => void; onRefresh?: () => void; refreshing?: boolean;
}) {
  const date = workspace.businessDate ?? '';
  const { reading, error, sending, generate, reload } = useMarketReading(date, workspace.generatedAt);
  const report = reading?.status === 'READY' ? reading.report : undefined;
  const facts = new Map((reading?.facts ?? []).map(fact => [fact.id, fact]));
  function evidence(ids: string[]) {
    const selected = ids.map(id => facts.get(id)).filter((fact): fact is ReadingFact => Boolean(fact));
    return selected.length ? <details className="mdr-facts"><summary>核对依据 <span>{selected.length}</span></summary>
      <ul>{selected.map(fact => <li key={fact.id}><small>{fact.businessDate} · {fact.label}</small><span>{fact.value}</span></li>)}</ul>
    </details> : null;
  }
  function point(item: ReadingPoint, index: number) {
    return <article key={`${item.title}-${index}`} className="mdr-point"><h4>{item.title}</h4><p>{item.detail}</p>{evidence(item.factIds)}</article>;
  }
  const breadth = workspace.breadth;
  const metrics = [
    ['上涨家数占比', breadth?.advanceRatio == null ? '—' : `${(breadth.advanceRatio * 100).toFixed(1)}%`],
    ['涨跌中位数', breadth?.medianChangePct == null ? '—' : `${breadth.medianChangePct > 0 ? '+' : ''}${breadth.medianChangePct.toFixed(2)}%`],
    ['全市场成交额', breadth?.totalAmount == null ? '—' : `${(breadth.totalAmount / 1e12).toFixed(2)} 万亿`],
    [`MA20 上方占比${breadth?.trendBreadth?.ma20ValidCount != null ? `（${breadth.trendBreadth.ma20ValidCount}只样本）` : ''}`, breadth?.trendBreadth?.ma20Ratio == null ? '—' : `${(breadth.trendBreadth.ma20Ratio * 100).toFixed(1)}%`],
  ];
  return <div className="mdr-workspace" aria-label="每日市场速读">
    <header className="mdr-header"><div><span className="mdr-eyebrow">DAILY MARKET READING</span><h2>今天的市场，先看这里</h2><p>先理解整体变化，再展开行业与数据。</p></div>
      <div className="mdr-header-actions"><label>交易日<select aria-label="速读交易日" value={date} onChange={event => onLoad(event.target.value)}>
        {[...new Set([date, ...dates])].filter(Boolean).map(item => <option key={item}>{item}</option>)}
      </select></label>{onRefresh && <button type="button" aria-label="立即补刷新" disabled={refreshing} onClick={onRefresh}>{refreshing ? '正在更新…' : '更新市场数据'}</button>}</div></header>
    <section className="mdr-thesis" aria-label={report ? 'AI市场研判' : '规则市场速读'}>
      <div className="mdr-thesis-meta"><span>{report ? report.stateLabel : '规则速读'}</span><small>{date} · {report ? 'AI 综合研判' : '基于已保存市场快照'}</small></div>
      <h3>{report?.headline ?? workspace.dailyReview?.headline ?? workspace.regime?.explanation ?? '等待当日市场快照'}</h3>
      <p>{report?.summary ?? workspace.dailyReview?.breadthConclusion ?? '可先展开全景与市场宽度，核对当天的事实。'}</p>
      <div className="mdr-metrics">{metrics.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>
      <footer><span>{report ? `数据截至 ${reading?.asOf?.replace('T', ' ').slice(0, 16) ?? date}` : reading?.status === 'DISABLED' ? '规则速读 · 可继续核对原始数据' : '规则结论会在研判完成后更新为 AI 速读'}</span>
        <button type="button" onClick={onOpenPanorama}>核对市场全景 <span aria-hidden="true">↗</span></button>
        <button type="button" onClick={onOpenBreadth}>查看市场宽度 <span aria-hidden="true">↗</span></button></footer>
    </section>
    {!report && <div className="mdr-status" role="status"><p>{error ? '研判暂时无法读取，当前显示规则速读。' : ['QUEUED', 'RUNNING'].includes(reading?.status ?? '') ? '正在对比近期市场数据，完成后会自动显示。' : reading?.status === 'DISABLED' ? '市场 AI 已关闭，继续使用规则速读与原始图表。' : reading?.status === 'UNAVAILABLE' ? 'AI 配置或当日市场数据不足，可先查看已保存的市场事实。' : reading?.message ?? '可生成一份当日市场研判，完成后会保存供重复查看。'}</p>
      {error ? <button type="button" onClick={reload}>重新读取研判</button>
        : ['NOT_GENERATED', 'FAILED'].includes(reading?.status ?? '') ? <button type="button" disabled={sending} onClick={() => void generate()}>{sending ? '正在提交…' : reading?.status === 'FAILED' ? '重试研判' : '生成当日研判'}</button> : null}</div>}
    {report && <>
      <section className="mdr-change"><span className="mdr-eyebrow">相比前期</span><h3>{report.change.title}</h3><p>{report.change.detail}</p>{evidence(report.change.factIds)}</section>
      <div className="mdr-two-column"><section className="mdr-section"><header><span className="mdr-signal mdr-support" /><h3>支撑当前判断的变化</h3></header>{report.drivers.map(point)}</section>
        <section className="mdr-section"><header><span className="mdr-signal mdr-caution" /><h3>还没有对齐的信号</h3></header>{report.contradictions.map(point)}</section></div>
      <section className="mdr-section mdr-sectors"><header><h3>接下来，先研究哪些行业</h3><span>按本次研判的观察顺序</span></header>
        {report.sectors.length ? report.sectors.map((sector, index) => <article key={sector.sectorCode}><div className="mdr-sector-title"><span>{String(index + 1).padStart(2, '0')}</span><h4>{reading?.sectorNames[sector.sectorCode] ?? sector.sectorCode}</h4></div><div><p>{sector.reason}</p><p className="mdr-watch"><strong>继续观察</strong>{sector.watchCondition}</p>{evidence(sector.factIds)}</div></article>) : <p>当前没有足够依据形成行业观察顺序。</p>}</section>
      <section className="mdr-section mdr-conditions"><header><h3>什么变化，会改变今天的判断</h3><span>留给后续交易日验证</span></header>{report.conditions.map(point)}</section>
    </>}
    <details className="mdr-rule-reference"><summary>查看规则复盘参考</summary><p>{workspace.dailyReview?.headline ?? workspace.regime?.explanation}</p><p>{workspace.dailyReview?.breadthConclusion}</p></details>
  </div>;
}
