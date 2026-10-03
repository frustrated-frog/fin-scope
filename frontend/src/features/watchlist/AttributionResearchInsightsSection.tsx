import { AttributionEventSource, AttributionResearchInsights, AttributionBusinessLink } from '../../shared/types';
import './attributionResearchInsights.css';

const directions = { POSITIVE: '偏利好', NEGATIVE: '偏利空', MIXED: '多空兼有', NEUTRAL: '影响中性', UNCLEAR: '影响待观察' };
const kinds = { STOCK: '目标股', PEER: '业务可比', SECTOR: '行业', BENCHMARK: '宽基' };

function Direction({ value }: { value?: AttributionBusinessLink['direction'] }) {
  if (!value) {
    return null;
  }
  return <span className={`attribution-insight-direction direction-${value.toLowerCase()}`}>{directions[value]}</span>;
}

function SourceLinks({ ids, sources }: { ids?: string[]; sources: AttributionEventSource[] }) {
  const selected = sources.filter(source => {
    try {
      return ids?.includes(source.id) && ['http:', 'https:'].includes(new URL(source.url).protocol);
    } catch {
      return false;
    }
  });
  if (!selected.length) {
    return null;
  }
  return <footer className="attribution-insight-references">{selected.map(source => <a key={source.id} href={source.url} target="_blank" rel="noreferrer" title={source.publishedAt || undefined}>
    {source.title || '相关披露'} ↗
  </a>)}</footer>;
}

function Detail({ label, children }: { label: string; children?: string }) {
  if (!children) {
    return null;
  }
  return <div className="attribution-insight-detail"><h6>{label}</h6><p>{children}</p></div>;
}

function Change({ value, points = false }: { value?: number | null; points?: boolean }) {
  if (value == null || !Number.isFinite(value)) {
    return <span className="attribution-insight-number missing" aria-label="暂无数据">—</span>;
  }
  return <span className={`attribution-insight-number ${value > 0 ? 'rise' : value < 0 ? 'fall' : ''}`}>
    {value > 0 ? '+' : ''}{value.toFixed(2)}<small>{points ? ' 个百分点' : '%'}</small>
  </span>;
}

/** 只在报告尾部追加；旧快照没有该字段时维持原来的 DOM。 */
export function AttributionResearchInsightsSection({ insights }: { insights?: AttributionResearchInsights | null }) {
  if (!insights) {
    return null;
  }
  const sources = insights.sources || [];
  const businesses = insights.businesses || [];
  const expectations = insights.expectations || [];
  const comparisons = insights.comparisons || [];
  return <div className="attribution-research-insights" aria-label="公司与市场深读">
    <section className="attribution-insight-section" aria-label="公司业务关联">
      <header className="attribution-insight-section-head"><div><span className="attribution-insight-eyebrow">从消息到经营</span><h4>公司业务关联</h4></div><span className="attribution-insight-date">{insights.asOfDate && `截至 ${insights.asOfDate}`}</span></header>
      <p className="attribution-insight-intro">消息落在哪块业务，如何传导到收入、成本和利润。</p>
      {businesses.length === 0 && <p className="attribution-insight-empty">本次业务关联尚未生成，重新归因可补充这一部分。</p>}
      {businesses.map((item, index) => <article className="attribution-insight-card" key={`${item.business}-${index}`}>
        <header className="attribution-insight-card-head"><h5>{item.business}</h5><Direction value={item.direction} /></header>
        {item.position && <p className="attribution-insight-position">{item.position}</p>}
        {item.financialAnchor && <div className="attribution-insight-anchor"><span>经营数据</span><p>{item.financialAnchor}</p></div>}
        <div className="attribution-insight-pair"><Detail label="相关消息">{item.catalyst}</Detail><Detail label="影响如何传导">{item.transmission}</Detail></div>
        {item.sensitivity && <div className="attribution-insight-condition"><strong>关键变量</strong><p>{item.sensitivity}</p></div>}
        <SourceLinks ids={item.sourceIds} sources={sources} />
      </article>)}
    </section>

    <section className="attribution-insight-section" aria-label="板块与同类对照">
      <header className="attribution-insight-section-head"><div><span className="attribution-insight-eyebrow">把个股放回市场</span><h4>板块与同类对照</h4></div><span className="attribution-insight-date">{insights.asOfDate} · 同日比较</span></header>
      {insights.comparisonSummary && <p className="attribution-insight-comparison-summary">{insights.comparisonSummary}</p>}
      {comparisons.length > 0 && <p className="attribution-insight-scroll-hint">左右滑动表格，查看涨跌与相对表现 →</p>}
      {comparisons.length > 0 ? <div className="attribution-insight-table-scroll" role="region" aria-label="行情对照表，可横向滚动" tabIndex={0}>
        <table className="attribution-insight-comparison-table">
          <caption>近 5 日含目标日；相对表现 = 目标股当日涨跌 − 对照标的当日涨跌。</caption>
          <thead><tr><th scope="col">对照标的</th><th scope="col">目标日涨跌</th><th scope="col">近 5 日累计</th><th scope="col">目标股相对表现</th></tr></thead>
          <tbody>{comparisons.map((row, index) => <tr key={`${row.kind}-${row.code}-${index}`} className={row.kind === 'STOCK' ? 'is-target' : ''}>
            <th scope="row"><div className="attribution-insight-security"><span className="attribution-insight-kind">{kinds[row.kind]}</span><strong>{row.name}</strong><small>{row.code}</small></div>
              {row.reason && <p>{row.reason}</p>}{row.note && <p className="attribution-insight-row-note">{row.note}</p>}
            </th>
            <td><Change value={row.changePct} /></td><td><Change value={row.fiveSessionChangePct} /></td>
            <td>{row.kind === 'STOCK' ? <span className="attribution-insight-number missing">—</span> : <Change value={row.stockRelativePct} points />}</td>
          </tr>)}</tbody>
        </table>
      </div> : <p className="attribution-insight-empty">本次行情对照暂未取得，业务与预期分析仍可单独阅读。</p>}
      {comparisons.length > 0 && <p className="attribution-insight-data-note">数据：{[...new Set(comparisons.map(row => row.source).filter(Boolean))].join('、')}。可比公司按业务选择，样本不等同于完整行业。历史报告按目标日展示，盘中当日日线可能未收盘。</p>}
    </section>

    <section className="attribution-insight-section" aria-label="市场预期变化">
      <header className="attribution-insight-section-head"><div><span className="attribution-insight-eyebrow">从经营到定价</span><h4>市场预期变化</h4></div></header>
      <p className="attribution-insight-intro">结合此前披露与本次消息，展开预期、兑现进度和后续催化。</p>
      {expectations.length === 0 && <p className="attribution-insight-empty">本次预期变化尚未展开，重新归因可补充这一部分。</p>}
      {expectations.map((item, index) => <article className="attribution-insight-card" key={`${item.topic}-${index}`}>
        <header className="attribution-insight-card-head"><h5>{item.topic}</h5><Direction value={item.direction} /></header>
        <div className="attribution-insight-pair expectation-pair"><Detail label="此前怎么看">{item.priorExpectation}</Detail><Detail label="现在多了什么">{item.newInformation}</Detail></div>
        {item.expectationBasis && <p className="attribution-insight-basis"><strong>预期出发点</strong>{item.expectationBasis}</p>}
        {item.repricingPath && <div className="attribution-insight-mechanism"><h6>为什么影响定价</h6><p>{item.repricingPath}</p></div>}
        <div className="attribution-insight-pair"><Detail label="已经兑现">{item.realized}</Detail><Detail label="下一步催化">{item.nextCatalyst}</Detail></div>
        <SourceLinks ids={item.sourceIds} sources={sources} />
      </article>)}
    </section>
    {!!insights.warnings?.length && <details className="attribution-insight-notes"><summary>补充研究说明</summary><ul>{insights.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul></details>}
  </div>;
}
