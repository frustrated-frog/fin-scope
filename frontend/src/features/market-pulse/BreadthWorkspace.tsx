import type { MarketBreadth, MarketPulseWorkspace } from './marketPulseTypes';
import { amount } from './panoramaModel';
import { numberText, shareText } from './breadthAnalysis';
import { useBreadthComparison } from './useBreadthComparison';
import { BreadthParticipation } from './BreadthParticipation';
import { BreadthDistribution } from './BreadthDistribution';
import { BreadthChanges, BreadthExtremes, BreadthTrend } from './BreadthStructure';
import './BreadthWorkspace.css';

type Props = {
  workspace: MarketPulseWorkspace;
  dates: string[];
  refreshing: boolean;
  loading?: boolean;
  onLoad: (date: string) => void;
  onRefresh: () => void;
};
const momentumLabels: Record<string, string> = {
  BULLISH_THRUST: '宽度冲击', RECOVERING: '参与修复', NEUTRAL: '中性震荡',
  WEAKENING: '参与减弱', UNAVAILABLE: '暂不可用'
};

function BreadthMomentum({ breadth }: { breadth?: MarketBreadth }) {
  const momentum = breadth?.breadthMomentum;
  const metrics = [
    { name: 'McClellan Oscillator', value: numberText(momentum?.mcclellanOscillator), detail: '样本净上涨家数 EMA19 − EMA39' },
    { name: '10 日参与率 EMA', value: shareText(momentum?.breadthThrustRatio), detail: '日K样本平滑后的上涨参与程度' },
    { name: 'TRIN', value: numberText(breadth?.volumePressure?.trin, 2), detail: '上涨/下跌家数比 ÷ 成交额比' },
    { name: 'A-D Line', value: numberText(breadth?.advanceDeclineLine, 0), detail: '近60个观测日的样本净上涨累计' },
    { name: '样本净上涨家数', value: numberText(breadth?.netAdvances, 0), detail: '日K样本上涨家数 − 下跌家数' },
    { name: '净上涨成交额', value: amount(breadth?.volumePressure?.netAdvancingAmount), detail: '上涨成交额 − 下跌成交额' },
  ];
  return <section className="mbw-panel mbw-momentum" aria-label="宽度动量与累计指标">
    <header><div><span>持续性观察</span><h3>宽度动量</h3></div><span className="mbw-status">{momentumLabels[momentum?.status ?? 'UNAVAILABLE'] ?? '暂不可用'}</span></header>
    <dl>{metrics.map(metric => <div key={metric.name}><dt>{metric.name}</dt><dd>{metric.value}</dd><small>{metric.detail}</small></div>)}</dl>
    <footer>动量与累计指标按所选日展示。净上涨成交额是涨跌股票成交额之差，不代表资金净流入。</footer>
  </section>;
}

export function BreadthWorkspace({ workspace, dates, refreshing, loading, onLoad, onRefresh }: Props) {
  const comparison = useBreadthComparison(workspace, dates);
  const breadth = workspace.breadth;
  const currentDates = [...new Set([workspace.businessDate ?? '', ...dates])].filter(Boolean).sort().reverse();
  const previous = comparison.previous;
  return <div className="mbw-workspace" aria-busy={loading || false}>
    <header className="mbw-masthead">
      <div><span className="mbw-eyebrow">MARKET INTERNALS / 市场内部结构</span><h2>市场宽度</h2><p>有多少股票参与，成交是否跟上，强弱向哪里扩散。</p></div>
      <div className="mbw-date-controls"><label>历史截面<select value={workspace.businessDate ?? ''} onChange={event => onLoad(event.target.value)} disabled={loading || refreshing}>{currentDates.map(date => <option key={date} value={date}>{date}</option>)}</select></label><button type="button" aria-label="立即补刷新" disabled={refreshing || loading} onClick={onRefresh}>{refreshing ? '刷新中…' : loading ? '加载中…' : '补刷新 ↻'}</button></div>
    </header>
    <div className="mbw-comparison" aria-label="宽度对照设置">
      <label>对照日期<select value={comparison.date || 'none'} onChange={event => comparison.choose(event.target.value)}><option value="none">不对照</option>{comparison.options.map(date => <option key={date} value={date}>{date}</option>)}</select></label>
      <div className="mbw-quick-dates"><button type="button" disabled={!comparison.options.length} aria-pressed={!!comparison.date && comparison.date === comparison.options[0]} onClick={() => comparison.choose(comparison.options[0])}>上一观测日</button><button type="button" disabled={comparison.options.length < 5} aria-pressed={!!comparison.date && comparison.date === comparison.options[4]} onClick={() => comparison.choose(comparison.options[4])}>前5个观测日</button></div>
      <div className="mbw-comparison-state" role="status">{comparison.status === 'loading' ? '正在加载对照…' : comparison.status === 'error' ? <><span>对照日暂不可用</span><button type="button" onClick={comparison.retry}>重试对照</button></> : comparison.status === 'ready' ? <><i />{workspace.businessDate} <span>对照</span> {comparison.date}</> : '仅显示所选日'}</div>
    </div>
    <div className="mbw-scope"><span>有效行情 <strong>{numberText(breadth?.validCount, 0)}</strong> 只{previous && <> / 对照 <strong>{numberText(previous.validCount, 0)}</strong> 只</>}</span><span>趋势与新高新低按各自样本统计</span><span>{breadth?.sourceFamily ?? '行情来源待更新'}</span></div>
    <BreadthParticipation breadth={breadth} previous={previous} businessDate={workspace.businessDate ?? ''} />
    <div className="mbw-pair mbw-pair-structure"><BreadthDistribution breadth={breadth} previous={previous} /><BreadthTrend breadth={breadth} previous={previous} /></div>
    <div className="mbw-pair mbw-pair-structure"><BreadthExtremes breadth={breadth} previous={previous} /><BreadthChanges breadth={breadth} previous={previous} /></div>
    <BreadthMomentum breadth={breadth} />
  </div>;
}
