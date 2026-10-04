import { useState } from 'react';
import type { MarketBreadth } from './marketPulseTypes';
import { finite } from './panoramaModel';
import { bucketRatio, deltaText, difference, numberText, shareText, tailShare, validCount } from './breadthAnalysis';

type Props = { breadth?: MarketBreadth; previous?: MarketBreadth };
export function BreadthDistribution({ breadth, previous }: Props) {
  const [mode, setMode] = useState<'ratio' | 'count'>('ratio');
  const [choice, setChoice] = useState('DOWN_3_7');
  const rows = (breadth?.returnDistribution ?? []).map(bucket => {
    const prior = previous?.returnDistribution?.find(item => item.code === bucket.code);
    const currentValue = mode === 'ratio' ? bucketRatio(bucket, breadth) : validCount(bucket.count) ? bucket.count : undefined;
    const priorValue = mode === 'ratio' ? bucketRatio(prior, previous) : validCount(prior?.count) ? prior.count : undefined;
    return { bucket, prior, currentValue, priorValue };
  });
  const selected = rows.find(row => row.bucket.code === choice) ?? rows[0];
  const maximum = Math.max(mode === 'ratio' ? .1 : 1, ...rows.flatMap(row => [row.currentValue, row.priorValue].filter(finite)));
  return <section className="mbw-panel mbw-distribution" aria-label="涨跌幅分布分析">
    <header><div><span>强弱分布</span><h3>涨跌幅分布</h3></div><div className="mbw-segment" role="group" aria-label="分布显示方式"><button type="button" aria-pressed={mode === 'ratio'} onClick={() => setMode('ratio')}>占比</button><button type="button" aria-pressed={mode === 'count'} onClick={() => setMode('count')}>家数</button></div></header>
    <div className="mbw-distribution-summary"><span>涨幅 ≥3% <strong data-tone="up">{shareText(tailShare(breadth, 'UP'))}</strong></span><span>跌幅 ≥3% <strong data-tone="down">{shareText(tailShare(breadth, 'DOWN'))}</strong></span><span>涨停 / 跌停 <strong>{numberText(breadth?.limitUpCount, 0)} / {numberText(breadth?.limitDownCount, 0)}</strong></span></div>
    <div className="mbw-histogram" role="group" aria-label="选择涨跌分档">{rows.map(row => <button type="button" key={row.bucket.code} aria-label={`${row.bucket.label}，${numberText(row.bucket.count, 0)} 家`} aria-pressed={selected?.bucket.code === row.bucket.code} onClick={() => setChoice(row.bucket.code)}>
      <strong>{mode === 'ratio' ? shareText(row.currentValue) : numberText(row.currentValue, 0)}</strong><span className="mbw-histogram-bar">{finite(row.priorValue) && <i style={{ height: `${row.priorValue / maximum * 100}%` }} title={`对照 ${mode === 'ratio' ? shareText(row.priorValue) : numberText(row.priorValue, 0)}`} />}{finite(row.currentValue) && <b data-tone={row.bucket.code.startsWith('UP') ? 'up' : row.bucket.code.startsWith('DOWN') ? 'down' : 'flat'} style={{ height: `${row.currentValue / maximum * 100}%` }} />}</span><small>{row.bucket.label}</small>
    </button>)}</div>
    {!rows.length && <p className="mbw-empty">所选日暂无涨跌分档数据。</p>}
    {selected && <div className="mbw-bucket-detail" aria-live="polite"><strong>{selected.bucket.label}</strong><span>{numberText(selected.bucket.count, 0)} 家 · {shareText(bucketRatio(selected.bucket, breadth))}</span>{previous && <span>较对照 <b>{deltaText(difference(bucketRatio(selected.bucket, breadth), bucketRatio(selected.prior, previous)))}</b></span>}</div>}
    <footer>{previous ? '彩色为所选日，灰色为对照日。' : '点击分档查看数量与占比。'} 占比按各日有效行情计算。</footer>
  </section>;
}
