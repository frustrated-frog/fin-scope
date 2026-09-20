import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../shared/api/client';
import type { DiscoveryStatus, EventType, ReactionCandidate, ReactionSample, SampleState } from './reactionTypes';
import { beforeEventReturn, dateTime, eventLabels, sourceHref, pathLabels, signed, statusLabels } from './reactionTypes';
import { ReactionDetail } from './ReactionDetail';
import { ReactionComparison } from './ReactionComparison';
import { ReactionRegistrationForm } from './ReactionRegistrationForm';
import { LegacyObservations } from './LegacyObservations';
import './investmentReaction.css';

type Tab = SampleState | 'ALL' | 'CANDIDATES' | 'LEGACY';
const tabs: Array<{ value: Tab; label: string }> = [
  { value: 'ALL', label: '自动观察' }, { value: 'OBSERVING', label: '反应矩阵' }, { value: 'DRAFT', label: '待关联线索' },
  { value: 'CANDIDATES', label: '手动补充' }, { value: 'ARCHIVED', label: '已归档' }, { value: 'LEGACY', label: '历史资料' }
];

export function InvestmentObservationView({ setMessage, addToast, onOpenMajorEvents, onResearch }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onOpenMajorEvents?: () => void;
  onResearch?: (question: string) => void;
}) {
  const [discovery, setDiscovery] = useState<DiscoveryStatus>();
  const [samples, setSamples] = useState<ReactionSample[]>([]);
  const [candidates, setCandidates] = useState<ReactionCandidate[]>([]);
  const [tab, setTab] = useState<Tab>('ALL');
  const [eventType, setEventType] = useState<EventType | 'ALL'>('ALL');
  const [capture, setCapture] = useState('ALL');
  const [selectedId, setSelectedId] = useState<number>();
  const [compareIds, setCompareIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [candidateError, setCandidateError] = useState('');
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [pageCursor, setPageCursor] = useState(0);
  const detailRef = useRef<HTMLElement>(null);
  const comparisonRef = useRef<HTMLDivElement>(null);
  const selected = samples.find(sample => sample.id === selectedId);
  const visible = samples.filter(sample => (tab === 'ALL' ? sample.state !== 'ARCHIVED' : sample.state === tab) && (eventType === 'ALL' || sample.eventType === eventType)
    && (capture === 'ALL' || (capture === 'BACKFILL') === sample.historicalBackfill));
  const comparisons = compareIds.flatMap(id => samples.find(sample => sample.id === id) || []);

  useEffect(() => {
    let active = true;
    void api<ReactionSample[]>('/api/investment-reactions/recent').then(result => {
      if (active) {
        setSamples(result);
        setHasMore(result.length === 100);
        setPageCursor(result.length ? result[result.length - 1].id : 0);
      }
    }).catch(reason => {
      if (active) {
        setError(message(reason));
      }
    }).finally(() => {
      if (active) {
        setLoading(false);
      }
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    let polling = false;
    async function poll() {
      if (polling) {
        return;
      }
      polling = true;
      try {
        const [status, latest] = await Promise.all([
          api<DiscoveryStatus>('/api/investment-reactions/discovery'),
          api<ReactionSample[]>('/api/investment-reactions/recent')
        ]);
        if (active) {
          setDiscovery(status);
          setHasMore(current => current || latest.length === 100);
          if (latest.length) {
            setPageCursor(current => current ? Math.min(current, latest[latest.length - 1].id) : latest[latest.length - 1].id);
          }
          setSamples(current => [...latest, ...current.filter(item => latest.length === 100
            && item.id < latest[latest.length - 1].id)]);
        }
      } catch (reason) {
        if (active) {
          setError(`自动更新读取失败：${message(reason)}`);
        }
      } finally {
        polling = false;
      }
    }
    void api<DiscoveryStatus>('/api/investment-reactions/discovery').then(status => {
      if (active) {
        setDiscovery(status);
      }
    }).catch(reason => {
      if (active) {
        setError(`自动发现状态读取失败：${message(reason)}`);
      }
    });
    const timer = window.setInterval(() => { void poll(); }, 15000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  useEffect(() => {
    if (selectedId != null) {
      detailRef.current?.focus();
    }
  }, [selectedId, tab]);

  async function loadCandidates() {
    setCandidateLoading(true);
    setCandidateError('');
    try {
      setCandidates(await api<ReactionCandidate[]>('/api/investment-reactions/candidates'));
    } catch (reason) {
      setCandidateError(message(reason));
    } finally {
      setCandidateLoading(false);
    }
  }

  function changeTab(next: Tab) {
    setTab(next);
    setSelectedId(undefined);
    if (next === 'CANDIDATES') {
      void loadCandidates();
    }
  }

  function replace(sample: ReactionSample) {
    setSamples(current => current.some(value => value.id === sample.id)
      ? current.map(value => value.id === sample.id ? sample : value) : [...current, sample]);
  }

  async function act(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (reason) {
      setError(message(reason));
      addToast(message(reason), 'error');
      if (reason instanceof ApiError && reason.status === 409 && selectedId != null) {
        try {
          replace(await api<ReactionSample>(`/api/investment-reactions/${selectedId}`));
        } catch {
          setError(`${message(reason)}；重新读取失败，请重试。`);
        }
      }
    } finally {
      setBusy(false);
    }
  }

  async function refreshSample(sample: ReactionSample) {
    const result = await api<ReactionSample>(`/api/investment-reactions/${sample.id}/refresh`, { method: 'POST' });
    replace(result);
    addToast(result.refreshError ? '暂未取得完整行情，已保留已有结果' : '市场反应已更新', result.refreshError ? 'info' : 'success');
  }

  async function register(candidate: ReactionCandidate) {
    const sample = await api<ReactionSample>('/api/investment-reactions', { method: 'POST', body: JSON.stringify({ majorEventId: candidate.majorEventId }) });
    replace(sample);
    setTab(sample.state);
    setSelectedId(sample.id);
    setEventType('ALL');
    setCapture('ALL');
    setMessage('事件已保存为待确认，请核对股票与公开时刻');
  }

  function addComparison(sample: ReactionSample) {
    if (comparisons.length && comparisons[0].eventType !== sample.eventType) {
      addToast('请选择相同事件类型；移除当前对照后可切换类型', 'info');
      return;
    }
    if (!compareIds.includes(sample.id) && compareIds.length >= 4) {
      addToast('最多同时对照 4 个样本', 'info');
      return;
    }
    setCompareIds(current => current.includes(sample.id) ? current : [...current, sample.id]);
    window.setTimeout(() => comparisonRef.current?.scrollIntoView?.({ block: 'start', behavior: 'auto' }), 0);
  }

  return <section className="reaction-workspace" aria-label="市场反应观察室">
    <header className="reaction-header"><div><p className="reaction-eyebrow">投资观察 / 事件与价格</p><h3>市场如何回应新信息</h3><p>自动发现新闻中的事件，记录相关股票在信息出现前后的走势、成交量与市场对照。</p></div>
      <button className="reaction-primary" disabled={busy} onClick={() => void act(async () => {
        setDiscovery(await api<DiscoveryStatus>('/api/investment-reactions/sync', { method: 'POST' }));
        addToast('自动发现与行情更新已提交，完成后页面会自动显示', 'info');
      })}>{busy || discovery?.running ? '自动更新中…' : '立即同步'}</button>
    </header>
    <div className="reaction-automation" role="status"><strong>{discovery?.running ? '正在发现事件并更新行情' : '自动观察已开启'}</strong><span>{discovery?.message || '正在读取后台状态'}{discovery?.lastCompletedAt && ` · 最近发现 ${dateTime(discovery.lastCompletedAt)}`}</span><small>每 5 分钟读取新闻与雷达；当前观察业绩、合同与订单。无需手动登记。</small></div>
    <nav className="reaction-tabs" aria-label="投资观察视图">{tabs.map(item => <button key={item.value} aria-pressed={tab === item.value} onClick={() => changeTab(item.value)}>{item.label}{['DRAFT', 'OBSERVING', 'ARCHIVED'].includes(item.value) && <small>{samples.filter(sample => sample.state === item.value).length}</small>}</button>)}</nav>
    {error && <div className="reaction-warning" role="alert">{error}<button disabled={busy} onClick={() => void act(async () => {
      const result = await api<ReactionSample[]>('/api/investment-reactions/recent');
      setSamples(result);
      setHasMore(result.length === 100);
      setPageCursor(result.length ? result[result.length - 1].id : 0);
    })}>重新读取</button></div>}
    {tab === 'LEGACY' ? <LegacyObservations /> : tab === 'CANDIDATES' ? <section className="reaction-candidates">
      <header><h4>从大事记登记事件</h4>{onOpenMajorEvents && <button onClick={onOpenMajorEvents}>前往大事记</button>}</header>
      <p>从最近 100 条大事记中提示业绩、合同相关线索。标题匹配仅供筛选，正式公告、公司关系与公开时刻需要核对。</p>
      {candidateError && <p role="alert">{candidateError}<button onClick={() => void loadCandidates()}>重试</button></p>}
      {candidateLoading ? <p>正在读取候选…</p> : !candidateError && candidates.length === 0 ? <p className="reaction-empty">还没有相关线索。先把业绩披露或正式合同保存到大事记，再回来登记。</p> : candidates.map(candidate => <article key={candidate.majorEventId}>
        <div><small>{eventLabels[candidate.suggestedType]} · {candidate.occurredDate || '日期待核对'}</small><h5>{candidate.title}</h5><p>{candidate.summary}</p></div>
        <button disabled={busy} onClick={() => void act(() => register(candidate))}>保存到待确认</button>
      </article>)}
    </section> : <>
      <div className="reaction-toolbar"><div><label>事件类型<select value={eventType} onChange={e => { setEventType(e.target.value as EventType | 'ALL'); setSelectedId(undefined); }}><option value="ALL">全部类型</option>{Object.entries(eventLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>登记方式<select value={capture} onChange={e => { setCapture(e.target.value); setSelectedId(undefined); }}><option value="ALL">全部样本</option><option value="CURRENT">当日登记</option><option value="BACKFILL">历史补录</option></select></label></div><p>{tab === 'DRAFT' ? '系统自动重试关联；手动补充为可选操作' : '相对沪深300 · 累计收益差 / 百分点'}</p></div>
      {loading ? <p className="reaction-empty" role="status">正在读取观察样本…</p> : visible.length === 0 ? <div className="reaction-empty"><h4>{tab === 'DRAFT' ? '没有待确认事件' : tab === 'ARCHIVED' ? '没有归档样本' : '正在等待自动发现的事件'}</h4><p>新闻同步后，系统会自动保存业绩、合同线索并跟踪相关股票。涨跌、无反应都会保留。</p></div> : (
        <div className="reaction-matrix" role="table" aria-label="事件反应矩阵">
          <div className="reaction-matrix-head" role="row"><span role="columnheader">事件 / 股票</span><span role="columnheader">事件前 5 日 · 个股</span>{[1, 3, 5].map(size => <span key={size} role="columnheader">事件后 {size} 日 · 相对</span>)}<span role="columnheader">当前路径</span></div>
          {visible.map(sample => <div className="reaction-matrix-row" role="row" key={sample.id} data-selected={sample.id === selectedId}>
            <div role="cell" className="reaction-subject"><button onClick={() => setSelectedId(sample.id)} aria-expanded={sample.id === selectedId}><strong>{sample.instrumentName || '股票待确认'}</strong><span>{sample.title}</span></button><small>{sample.publishedAt ? dateTime(sample.publishedAt) : '公开时刻待核对'}{sample.historicalBackfill && ' · 历史补录'}{sample.automatic && ' · 自动发现'}</small>{sample.discoveryIssue && <p className="reaction-note">{sample.discoveryIssue}</p>}</div>
            <div role="cell" className="reaction-value"><small>事件前 5 日</small><strong>{signed(beforeEventReturn(sample), '%')}</strong></div>
            {[1, 3, 5].map(size => {
              const window = sample.calculation?.windows.find(value => value.sessions === size);
              return <div role="cell" className="reaction-value" key={size} data-direction={window?.relativeReturnPp == null ? 'neutral' : window.relativeReturnPp > 0 ? 'up' : 'down'}>
                <small>前 {size} 日</small><strong>{sample.state === 'DRAFT' ? '待关联' : window?.status === 'READY' ? signed(window.relativeReturnPp) : window ? statusLabels[window.status] : '待更新'}</strong>
                {window && <span>{window.endDate.slice(5)}</span>}
              </div>;
            })}
            <div role="cell" className="reaction-row-state"><span>{sample.refreshError ? '更新暂不可用' : sample.calculation ? pathLabels[sample.calculation.pathType] : sample.state === 'DRAFT' ? '自动补全中' : '等待自动计算'}</span>{sample.calculation && <button onClick={() => addComparison(sample)} aria-label={`对照：${sample.title}`}>对照 +</button>}</div>
          </div>)}
        </div>
      )}
      {hasMore && <button disabled={busy} onClick={() => void act(async () => {
        const next = await api<ReactionSample[]>(`/api/investment-reactions/recent?beforeId=${pageCursor}&limit=100`);
        setSamples(current => [...current, ...next.filter(item => !current.some(value => value.id === item.id))]);
        setHasMore(next.length === 100);
        if (next.length > 0) {
          setPageCursor(next[next.length - 1].id);
        }
      })}>加载更多样本（筛选与数量仅包含已加载项）</button>}
      {selected && (tab === 'ALL' || selected.state === tab) && <section ref={detailRef} tabIndex={-1} className="reaction-selected" aria-label={`样本详情：${selected.title}`}>
        <button className="reaction-close" onClick={() => setSelectedId(undefined)}>收起详情</button>
        {selected.state === 'DRAFT' ? <><h4>{selected.title}</h4><p>{selected.summary}</p><p className="reaction-note">{selected.discoveryIssue}</p>{sourceHref(selected.sourceUrl) && <a href={sourceHref(selected.sourceUrl)} target="_blank" rel="noreferrer">查看原始来源 ↗</a>}<details open={!selected.automatic}><summary>手动补充股票（可选）</summary><ReactionRegistrationForm key={selected.id} sample={selected} busy={busy} onConfirm={body => act(async () => {
          const confirmed = await api<ReactionSample>(`/api/investment-reactions/${selected.id}/confirm`, { method: 'POST', body: JSON.stringify(body) });
          replace(confirmed);
          setTab('OBSERVING');
          setEventType('ALL');
          setCapture('ALL');
          addToast('事件快照已确认，正在获取行情', 'success');
          await refreshSample(confirmed);
        })} /></details><button disabled={busy} onClick={() => void act(async () => {
          replace(await api<ReactionSample>(`/api/investment-reactions/${selected.id}/archive`, { method: 'PATCH', body: JSON.stringify({ archived: true, revision: selected.revision }) }));
          setSelectedId(undefined);
        })}>归档待确认线索</button></> : <ReactionDetail sample={selected} samples={samples} busy={busy} onRefresh={() => void act(() => refreshSample(selected))}
          onArchive={() => void act(async () => {
            const result = await api<ReactionSample>(`/api/investment-reactions/${selected.id}/archive`, { method: 'PATCH', body: JSON.stringify({ archived: selected.state !== 'ARCHIVED', revision: selected.revision }) });
            replace(result);
            setTab(result.state);
          })} onCompare={() => addComparison(selected)} onResearch={onResearch} />}
      </section>}
    </>}
    {comparisons.length > 0 && <div ref={comparisonRef}><ReactionComparison samples={comparisons} onRemove={id => setCompareIds(current => current.filter(value => value !== id))} /></div>}
    <footer className="reaction-footer">首个反应交易日前一日收盘为起点，盘后与休市日信息顺延到下一交易日。数据缺失和未到期均不计为零。后台自动分批更新行情；每批最多 20 项，已完成样本可在详情中重新计算。</footer>
  </section>;
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : '操作失败，请稍后重试';
}
