import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../shared/api/client';
import { StrategyPlaybook, StrategyPlaybookRule } from '../../shared/types';
import { RealHoldingsLab } from './RealHoldingsLab';

type Tab = 'real-holdings' | 'playbooks';
const statusLabels: Record<string, string> = { RESEARCHING: '研究中', ACTIVE: '使用中', PAUSED: '已暂停' };
const validationLabels: Record<string, string> = { UNVALIDATED: '尚未验证', IN_RESEARCH: '验证中', SUPPORTED: '已有支持', REFUTED: '已被反驳', INCONCLUSIVE: '结论不足' };
const ruleTypeLabels: Record<string, string> = { PRINCIPLE: '原则', FILTER: '筛选', ENTRY: '买点', EXIT: '退出', CAUTION: '注意' };
const testabilityLabels: Record<string, string> = { QUALITATIVE: '定性判断', CANDIDATE_RULE: '待量化', DETERMINISTIC: '可直接执行' };

export function LongTermStrategyView({ addToast, setMessage }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void; setMessage: (message: string) => void }) {
  const [tab, setTab] = useState<Tab>('playbooks');
  const [playbooks, setPlaybooks] = useState<StrategyPlaybook[]>([]);
  const [selectedPlaybook, setSelectedPlaybook] = useState<StrategyPlaybook | null>(null);
  const [playbookDetail, setPlaybookDetail] = useState<StrategyPlaybook | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      setPlaybooks(await api<StrategyPlaybook[]>('/api/strategy/playbooks'));
      setMessage('策略工作台已同步');
    } catch (error) { addToast(error instanceof Error ? error.message : '策略工作台加载失败', 'error'); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  const detailSections = useMemo(() => (playbookDetail?.rules ?? []).reduce<Array<{ code: string; title: string; rules: StrategyPlaybookRule[] }>>((sections, rule) => {
    let section = sections.find(item => item.code === rule.sectionCode);
    if (!section) { section = { code: rule.sectionCode, title: rule.sectionTitle, rules: [] }; sections.push(section); }
    section.rules.push(rule);
    return sections;
  }, []), [playbookDetail]);

  async function setPlaybook(item: StrategyPlaybook, status: StrategyPlaybook['status']) { await api(`/api/strategy/playbooks/${item.code}/status`, { method: 'PUT', body: JSON.stringify({ status, note: item.note ?? '', revision: item.revision }) }); await load(); }
  async function openPlaybook(item: StrategyPlaybook) {
    setSelectedPlaybook(item); setPlaybookDetail(null); setDetailLoading(true);
    try { setPlaybookDetail(await api<StrategyPlaybook>(`/api/strategy/playbooks/${item.code}`)); }
    catch (error) { addToast(error instanceof Error ? error.message : '策略详情加载失败', 'error'); setSelectedPlaybook(null); }
    finally { setDetailLoading(false); }
  }
  function closePlaybook() { setSelectedPlaybook(null); setPlaybookDetail(null); }
  return <section className="strategy-workspace">
    <nav className="strategy-tabs" role="tablist">
      {([['real-holdings', '真实持仓'], ['playbooks', '策略库']] as Array<[Tab, string]>).map(([id, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}
    </nav>
    {loading && tab === 'playbooks' ? <div className="strategy-empty">正在加载策略数据…</div> : null}
    {tab === 'real-holdings' ? <RealHoldingsLab addToast={addToast} /> : null}
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
  </section>;
}
