import type { ForecastReturnDistribution } from './quantTypes';

const signedPercent = (value?: number) => value == null
  ? '—'
  : `${value >= 0 ? '+' : ''}${(value * 100).toFixed(1)}%`;
const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function ForecastReturnPrism({ distribution }: { distribution: ForecastReturnDistribution }) {
  if (distribution.status !== 'AVAILABLE' || distribution.p10 == null
      || distribution.p50 == null || distribution.p90 == null) {
    return <section className="forecast-return-prism forecast-return-prism-empty">
      <div><span>RETURN DISTRIBUTION / LOCKED</span><h4>概率—收益棱镜</h4></div>
      <p>{distribution.reason || '收益分布的独立校准样本仍不足。'}</p>
    </section>;
  }

  const values = [distribution.p10, distribution.p50, distribution.p90, 0];
  const low = Math.min(...values);
  const high = Math.max(...values);
  const padding = Math.max((high - low) * .14, .005);
  const domainLow = low - padding;
  const domainHigh = high + padding;
  const position = (value: number) => `${((value - domainLow) / (domainHigh - domainLow)) * 100}%`;

  return <section className="forecast-return-prism" aria-label="锁定样本收益分布">
    <header><div><span>RETURN DISTRIBUTION / LOCKED</span><h4>概率—收益棱镜</h4></div><p>不是单点承诺：P10 到 P90 展示模型认为较常见的净收益范围，零轴用于识别下行跨度。</p></header>
    <div className="forecast-prism-plot">
      <div className="forecast-prism-domain"><span>{signedPercent(domainLow)}</span><b>未来 {distribution.horizonDays} 个交易日净收益</b><span>{signedPercent(domainHigh)}</span></div>
      <div className="forecast-prism-axis">
        <i className="forecast-prism-zero" style={{ left: position(0) }}><small>0</small></i>
        <em style={{ left: position(distribution.p10), width: `${((distribution.p90 - distribution.p10) / (domainHigh - domainLow)) * 100}%` }} />
        {[['P10', distribution.p10], ['P50', distribution.p50], ['P90', distribution.p90]].map(([label, value]) =>
          <b key={label as string} data-quantile={label} style={{ left: position(value as number) }}><small>{label}</small><strong>{signedPercent(value as number)}</strong></b>)}
      </div>
    </div>
    <footer><article><span>Conformal 锁定覆盖</span><strong>{percent(distribution.lockedCoverage)}</strong><small>{distribution.lockedCount} 个从未参与训练或校准的样本</small></article><article><span>平均区间宽度</span><strong>{percent(distribution.meanIntervalWidth)}</strong><small>越窄越精确，但必须结合覆盖率看</small></article><article><span>Pinball loss</span><strong>{distribution.lockedPinballLoss?.toFixed(3) ?? '—'}</strong><small>分位数预测误差，越低越好</small></article><article><span>Conformal 扩张</span><strong>{signedPercent(distribution.conformalRadius)}</strong><small>由独立校准段修正原始区间</small></article></footer>
  </section>;
}
