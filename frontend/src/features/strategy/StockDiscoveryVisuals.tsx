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

function extent(values: number[]) {
  const minimum = Math.min(...values);
  const maximum = Math.max(...values);
  const padding = Math.max((maximum - minimum) * .12, .01);
  return [minimum - padding, maximum + padding] as const;
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
  const [xMin, xMax] = extent(drawdowns);
  const [yMin, yMax] = extent(sharpes);
  const x = (value: number) => 46 + (value - xMin) / Math.max(.0001, xMax - xMin) * 636;
  const y = (value: number) => 224 - (value - yMin) / Math.max(.0001, yMax - yMin) * 190;
  return <section className="discovery-visual-card risk-return-map">
    <header><div><span>RISK / RETURN FIELD</span><h4>深度候选风险收益分布</h4></div><p><b>全部深度候选 {evidence.length} 只</b><strong>最终入选 {finalCodes.size} 只</strong></p></header>
    <svg viewBox="0 0 720 270" role="img" aria-label={`深度候选风险收益分布，共 ${evidence.length} 只，横轴为最大回撤绝对值，纵轴为风险调整收益，气泡大小为上涨概率`}>
      <path d="M46 34V224H682M46 81.5H682M46 129H682M46 176.5H682" />
      {evidence.map(item => {
        const isFinal = finalCodes.has(item.code);
        const name = candidateByCode.get(item.code)?.name ?? item.code;
        const cx = x(Math.abs(item.max_drawdown));
        const cy = y(item.risk_adjusted_return);
        return <g key={item.code} data-final={isFinal || undefined}>
          <circle cx={cx} cy={cy} r={5 + item.calibrated_probability * 8} />
          <title>{`${name}：上涨概率 ${pct(item.calibrated_probability)}，最大回撤 ${pct(Math.abs(item.max_drawdown))}，风险调整收益 ${item.risk_adjusted_return.toFixed(2)}`}</title>
          {isFinal && <text x={Math.min(635, cx + 12)} y={Math.max(24, cy - 10)}>{name}</text>}
        </g>;
      })}
      <text className="risk-axis-label" x="364" y="262">最大回撤绝对值 →</text>
      <text className="risk-axis-label" x="10" y="129" transform="rotate(-90 10 129)">风险调整收益 →</text>
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
    return <p className="quant-visual-empty discovery-visual-empty">没有最终候选，因此不生成因子对比。</p>;
  }
  const candidateByCode = new Map(candidates.map(item => [item.code, item]));
  const rows = evidence.map(item => ({ evidence: item, candidate: candidateByCode.get(item.code) }));
  return <section className="discovery-visual-card candidate-factor-matrix">
    <header><div><span>RELATIVE FACTOR TAPE</span><h4>最终候选因子对比</h4></div><small>当前前五内部相对强弱 · 波动率越低越优</small></header>
    <div><table aria-label="最终候选因子对比"><thead><tr><th>候选</th>{factorColumns.map(item => <th key={item.key}>{item.label}</th>)}</tr></thead>
      <tbody>{rows.map(({ evidence: item, candidate }) => <tr key={item.code}><th><b>#{item.final_rank ?? '—'}</b><span>{candidate?.name ?? item.code}</span><small>{item.code}</small></th>{factorColumns.map(factor => {
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
    <footer>“强/中/弱”只表示本批最终候选之间的相对位置，不代表跨批次绝对评分。</footer>
  </section>;
}
