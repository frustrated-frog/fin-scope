import { useState } from 'react';
import { finite, pct, ratio } from './marketResearch';
import { ResearchStockActions } from './ResearchStockActions';
import type { DailyResearch, ResearchGroup, ResearchStock } from './marketResearchTypes';

function GroupCard({ group, stocks, onOpenStock }: { group: ResearchGroup; stocks: Map<string, ResearchStock>; onOpenStock?: (code: string) => void }) {
  const [limit, setLimit] = useState(30);
  const returns = group.members.map(code => stocks.get(code)?.return1d).filter(finite);
  const buckets = [
    { label: '≤ -3%', count: returns.filter(value => value <= -3).length },
    { label: '-3% ~ 0', count: returns.filter(value => value > -3 && value < 0).length },
    { label: '平盘', count: returns.filter(value => value === 0).length },
    { label: '0 ~ 3%', count: returns.filter(value => value > 0 && value < 3).length },
    { label: '≥ 3%', count: returns.filter(value => value >= 3).length }
  ];
  return <article className="mp-cohort-card"><h4>{group.label}</h4><p>{group.definition}</p>
    <dl><div><dt>收益中位数</dt><dd>{pct(group.medianReturn)}</dd></div><div><dt>上涨比例</dt><dd>{ratio(group.advanceRatio)}</dd></div></dl>
    <p className="mp-research-note">可判断 {group.eligibleCount} 只 · 入组 {group.memberCount} 只 · 今日有效 {group.validCount} 只 · 覆盖 {ratio(group.memberCount ? group.validCount / group.memberCount : undefined)}</p>
    {group.validCount < 5 && <p className="mp-research-note">有效样本少于5只，暂不输出统计结论。</p>}
    <div className="mp-return-distribution" aria-label={`${group.label}收益分布`}>{buckets.map(bucket => <div key={bucket.label}><span>{bucket.label}</span><meter min={0} max={Math.max(1, returns.length)} value={bucket.count} /><b>{bucket.count}只</b></div>)}</div>
    <details><summary>查看成员（{group.memberCount}）</summary>
      <ul className="mp-research-members">{group.members.slice(0, limit).map(code => <li key={code}><div><strong>{code}</strong><span>{pct(stocks.get(code)?.return1d)}</span></div><ResearchStockActions code={code} onOpenStock={onOpenStock} /></li>)}</ul>
      {group.members.length > limit && <button type="button" onClick={() => setLimit(limit + 30)}>再显示30只</button>}
    </details>
  </article>;
}
export function CohortResearchPanel({ research, onOpenStock }: { research: DailyResearch; onOpenStock?: (code: string) => void }) {
  const stocks = new Map(research.stocks.map(stock => [stock.instrumentCode, stock]));
  return <section className="mp-research-section" aria-label="基础赚钱效应"><header className="mp-research-heading"><div><span>观察不同走势的延续</span><h3>基础赚钱效应</h3></div><span>本地日频样本</span></header>
    <p className="mp-research-note">以 {research.selectionDate ?? '前一交易日（暂缺）'} 的数据选组，观察 {research.businessDate} 的表现。检查 {research.sampleCount} 只本地股票，不代表完整全A；组间允许重叠。缺行情成员保留，统计只计算有效收益。</p>
    <div className="mp-cohort-grid">{research.groups.map(group => <GroupCard key={group.code} group={group} stocks={stocks} onOpenStock={onOpenStock} />)}</div>
  </section>;
}
