import type {
  StockDiscoveryCandidate,
  StockDiscoveryEvidence,
  StockDiscoveryReport
} from './quantTypes';
import './QuantVisualizations.css';

const pct = (value: number, digits = 1) => `${(value * 100).toFixed(digits)}%`;
const signedPct = (value: number) => `${value >= 0 ? '+' : ''}${pct(value)}`;

export function DiscoveryFunnel({ funnel }: { funnel: StockDiscoveryReport['funnel'] }) {
  const stages = [
    ['板块成分', funnel.constituent_count],
    ['资金与质量门禁', funnel.admitted_count],
    ['轻量量化', funnel.quantified_count],
    ['深度预测', funnel.deep_review_count],
    ['最终入选', funnel.final_count]
  ] as const;
  const largest = Math.max(1, ...stages.map(([, value]) => value));
  return <div className="discovery-funnel discovery-funnel-visual" aria-label="股票发现筛选漏斗">
    {stages.map(([label, value], index) => {
      const previous = index === 0 ? value : stages[index - 1][1];
      const retention = index === 0 ? 1 : previous > 0 ? value / previous : 0;
      return <article key={label}>
        <span>0{index + 1}</span><div><strong>{value}</strong><small>{label}</small></div>
        <i aria-hidden="true"><b style={{ width: `${Math.max(value > 0 ? 4 : 0, value / largest * 100)}%` }} /></i>
        <em>{index === 0 ? '候选基数' : `${pct(retention)} 保留`}</em>
      </article>;
    })}
  </div>;
}

export function PanelCoverageMatrix({ evidence, candidates }: {
  evidence: StockDiscoveryEvidence[];
  candidates: StockDiscoveryCandidate[];
}) {
  if (!evidence.length) {
    return <p className="quant-visual-empty discovery-visual-empty">本批没有深度候选，联合模型覆盖不可用。</p>;
  }
  const candidateByCode = new Map(candidates.map(item => [item.code, item]));
  const rows = evidence.map(item => ({ item, panel: item.forecast_report?.panelModel }));
  const blended = rows.filter(({ panel }) => panel?.status === 'BLENDED').length;
  const shadow = rows.filter(({ panel }) => panel?.status === 'SHADOW').length;
  const unavailable = rows.length - blended - shadow;
  return <section className="discovery-visual-card panel-coverage-matrix">
    <header><div><span>PANEL COVERAGE / SAME ARTIFACT</span><h4>联合模型覆盖与概率增量</h4></div><p><b>联合生效 {blended}</b><strong>影子观察 {shadow}</strong><em>回退 {unavailable}</em></p></header>
    <div><table aria-label="深度候选联合模型覆盖"><thead><tr><th>深度候选</th><th>运行模式</th><th>个股概率</th><th>联合概率</th><th>最终概率</th><th>概率增量</th><th>漂移 / 覆盖</th></tr></thead><tbody>
      {rows.map(({ item, panel }) => {
        const candidate = candidateByCode.get(item.code);
        const delta = panel?.finalProbability != null && panel.individualProbability != null
          ? panel.finalProbability - panel.individualProbability : undefined;
        return <tr key={item.code} data-status={panel?.status ?? 'NOT_AVAILABLE'}><th><strong>{candidate?.name ?? item.code}</strong><small>{item.code}</small></th><td><b>{panel?.status === 'BLENDED' ? '联合生效' : panel?.status === 'SHADOW' ? '影子观察' : '个股回退'}</b><small>{panel?.mode === 'PANEL_CORE' ? '核心模型' : panel?.mode === 'PANEL_FULL' ? '完整模型' : '无产物'}</small></td><td>{panel?.individualProbability == null ? '—' : pct(panel.individualProbability)}</td><td>{panel?.panelProbability == null ? '—' : pct(panel.panelProbability)}</td><td><strong>{panel?.finalProbability == null ? pct(item.calibrated_probability) : pct(panel.finalProbability)}</strong></td><td data-positive={delta != null && delta > 0 || undefined}>{delta == null ? '—' : signedPct(delta)}</td><td><b>{panel?.driftStatus ?? 'UNAVAILABLE'}</b><small>{panel ? `${pct(panel.featureCoverage)} · ${panel.featureDistance?.toFixed(2) ?? '—'}σ` : '保持个股模型'}</small></td></tr>;
      })}
    </tbody></table></div>
    <footer>概率增量只表示联合模型对个股冠军的审慎修正；影子模型不会改变最终概率。</footer>
  </section>;
}

function extent(values: number[]) {
  const minimum = Math.min(...values);
  const maximum = Math.max(...values);
  const padding = Math.max((maximum - minimum) * .12, .01);
  return [minimum - padding, maximum + padding] as const;
}

function axisTicks(minimum: number, maximum: number, count = 5) {
  return Array.from({ length: count }, (_, index) =>
    minimum + (maximum - minimum) * index / (count - 1));
}

export function RiskReturnMap({ evidence, candidates, finalCodes }: {
  evidence: StockDiscoveryEvidence[];
  candidates: StockDiscoveryCandidate[];
  finalCodes: Set<string>;
}) {
  if (!evidence.length) {
    return <p className="quant-visual-empty discovery-visual-empty">本批没有可绘制的深度候选。</p>;
  }
  const candidateByCode = new Map(candidates.map(item => [item.code, item]));
  const drawdowns = evidence.map(item => Math.abs(item.max_drawdown));
  const sharpes = evidence.map(item => item.risk_adjusted_return);
  const xMin = 0;
  const xMax = Math.max(.01, ...drawdowns) * 1.1;
  const [yMin, yMax] = extent(sharpes);
  const plot = { left: 95, right: 570, top: 26, bottom: 365 } as const;
  const x = (value: number) => plot.left + (value - xMin) / Math.max(.0001, xMax - xMin) * (plot.right - plot.left);
  const y = (value: number) => plot.bottom - (value - yMin) / Math.max(.0001, yMax - yMin) * (plot.bottom - plot.top);
  const xTicks = axisTicks(xMin, xMax);
  const yTicks = axisTicks(yMin, yMax);
  return <section className="discovery-visual-card risk-return-map">
    <header><div><span>RISK / RETURN FIELD</span><h4>深度候选风险收益分布</h4></div><p><b>全部深度候选 {evidence.length} 只</b><strong>最终入选 {finalCodes.size} 只</strong></p></header>
    <svg viewBox="0 0 600 430" role="img" aria-label={`深度候选风险收益分布，共 ${evidence.length} 只，横轴为最大回撤绝对值，纵轴为风险调整收益，气泡大小为上涨概率`}>
      <g className="risk-grid" aria-hidden="true">
        {yTicks.map(value => <g key={`y-${value}`} data-axis="y">
          <line x1={plot.left} x2={plot.right} y1={y(value)} y2={y(value)} />
          <text className="risk-axis-tick" x={plot.left - 13} y={y(value) + 4} textAnchor="end">{value.toFixed(2)}</text>
        </g>)}
        {xTicks.map(value => <g key={`x-${value}`} data-axis="x">
          <line x1={x(value)} x2={x(value)} y1={plot.top} y2={plot.bottom} />
          <text className="risk-axis-tick" x={x(value)} y={plot.bottom + 24} textAnchor="middle">{pct(value)}</text>
        </g>)}
      </g>
      <path d={`M${plot.left} ${plot.top}V${plot.bottom}H${plot.right}`} />
      {evidence.map(item => {
        const isFinal = finalCodes.has(item.code);
        const name = candidateByCode.get(item.code)?.name ?? item.code;
        const cx = x(Math.abs(item.max_drawdown));
        const cy = y(item.risk_adjusted_return);
        return <g key={item.code} data-final={isFinal || undefined}>
          <circle cx={cx} cy={cy} r={5 + item.calibrated_probability * 8} />
          <title>{`${name}：上涨概率 ${pct(item.calibrated_probability)}，最大回撤 ${pct(Math.abs(item.max_drawdown))}，风险调整收益 ${item.risk_adjusted_return.toFixed(2)}`}</title>
          {isFinal && <text className="risk-point-label" x={Math.min(500, cx + 14)} y={Math.max(24, cy - 12)}>{name}</text>}
        </g>;
      })}
      <text className="risk-axis-label" x={(plot.left + plot.right) / 2} y="423">最大回撤绝对值 →</text>
      <text className="risk-axis-label" x="12" y={(plot.top + plot.bottom) / 2} transform={`rotate(-90 12 ${(plot.top + plot.bottom) / 2})`}>风险调整收益 →</text>
    </svg>
    <footer><span><i />深度候选</span><span><i />最终入选</span><p>越靠上代表风险调整收益越高，越靠左代表历史最大回撤越小。</p></footer>
  </section>;
}

const factorColumns = [
  { key: 'relative_momentum_20', label: '相对动量', risk: false, format: signedPct },
  { key: 'momentum_60', label: '60日动量', risk: false, format: signedPct },
  { key: 'trend_consistency', label: '趋势一致', risk: false, format: pct },
  { key: 'liquidity', label: '流动性', risk: false, format: (value: number) => value.toFixed(2) },
  { key: 'volatility_20', label: '20日波动', risk: true, format: pct },
  { key: 'drawdown_60', label: '60日回撤', risk: false, format: signedPct }
] as const;

function median(values: number[]) {
  const ordered = [...values].sort((a, b) => a - b);
  const middle = Math.floor(ordered.length / 2);
  return ordered.length % 2 ? ordered[middle] : (ordered[middle - 1] + ordered[middle]) / 2;
}

function relativeLevel(value: number, values: number[], risk: boolean) {
  const center = median(values);
  const scale = median(values.map(item => Math.abs(item - center))) * 1.4826;
  const score = scale > 1e-12 ? (value - center) / scale * (risk ? -1 : 1) : 0;
  if (score > .35) return '强';
  if (score < -.35) return '弱';
  return '中';
}

export function CandidateFactorMatrix({ evidence, candidates }: {
  evidence: StockDiscoveryEvidence[];
  candidates: StockDiscoveryCandidate[];
}) {
  if (!evidence.length) {
    return <p className="quant-visual-empty discovery-visual-empty">没有相对候选，因此不生成因子对比。</p>;
  }
  const candidateByCode = new Map(candidates.map(item => [item.code, item]));
  const rows = evidence.map(item => ({ evidence: item, candidate: candidateByCode.get(item.code) }));
  return <section className="discovery-visual-card candidate-factor-matrix">
    <header><div><span>RELATIVE FACTOR TAPE</span><h4>相对候选因子对比</h4></div><small>当前 Top 5 内部相对强弱 · 波动率越低越优</small></header>
    <div><table aria-label="相对候选因子对比"><thead><tr><th>候选</th>{factorColumns.map(item => <th key={item.key}>{item.label}</th>)}</tr></thead>
      <tbody>{rows.map(({ evidence: item, candidate }) => <tr key={item.code}><th><b>#{item.relative_rank ?? item.final_rank ?? '—'}</b><span>{candidate?.name ?? item.code}</span><small>{item.code}</small></th>{factorColumns.map(factor => {
        const value = candidate?.factors?.[factor.key];
        if (typeof value !== 'number' || !Number.isFinite(value)) {
          return <td key={factor.key} data-level="无数据"><strong>无数据</strong><small>—</small></td>;
        }
        const values = rows
          .map(row => row.candidate?.factors?.[factor.key])
          .filter((item): item is number => typeof item === 'number' && Number.isFinite(item));
        const level = relativeLevel(value, values, factor.risk);
        return <td key={factor.key} data-level={level}><strong>{level}</strong><small>{factor.format(value)}</small></td>;
      })}</tr>)}</tbody>
    </table></div>
    <footer>“强/中/弱”只表示本批相对候选之间的位置，不代表跨批次绝对评分或买入建议。</footer>
  </section>;
}
