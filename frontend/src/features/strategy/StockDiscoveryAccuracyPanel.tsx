import type { StockDiscoveryAccuracyReport } from './quantTypes';
import './StockDiscoveryAccuracyPanel.css';

const pct = (value?: number, digits = 1) => value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
const signedPct = (value?: number, digits = 1) => value == null
  ? '—'
  : `${value >= 0 ? '+' : ''}${(value * 100).toFixed(digits)}%`;
const number = (value?: number, digits = 3) => value == null ? '—' : value.toFixed(digits);

const statusCopy: Record<StockDiscoveryAccuracyReport['status'], string> = {
  ACCUMULATING: '证据积累中',
  HEALTHY: '真实表现健康',
  WATCH: '需要观察'
};

function CalibrationChart({ report }: { report: StockDiscoveryAccuracyReport }) {
  const plot = { left: 72, right: 696, top: 26, bottom: 270 };
  const x = (value: number) => plot.left + value * (plot.right - plot.left);
  const y = (value: number) => plot.bottom - value * (plot.bottom - plot.top);
  const ticks = [0, .25, .5, .75, 1];
  const points = report.reliabilityBins.filter(item => item.count > 0
    && item.meanProbability != null && item.observedUpRate != null);
  return <figure className="discovery-calibration">
    <figcaption><div><span>PROBABILITY CALIBRATION</span><h5>预测概率有没有说真话</h5></div><p>圆点越贴近斜线，预测概率与真实上涨频率越一致。</p></figcaption>
    <svg viewBox="0 0 720 322" role="img" aria-label={`股票发现真实概率校准图，共 ${report.probabilityQuality.sampleCount} 个概率样本`}>
      <g className="discovery-calibration-grid" aria-hidden="true">
        {ticks.map(tick => <g key={`x-${tick}`}><line x1={x(tick)} x2={x(tick)} y1={plot.top} y2={plot.bottom} /><text x={x(tick)} y="296" textAnchor="middle">{pct(tick, 0)}</text></g>)}
        {ticks.map(tick => <g key={`y-${tick}`}><line x1={plot.left} x2={plot.right} y1={y(tick)} y2={y(tick)} /><text x="58" y={y(tick) + 5} textAnchor="end">{pct(tick, 0)}</text></g>)}
      </g>
      <path className="discovery-calibration-axis" d={`M${plot.left} ${plot.top}V${plot.bottom}H${plot.right}`} />
      <line className="discovery-calibration-truth" x1={x(0)} y1={y(0)} x2={x(1)} y2={y(1)} />
      {points.map(item => <g key={`${item.lowerBound}-${item.upperBound}`}>
        <circle cx={x(item.meanProbability!)} cy={y(item.observedUpRate!)} r={Math.min(18, 6 + Math.sqrt(item.count) * 2.5)} />
        <title>{`${pct(item.lowerBound, 0)}–${pct(item.upperBound, 0)}：平均预测 ${pct(item.meanProbability)}，实际上涨 ${pct(item.observedUpRate)}，${item.count} 个样本`}</title>
      </g>)}
      {!points.length && <text className="discovery-calibration-empty" x="384" y="154" textAnchor="middle">等待第一批 5 日预测到期</text>}
      <text className="discovery-calibration-label" x="384" y="320" textAnchor="middle">模型给出的上涨概率 →</text>
      <text className="discovery-calibration-label" x="16" y="148" transform="rotate(-90 16 148)" textAnchor="middle">真实上涨频率 →</text>
    </svg>
  </figure>;
}

function SelectionLedger({ report }: { report: StockDiscoveryAccuracyReport }) {
  return <section className="discovery-selection-ledger">
    <header><span>SELECTION EDGE</span><h5>前五是否真的优于候选池</h5></header>
    <div>{report.selectionMetrics.map(metric => <article key={metric.limit}>
      <div><b>TOP {metric.limit}</b><small>{metric.maturedRunCount} 个到期批次 · {metric.sampleCount} 只</small></div>
      <dl><div><dt>命中率</dt><dd>{pct(metric.hitRate)}</dd></div><div><dt>净收益</dt><dd data-positive={metric.averageNetReturn != null && metric.averageNetReturn > 0 || undefined}>{signedPct(metric.averageNetReturn)}</dd></div><div><dt>相对候选池</dt><dd data-positive={metric.averageExcessVsAdmittedPool != null && metric.averageExcessVsAdmittedPool > 0 || undefined}>{signedPct(metric.averageExcessVsAdmittedPool)}</dd></div></dl>
      <i aria-hidden="true"><b style={{ width: `${Math.max(0, Math.min(100, (metric.hitRate ?? 0) * 100))}%` }} /></i>
    </article>)}</div>
    <footer>候选池基准是同一批所有通过资金与质量门禁的股票，不是沪深 300。</footer>
  </section>;
}

export function StockDiscoveryAccuracyPanel({ report }: { report: StockDiscoveryAccuracyReport }) {
  const quality = report.probabilityQuality;
  const topOne = report.selectionMetrics.find(item => item.limit === 1);
  const champion = report.modelRace.candidates.find(item => item.role === 'CHAMPION');
  return <section className="discovery-accuracy" data-status={report.status} aria-label="股票发现真实预测验收台">
    <header className="discovery-accuracy-head">
      <div><span>FORWARD OUTCOME / NO LOOK-AHEAD</span><h4>真实预测验收台</h4><p>{report.conclusion}</p></div>
      <div className="discovery-maturity-rail"><article><strong>{report.maturedRunCount}</strong><span>到期批次</span></article><i /><article><strong>{report.maturedFinalCount}</strong><span>到期入选</span></article><i /><article data-pending="true"><strong>{report.pendingCount}</strong><span>等待到期</span></article></div>
      <aside><i /><b>{statusCopy[report.status]}</b><small>截至 {report.asOfDate} · {report.horizonDays} 日持有期</small></aside>
    </header>

    <div className="discovery-accuracy-scoreboard">
      <article><span>真实方向命中率</span><strong>{pct(quality.accuracy)}</strong><small>{quality.sampleCount} 个带概率样本</small></article>
      <article><span>Brier 技能分</span><strong data-positive={quality.brierSkillScore != null && quality.brierSkillScore > 0 || undefined}>{number(quality.brierSkillScore)}</strong><small>&gt; 0 才优于常数基准</small></article>
      <article><span>概率校准误差 ECE</span><strong>{number(quality.expectedCalibrationError)}</strong><small>越接近 0 越可信</small></article>
      <article><span>TOP 1 相对候选池</span><strong data-positive={topOne?.averageExcessVsAdmittedPool != null && topOne.averageExcessVsAdmittedPool > 0 || undefined}>{signedPct(topOne?.averageExcessVsAdmittedPool)}</strong><small>相同批次、相同持有期</small></article>
    </div>

    <div className="discovery-accuracy-main"><CalibrationChart report={report} /><SelectionLedger report={report} /></div>

    <section className="discovery-window-strip" aria-label="股票发现分窗口真实表现">
      <header><span>TIME WINDOWS</span><b>不是只看一段好运气</b></header>
      {report.windows.map(window => <article key={window.windowDays}><strong>{window.windowDays}D</strong><dl><div><dt>入选命中</dt><dd>{pct(window.finalHitRate)}</dd></div><div><dt>平均净收益</dt><dd>{signedPct(window.finalAverageNetReturn)}</dd></div><div><dt>Brier 技能</dt><dd>{number(window.brierSkillScore)}</dd></div></dl><small>{window.maturedRunCount} 批 · {window.finalCount} 只入选到期</small></article>)}
    </section>

    {report.sectorPerformance.length > 0 && <section className="discovery-sector-accuracy" aria-label="股票发现热门板块真实表现"><header><span>SECTOR ATTRIBUTION</span><b>优势来自哪里</b><small>只统计最终入选的真实到期结果</small></header><div>{report.sectorPerformance.slice(0, 8).map(sector => <article key={sector.sectorName}><strong>{sector.sectorName}</strong><dl><div><dt>样本</dt><dd>{sector.sampleCount}</dd></div><div><dt>命中率</dt><dd>{pct(sector.hitRate)}</dd></div><div><dt>平均净收益</dt><dd data-positive={sector.averageNetReturn > 0 || undefined}>{signedPct(sector.averageNetReturn)}</dd></div></dl></article>)}</div></section>}

    <div className="discovery-accuracy-lower">
      <section className="discovery-model-race"><header><div><span>CHAMPION / CHALLENGER</span><h5>模型只影子赛马，不自动换帅</h5></div><b data-status={report.modelRace.status}>{report.modelRace.status.replace(/_/g, ' ')}</b></header><p>{report.modelRace.conclusion}</p>{report.modelRace.candidates.length > 0 ? <div><table aria-label="股票发现真实模型赛马"><thead><tr><th>模型</th><th>角色</th><th>样本</th><th>Brier</th><th>覆盖准确率</th><th>对冠军变化</th></tr></thead><tbody>{report.modelRace.candidates.map(model => <tr key={model.modelCode} data-eligible={model.promotionEligible || undefined}><th><strong>{model.modelName}</strong><small>{model.modelCode}</small></th><td>{model.role === 'CHAMPION' ? '冠军' : model.role === 'CHALLENGER' ? '挑战者' : '基线'}</td><td>{model.sampleCount}</td><td>{number(model.brierScore)}</td><td>{pct(model.coveredAccuracy)}<small>覆盖 {pct(model.coverage)}</small></td><td>{model.role === 'CHAMPION' ? '基准' : number(model.brierDeltaVsChampion)}</td></tr>)}</tbody></table></div> : <small className="discovery-model-empty">当前冠军 {champion?.modelName ?? '尚未形成'}；模型真实样本会随每个到期候选自动积累。</small>}</section>
      <aside className="discovery-outcome-tape"><header><span>RECENT OUTCOMES</span><b>最近到期</b></header>{report.recentOutcomes.length > 0 ? report.recentOutcomes.slice(0, 6).map(item => <article key={`${item.runId}-${item.instrumentCode}`}><i data-up={item.actualDirection === 'UP'}>{item.actualDirection === 'UP' ? '↑' : '↓'}</i><div><strong>{item.instrumentCode}</strong><small>{item.asOfDate} · TOP {item.finalRank}</small></div><b data-positive={item.actualNetReturn > 0 || undefined}>{signedPct(item.actualNetReturn)}</b></article>) : <div className="discovery-outcome-empty"><strong>样本仍在到期</strong><p>新批次从次日开盘进入观察，按第 6 个交易日开盘结算，并扣除双边成本。</p></div>}</aside>
    </div>
    <footer className="discovery-accuracy-foot"><span>ENTRY</span> T+1 开盘 <i /> <span>EXIT</span> T+6 开盘 <i /> <span>COST</span> 0.15% 往返成本 <b>只展示冻结后真实发生的结果</b></footer>
  </section>;
}
