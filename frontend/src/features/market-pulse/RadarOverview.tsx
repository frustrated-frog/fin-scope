import { useId } from 'react';
import { finite, pct, ratio } from './marketResearch';
import type { MarketBreadth, MarketPulseWorkspace } from './marketPulseTypes';

type Props = {
  workspace: MarketPulseWorkspace;
  stage: string;
  dimensions: { name: string; value: string }[];
  dates: string[];
  refreshing: boolean;
  onLoad: (date: string) => void;
  onRefresh: () => void;
};

function ParticipationRadar({ breadth }: { breadth?: MarketBreadth }) {
  const id = useId();
  const metrics = [
    { name: '上涨家数', value: breadth?.advanceRatio, x: 180, y: 30, lx: 180, ly: 13 },
    { name: 'MA20 上方', value: breadth?.trendBreadth?.ma20Ratio, x: 280, y: 130, lx: 313, ly: 126 },
    { name: '上涨成交', value: breadth?.volumePressure?.advanceAmountRatio, x: 180, y: 230, lx: 180, ly: 256 },
    { name: 'MA60 上方', value: breadth?.trendBreadth?.ma60Ratio, x: 80, y: 130, lx: 47, ly: 126 },
  ];
  const available = metrics.map(metric => ({ ...metric, value: finite(metric.value) && metric.value >= 0 && metric.value <= 1 ? metric.value : undefined }));
  const point = (metric: typeof available[number]) => `${180 + (metric.x - 180) * (metric.value ?? 0)},${130 + (metric.y - 130) * (metric.value ?? 0)}`;
  return <figure className="mpr-participation">
    <svg viewBox="0 0 360 285" role="img" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}>
      <title id={`${id}-title`}>市场参与度雷达</title>
      <desc id={`${id}-description`}>{available.map(metric => `${metric.name} ${ratio(metric.value)}`).join('；')}。四轴比例为0至100%，缺失指标不绘制。</desc>
      <defs><linearGradient id={`${id}-fill`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="var(--mpr-accent)" stopOpacity=".38" /><stop offset="1" stopColor="var(--mpr-violet)" stopOpacity=".1" /></linearGradient></defs>
      <g className="mpr-grid" fill="none">
        {[.25, .5, .75, 1].map(scale => <path key={scale} d={`M180,${130 - 100 * scale} L${180 + 100 * scale},130 L180,${130 + 100 * scale} L${180 - 100 * scale},130 Z`} />)}
        <path d="M180 30V230M80 130H280" />
        <circle cx="180" cy="130" r="111" strokeDasharray="2 9" />
      </g>
      <g className="mpr-scan" aria-hidden="true"><path d="M180 130L180 19" /><circle cx="180" cy="19" r="3" /></g>
      {available.every(metric => metric.value != null) && <polygon className="mpr-shape" points={available.map(point).join(' ')} fill={`url(#${id}-fill)`} />}
      {available.filter(metric => metric.value != null).map(metric => <circle className="mpr-point" key={metric.name} cx={180 + (metric.x - 180) * metric.value!} cy={130 + (metric.y - 130) * metric.value!} r="4" />)}
      <text className="mpr-scale-label" x="186" y="107">25%</text><text className="mpr-scale-label" x="186" y="57">75%</text>
      {available.map(metric => <text key={metric.name} textAnchor="middle" x={metric.lx} y={metric.ly} className="mpr-axis-label">{metric.name}<tspan x={metric.lx} dy="17" className="mpr-axis-value">{ratio(metric.value)}</tspan></text>)}
    </svg>
    <figcaption><i />市场参与度 <span>四轴均为比例 · 0–100%</span></figcaption>
  </figure>;
}

export function RadarOverview({ workspace, stage, dimensions, dates, refreshing, onLoad, onRefresh }: Props) {
  const confidence = workspace.regime?.confidenceScore;
  const breadth = workspace.breadth;
  const amount = breadth?.totalAmount;
  return <header className="mpr-overview">
    <div className="mpr-overview-toolbar">
      <div className="mpr-wordmark"><span className="mpr-mark" aria-hidden="true"><i /><i /><i /></span><div><span>MARKET PULSE</span><h2>今日雷达</h2></div></div>
      <div className="mpr-date-controls">
        <label><span>历史截面</span><select value={workspace.businessDate ?? ''} onChange={event => onLoad(event.target.value)}>
          {!dates.length && <option value={workspace.businessDate}>{workspace.businessDate}</option>}
          {dates.map(date => <option key={date} value={date}>{date}</option>)}
        </select></label>
        <button type="button" className="mpr-refresh" aria-label="立即补刷新" disabled={refreshing} onClick={onRefresh}>
          <svg className={refreshing ? 'is-refreshing' : ''} viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M5.2 8a7 7 0 0 1 11.5-2L20 12M4 12l3.3 6A7 7 0 0 0 18.8 16" /></svg>{refreshing ? '正在更新' : '更新截面'}
        </button>
      </div>
    </div>
    <div className="mpr-overview-body">
      <div className="mpr-thesis">
        <div className="mpr-thesis-meta"><span><i />市场状态</span><span>{workspace.qualityStatus === 'READY' ? '数据就绪' : workspace.qualityStatus === 'STALE' ? '沿用历史数据' : '部分数据可用'}</span></div>
        <h3>{stage}<span className="mpr-title-dot" aria-hidden="true">.</span></h3>
        <p>{workspace.regime?.explanation ?? '正在等待足够行情数据形成判断。'}</p>
        <div className="mpr-dimensions" aria-label="市场状态维度">{dimensions.map(dimension => <div key={dimension.name}><span>{dimension.name}</span><strong>{dimension.value}</strong></div>)}</div>
        <div className="mpr-confidence"><span>判断置信度</span><div aria-hidden="true"><i style={{ transform: `scaleX(${finite(confidence) ? Math.max(0, Math.min(100, confidence)) / 100 : 0})` }} /></div><strong>{finite(confidence) ? confidence : '—'}<small>/100</small></strong></div>
      </div>
      <ParticipationRadar breadth={breadth} />
    </div>
    <div className="mpr-overview-bottom">
      <div className="mpr-key-metrics"><div><span>全市场成交额</span><strong>{finite(amount) ? (amount / 1e12).toFixed(2) : '—'}<small>万亿</small></strong></div><div><span>涨跌中位数</span><strong className={finite(breadth?.medianChangePct) ? breadth.medianChangePct >= 0 ? 'mpr-up' : 'mpr-down' : ''}>{pct(breadth?.medianChangePct)}</strong></div><div><span>上涨 / 下跌家数</span><strong><b className="mpr-up">{breadth?.advanceCount?.toLocaleString('zh-CN') ?? '—'}</b><small>/</small><b className="mpr-down">{breadth?.declineCount?.toLocaleString('zh-CN') ?? '—'}</b></strong></div></div>
      <div className="mpr-schedule" aria-label="自动刷新计划"><span><i />交易日 15:30</span><small>错过后每小时补跑</small></div>
    </div>
  </header>;
}
