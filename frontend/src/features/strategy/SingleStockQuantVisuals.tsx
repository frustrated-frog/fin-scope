import type { CSSProperties } from 'react';
import type { SingleStockForecast } from './quantTypes';
import './QuantVisualizations.css';

type Factor = SingleStockForecast['factorExplanations'][number];
type EquityPoint = SingleStockForecast['equityCurve'][number];
type Stability = NonNullable<SingleStockForecast['parameterStability']>;

const pct = (value: number, digits = 1) => `${(value * 100).toFixed(digits)}%`;
const signedPct = (value: number) => `${value >= 0 ? '+' : ''}${pct(value)}`;

export function FactorContributionChart({ factors }: { factors: Factor[] }) {
  if (!factors.length) {
    return <p className="quant-visual-empty">本次报告没有可展示的因子贡献。</p>;
  }
  const ordered = [...factors].sort((left, right) => Math.abs(right.contribution) - Math.abs(left.contribution));
  const scale = Math.max(...ordered.map(item => Math.abs(item.contribution)), .0001);
  return <div className="factor-contribution-chart" role="img" aria-label={`因子贡献坐标轴，共 ${ordered.length} 项，正值支持上涨概率，负值压低上涨概率`}>
    <header><span>压低上涨概率</span><b>零贡献轴</b><span>支持上涨概率</span></header>
    <div>{ordered.map(item => {
      const magnitude = Math.max(2, Math.abs(item.contribution) / scale * 48);
      return <article key={item.code} data-direction={item.contribution >= 0 ? 'positive' : 'negative'}>
        <p><strong>{item.name}</strong><small>{item.category} · {item.direction}</small></p>
        <div className="factor-contribution-track"><i /><b style={{ width: `${magnitude}%` }} /></div>
        <p><strong>{item.contribution >= 0 ? '+' : ''}{item.contribution.toFixed(3)}</strong><small>历史分位 {pct(item.historicalPercentile)}</small></p>
      </article>;
    })}</div>
  </div>;
}

function linePoints(values: number[], top: number, height: number, width = 720) {
  const minimum = Math.min(...values);
  const maximum = Math.max(...values);
  const span = Math.max(maximum - minimum, .000001);
  return values.map((value, index) => `${index / Math.max(1, values.length - 1) * width},${top + height - (value - minimum) / span * height}`).join(' ');
}

export function EquityDrawdownChart({ points, strategyReturn, benchmarkReturn, maxDrawdown,
  drawdownStart, drawdownTrough }: {
  points: EquityPoint[]; strategyReturn: number; benchmarkReturn: number; maxDrawdown: number;
  drawdownStart: string; drawdownTrough: string;
}) {
  const step = Math.max(1, Math.ceil(points.length / 240));
  const sampled = points.filter((_, index) => index % step === 0 || index === points.length - 1);
  if (sampled.length < 2) {
    return <p className="quant-visual-empty">净值序列不足，暂不绘制收益与回撤。</p>;
  }
  const strategy = linePoints(sampled.map(item => item.strategyNav), 14, 154);
  const benchmark = linePoints(sampled.map(item => item.benchmarkNav), 14, 154);
  const drawdownDepth = Math.max(...sampled.map(item => Math.abs(item.drawdown)), .0001);
  const drawdown = sampled.map((item, index) => `${index / Math.max(1, sampled.length - 1) * 720},${214 + Math.abs(item.drawdown) / drawdownDepth * 54}`).join(' ');
  const area = `0,214 ${drawdown} 720,214`;
  return <div className="equity-drawdown-chart">
    <div className="quant-visual-legend"><span data-series="strategy">策略净值</span><span data-series="benchmark">同股买入并持有</span><span data-series="drawdown">水下回撤</span><small>{sampled[0].tradeDate} — {sampled[sampled.length - 1].tradeDate}</small></div>
    <svg viewBox="0 0 720 282" role="img" aria-label={`收益与水下回撤联动图：策略累计收益 ${signedPct(strategyReturn)}，同股买入并持有 ${signedPct(benchmarkReturn)}，策略最大回撤 ${pct(maxDrawdown)}`} preserveAspectRatio="none">
      <path className="quant-grid" d="M0 52H720M0 91H720M0 130H720M0 169H720M0 214H720M0 268H720" />
      <polyline data-series="benchmark" points={benchmark} />
      <polyline data-series="strategy" points={strategy} />
      <polygon data-series="drawdown-area" points={area} />
      <polyline data-series="drawdown" points={drawdown} />
    </svg>
    <footer><span>最大回撤区间</span><strong>{drawdownStart} → {drawdownTrough}</strong><b>-{pct(maxDrawdown)}</b></footer>
  </div>;
}

export function ParameterStabilityMap({ stability }: { stability: Stability }) {
  if (!stability.scenarios.length) {
    return <p className="quant-visual-empty">没有预声明的相邻参数场景。</p>;
  }
  const thresholds = [...new Set(stability.scenarios.map(item => item.threshold))].sort((a, b) => a - b);
  const holdingDays = [...new Set(stability.scenarios.map(item => item.holdingDays))].sort((a, b) => a - b);
  const byKey = new Map(stability.scenarios.map(item => [`${item.holdingDays}-${item.threshold}`, item]));
  const largest = Math.max(...stability.scenarios.map(item => Math.abs(item.excessReturn)), .0001);
  return <div className="parameter-stability-map" role="img" aria-label={`参数稳定性面板，${holdingDays.length} 个持有期，${thresholds.length} 个概率阈值，缺少组合不插值`}>
    <header><div><span>PARAMETER SURFACE</span><strong>同股超额收益</strong></div><p><i data-tone="negative" />落后基准<i data-tone="neutral" />接近基准<i data-tone="positive" />跑赢基准</p></header>
    <div className="parameter-map-scroll">
      <div className="parameter-map-grid" style={{ gridTemplateColumns: `82px repeat(${thresholds.length}, minmax(92px, 1fr))` }}>
        <span className="parameter-map-corner">持有 / 阈值</span>{thresholds.map(value => <b key={value}>{pct(value, 0)}</b>)}
        {holdingDays.flatMap(days => [
          <b key={`label-${days}`}>{days} 日</b>,
          ...thresholds.map(threshold => {
            const scenario = byKey.get(`${days}-${threshold}`);
            if (!scenario) return <span className="parameter-map-missing" key={`${days}-${threshold}`}>—</span>;
            const intensity = Math.abs(scenario.excessReturn) / largest;
            return <article key={`${days}-${threshold}`} data-primary={scenario.primary || undefined} data-tone={scenario.excessReturn > 0 ? 'positive' : scenario.excessReturn < 0 ? 'negative' : 'neutral'} style={{ '--cell-fill': `${Math.round(6 + intensity * 18)}%` } as CSSProperties}>
              {scenario.primary && <em>主方案</em>}<strong>{signedPct(scenario.excessReturn)}</strong><small>Sharpe {scenario.sharpeRatio.toFixed(2)} · {scenario.tradeCount} 笔</small>
            </article>;
          })
        ])}
      </div>
    </div>
    <footer><strong>缺少组合不插值</strong><span>颜色仅比较预声明场景，不自动选择最高点</span></footer>
  </div>;
}
