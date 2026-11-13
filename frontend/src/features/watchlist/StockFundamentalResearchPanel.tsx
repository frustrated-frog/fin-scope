import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { StockFundamentalsPanel } from './StockFundamentalsPanel';
import { subtypeLabels } from '../investment-observation/reactionTypes';
import { dimensionLabels, emptyNotes, fundamentalSource, fundamentalValue, watchStatusLabels } from './fundamentalResearchTypes';
import type { BusinessNotes, FundamentalWatch, FundamentalWatchStatus, FundamentalWorkspace } from './fundamentalResearchTypes';
import './fundamentalResearch.css';

const noteFields = [
  { key: 'revenueModel', label: '收入从哪里来', hint: '主要产品、业务分部及收入来源' },
  { key: 'customers', label: '客户与回款', hint: '客户结构、结算方式与回款节奏' },
  { key: 'costDrivers', label: '关键成本', hint: '原材料、人工、折旧与资金占用' },
  { key: 'advantage', label: '优势及其依据', hint: '写下竞争优势，并注明支持它的材料' },
] as const;

export function StockFundamentalResearchPanel({ code }: { code: string }) {
  const [view, setView] = useState<FundamentalWorkspace>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [mode, setMode] = useState<'research' | 'financials'>('research');
  const [notes, setNotes] = useState<BusinessNotes>();
  const [draft, setDraft] = useState<FundamentalWatch>();
  const [review, setReview] = useState<{ id: number; status: FundamentalWatchStatus; note: string; revision: number }>();
  const version = useRef(0);
  const editorRef = useRef<HTMLDivElement>(null);
  const base = `/api/watchlist/stocks/${encodeURIComponent(code)}/fundamental-research`;

  async function request(path: string, method = 'GET', body?: unknown, success = '') {
    const current = ++version.current;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const result = await api<FundamentalWorkspace>(path, method === 'GET' ? undefined : {
        method, ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      if (version.current !== current) {
        return false;
      }
      setView(result);
      setMessage(success);
      return true;
    } catch (reason) {
      if (version.current === current) {
        setError(reason instanceof Error ? reason.message : '研究底稿读取失败，请重试');
      }
      return false;
    } finally {
      if (version.current === current) {
        setBusy(false);
      }
    }
  }

  useEffect(() => {
    setView(undefined);
    setNotes(undefined);
    setDraft(undefined);
    setReview(undefined);
    setMode('research');
    void request(base);
    return () => { version.current += 1; };
  }, [code]);

  function openWatch(watch?: FundamentalWatch) {
    setDraft(watch ? { ...watch } : {
      originKey: `MANUAL:${crypto.randomUUID()}`, title: '', hypothesis: '', baseline: '', supportCondition: '', challengeCondition: '',
      nextReviewDate: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10), status: 'TRACKING', revision: 0, reviews: [],
    });
    setTimeout(() => editorRef.current?.querySelector<HTMLInputElement>('input')?.focus(), 0);
  }

  const watches = view?.watches ?? [];
  const active = watches.filter(watch => watch.status !== 'CLOSED');
  const firstPriority = view?.insights?.find(insight => insight.attention)?.nextCheck
    || view?.events?.[0]?.nextCheck || '整理主营业务与财报，确定第一项需要持续核对的经营假设。';
  const reportLabel = view?.periodEnd ? `${view.periodEnd} · 合并${view.reportType === 'ANNUAL' ? '年度' : '累计'}报告 · ${view.currency}` : '尚无合并财报';

  return <div className="fundamental-research">
    <nav className="fr-view-switch" aria-label="基本面内容">
      <button type="button" aria-pressed={mode === 'research'} onClick={() => setMode('research')}>研究底稿</button>
      <button type="button" aria-pressed={mode === 'financials'} onClick={() => setMode('financials')}>季度趋势与估值</button>
    </nav>
    {mode === 'financials' ? <StockFundamentalsPanel code={code} /> : <>
      <header className="fr-heading">
        <div><p className="fr-eyebrow">COMPANY RESEARCH</p><h3>把经营变化，放回公司全貌</h3><p>{view?.summary || '整理已有公司资料、财报和公告，建立持续更新的研究底稿。'}</p></div>
        <button type="button" className="fr-primary" disabled={busy} onClick={() => void request(`${base}/refresh`, 'POST', undefined, '经营资料与财报已重新整理')}>{busy ? '正在整理…' : '更新底稿'}</button>
      </header>
      {error && <p role="alert" className="fr-feedback is-error">{error} <button type="button" disabled={busy} onClick={async () => {
        if (await request(base)) {
          setNotes(undefined);
          setReview(undefined);
        }
      }}>重新读取</button></p>}
      {message && <p role="status" className="fr-feedback">{message}</p>}
      {!view && busy && <p role="status" className="fr-empty">正在读取公司底稿…</p>}
      {view && <>
        <div className="fr-orientation">
          <div><span>当前财务基线</span><strong>{reportLabel}</strong><small>{view.sourceCode || '点击更新底稿，补齐已有财报'}</small></div>
          <div><span>已关联经营事项</span><strong>{view.events?.length ?? 0} 件</strong><small>公告与财报一起核对</small></div>
          <div><span>优先验证</span><p>{firstPriority}</p><small>{active.length} 项正在跟踪</small></div>
        </div>

        <section className="fr-section" aria-labelledby="fr-business-title">
          <header><div><span className="fr-kicker">经营底稿</span><h4 id="fr-business-title">公司怎样赚钱</h4></div><button type="button" disabled={busy} onClick={() => setNotes({ ...emptyNotes, ...view.notes })}>{notes ? '重新载入笔记' : '补充经营笔记'}</button></header>
          <div className="fr-business-layout">
            <article className="fr-company-profile">
              <span className="fr-industry">{view.profile?.industry || '公司资料'}</span>
              <h5>{view.profile?.companyName || view.name}</h5>
              {view.profile ? <>
                <p className="fr-profile-summary">{view.profile.description?.slice(0, 250)}{view.profile.description?.length > 250 ? '…' : ''}</p>
                <details><summary>经营范围与完整简介</summary><p>{view.profile.businessScope || '来源未提供经营范围'}</p><p>{view.profile.description}</p></details>
                {fundamentalSource(view.profile.sourceUrl) && <a className="fr-source" href={fundamentalSource(view.profile.sourceUrl)} target="_blank" rel="noreferrer">东方财富公司资料 ↗ · {view.profile.fetchedAt?.slice(0, 10)}</a>}
              </> : <p className="fr-empty">点击“更新底稿”读取公开公司简介与经营范围。</p>}
            </article>
            <div className="fr-business-notes">
              {noteFields.map(field => <div key={field.key}><h5>{field.label}</h5><p className={view.notes?.[field.key] ? '' : 'fr-muted'}>{view.notes?.[field.key] || field.hint}</p></div>)}
              {fundamentalSource(view.notes?.sourceUrl) && <a className="fr-source" href={fundamentalSource(view.notes.sourceUrl)} target="_blank" rel="noreferrer">经营笔记参考材料 ↗</a>}
            </div>
          </div>
          {notes && <form className="fr-editor" onSubmit={async event => {
            event.preventDefault();
            if (await request(`${base}/notes`, 'PUT', notes, '经营笔记已保存')) {
              setNotes(undefined);
            }
          }}>
            <div className="fr-editor-heading"><h5>你的经营笔记</h5><span>保留判断依据，随新披露继续修订</span></div>
            <div className="fr-form-grid">{noteFields.map(field => <label key={field.key}>{field.label}<textarea maxLength={2000} placeholder={field.hint} value={notes[field.key]} onChange={event => setNotes({ ...notes, [field.key]: event.target.value })} /></label>)}</div>
            <label>参考材料链接<input type="url" maxLength={1000} placeholder="https://" value={notes.sourceUrl} onChange={event => setNotes({ ...notes, sourceUrl: event.target.value })} /></label>
            <div className="fr-actions"><button type="button" disabled={busy} onClick={() => setNotes(undefined)}>取消</button><button className="fr-primary" disabled={busy}>保存经营笔记</button></div>
          </form>}
        </section>

        <section className="fr-section" aria-labelledby="fr-analysis-title">
          <header><div><span className="fr-kicker">经营与财务变化</span><h4 id="fr-analysis-title">利润、现金与资金投入</h4></div><span className="fr-muted">{reportLabel}</span></header>
          {(view.insights ?? []).length === 0 ? <p className="fr-empty">更新底稿后，将从已有合并财报整理增长、现金兑现和资金投入。</p> : <div className="fr-insights">
            {view.insights.map(insight => <article key={insight.dimension} className={`fr-insight${insight.attention ? ' needs-attention' : ''}`}>
              <div className="fr-insight-heading"><span>{dimensionLabels[insight.dimension]}</span>{insight.attention && <small>优先核对</small>}</div>
              <h5>{insight.title}</h5><p>{insight.conclusion}</p>
              <dl>{insight.metrics.map(metric => <div key={metric.conceptCode}><dt>{metric.label}</dt><dd title={`${metric.value} ${metric.unit} · ${metric.periodEnd}`}>{fundamentalValue(metric.value, metric.unit)}</dd></div>)}</dl>
              <p className="fr-next-check"><span>下次看什么</span>{insight.nextCheck}</p>
              {view.suggestions?.find(watch => watch.originKey === `FINANCIAL:${view.reportId}:${insight.dimension}`) && <button type="button" className="fr-text-action" disabled={busy} onClick={() => openWatch(view.suggestions.find(watch => watch.originKey === `FINANCIAL:${view.reportId}:${insight.dimension}`))}>加入验证清单 →</button>}
            </article>)}
          </div>}
          {view.reportId && <p className="fr-source">财报来源 {view.sourceCode} · 报告期 {view.periodEnd}{view.publishedAt ? ` · 披露 ${view.publishedAt.slice(0, 10)}` : ''}</p>}
        </section>

        <div className="fr-tracking-layout">
          <section className="fr-section" aria-labelledby="fr-events-title">
            <header><div><span className="fr-kicker">事项如何影响经营</span><h4 id="fr-events-title">近期公告与跟踪重点</h4></div></header>
            {(view.events ?? []).length === 0 ? <p className="fr-empty">已识别的公司业绩、合同和回购事项会在这里关联展示。</p> : <ol className="fr-events">{view.events.map(item => <li key={item.sampleId}>
              <div className="fr-event-meta"><time>{item.publishedAt?.replace('T', ' ').slice(0, 16) || '公开时间未提供'}</time><span>{subtypeLabels[item.subtype] || dimensionLabels[item.dimension]}</span></div>
              <h5>{fundamentalSource(item.sourceUrl) ? <a href={fundamentalSource(item.sourceUrl)} target="_blank" rel="noreferrer">{item.title} ↗</a> : item.title}</h5>
              <p>{item.impact}</p><p className="fr-event-next">下一步：{item.nextCheck}</p>
              {view.suggestions?.find(watch => watch.originKey === `EVENT:${item.sampleId}`) && <button type="button" className="fr-text-action" onClick={() => openWatch(view.suggestions.find(watch => watch.originKey === `EVENT:${item.sampleId}`))}>跟踪这件事 →</button>}
            </li>)}</ol>}
          </section>

          <section className="fr-section" aria-labelledby="fr-watches-title">
            <header><div><span className="fr-kicker">持续验证</span><h4 id="fr-watches-title">下一次披露，验证什么</h4></div><button type="button" disabled={busy} onClick={() => openWatch()}>新增验证</button></header>
            <div ref={editorRef}>{draft && <form className="fr-editor fr-watch-editor" onSubmit={async event => {
              event.preventDefault();
              if (await request(`${base}/watches`, 'POST', draft, '已加入验证清单')) {
                setDraft(undefined);
              }
            }}>
              <h5>记录一个可复核的判断</h5>
              <label>验证事项<input required maxLength={300} value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
              <label>当前判断<textarea required maxLength={2000} value={draft.hypothesis} onChange={event => setDraft({ ...draft, hypothesis: event.target.value })} /></label>
              <label>当前事实与基线<textarea required maxLength={3000} value={draft.baseline} onChange={event => setDraft({ ...draft, baseline: event.target.value })} /></label>
              <label>什么变化会支持判断<textarea required maxLength={2000} value={draft.supportCondition} onChange={event => setDraft({ ...draft, supportCondition: event.target.value })} /></label>
              <label>什么变化会推翻判断<textarea required maxLength={2000} value={draft.challengeCondition} onChange={event => setDraft({ ...draft, challengeCondition: event.target.value })} /></label>
              <label>计划复核日期<input required type="date" value={draft.nextReviewDate} onChange={event => setDraft({ ...draft, nextReviewDate: event.target.value })} /></label>
              <div className="fr-actions"><button type="button" disabled={busy} onClick={() => setDraft(undefined)}>取消</button><button className="fr-primary" disabled={busy}>保存验证事项</button></div>
            </form>}</div>
            {watches.length === 0 && !draft && <div className="fr-watch-empty"><span aria-hidden="true">↗</span><h5>留下判断，也留下验证条件</h5><p>从财务变化或公告加入一项观察，后续记录支持它或推翻它的新事实。</p></div>}
            <div className="fr-watches">{watches.map(watch => <article key={watch.id} className={`fr-watch is-${watch.status.toLowerCase()}`}>
              <div className="fr-watch-meta"><span>{watchStatusLabels[watch.status]}</span><time>计划复核 {watch.nextReviewDate}</time></div>
              <h5>{watch.title}</h5><p>{watch.hypothesis}</p>
              <details><summary>基线与验证条件</summary><p><strong>事前基线：</strong>{watch.baseline}</p><p><strong>支持条件：</strong>{watch.supportCondition}</p><p><strong>反证条件：</strong>{watch.challengeCondition}</p></details>
              {watch.reviews?.length > 0 && <details><summary>最近复核 · {watch.reviews.length} 条</summary>{watch.reviews.map(item => <p key={item.id}><small>{item.reviewedAt.replace('T', ' ').slice(0, 16)} · {watchStatusLabels[item.status]}</small><br />{item.note}</p>)}</details>}
              {review && review.id === watch.id ? <form className="fr-review-form" onSubmit={async event => {
                event.preventDefault();
                if (await request(`${base}/watches/${watch.id}/review`, 'PATCH', review, '本次复核已记录')) {
                  setReview(undefined);
                }
              }}>
                <label>复核结果<select value={review.status} onChange={event => setReview({ ...review, status: event.target.value as FundamentalWatchStatus })}>{Object.entries(watchStatusLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
                <label>本次看到的新事实<textarea required maxLength={2000} placeholder="记录新披露、实际值及判断为何发生变化" value={review.note} onChange={event => setReview({ ...review, note: event.target.value })} /></label>
                <div className="fr-actions"><button type="button" disabled={busy} onClick={() => setReview(undefined)}>取消</button><button className="fr-primary" disabled={busy}>保存复核</button></div>
              </form> : <button type="button" className="fr-text-action" disabled={busy} onClick={() => setReview({ id: watch.id!, status: watch.status, note: '', revision: watch.revision })}>记录本次复核 →</button>}
            </article>)}</div>
          </section>
        </div>
        {(view.notices ?? []).map((notice, index) => <p className="fr-feedback" key={index}>{notice}</p>)}
        <footer className="fr-footer">底稿整理于 {view.assembledAt?.replace('T', ' ').slice(0, 16)} · 经营笔记与验证结果由你持续维护</footer>
      </>}
    </>}
  </div>;
}
