import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import {
  FinancialEvidence,
  FinancialInterpretation,
  FinancialInterpretationClaim,
  FinancialInterpretationDimension,
  FinancialInterpretationStatus
} from './financialTypes';

const statusLabels: Record<FinancialInterpretationStatus, string> = {
  QUEUED: '等待解读',
  RUNNING: '正在组织经营叙事',
  VALIDATING: '正在核对证据引用',
  SUCCESS: '证据校验通过',
  FALLBACK: '规则解读',
  FAILED: '本次生成失败'
};

const operatingStateLabels = {
  IMPROVING: '改善',
  STABLE: '稳定',
  UNDER_PRESSURE: '承压',
  INSUFFICIENT_EVIDENCE: '证据不足'
};

const confidenceLabels = { HIGH: '高置信度', MEDIUM: '中置信度', LOW: '低置信度' };

const dimensionLabels: Record<string, string> = {
  GROWTH: '成长性',
  PROFITABILITY: '盈利能力',
  EARNINGS_QUALITY: '盈利质量',
  CASH_QUALITY: '现金质量',
  ASSET_QUALITY: '资产质量',
  SOLVENCY_CAPITAL_DISCIPLINE: '偿债与资本纪律'
};

const assessmentLabels: Record<string, string> = {
  POSITIVE: '积极',
  NEUTRAL: '中性',
  NEGATIVE: '承压',
  INSUFFICIENT_EVIDENCE: '证据不足'
};

const pendingStatuses: FinancialInterpretationStatus[] = ['QUEUED', 'RUNNING', 'VALIDATING'];

export function FinancialInterpretationPanel({ reportId }: { reportId: number }) {
  const [task, setTask] = useState<FinancialInterpretation>();
  const [displayed, setDisplayed] = useState<FinancialInterpretation>();
  const [history, setHistory] = useState<FinancialInterpretation[]>([]);
  const [evidence, setEvidence] = useState<FinancialEvidence[]>([]);
  const [selectedEvidence, setSelectedEvidence] = useState<FinancialEvidence>();
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setTask(undefined);
    setDisplayed(undefined);
    setEvidence([]);
    setSelectedEvidence(undefined);
    setError('');

    Promise.allSettled([
      api<FinancialInterpretation[]>(`/api/financials/reports/${reportId}/interpretations?limit=20`),
      api<FinancialInterpretation>(`/api/financials/reports/${reportId}/interpretations/latest`)
    ]).then(([historyResult, latestResult]) => {
      if (!active) return;
      const items = historyResult.status === 'fulfilled' ? historyResult.value : [];
      const latest = latestResult.status === 'fulfilled' ? latestResult.value : undefined;
      setHistory(items);
      setTask(latest);
      setDisplayed(isDisplayable(latest) ? latest : items.find(isDisplayable));
      if (historyResult.status === 'rejected') {
        setError(messageOf(historyResult.reason, '历史解读加载失败，请稍后重试'));
      } else if (latestResult.status === 'rejected') {
        const status = (latestResult.reason as { status?: number } | undefined)?.status;
        if (status != null && status !== 404) {
          setError(messageOf(latestResult.reason, '最近一次解读加载失败，请稍后重试'));
        }
      }
    }).finally(() => {
      if (active) setLoading(false);
    });

    return () => { active = false; };
  }, [reportId]);

  useEffect(() => {
    if (!displayed?.id) {
      setEvidence([]);
      return;
    }
    let active = true;
    api<FinancialEvidence[]>(`/api/financials/interpretations/${displayed.id}/evidence`)
      .then((items) => { if (active) setEvidence(items); })
      .catch((reason) => {
        if (active) {
          setEvidence([]);
          setError(messageOf(reason, '解读证据加载失败，请稍后重试'));
        }
      });
    return () => { active = false; };
  }, [displayed?.id]);

  useEffect(() => {
    if (!task || !pendingStatuses.includes(task.status)) return;
    let active = true;
    const taskId = task.id;
    const poll = async () => {
      let current = task;
      let consecutiveFailures = 0;
      while (active && pendingStatuses.includes(current.status)) {
        const waitMs = Math.min(2_000 * Math.pow(2, consecutiveFailures), 10_000);
        await wait(waitMs);
        if (!active) return;
        try {
          current = await api<FinancialInterpretation>(`/api/financials/interpretations/${taskId}`);
          if (!active) return;
          consecutiveFailures = 0;
          applyTask(current);
          if (isDisplayable(current)) await refreshHistory();
        } catch (reason) {
          consecutiveFailures += 1;
          if (active) setError(messageOf(reason, '解读状态连接中断，系统正在重试'));
        }
      }
    };
    void poll();
    return () => {
      active = false;
    };
  }, [task?.id]);

  async function generate(force: boolean) {
    setGenerating(true);
    setError('');
    try {
      const created = await api<FinancialInterpretation>(
        `/api/financials/reports/${reportId}/interpretations`,
        { method: 'POST', body: JSON.stringify({ force }) }
      );
      applyTask(created);
      if (isDisplayable(created)) {
        await refreshHistory();
      } else if (pendingStatuses.includes(created.status)) {
        const current = await api<FinancialInterpretation>(`/api/financials/interpretations/${created.id}`);
        applyTask(current);
        if (isDisplayable(current)) await refreshHistory();
      }
    } catch (reason) {
      setError(messageOf(reason, 'Agent 解读生成失败，请稍后重试'));
    } finally {
      setGenerating(false);
    }
  }

  function applyTask(current: FinancialInterpretation) {
    setTask(current);
    if (isDisplayable(current)) {
      setDisplayed(current);
      setError('');
    } else if (current.status === 'FAILED') {
      setError(current.failureMessage || '本次解读未能完成，可重新生成');
    }
  }

  async function refreshHistory() {
    const items = await api<FinancialInterpretation[]>(`/api/financials/reports/${reportId}/interpretations?limit=20`);
    setHistory(items);
  }

  function chooseHistory(id: number) {
    const item = history.find((candidate) => candidate.id === id);
    if (!item || !isDisplayable(item)) return;
    setDisplayed(item);
    setSelectedEvidence(undefined);
  }

  const evidenceById = useMemo(
    () => new Map(evidence.map((item) => [item.id, item])),
    [evidence]
  );
  const pending = Boolean(task && pendingStatuses.includes(task.status));

  if (loading) {
    return <section className="financial-agent-empty" aria-live="polite">正在读取历史解读…</section>;
  }

  return (
    <section className="financial-agent-panel">
      <header className="financial-agent-header">
        <div>
          <p className="financials-section-kicker">Evidence-constrained interpretation</p>
          <h4>财报解读 Agent</h4>
          <p>Agent 只负责组织叙事；数字、趋势与引用均来自当前可复算快照。</p>
        </div>
        <div className="financial-agent-actions">
          {displayed && (
            <label>
              <span>历史版本</span>
              <select
                aria-label="历史解读版本"
                value={displayed.id}
                onChange={(event) => chooseHistory(Number(event.target.value))}
              >
                {history.filter(isDisplayable).map((item) => (
                  <option key={item.id} value={item.id}>{historyLabel(item)}</option>
                ))}
              </select>
            </label>
          )}
          {displayed && (
            <button
              className="ghost-button"
              type="button"
              disabled={generating || pending}
              onClick={() => generate(true)}
            >
              {generating || pending ? '生成中…' : '重新生成'}
            </button>
          )}
        </div>
      </header>

      {pending && task && (
        <div className="financial-agent-progress" role="status">
          <span className="financial-agent-pulse" aria-hidden="true" />
          <div><strong>{statusLabels[task.status]}</strong><small>完成后会自动刷新，当前页面可以继续查看三张表。</small></div>
        </div>
      )}

      {error && <div className="financial-agent-error" role="alert">{error}</div>}

      {!displayed ? (
        <div className="financial-agent-empty">
          <div className="financial-agent-orbit" aria-hidden="true"><span>证据</span></div>
          <div>
            <h4>从三张表到一条可核查的经营叙事</h4>
            <p>生成后将得到经营状态、六维分析、积极信号、风险、拐点与观察清单。所有实质判断都可点击查看证据。</p>
          </div>
          <button className="primary-button" type="button" disabled={generating || pending} onClick={() => generate(false)}>
            {generating || pending ? '生成中…' : '生成 Agent 解读'}
          </button>
        </div>
      ) : displayed.result ? (
        <InterpretationResult
          interpretation={displayed}
          evidenceById={evidenceById}
          onEvidence={(id) => setSelectedEvidence(evidenceById.get(id) ?? fallbackEvidence(id))}
        />
      ) : null}

      {selectedEvidence && (
        <div className="financial-evidence-backdrop" onClick={() => setSelectedEvidence(undefined)}>
          <aside
            className="financial-evidence-drawer"
            role="dialog"
            aria-label="证据详情"
            aria-modal="true"
            onClick={(event) => event.stopPropagation()}
          >
            <header>
              <div><span>{selectedEvidence.type}</span><h4>{selectedEvidence.label}</h4></div>
              <button type="button" aria-label="关闭证据详情" onClick={() => setSelectedEvidence(undefined)}>×</button>
            </header>
            <dl>
              <div><dt>证据编号</dt><dd>{selectedEvidence.id}</dd></div>
              {selectedEvidence.value && <div><dt>披露或计算值</dt><dd>{selectedEvidence.value}{selectedEvidence.unit || ''}</dd></div>}
              {selectedEvidence.period && <div><dt>报告期</dt><dd>{selectedEvidence.period}</dd></div>}
            </dl>
            {selectedEvidence.detail && <p>{selectedEvidence.detail}</p>}
            {selectedEvidence.sourceRefs && selectedEvidence.sourceRefs.length > 0 && (
              <div className="financial-evidence-sources"><strong>上游证据</strong><span>{selectedEvidence.sourceRefs.join(' · ')}</span></div>
            )}
          </aside>
        </div>
      )}
    </section>
  );
}

function InterpretationResult({
  interpretation,
  evidenceById,
  onEvidence
}: {
  interpretation: FinancialInterpretation;
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
}) {
  const result = interpretation.result!;
  return (
    <div className="financial-agent-result">
      {interpretation.snapshotStale && (
        <div className="financial-agent-stale">当前解读基于旧快照，建议重新生成。</div>
      )}
      <section className="financial-agent-verdict">
        <div>
          <span>经营状态</span>
          <strong>{operatingStateLabels[result.operatingState]}</strong>
        </div>
        <div className="financial-agent-meta">
          <span>{confidenceLabels[result.confidence]}</span>
          <span>{interpretation.generationMode === 'DETERMINISTIC_FALLBACK' ? '规则解读' : statusLabels[interpretation.status]}</span>
          {interpretation.durationMs != null && <span>{(interpretation.durationMs / 1000).toFixed(1)} 秒</span>}
        </div>
      </section>

      <ClaimSection
        title="执行摘要"
        kicker="Core reading"
        claims={result.executiveSummary}
        evidenceById={evidenceById}
        onEvidence={onEvidence}
        prominent
      />

      {(result.periodChanges?.length || result.crossStatementInsights?.length) ? (
        <div className="financial-agent-claim-grid">
          {result.periodChanges?.length ? (
            <ClaimSection
              title="核心变化"
              kicker="Period changes"
              claims={result.periodChanges}
              evidenceById={evidenceById}
              onEvidence={onEvidence}
            />
          ) : null}
          {result.crossStatementInsights?.length ? (
            <ClaimSection
              title="三表联动"
              kicker="Cross-statement reading"
              claims={result.crossStatementInsights}
              evidenceById={evidenceById}
              onEvidence={onEvidence}
            />
          ) : null}
        </div>
      ) : null}

      <section className="financial-agent-dimensions">
        <header><div><p className="financials-section-kicker">Six-lens review</p><h4>六维分析</h4></div></header>
        <div>
          {result.dimensions.map((dimension) => (
            <DimensionCard key={dimension.code} dimension={dimension} evidenceById={evidenceById} onEvidence={onEvidence} />
          ))}
        </div>
      </section>

      <div className="financial-agent-claim-grid">
        <ClaimSection title="积极信号" kicker="Positive signals" claims={result.positiveSignals} evidenceById={evidenceById} onEvidence={onEvidence} />
        <ClaimSection title="风险与反证" kicker="Risks & counter-evidence" claims={result.risks} evidenceById={evidenceById} onEvidence={onEvidence} tone="risk" />
        <ClaimSection title="可能的拐点" kicker="Turning points" claims={result.turningPoints} evidenceById={evidenceById} onEvidence={onEvidence} />
        <ClaimSection title="后续观察" kicker="Watchlist" claims={result.watchpoints} evidenceById={evidenceById} onEvidence={onEvidence} tone="watch" />
      </div>

      <footer className="financial-agent-limitations">
        <div><strong>数据限制</strong>{result.limitations.length ? result.limitations.map((item) => <span key={item}>{item}</span>) : <span>未声明额外限制。</span>}</div>
        <p>{result.disclaimer}</p>
      </footer>
    </div>
  );
}

function DimensionCard({
  dimension,
  evidenceById,
  onEvidence
}: {
  dimension: FinancialInterpretationDimension;
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
}) {
  return (
    <article data-assessment={dimension.assessment.toLowerCase()}>
      <header><strong>{dimensionLabels[dimension.code] || dimension.code}</strong><span>{assessmentLabels[dimension.assessment]}</span></header>
      <p>{dimension.summary}</p>
      <EvidenceRefs refs={dimension.refs} evidenceById={evidenceById} onEvidence={onEvidence} />
      {dimension.details?.length ? (
        <div className="financial-agent-dimension-details">
          {dimension.details.map((detail, index) => (
            <div key={`${detail.claim}-${index}`}>
              <span>{claimTypeLabel(detail.claimType)}</span>
              <p>{detail.claim}</p>
              <EvidenceRefs refs={detail.refs} evidenceById={evidenceById} onEvidence={onEvidence} />
            </div>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function ClaimSection({
  title,
  kicker,
  claims,
  evidenceById,
  onEvidence,
  prominent = false,
  tone = 'neutral'
}: {
  title: string;
  kicker: string;
  claims: FinancialInterpretationClaim[];
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
  prominent?: boolean;
  tone?: 'neutral' | 'risk' | 'watch';
}) {
  return (
    <section className={`financial-agent-claims ${prominent ? 'prominent' : ''}`} data-tone={tone}>
      <header><p className="financials-section-kicker">{kicker}</p><h4>{title}</h4></header>
      {claims.length ? (
        <div className="financial-agent-claim-list">
          {claims.map((claim, index) => (
            <article key={`${claim.claim}-${index}`}>
              <span>{claimTypeLabel(claim.claimType)}</span>
              <div><p>{claim.claim}</p><EvidenceRefs refs={claim.refs} evidenceById={evidenceById} onEvidence={onEvidence} /></div>
            </article>
          ))}
        </div>
      ) : <p className="financial-agent-no-claim">当前证据未支持明确结论。</p>}
    </section>
  );
}

function claimTypeLabel(claimType: FinancialInterpretationClaim['claimType']) {
  return claimType === 'FACT' ? '事实' : claimType === 'INFERENCE' ? '推断' : '观察';
}

function EvidenceRefs({
  refs,
  evidenceById,
  onEvidence
}: {
  refs: string[];
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
}) {
  return (
    <div className="financial-evidence-refs">
      {refs.map((id) => {
        const label = evidenceById.get(id)?.label || id;
        return <button key={id} type="button" aria-label={`${label}证据`} onClick={() => onEvidence(id)}>{label}</button>;
      })}
    </div>
  );
}

function isDisplayable(value?: FinancialInterpretation): boolean {
  return Boolean(value?.result && (value.status === 'SUCCESS' || value.status === 'FALLBACK'));
}

function historyLabel(item: FinancialInterpretation) {
  const created = item.createdAt ? item.createdAt.replace('T', ' ').slice(0, 16) : `版本 ${item.id}`;
  return `${created} · ${item.generationMode === 'DETERMINISTIC_FALLBACK' ? '规则解读' : 'Agent 解读'}`;
}

function fallbackEvidence(id: string): FinancialEvidence {
  return { id, type: 'EVIDENCE', label: id, detail: '证据详情暂未加载，可稍后重试。' };
}

function wait(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));
}

function messageOf(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback;
}
