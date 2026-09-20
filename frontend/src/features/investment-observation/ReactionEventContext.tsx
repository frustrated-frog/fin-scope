import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { ReactionComparisonGroup, ReactionHistoryComparison, ReactionSample, ReactionSource } from './reactionTypes';
import { dateTime, signed, sourceHref, subtypeLabels } from './reactionTypes';
import { ReactionChart } from './ReactionChart';

const colors = ['var(--reaction-ink)', 'var(--reaction-teal)', '#966f38', '#796393'];
export function ReactionEventContext({ sample, onOpen }: { sample: ReactionSample; onOpen: (id: number) => void }) {
  const [peers, setPeers] = useState<ReactionSample[]>([]);
  const [sources, setSources] = useState<ReactionSource[]>([]);
  const [history, setHistory] = useState<ReactionHistoryComparison>();
  const [sessions, setSessions] = useState(5);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
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
        setPeers(nextPeers.filter(value => value.state !== 'DRAFT'));
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
    {loading && <p role="status">正在检索事件来源与完整历史样本…</p>}
    {error && <p role="alert" className="reaction-warning">{error}</p>}
    {peers.length > 1 && <section><h5>同一事件，不同股票</h5>
      <ReactionChart relative series={peers.filter(value => value.calculation).map((value, index) => ({ label: value.instrumentName || value.instrumentCode,
        metric: 'relativeReturnPp', points: value.calculation!.points, color: colors[index % colors.length] }))} />
      <p className="reaction-note">同一时间轴上的相对基准表现（百分点）；这些股票属于同一事件，不算多次独立事件。</p>
      <ul>{peers.map(peer => <li key={peer.id}><button onClick={() => onOpen(peer.id)}>{peer.instrumentName || peer.instrumentCode}</button> · {peer.relationNote || '标题直接提及的事件主体'}</li>)}</ul>
    </section>}
    <details><summary>来源与识别依据 · {sources.length} 条报道（最多展示 100 条）</summary>
      <p>{sample.fact || sample.title}</p><p className="reaction-note">{sample.ruleEvidence && sample.eventSubtype ? sample.ruleEvidence.replace(sample.eventSubtype, subtypeLabels[sample.eventSubtype] || sample.eventSubtype) : '历史样本未记录规则依据。'} · 子类：{subtypeLabels[sample.eventSubtype || 'UNCLASSIFIED'] || '未分类'}</p>
      <p className="reaction-note">公开时间采用来源报道的时间，不等同于公告最早发布时间。仅有明确事件标识或同日完全一致标题时自动合并报道。</p>
      {sources.map(source => <p key={`${source.originType}:${source.originKey}`}>{sourceHref(source.url) ? <a href={sourceHref(source.url)} target="_blank" rel="noreferrer">{source.title}</a> : source.title}<small> · 公开 {dateTime(source.publishedAt)} · 捕获 {dateTime(source.capturedAt)}</small></p>)}
    </details>
    <header className="reaction-history-heading"><h5>自动历史对照</h5><label>比较窗口<select value={sessions} onChange={event => setSessions(Number(event.target.value))}>{[1, 3, 5].map(value => <option key={value} value={value}>事件后 {value} 日</option>)}</select></label></header>
    <p className="reaction-note">检索数据库全部历史样本，分组使用事件子类、公开时段与事前表现；事后收益只用于描述。事前相对表现按 ≥ 2pp、≤ −2pp 和两者之间分组；公开时段区分盘前、盘中、盘后与休市日。最新四例按事件时间选取。</p>
    {(!sample.eventSubtype || sample.eventSubtype === 'UNCLASSIFIED') && <p className="reaction-note">此样本尚无可靠事件子类，暂不纳入自动同类统计。</p>}
    {history?.sameCompany && <div className="reaction-history-groups"><HistoryGroup label="同一公司" group={history.sameCompany} onOpen={onOpen} /><HistoryGroup label="其他公司" group={history.otherCompanies} onOpen={onOpen} /></div>}
  </section>;
}

function HistoryGroup({ label, group, onOpen }: { label: string; group: ReactionComparisonGroup; onOpen: (id: number) => void }) {
  return <article><h5>{label}</h5><p>{group.criteria}</p>
    <p><strong>{group.eventCount} 个事件 / {group.sampleCount} 个股票样本</strong></p>
    <p className="reaction-note">完整 {group.completeCount} · 未到期 {group.notDueCount} · 缺失或停牌 {group.missingCount}</p>
    {group.completeCount > 0 ? <p>相对收益中位数 <strong>{signed(group.median, ' pp')}</strong><br />中间 50% 区间 {signed(group.lowerQuartile)} 至 {signed(group.upperQuartile, ' pp')}</p> : <p>尚无可计算分布的完整样本。</p>}
    <p className="reaction-note">分布按股票样本计算；同事件股票存在关联，不代表独立证据或未来概率。</p>
    {group.cases.length > 0 && <><ReactionChart relative series={group.cases.map((item, index) => ({ label: `${item.instrumentName || item.instrumentCode} · ${item.publishedAt.slice(0, 10)}`,
      metric: 'relativeReturnPp', points: item.calculation.points, color: colors[index % colors.length] }))} />
      <ul>{group.cases.map(item => <li key={item.sampleId}><button onClick={() => onOpen(item.sampleId)}>{item.title}</button><small> · {dateTime(item.publishedAt)} · {item.matchReason}</small></li>)}</ul></>}
  </article>;
}
