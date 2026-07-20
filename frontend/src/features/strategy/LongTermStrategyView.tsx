import { FormEvent, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../shared/api/client';
import { StrategyOverview, StrategyPlaybook, StrategyPlaybookRule, StrategyReview, StrategyStockThesis } from '../../shared/types';

type Tab = 'portfolio' | 'playbooks' | 'theses' | 'reviews';
const roleLabels: Record<string, string> = { CORE: '核心', SATELLITE: '卫星', DEFENSIVE: '防守', OBSERVE: '观察', SIMULATED: '模拟', LIVE_VALIDATION: '真实验证' };
const stageLabels: Record<string, string> = { RESEARCH_POOL: '研究池', WATCH_POOL: '观察池', SIMULATED_PORTFOLIO: '模拟组合', LIVE_VALIDATION: '小仓位验证' };
const statusLabels: Record<string, string> = { RESEARCHING: '研究中', ACTIVE: '使用中', PAUSED: '已暂停' };
const validationLabels: Record<string, string> = { UNVALIDATED: '尚未验证', IN_RESEARCH: '验证中', SUPPORTED: '已有支持', REFUTED: '已被反驳', INCONCLUSIVE: '结论不足' };
const ruleTypeLabels: Record<string, string> = { PRINCIPLE: '原则', FILTER: '筛选', ENTRY: '买点', EXIT: '退出', CAUTION: '注意' };
const testabilityLabels: Record<string, string> = { QUALITATIVE: '定性判断', CANDIDATE_RULE: '待量化', DETERMINISTIC: '可直接执行' };

export function LongTermStrategyView({ addToast, setMessage }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void; setMessage: (message: string) => void }) {
  const [tab, setTab] = useState<Tab>('portfolio');
  const [overview, setOverview] = useState<StrategyOverview>({ holdings: [], targetWeight: 0, currentWeight: 0 });
  const [playbooks, setPlaybooks] = useState<StrategyPlaybook[]>([]);
  const [theses, setTheses] = useState<StrategyStockThesis[]>([]);
  const [reviews, setReviews] = useState<StrategyReview[]>([]);
  const [selectedPlaybook, setSelectedPlaybook] = useState<StrategyPlaybook | null>(null);
  const [playbookDetail, setPlaybookDetail] = useState<StrategyPlaybook | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [assetOpen, setAssetOpen] = useState(false);
  const [thesisOpen, setThesisOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [asset, setAsset] = useState({ code: '', type: 'FUND', role: 'CORE', targetWeight: '', currentWeight: '0', note: '' });
  const [thesis, setThesis] = useState({ code: '', thesis: '', buyConditions: '', invalidationConditions: '', watchFocus: '', note: '' });
  const [review, setReview] = useState({ reviewDate: new Date().toISOString().slice(0, 10), facts: '', reasoning: '', nextAction: '' });

  async function load() {
    try {
      const [o, p, t, r] = await Promise.all([
        api<StrategyOverview>('/api/strategy/overview'), api<StrategyPlaybook[]>('/api/strategy/playbooks'),
        api<StrategyStockThesis[]>('/api/strategy/stock-theses'), api<StrategyReview[]>('/api/strategy/reviews')
      ]);
      setOverview(o); setPlaybooks(p); setTheses(t); setReviews(r); setMessage('策略工作台已同步');
    } catch (error) { addToast(error instanceof Error ? error.message : '策略工作台加载失败', 'error'); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  const roleTotals = useMemo(() => overview.holdings.reduce<Record<string, number>>((acc, item) => { acc[item.role] = (acc[item.role] ?? 0) + item.targetWeight; return acc; }, {}), [overview.holdings]);
  const detailSections = useMemo(() => (playbookDetail?.rules ?? []).reduce<Array<{ code: string; title: string; rules: StrategyPlaybookRule[] }>>((sections, rule) => {
    let section = sections.find(item => item.code === rule.sectionCode);
    if (!section) { section = { code: rule.sectionCode, title: rule.sectionTitle, rules: [] }; sections.push(section); }
    section.rules.push(rule);
    return sections;
  }, []), [playbookDetail]);

  async function addAsset(event: FormEvent) {
    event.preventDefault();
    try { await api('/api/strategy/holdings', { method: 'POST', body: JSON.stringify({ ...asset, targetWeight: Number(asset.targetWeight), currentWeight: Number(asset.currentWeight) }) }); setAssetOpen(false); setAsset({ code: '', type: 'FUND', role: 'CORE', targetWeight: '', currentWeight: '0', note: '' }); await load(); addToast('资产已加入组合', 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '添加失败', 'error'); }
  }
  async function removeAsset(id: number, revision: number) { await api(`/api/strategy/holdings/${id}?revision=${revision}`, { method: 'DELETE' }); await load(); }
  async function setPlaybook(item: StrategyPlaybook, status: StrategyPlaybook['status']) { await api(`/api/strategy/playbooks/${item.code}/status`, { method: 'PUT', body: JSON.stringify({ status, note: item.note ?? '', revision: item.revision }) }); await load(); }
  async function openPlaybook(item: StrategyPlaybook) {
    setSelectedPlaybook(item); setPlaybookDetail(null); setDetailLoading(true);
    try { setPlaybookDetail(await api<StrategyPlaybook>(`/api/strategy/playbooks/${item.code}`)); }
    catch (error) { addToast(error instanceof Error ? error.message : '策略详情加载失败', 'error'); setSelectedPlaybook(null); }
    finally { setDetailLoading(false); }
  }
  function closePlaybook() { setSelectedPlaybook(null); setPlaybookDetail(null); }
  async function addThesis(event: FormEvent) { event.preventDefault(); try { await api('/api/strategy/stock-theses', { method: 'POST', body: JSON.stringify(thesis) }); setThesisOpen(false); setThesis({ code: '', thesis: '', buyConditions: '', invalidationConditions: '', watchFocus: '', note: '' }); await load(); } catch (error) { addToast(error instanceof Error ? error.message : '保存失败', 'error'); } }
  async function advance(item: StrategyStockThesis) { const stages = ['RESEARCH_POOL', 'WATCH_POOL', 'SIMULATED_PORTFOLIO', 'LIVE_VALIDATION']; const next = stages[Math.min(stages.indexOf(item.stage) + 1, stages.length - 1)]; await api(`/api/strategy/stock-theses/${item.id}`, { method: 'PATCH', body: JSON.stringify({ ...item, stage: next }) }); await load(); }
  async function addReview(event: FormEvent) { event.preventDefault(); try { await api('/api/strategy/reviews', { method: 'POST', body: JSON.stringify(review) }); setReviewOpen(false); setReview({ reviewDate: new Date().toISOString().slice(0, 10), facts: '', reasoning: '', nextAction: '' }); await load(); } catch (error) { addToast(error instanceof Error ? error.message : '保存失败', 'error'); } }

  return <section className="strategy-workspace">
    <header className="strategy-hero">
      <div><p className="strategy-kicker">Compound discipline · 复利纪律</p><h3>长期投资地图</h3><p>把持仓、策略、研究假设和复盘放在同一条可验证的轨道上。</p></div>
      <button className="primary-button" type="button" onClick={() => setAssetOpen(true)}>添加资产</button>
    </header>
    <div className="strategy-rail" aria-label="复利轨道">
      {['CORE', 'SATELLITE', 'DEFENSIVE', 'OBSERVE'].map(role => <div className="strategy-rail-segment" data-role={role} key={role}><span>{roleLabels[role]}</span><strong>{(roleTotals[role] ?? 0).toFixed(0)}%</strong><i style={{ width: `${Math.min(100, roleTotals[role] ?? 0)}%` }} /></div>)}
    </div>
    <nav className="strategy-tabs" role="tablist">
      {([['portfolio', '组合'], ['playbooks', '策略库'], ['theses', '股票孵化'], ['reviews', '复盘']] as Array<[Tab, string]>).map(([id, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}
    </nav>
    {loading ? <div className="strategy-empty">正在加载策略数据…</div> : null}
    {!loading && tab === 'portfolio' && <div className="strategy-layout"><div className="strategy-main"><div className="strategy-section-head"><div><span>Portfolio map</span><h4>组合资产</h4></div><b>{overview.targetWeight.toFixed(0)}% 已配置</b></div>{overview.holdings.length === 0 ? <div className="strategy-empty"><h4>还没有组合资产</h4><p>从你真实持有的第一只场外基金开始，定义它在组合里的角色。</p><button type="button" onClick={() => setAssetOpen(true)}>添加资产</button></div> : <div className="strategy-asset-grid">{overview.holdings.map(item => <article className="strategy-asset" key={item.id}><div><span>{item.type === 'FUND' ? '场外基金' : '股票'} · {roleLabels[item.role]}</span><h4>{item.name || item.code}</h4><small>{item.code}</small></div><div className="strategy-weight"><strong>{item.targetWeight.toFixed(0)}%</strong><small>目标权重</small></div><p>{item.note || '尚未记录配置理由'}</p><button type="button" className="text-button" onClick={() => removeAsset(item.id, item.revision)}>移出组合</button></article>)}</div>}</div><aside className="strategy-aside"><span>This cycle</span><h4>本周期行动</h4><ol><li>让目标权重形成完整结构</li><li>启用一项正在训练的策略</li><li>记录事实、推理与下一步行动</li></ol></aside></div>}
    {!loading && tab === 'playbooks' && (
      <div className="strategy-card-grid">
        {playbooks.map(item => (
          <article className="strategy-playbook" key={item.code}>
            <header>
              <span>{item.scope}</span>
              <em className="strategy-playbook-status" data-status={item.status}>{statusLabels[item.status]}</em>
            </header>
            <div className="strategy-playbook-title">
              <h4>{item.title}</h4>
              <b data-validation={item.validationStatus}>{validationLabels[item.validationStatus] ?? item.validationStatus}</b>
            </div>
            {item.author || item.sourceTitle ? (
              <small className="strategy-playbook-source">{[item.author, item.sourceTitle].filter(Boolean).join(' · ')}</small>
            ) : null}
            <p>{item.summary}</p>
            <dl>
              <div><dt>执行节奏</dt><dd>{item.cadence}</dd></div>
              <div><dt>风险边界</dt><dd>{item.riskBoundary}</dd></div>
            </dl>
            <div className="strategy-card-actions">
              <button type="button" className="strategy-card-detail" onClick={() => openPlaybook(item)}>查看详情</button>
              <button type="button" className="strategy-card-use" onClick={() => setPlaybook(item, 'ACTIVE')}>开始使用</button>
              <button
                type="button"
                className="strategy-card-state"
                onClick={() => setPlaybook(item, item.status === 'PAUSED' ? 'RESEARCHING' : 'PAUSED')}
              >
                {item.status === 'PAUSED' ? '恢复研究' : '暂停'}
              </button>
            </div>
          </article>
        ))}
      </div>
    )}
    {!loading && tab === 'theses' && <><div className="strategy-section-head"><div><span>Stock incubation</span><h4>股票研究卡</h4></div><button type="button" onClick={() => setThesisOpen(true)}>新建研究卡</button></div>{theses.length === 0 ? <div className="strategy-empty"><h4>股票研究从一张逻辑卡开始</h4><p>先写清为什么观察，以及什么情况说明自己错了。</p></div> : <div className="strategy-card-grid">{theses.map(item => <article className="strategy-thesis" key={item.id}><span>{stageLabels[item.stage]}</span><h4>{item.name || item.code}</h4><p>{item.thesis}</p><div className="strategy-condition risk"><b>失效条件</b>{item.invalidationConditions}</div><div className="strategy-card-actions"><button type="button" onClick={() => advance(item)}>推进阶段</button></div></article>)}</div>}</>}
    {!loading && tab === 'reviews' && <><div className="strategy-section-head"><div><span>Decision journal</span><h4>复盘记录</h4></div><button type="button" onClick={() => setReviewOpen(true)}>记录复盘</button></div>{reviews.length === 0 ? <div className="strategy-empty"><h4>还没有复盘</h4><p>用事实、推理和行动把一次判断留存下来。</p></div> : <div className="strategy-review-list">{reviews.map(item => <article key={item.id}><time>{item.reviewDate}</time><div><b>事实</b><p>{item.facts}</p></div><div><b>推理</b><p>{item.reasoning}</p></div><div><b>下一步</b><p>{item.nextAction}</p></div></article>)}</div>}</>}
    {selectedPlaybook && createPortal(
      <div
        className="modal-overlay strategy-detail-overlay"
        onMouseDown={event => { if (event.target === event.currentTarget) closePlaybook(); }}
      >
        <section
          className="modal strategy-playbook-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="strategy-playbook-detail-title"
        >
          <header className="strategy-detail-head">
            <div className="strategy-detail-title">
              <p>
                <span>{selectedPlaybook.scope} strategy</span>
                <code>{selectedPlaybook.code}</code>
              </p>
              <h4 id="strategy-playbook-detail-title">{selectedPlaybook.title}</h4>
            </div>
            <button
              type="button"
              className="strategy-detail-close"
              aria-label="关闭策略详情"
              onClick={closePlaybook}
            >
              <span aria-hidden="true">×</span>
            </button>
          </header>
          {detailLoading ? (
            <div className="strategy-detail-loading">正在读取策略规则…</div>
          ) : playbookDetail ? (
            <>
              <div className="strategy-detail-summary">
                <p className="strategy-detail-lede">{playbookDetail.summary}</p>
                <dl>
                  <div><dt>作者</dt><dd>{playbookDetail.author || '未标注'}</dd></div>
                  <div><dt>来源</dt><dd>{playbookDetail.sourceTitle || '未标注'}{playbookDetail.sourcePublishedAt ? ` · ${playbookDetail.sourcePublishedAt}` : ''}</dd></div>
                  <div><dt>验证状态</dt><dd>{validationLabels[playbookDetail.validationStatus] ?? playbookDetail.validationStatus}</dd></div>
                  <div><dt>执行节奏</dt><dd>{playbookDetail.cadence}</dd></div>
                </dl>
                <div className="strategy-detail-risk">
                  <b>风险纪律</b>
                  <span>{playbookDetail.riskBoundary}</span>
                </div>
              </div>
              {detailSections.length ? (
                <div className="strategy-rule-sections">
                  {detailSections.map(section => (
                    <section key={section.code}>
                      <header><span>{section.code}</span><h5>{section.title}</h5></header>
                      <ol>
                        {section.rules.map((rule, index) => (
                          <li key={rule.id ?? `${section.code}-${index}`}>
                            <p>{rule.ruleText}</p>
                            <footer>
                              <span>{ruleTypeLabels[rule.ruleType] ?? rule.ruleType}</span>
                              <span>{testabilityLabels[rule.testability] ?? rule.testability}</span>
                              {rule.sourcePage ? <span>第 {rule.sourcePage} 页</span> : null}
                            </footer>
                          </li>
                        ))}
                      </ol>
                    </section>
                  ))}
                </div>
              ) : <div className="strategy-detail-loading">该策略尚无结构化规则。</div>}
            </>
          ) : null}
        </section>
      </div>,
      document.body
    )}
    {assetOpen && <div className="modal-overlay"><form className="modal strategy-form" onSubmit={addAsset}><div className="modal-header"><div><p className="modal-kicker">Portfolio entry</p><h4>添加组合资产</h4></div></div><label>资产类型<select value={asset.type} onChange={e => { const type=e.target.value; setAsset({ ...asset, type, role: type === 'FUND' ? 'CORE' : 'OBSERVE' }); }}><option value="FUND">场外基金</option><option value="STOCK">股票</option></select></label><label>标的代码<input aria-label="标的代码" value={asset.code} onChange={e => setAsset({ ...asset, code: e.target.value })} required /></label><label>组合角色<select value={asset.role} onChange={e => setAsset({ ...asset, role: e.target.value })}>{(asset.type === 'FUND' ? ['CORE','SATELLITE','DEFENSIVE','OBSERVE'] : ['OBSERVE','SIMULATED','LIVE_VALIDATION']).map(v => <option key={v} value={v}>{roleLabels[v]}</option>)}</select></label><label>目标权重<input aria-label="目标权重" type="number" min="0" max="100" value={asset.targetWeight} onChange={e => setAsset({ ...asset, targetWeight: e.target.value })} required /></label><label>配置理由<textarea value={asset.note} onChange={e => setAsset({ ...asset, note: e.target.value })} /></label><div className="modal-actions"><button type="button" className="ghost-button" onClick={() => setAssetOpen(false)}>取消</button><button type="submit">保存资产</button></div></form></div>}
    {thesisOpen && <div className="modal-overlay"><form className="modal strategy-form" onSubmit={addThesis}><h4>新建股票研究卡</h4><label>股票代码<input value={thesis.code} onChange={e=>setThesis({...thesis,code:e.target.value})} required /></label><label>投资逻辑<textarea value={thesis.thesis} onChange={e=>setThesis({...thesis,thesis:e.target.value})} required /></label><label>买入条件<textarea value={thesis.buyConditions} onChange={e=>setThesis({...thesis,buyConditions:e.target.value})} required /></label><label>失效条件<textarea value={thesis.invalidationConditions} onChange={e=>setThesis({...thesis,invalidationConditions:e.target.value})} required /></label><label>观察重点<textarea value={thesis.watchFocus} onChange={e=>setThesis({...thesis,watchFocus:e.target.value})} required /></label><div className="modal-actions"><button type="button" onClick={()=>setThesisOpen(false)}>取消</button><button type="submit">保存研究卡</button></div></form></div>}
    {reviewOpen && <div className="modal-overlay"><form className="modal strategy-form" onSubmit={addReview}><h4>记录一次复盘</h4><label>日期<input type="date" value={review.reviewDate} onChange={e=>setReview({...review,reviewDate:e.target.value})}/></label><label>事实<textarea value={review.facts} onChange={e=>setReview({...review,facts:e.target.value})} required /></label><label>推理<textarea value={review.reasoning} onChange={e=>setReview({...review,reasoning:e.target.value})} required /></label><label>下一步行动<textarea value={review.nextAction} onChange={e=>setReview({...review,nextAction:e.target.value})} required /></label><div className="modal-actions"><button type="button" onClick={()=>setReviewOpen(false)}>取消</button><button type="submit">保存复盘</button></div></form></div>}
  </section>;
}
