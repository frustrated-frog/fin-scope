import { useState } from 'react';
import type { MarketBreadth } from './marketPulseTypes';
import { amount, finite, pct } from './panoramaModel';
import { deltaText, difference, directionalParticipation, indexDivergence, numberText, participationGap, shareText, validRatio } from './breadthAnalysis';

type Props = { breadth?: MarketBreadth; previous?: MarketBreadth; businessDate: string };
export function BreadthParticipation({ breadth, previous, businessDate }: Props) {
  const indices = (breadth?.indices ?? []).filter(index => !index.businessDate || index.businessDate === businessDate);
  const [choice, setChoice] = useState('000300.SH');
  const index = indices.find(item => item.code === choice) ?? indices[0];
  const indexReturn = index?.return1d;
  const median = breadth?.medianChangePct;
  const gap = difference(indexReturn, median);
  const extent = Math.max(1, Math.abs(finite(indexReturn) ? indexReturn : 0), Math.abs(finite(median) ? median : 0));
  const peopleShare = directionalParticipation(breadth);
  const pressure = breadth?.volumePressure;
  const amountGap = participationGap(breadth);
  return <div className="mbw-pair">
    <section className="mbw-panel mbw-divergence" aria-label="指数与个股温差">
      <header><div><span>价格与体感</span><h3>指数与个股温差</h3></div><select aria-label="宽度对照指数" value={index?.code ?? ''} onChange={event => setChoice(event.target.value)} disabled={!indices.length}>
        {!indices.length && <option value="">暂无指数</option>}{indices.map(item => <option key={item.code} value={item.code}>{item.name}</option>)}
      </select></header>
      <div className="mbw-diagnosis"><strong>{indexDivergence(indexReturn, median)}</strong><span>指数 − 个股中位数 <b>{deltaText(gap, '个百分点', 1)}</b></span></div>
      <div className="mbw-price-bars">{[{ name: index?.name ?? '指数', value: indexReturn }, { name: '个股涨跌中位数', value: median }].map(row => <div key={row.name}>
        <span>{row.name}</span><div className="mbw-zero-track"><i />{finite(row.value) && <b data-tone={row.value >= 0 ? 'up' : 'down'} style={{ left: row.value >= 0 ? '50%' : `${50 + row.value / extent * 47}%`, width: `${Math.abs(row.value) / extent * 47}%` }} />}</div><strong>{pct(row.value)}</strong>
      </div>)}</div>
      <div className="mbw-participation-counts"><span><i data-tone="up" />{numberText(breadth?.advanceCount, 0)} 上涨</span><span><i />{numberText(breadth?.flatCount, 0)} 平盘</span><span><i data-tone="down" />{numberText(breadth?.declineCount, 0)} 下跌</span></div>
      <footer>上涨股票占全部有效行情 {shareText(breadth?.advanceRatio)} · 全市场成交 {amount(breadth?.totalAmount)}</footer>
    </section>
    <section className="mbw-panel" aria-label="参与与成交配合">
      <header><div><span>家数 × 成交</span><h3>参与与成交配合</h3></div><span className="mbw-unit">剔除平盘</span></header>
      <div className="mbw-diagnosis"><strong>{finite(amountGap) ? Math.abs(amountGap) < .005 ? '成交与家数占比接近' : amountGap > 0 ? '上涨成交占比高于家数占比' : '上涨成交占比低于家数占比' : '等待参与和成交数据'}</strong><span>成交参与差 <b>{deltaText(amountGap)}</b></span></div>
      <div className="mbw-share-rows">{[
        { name: '上涨家数占比', value: peopleShare, prior: directionalParticipation(previous) },
        { name: '上涨成交额占比', value: pressure?.advanceAmountRatio, prior: previous?.volumePressure?.advanceAmountRatio },
      ].map(row => <div key={row.name}><div><span>{row.name}</span><strong>{shareText(row.value)}</strong></div><div className="mbw-share-track"><i className="mbw-midpoint" />{validRatio(row.value) && <b style={{ width: `${row.value * 100}%` }} />}{validRatio(row.prior) && <i className="mbw-prior-tick" style={{ left: `${row.prior * 100}%` }} title={`对照 ${shareText(row.prior)}`} />}</div></div>)}</div>
      <div className="mbw-inline-stats"><span>上涨成交 <b>{amount(pressure?.advanceAmount)}</b></span><span>下跌成交 <b>{amount(pressure?.declineAmount)}</b></span><span>平盘成交 <b>{amount(pressure?.flatAmount)}</b></span></div>
      <footer>两项占比均排除平盘。{previous ? '竖线为对照日位置。' : '以相同方向口径观察成交配合。'}</footer>
    </section>
  </div>;
}
