import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { FactorResearchAgentRun, QuantDataset, ResearchFactorDefinition } from './quantTypes';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

interface Props {
  factor: ResearchFactorDefinition;
  dataset?: QuantDataset;
  researchDraftId?: number;
  enabled: boolean;
  addToast: Toast;
}

const toolLabels: Record<string, string> = {
  inspect_research_draft: '读取研究草稿', inspect_dataset: '检查数据集',
  inspect_factor_definition: '核对因子定义', run_factor_diagnostics: '运行因子诊断'
};

function parseFinding(value?: string) {
  try { return value ? JSON.parse(value) as { verdict: string; summary: string; counterEvidence: string[]; blockingReasons: string[]; nextSteps: string[] } : undefined; }
  catch { return undefined; }
}

export function FactorResearchAgentPanel({ factor, dataset, researchDraftId, enabled, addToast }: Props) {
  const [run, setRun] = useState<FactorResearchAgentRun>();
  const [busy, setBusy] = useState(false);
  useEffect(() => setRun(undefined), [factor.identity.code, dataset?.id, researchDraftId]);
  const finding = useMemo(() => parseFinding(run?.findingJson), [run?.findingJson]);

  async function createPlan() {
    if (!dataset) return; setBusy(true);
    try {
      const value = await api<FactorResearchAgentRun>('/api/factor-research/agent-runs', {
        method: 'POST', body: JSON.stringify({ datasetId: dataset.id, factorNamespace: factor.identity.namespace,
          factorCode: factor.identity.code, factorVersion: factor.identity.version, researchDraftId })
      });
      setRun(value); addToast('受控研究计划已生成，等待批准', 'info');
    } catch (error) { addToast(error instanceof Error ? error.message : '研究计划生成失败', 'error'); }
    finally { setBusy(false); }
  }

  async function approve() {
    if (!run) return; setBusy(true);
    try {
      const value = await api<FactorResearchAgentRun>(`/api/factor-research/agent-runs/${run.id}/approve`, { method: 'POST' });
      setRun(value); addToast('研究 Agent 已完成只读复核', value.status === 'COMPLETED' ? 'success' : 'error');
    } catch (error) { addToast(error instanceof Error ? error.message : '研究 Agent 运行失败', 'error'); }
    finally { setBusy(false); }
  }

  return <section className="quant-research-agent" aria-labelledby="research-agent-title">
    <header>
      <div><span>CONTROLLED RESEARCH AGENT</span><h5 id="research-agent-title">让 Agent 复核证据，但不给它越权空间</h5></div>
      <b>{run ? run.status : '尚未创建计划'}</b>
    </header>
    <p>Agent 只能调用登记过的只读工具；先展示计划、再由你批准。结论必须服从确定性评价门禁，不能自动改因子状态、生成策略或启动回测。</p>
    {!run && <div className="quant-agent-start">
      <div><strong>本次边界</strong><small>最多 4 次工具 · 0 次大模型 · 60 秒 · 0 次新实验</small></div>
      <button type="button" disabled={!enabled || busy} onClick={createPlan}>{busy ? '正在编制计划…' : '生成复核计划'}</button>
    </div>}
    {run && <>
      <div className="quant-agent-plan">
        <div><span>执行计划</span><ol>{run.plan.map(item => <li key={item}>{item}</li>)}</ol></div>
        <div><span>工具白名单</span><ul>{run.allowedTools.map(item => <li key={item}>{toolLabels[item] ?? item}</li>)}</ul></div>
        <div><span>硬预算</span><dl><div><dt>工具</dt><dd>{run.toolCallsUsed}/{run.maxToolCalls}</dd></div><div><dt>LLM</dt><dd>{run.llmCallsUsed}/{run.maxLlmCalls}</dd></div><div><dt>时限</dt><dd>{run.maxRunSeconds}s</dd></div></dl></div>
      </div>
      {run.status === 'AWAITING_APPROVAL' && <div className="quant-agent-approval"><p>批准后仅运行上面的只读步骤，不会创建实验。</p><button type="button" disabled={busy} onClick={approve}>{busy ? '正在执行复核…' : '批准并运行'}</button></div>}
      {run.trace?.length > 0 && <div className="quant-agent-trace"><span>可审计时间线</span>{run.trace.map((item, index) => <div key={item.id ?? `${item.nodeName}-${index}`}><i>{index + 1}</i><p><strong>{item.nodeName}</strong><small>{item.output}</small></p><b>{item.status}</b></div>)}</div>}
      {finding && <article className="quant-agent-finding" data-verdict={finding.verdict}>
        <header><span>确定性结论</span><strong>{finding.verdict === 'SUPPORTED' ? '支持研究假设' : finding.verdict === 'REFUTED' ? '反驳研究假设' : '证据不足'}</strong></header>
        <p>{finding.summary}</p>
        <div><section><h6>强制反证与限制</h6><ul>{finding.counterEvidence.map(item => <li key={item}>{item}</li>)}</ul></section><section><h6>下一步</h6><ul>{finding.nextSteps.map(item => <li key={item}>{item}</li>)}</ul></section></div>
        <small>证据哈希 {run.evidenceHash.slice(0, 16)} · 停止原因 {run.stopReason}</small>
      </article>}
      {run.status === 'FAILED' && <p className="quant-factor-blocker">运行失败：{run.stopReason}</p>}
    </>}
    {!dataset && <p className="quant-factor-blocker">先选择数据集后才能生成复核计划。</p>}
    {dataset && !enabled && <p className="quant-factor-blocker">只有质量通过且具备当前因子输入的数据集才能运行。</p>}
  </section>;
}
