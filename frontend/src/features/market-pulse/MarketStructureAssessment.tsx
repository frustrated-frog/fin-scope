import { useMarketReading } from './useMarketReading';
import type { ReadingPoint, ScenarioCondition } from './useMarketReading';
import type { MarketPulseWorkspace, MarketTransitionDecision, StockDiscoveryMarketContext } from './marketPulseTypes';
import { MarketTransitionPanel } from './MarketTransitionPanel';
import './MarketStructureAssessment.css';

const metricLabels: Record<string, string> = { ADVANCE_RATIO: '上涨比例', MEDIAN_RETURN: '涨跌中位数', TOTAL_AMOUNT: '成交额', ADVANCE_AMOUNT_RATIO: '上涨成交额占比', MA20_RATIO: 'MA20 上方比例' };
const conditionLabels = { MET: '条件已出现', NOT_MET: '条件未出现', UNAVAILABLE: '数据不足，暂不判断' };

export function MarketStructureAssessment({ workspace, decision, dates = [], onLoad, onRefresh, refreshing, onOpenStockDiscovery }: {
  workspace: MarketPulseWorkspace; decision: MarketTransitionDecision;
  dates?: string[]; onLoad?: (date: string) => void; onRefresh?: () => void; refreshing?: boolean;
  onOpenStockDiscovery?: (context: StockDiscoveryMarketContext) => void;
}) {
  const date = workspace.businessDate ?? '';
  const { reading, error, sending, generate, reload } = useMarketReading(date, workspace.generatedAt);
  const report = reading?.status === 'READY' ? reading.report : undefined;
  const scores = reading?.structure ?? [];
  const assessed = scores.some(row => row.adjustment != null);
  const facts = new Map((reading?.facts ?? []).map(fact => [fact.id, fact]));
  function evidence(ids: string[]) {
    return <details className="msa-evidence"><summary>核对数据依据</summary>{ids.map(id => {
      const fact = facts.get(id);
      return fact ? <p key={id}><time>{fact.businessDate} · {fact.label}</time>{fact.value}</p> : null;
    })}</details>;
  }
  function point(item: ReadingPoint) {
    return <><p>{item.detail}</p>{evidence(item.factIds)}</>;
  }
  function condition(item: ScenarioCondition, label: string) {
    return <div className="msa-condition"><span>{label}</span><strong>{metricLabels[item.metric] ?? item.metric}较 {date} {item.direction === 'RISE' ? '上升' : '下降'}</strong>{point(item.explanation)}</div>;
  }
  const score = (dimension: string) => scores.find(row => row.dimension === dimension)?.finalScore;
  const repair = score('REPAIR_QUALITY');
  const fragile = score('FRAGILITY');
  const turn = score('TURN_CONFIRMATION');
  const baselineTitle = fragile != null && fragile >= 65 ? '卖压与结构压力仍需消化'
    : repair != null && repair >= 60 && turn != null && turn >= 60 ? '修复证据正在增加'
      : repair != null ? '市场结构仍待进一步确认' : '等待可比的市场结构数据';
  return <section className="msa-workspace" aria-label="规则与AI结构评估">
    <header className="msa-intro"><div><span>STRUCTURE / SCENARIOS</span><h2>转折，先看证据有没有变</h2><p>{date} · {assessed ? '规则基准与 AI 调整共同评估' : '规则基准评估'} · 脆弱性越高越需留意，其他维度越高证据越强</p></div>
      <div className="msa-controls">{onLoad && <label>交易日<select aria-label="情景交易日" value={date} onChange={event => onLoad(event.target.value)}>{[...new Set([date, ...dates])].filter(Boolean).map(item => <option key={item}>{item}</option>)}</select></label>}
      {onRefresh && <button type="button" disabled={refreshing} onClick={onRefresh}>{refreshing ? '正在更新…' : '更新市场数据'}</button>}
      {['NOT_GENERATED', 'FAILED'].includes(reading?.status ?? '') && <button type="button" disabled={sending} onClick={() => void generate()}>{sending ? '正在提交…' : reading?.status === 'FAILED' ? '重试当日研判' : '生成当日研判'}</button>}
      {error && <button type="button" onClick={reload}>重新读取研判</button>}</div></header>
    {!report && <p className="msa-notice" role="status">{error ? '研判暂不可用，可继续查看规则轨迹与情景。' : reading?.status === 'DISABLED' ? 'AI 已关闭，规则基准和历史情景核对继续运行。' : reading?.status === 'UNAVAILABLE' ? 'AI 配置或当日可比数据不足，当前保留规则基准。' : ['RUNNING', 'QUEUED'].includes(reading?.status ?? '') ? '正在评估当日结构，完成后自动显示。' : reading?.message ?? '当前使用规则基准；AI 研判与今日速读共用一份结果。'}</p>}
    <section className="msa-thesis"><span>{report?.stateLabel ?? '规则判断'}</span><h3>{report?.headline ?? baselineTitle}</h3><p>{report?.summary ?? '结合当日参与面、成交支持与近期变化观察结构。数据不足的维度保留为空，可展开分项核对。'}</p></section>
    <div className="msa-scores">{scores.map(row => <article key={row.dimension} data-fragile={row.dimension === 'FRAGILITY'} aria-label={row.label}>
      <header><h3>{row.label}</h3><strong>{row.finalScore ?? '—'}<small>{row.finalScore == null ? '数据不足' : '/ 100'}</small></strong></header>
      {row.baselineScore != null && <div className="msa-score-path"><span>规则 <b>{row.baselineScore}</b></span><span>AI <b>{row.adjustment == null ? (reading?.status === 'DISABLED' ? '未启用' : '待评估') : `${row.adjustment >= 0 ? '+' : ''}${row.adjustment}`}</b></span><span>最终 <b>{row.finalScore}</b></span></div>}
      <p>{row.reason ?? row.scope}</p>{row.counterEvidence && <p className="msa-counter"><span>仍需核对</span>{row.counterEvidence}</p>}
      <details className="msa-breakdown"><summary>查看基准分项与适用范围</summary><p>{row.scope}</p><ul>{Object.entries(row.components).map(([label, value]) => <li key={label}><span>{label}</span><b>{value}</b></li>)}</ul><p>有效分项等权平均；AI 调整最多 ±{reading?.adjustmentLimit ?? 15} 分。评分用于结构比较。</p></details>{evidence(row.factIds)}
    </article>)}</div>
    {!!reading?.previousScenarios?.length && <section className="msa-review"><header><h3>上一交易日的条件，今天出现了吗</h3><p>程序核对已保存的方向条件；单项出现不等于整个情景成立。</p></header>{reading.previousScenarios.map(item => <article key={item.title}><h4>{item.sourceDate} · {item.title}</h4><div><span>确认条件 · {conditionLabels[item.confirmation]}</span><p>{item.confirmationDetail}</p></div><div><span>否定条件 · {conditionLabels[item.invalidation]}</span><p>{item.invalidationDetail}</p></div></article>)}</section>}
    {reading && !reading.previousScenarios?.length && <p className="msa-review-empty">上一交易日尚无在当时生成的有效情景，后续交易日将自动核对已保存的条件。</p>}
    {!!report?.scenarios?.length && <section className="msa-scenarios"><header><h3>下一交易日，重点验证什么</h3><p>按当前证据的观察顺序排列；条件相对本日截面核对。</p></header>{report.scenarios.map((item, index) => <article key={item.thesis.title}><header><span>{index === 0 ? '优先观察' : '备选路径'}</span><h4>{item.thesis.title}</h4></header>{point(item.thesis)}<div className="msa-condition-grid">{condition(item.confirmation, '支持这个情景')}{condition(item.invalidation, '削弱这个情景')}</div></article>)}</section>}
    <details className="msa-legacy" open={!scores.length}><summary>规则轨迹、参考情景与股票发现</summary><MarketTransitionPanel decision={decision} onOpenStockDiscovery={onOpenStockDiscovery} hideAssessment={scores.length > 0} hideScenarios={Boolean(report?.scenarios?.length)} /></details>
  </section>;
}
