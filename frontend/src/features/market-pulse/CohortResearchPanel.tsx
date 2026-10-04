import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import type { WatchFocus } from '../watchlist/watchFocusTypes';
import { finite, pct, ratio } from './marketResearch';
import { stockCode } from './personalMarket';
import { ResearchStockActions } from './ResearchStockActions';
import { buildCohortResearch, cohortOutcome, opportunityDescriptions, opportunityLabels, outcomeLabels, sectorConcentration } from './cohortResearch';
import type { CohortOutcome } from './cohortResearch';
import type { DailyResearch, OpportunityState, ResearchGroupCode } from './marketResearchTypes';
import './CohortResearchPanel.css';

type Props = { research: DailyResearch; onOpenStock?: (code: string) => void };
const outcomes = ['UP', 'FLAT', 'DOWN', 'MISSING'] as const;
const outcomeRules = { UP: '> +1%', FLAT: '−1% ～ +1%', DOWN: '< −1%', MISSING: '未计入比例' };

export function CohortResearchPanel({ research, onOpenStock }: Props) {
  const [groupCode, setGroupCode] = useState<ResearchGroupCode>('STRONG');
  const [opportunity, setOpportunity] = useState<OpportunityState>();
  const [outcome, setOutcome] = useState<CohortOutcome>('ALL');
  const [membersOpen, setMembersOpen] = useState(false);
  const [limit, setLimit] = useState(30);
  const [onlyWatched, setOnlyWatched] = useState(false);
  const [watched, setWatched] = useState<Set<string>>();
  const [watchError, setWatchError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!onlyWatched) {
      return;
    }
    const abort = new AbortController();
    setWatched(undefined);
    setWatchError('');
    api<WatchFocus[]>('/api/watchlist/focuses', { signal: abort.signal }).then(values => {
      if (!Array.isArray(values)) {
        throw new Error('自选列表暂不可用');
      }
      if (!abort.signal.aborted) {
        setWatched(new Set(values.filter(value => value.type === 'STOCK').map(value => stockCode(value.code))));
      }
    }).catch(cause => {
      if (!abort.signal.aborted) {
        setWatchError(cause instanceof Error ? cause.message : '自选列表暂不可用');
      }
    });
    return () => abort.abort();
  }, [onlyWatched, attempt]);
  const data = useMemo(() => buildCohortResearch(research, onlyWatched ? watched ?? new Set() : undefined), [research, onlyWatched, watched]);
  const group = data.cohorts.find(item => item.code === groupCode) ?? data.cohorts[0];
  const chosenOpportunity = data.opportunities.find(item => item.state === opportunity);
  const members = (chosenOpportunity?.members ?? group?.members ?? []).filter(stock => opportunity || outcome === 'ALL' || cohortOutcome(stock) === outcome);
  const memberTitle = opportunity ? opportunityLabels[opportunity] : `${group?.label ?? '观察组'} · ${outcomeLabels[outcome]}`;
  const scale = Math.max(1, ...data.cohorts.map(item => Math.abs(item.median ?? 0)));
  const watchPending = onlyWatched && !watched;
  function chooseGroup(code: ResearchGroupCode) {
    setGroupCode(code);
    setOpportunity(undefined);
    setOutcome('ALL');
    setLimit(30);
  }
  function chooseOutcome(value: CohortOutcome) {
    setOpportunity(undefined);
    setOutcome(value);
    setLimit(30);
    setMembersOpen(true);
  }
  function chooseOpportunity(value: OpportunityState) {
    setOpportunity(value);
    setLimit(30);
    setMembersOpen(true);
  }
  return <section className="mp-research-section mp-cohort-lab" aria-label="基础赚钱效应">
    <header className="mp-research-heading">
      <div><span>今日雷达 · 日频观察</span><h3>赚钱效应与信号跟踪</h3></div>
      <div className="mp-cohort-scope" role="group" aria-label="赚钱效应样本范围">
        <button type="button" aria-pressed={!onlyWatched} onClick={() => { setOnlyWatched(false); setLimit(30); }}>全部样本</button>
        <button type="button" aria-pressed={onlyWatched} onClick={() => { setOnlyWatched(true); setLimit(30); }}>仅看自选</button>
      </div>
    </header>
    <div className="mp-cohort-dates"><span>选组 <b>{research.selectionDate ?? '日期待补齐'}</b><i aria-hidden="true">→</i>观察 <b>{research.businessDate}</b></span><span>{onlyWatched ? '当前自选中的本地样本' : `${research.sampleCount.toLocaleString('zh-CN')} 只本地样本`}</span></div>
    {watchPending ? <div className="mp-cohort-empty" role="status">{watchError || '正在读取自选…'}{watchError && <button type="button" onClick={() => setAttempt(value => value + 1)}>重试自选加载</button>}</div> : <>
      {onlyWatched && !data.stocks.length && <p className="mp-cohort-empty">当前自选没有可用的日频样本。可切回全部样本，或在自选页补齐关注股票的行情。</p>}
      <div className="mp-cohort-comparison" aria-label="昨日分组今日表现对比">
        <div className="mp-cohort-columns" aria-hidden="true"><span>昨日分组 · 点击切换</span><span>今日收益中位数</span><span>收涨比例</span><span>跌幅 ≥3%</span></div>
        {data.cohorts.map(item => <button type="button" className="mp-cohort-row" key={item.code} aria-pressed={group?.code === item.code} onClick={() => chooseGroup(item.code)}>
          <span className="mp-cohort-identity"><strong>{item.label}</strong><small>{item.validCount} / {item.members.length} 只有效</small></span>
          <span className="mp-cohort-return"><span className="mp-cohort-return-label"><small>收益中位数</small><b className={item.median == null ? '' : item.median >= 0 ? 'is-up' : 'is-down'}>{pct(item.median)}</b></span><span className="mp-cohort-return-track" aria-hidden="true"><i style={{ left: `${item.median != null && item.median < 0 ? 50 - Math.abs(item.median) / scale * 50 : 50}%`, width: `${Math.abs(item.median ?? 0) / scale * 50}%` }} className={(item.median ?? 0) >= 0 ? 'is-up' : 'is-down'} /></span></span>
          <span className="mp-cohort-stat"><small>收涨比例</small><b>{ratio(item.advanceRatio)}</b></span>
          <span className="mp-cohort-stat"><small>跌幅 ≥3%</small><b>{ratio(item.drawdownRatio)}</b></span>
        </button>)}
        {!data.cohorts.length && <p className="mp-cohort-empty">当日观察组尚未生成。</p>}
      </div>
      <div className="mp-cohort-analysis">
        <section className="mp-cohort-continuation" aria-label="昨日信号的今日表现">
          <header><span className="mp-cohort-eyebrow">观察延续</span><h4>昨日信号，今天走得如何</h4><p>{group?.definition ?? '选组条件待补齐'}</p></header>
          <div className="mp-cohort-continuation-total"><strong>{group?.validCount ?? 0}<small>只有效</small></strong><span>入组 {group?.members.length ?? 0} 只 · 缺行情 {group?.outcomes.MISSING ?? 0} 只</span></div>
          {group?.ready ? <div className="mp-cohort-stacked" aria-label="上涨横盘回落比例">
            {outcomes.filter(value => value !== 'MISSING').map(value => <span key={value} className={`outcome-${value.toLowerCase()}`} style={{ width: `${group.outcomes[value] / group.validCount * 100}%` }} title={`${outcomeLabels[value]} ${group.outcomes[value]}只`} />)}
          </div> : <p className="mp-cohort-empty">有效样本少于5只，暂不输出统计结论；仍可查看成员。</p>}
          <div className="mp-cohort-outcomes">{outcomes.map(value => <button type="button" key={value} aria-pressed={!opportunity && outcome === value} onClick={() => chooseOutcome(value)}>
            <span><i className={`outcome-${value.toLowerCase()}`} />{outcomeLabels[value]}<b>{group?.outcomes[value] ?? 0}<small>只</small></b></span><small>{outcomeRules[value]}<em>{group?.ready && value !== 'MISSING' ? ratio(group.outcomes[value] / group.validCount) : ''}</em></small>
          </button>)}</div>
        </section>
        <section className="mp-cohort-opportunities" aria-label="今日机会结构">
          <header><span className="mp-cohort-eyebrow">发现变化</span><h4>今日机会结构</h4><p>同一批 {data.comparableCount} 只股票，对比前一交易日</p></header>
          {data.comparableCount ? <div className="mp-cohort-opportunity-grid">{data.opportunities.map(item => {
            const sector = sectorConcentration(item.members)[0];
            return <button type="button" className={`mp-cohort-opportunity state-${item.state.toLowerCase()}`} key={item.state} aria-pressed={opportunity === item.state} onClick={() => chooseOpportunity(item.state)} title={opportunityDescriptions[item.state]}>
              <span>{opportunityLabels[item.state]}<small>{item.change === 0 ? '较昨日持平' : `较昨日 ${(item.change ?? 0) > 0 ? '+' : ''}${item.change}只`}</small></span>
              <strong>{item.members.length}<small>只</small></strong><span className="mp-cohort-sector">{sector ? `${sector[0]} · ${sector[1]}只` : '暂无行业分布'}</span>
            </button>;
          })}</div> : <p className="mp-cohort-empty">暂缺连续两日的走势状态，补齐日频历史后可查看机会结构。</p>}
          <p className="mp-cohort-footnote">其他走势 {data.otherCount} 只 · 待补齐 {data.unavailableCount} 只</p>
        </section>
      </div>
      <details className="mp-cohort-members" open={membersOpen} onToggle={event => setMembersOpen(event.currentTarget.open)}>
        <summary><span>查看成员（{members.length}）</span><small>{memberTitle}</small></summary>
        {!!chosenOpportunity && <p className="mp-cohort-footnote">{opportunityDescriptions[chosenOpportunity.state]}。当前行业分布：{sectorConcentration(members).map(([name, count]) => `${name} ${count}只`).join(' · ') || '暂无行业信息'}</p>}
        {!members.length && <p className="mp-cohort-empty">当前条件下没有成员，可切换上方分组或走势类型。</p>}
        <ul className="mp-research-members">{members.slice(0, limit).map(stock => <li key={stock.instrumentCode}>
          <div><strong>{stock.instrumentName ? `${stock.instrumentName} ${stock.instrumentCode}` : stock.instrumentCode}</strong><span className={finite(stock.return1d) ? stock.return1d >= 0 ? 'is-up' : 'is-down' : ''}>{pct(stock.return1d)}</span></div>
          <small>{stock.sectorNames?.join(' / ') || '行业待补齐'} · {outcomeLabels[cohortOutcome(stock)]}</small><ResearchStockActions code={stock.instrumentCode} onOpenStock={onOpenStock} />
        </li>)}</ul>
        {members.length > limit && <button type="button" onClick={() => setLimit(value => value + 30)}>再显示30只</button>}
      </details>
      <details className="mp-cohort-method"><summary>查看分组与统计口径</summary>
        <p>分组以 {research.selectionDate ?? '前一交易日'} 的数据确定，观察 {research.businessDate} 的表现；组间允许重叠。收涨为涨幅大于0，明显回撤为跌幅至少3%；延续分布以 ±1% 区分上涨、横盘和回落，缺行情不计入比例。</p>
        <p>机会结构按同样本比较：仅纳入两日状态均可判断的股票，每只只归入一类。趋势条件为收盘高于20日均线且5日收益为正。“新转强”指相对昨日转强，“弱势修复”指未满足趋势条件但当日收涨。</p>
        <p>使用本地日频样本，不代表完整全A。行业采用当前目录归属，多重归属可重复计数；自选筛选采用当前名单。</p>
      </details>
    </>}
  </section>;
}
