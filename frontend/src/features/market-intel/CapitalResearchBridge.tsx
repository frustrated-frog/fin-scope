import { useState } from 'react';
import { api } from '../../shared/api/client';
import { QuantResearchEntryIntent, ResearchDraft } from '../strategy/quantTypes';
import { MarketIntelCapitalOverview } from './marketIntelTypes';

interface Props {
  overview: MarketIntelCapitalOverview;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onOpenQuantResearch?: (intent: QuantResearchEntryIntent) => void;
}

export function CapitalResearchBridge({ overview, addToast, onOpenQuantResearch }: Props) {
  const [busy, setBusy] = useState(false);
  const snapshot = overview.snapshot;
  const tags = overview.metrics?.objectiveTags ?? [];
  const signalCode = tags[0]?.code ?? 'MAIN_FLOW_SHARE_OBSERVATION';
  const evidenceRefs = Array.from(new Set([
    ...(snapshot ? [`snapshot:${snapshot.id}`] : []),
    ...tags.flatMap(tag => tag.metricRefs),
    ...overview.factorObservations.flatMap(item => item.metricRefs)
  ]));
  const instrumentCode = overview.instrument.code.includes('.')
    ? overview.instrument.code
    : `${overview.instrument.code}.${overview.instrument.market}`;
  const canCreate = Boolean(snapshot && evidenceRefs.length && onOpenQuantResearch);

  async function createDraft() {
    if (!snapshot || !canCreate || busy) return;
    setBusy(true);
    try {
      const draft = await api<ResearchDraft>('/api/factor-research/research-drafts/from-capital-signal', {
        method: 'POST',
        body: JSON.stringify({
          instrumentCode,
          instrumentName: overview.instrument.name,
          observedAt: snapshot.asOf,
          signalCode,
          snapshotId: snapshot.id,
          snapshotFingerprint: snapshot.fingerprint,
          evidenceRefs,
          objectiveTags: tags.map(tag => tag.code)
        })
      });
      addToast('研究草稿已保存，尚未运行诊断或回测', 'success');
      onOpenQuantResearch?.({
        draftId: draft.id,
        factorCode: draft.factor.code,
        sourceLabel: `${overview.instrument.name} · ${snapshot.asOf}`
      });
    } catch (error) {
      addToast(error instanceof Error ? error.message : '研究草稿创建失败', 'error');
    } finally {
      setBusy(false);
    }
  }

  return <section className="capital-research-bridge" aria-labelledby="capital-research-bridge-title">
    <div className="capital-research-track" aria-hidden="true">
      <span>实时证据</span><i /><span>冻结研究</span><i /><span>横截面验证</span>
    </div>
    <div className="capital-research-copy">
      <p>CAPITAL → QUANT</p>
      <h3 id="capital-research-bridge-title">把这次资金观察变成可证伪的问题</h3>
      <p>只保存标的、时点和证据引用。进入量化工作台后仍需冻结股票池数据并主动运行验证。</p>
    </div>
    <div className="capital-research-actions">
      <button type="button" disabled={!onOpenQuantResearch} onClick={() => onOpenQuantResearch?.({ factorCode: 'MAIN_FLOW_SHARE' })}>查看因子说明</button>
      <button type="button" className="primary" disabled={!canCreate || busy} onClick={createDraft}>{busy ? '正在保存草稿…' : '创建量化研究草稿'}</button>
    </div>
    {!snapshot && <small>当前没有可追溯快照，暂时不能创建研究草稿。</small>}
  </section>;
}
