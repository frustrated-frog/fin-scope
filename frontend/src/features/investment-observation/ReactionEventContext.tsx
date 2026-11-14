import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { ReactionComparisonGroup, ReactionHistoryComparison, ReactionSample, ReactionSource } from './reactionTypes';
import { dateTime, signed, sourceHref, subtypeLabels } from './reactionTypes';
import { ReactionChart } from './ReactionChart';
import './reactionEventContext.css';

const colors = ['var(--reaction-ink)', 'var(--reaction-teal)', '#966f38', '#796393'];
export function ReactionEventContext({ sample, onOpen }: { sample: ReactionSample; onOpen: (id: number) => void }) {
  const [peers, setPeers] = useState<ReactionSample[]>([]);
  const [sources, setSources] = useState<ReactionSource[]>([]);
  const [history, setHistory] = useState<ReactionHistoryComparison>();
  const [sessions, setSessions] = useState(5);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const subtype = sample.caseSubtype || sample.eventSubtype || 'UNCLASSIFIED';
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setHistory(undefined);
    setPeers([]);
    setSources([]);
    void Promise.all([
      api<ReactionSample[]>(`/api/investment-reactions/${sample.id}/peers`),
      api<ReactionSource[]>(`/api/investment-reactions/${sample.id}/sources`),
      api<ReactionHistoryComparison>(`/api/investment-reactions/${sample.id}/comparables?sessions=${sessions}`)
    ]).then(([nextPeers, nextSources, nextHistory]) => {
      if (active) {
        setPeers(nextPeers);
        setSources(nextSources);
        setHistory(nextHistory);
      }
    }).catch(reason => {
      if (active) {
        setError(reason instanceof Error ? reason.message : '事件对照读取失败');
      }
    }).finally(() => {
      if (active) {
        setLoading(false);
      }
    });
    return () => { active = false; };
  }, [sample.id, sample.revision, sessions]);
  return <section className="reaction-context" aria-label="事件来源与历史对照">
    <div className="reaction-context-intro"><span>历史可比样本</span><h4>{sample.instrumentName || sample.instrumentCode} · {subtypeLabels[subtype] || '未分类事件'}</h4><p>{sample.title}</p></div>
    {loading && <p role="status">正在检索事件来源与完整历史样本…</p>}
    {error && <p role="alert" className="reaction-warning">{error}</p>}
    {peers.length > 1 && <section><h5>同一事件，不同股票</h5>
      <ReactionChart relative series={peers.filter(value => value.calculation && !value.excluded).map((value, index) => ({ label: value.instrumentName || value.instrumentCode,
        metric: 'relativeReturnPp', points: value.calculation!.points, color: colors[index % colors.length] }))} />
      <p className="reaction-note">同一时间轴上的相对基准表现（百分点）；这些股票属于同一事件，不算多次独立事件。</p>
      <ul>{peers.map(peer => <li key={peer.id}><button onClick={() => onOpen(peer.id)}>{peer.instrumentName || peer.instrumentCode || '待补全'}{peer.excluded ? '（已排除）' : ''}</button> · {peer.relationNote || '标题直接提及的事件主体'}</li>)}</ul>
    </section>}
    {!!history?.relatedStages?.length && (
      <details open={sample.eventType === 'REPURCHASE' ? true : undefined}>
        <summary>{sample.eventType === 'REPURCHASE' ? '同一回购事项的进展' : '同一事项的其他阶段'} · {history.relatedStages.length} 件</summary>
        <ul>{history.relatedStages.map(stage => (
          <li key={stage.sampleId}>
            <small>{subtypeLabels[stage.subtype]} · {dateTime(stage.publishedAt)} </small>
            <button onClick={() => onOpen(stage.sampleId)}>{stage.title}</button>
          </li>
        ))}</ul>
      </details>
    )}
    <header className="reaction-history-heading"><h5>自动历史对照</h5><label>比较窗口<select value={sessions} onChange={event => setSessions(Number(event.target.value))}>{[1, 3, 5].map(value => <option key={value} value={value}>事件后 {value} 日</option>)}</select></label></header>
    <p className="reaction-history-lead">按事件子类、公开时段与事前表现匹配，查看最近四例及完整样本分布。</p>
    {['UNCLASSIFIED', 'OPERATING_UPDATE'].includes(subtype) && <p className="reaction-note">此样本尚无可靠事件子类，暂不纳入自动同类统计。</p>}
    {history?.sameCompany && <div className="reaction-history-groups"><HistoryGroup label="同一公司" group={history.sameCompany} onOpen={onOpen} /><HistoryGroup label="其他公司" group={history.otherCompanies} onOpen={onOpen} /></div>}
    <details className="reaction-comparison-method"><summary>匹配口径与样本覆盖</summary>
    <p className="reaction-note">检索数据库全部历史样本，分组使用事件子类、公开时段与事前表现；事后收益只用于描述。事前相对表现按 ≥ 2pp、≤ −2pp 和两者之间分组；公开时段区分盘前、盘中、盘后与休市日。最新四例按事件时间选取。</p>
      <div className="reaction-method-grid">{history && ([['同一公司', history.sameCompany], ['其他公司', history.otherCompanies]] as const).map(([label, group]) => group && <div key={label}><strong>{label}</strong><p>{group.criteria}</p><p>基础候选 {group.baseCandidateCount ?? group.sampleCount} · 无法判断事前可比性 {group.unknownComparabilityCount ?? 0}</p></div>)}</div>
      <p className="reaction-note">分布按股票样本计算；同事件股票存在关联，不代表独立证据或未来概率。</p>
    </details>
    <details><summary>来源与识别依据 · {sources.length} 条报道（最多展示 100 条）</summary>
      <p>{sample.fact || sample.title}</p><p className="reaction-note">案例分类：{subtypeLabels[subtype] || '未分类'}</p>
      <p className="reaction-note">同一披露的多条报道可在这里核对。合并案例采用最早收录的报道时间。</p>
      {sources.map(source => <p key={`${source.originType}:${source.originKey}`}>{sourceHref(source.url) ? <a href={sourceHref(source.url)} target="_blank" rel="noreferrer">{source.title}</a> : source.title}<small> · 公开 {dateTime(source.publishedAt)} · 捕获 {dateTime(source.capturedAt)}</small></p>)}
    </details>

  </section>;
}

function HistoryGroup({ label, group, onOpen }: { label: string; group: ReactionComparisonGroup; onOpen: (id: number) => void }) {
  const hasData = group.completeCount > 0;
  return <article className="reaction-history-group" data-empty={!hasData}>
    <header className="reaction-history-group-head"><h5>{label}</h5><span>{group.eventCount} 个事件 / {group.sampleCount} 个股票样本</span></header>
    {hasData ? <>
      <div className="reaction-comparison-stats">
        <div><span>相对收益中位数</span><strong>{signed(group.median, ' pp')}</strong></div>
        <div><span>中间 50% 区间</span><strong>{signed(group.lowerQuartile)}<small> 至 </small>{signed(group.upperQuartile, ' pp')}</strong></div>
        <div className="reaction-comparison-coverage"><span>样本覆盖</span><p>完整 {group.completeCount} · 未到期 {group.notDueCount} · 缺失或停牌 {group.missingCount}</p></div>
      </div>
    </> : <div className="reaction-comparison-empty"><span aria-hidden="true">—</span><div><p>尚无可计算分布的完整样本。</p><small>完整 {group.completeCount} · 未到期 {group.notDueCount} · 缺失或停牌 {group.missingCount}</small></div></div>}
    {group.cases.length > 0 && <div className="reaction-comparison-paths"><ReactionChart relative series={group.cases.map((item, index) => ({ label: `${item.instrumentName || item.instrumentCode} · ${item.publishedAt.slice(0, 10)}`,
      metric: 'relativeReturnPp', points: item.calculation.points, color: colors[index % colors.length] }))} />
      <div className="reaction-comparison-cases"><span>最近案例</span>{group.cases.map((item, index) => <button type="button" key={item.sampleId} onClick={() => onOpen(item.sampleId)}>
        <i style={{ background: colors[index % colors.length] }} aria-hidden="true" /><div><strong>{item.title}</strong><small>{dateTime(item.publishedAt)} · {item.matchReason}</small></div><span aria-hidden="true">↗</span>
      </button>)}</div></div>}
  </article>;
}
