import type { ForecastSelectionBiasAudit } from './quantTypes';

const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const number = (value?: number) => value == null ? '—' : value.toFixed(2);
const verdictCopy: Record<ForecastSelectionBiasAudit['verdict'], string> = {
  PASS: '选择偏差风险可接受', CAUTION: '需要谨慎解释', HIGH_RISK: '过拟合风险偏高', NOT_EVALUATED: '证据不足'
};

export function ForecastProfessionalAudit({ audit }: { audit: ForecastSelectionBiasAudit }) {
  return <section className="forecast-paper-section forecast-professional-audit" data-verdict={audit.verdict}>
    <header><div><span>SELECTION BIAS / MULTIPLE TRIALS</span><h4>专业过拟合审计</h4><p>把模型竞赛和相邻参数都视为真实试验，避免只展示“最幸运”的 Sharpe。</p></div><b>{verdictCopy[audit.verdict]}</b></header>
    {audit.status === 'AVAILABLE' ? <>
      <div className="forecast-audit-core"><article><span>Deflated Sharpe</span><strong>{percent(audit.deflatedSharpeProbability)}</strong><small>扣除 {audit.trialCount} 次试验选择偏差后，Sharpe 仍为正的置信度</small></article><article><span>回测过拟合概率</span><strong>{percent(audit.probabilityOfBacktestOverfitting)}</strong><small>基于组合对称交叉验证；越低越好</small></article><article><span>Probabilistic Sharpe</span><strong>{percent(audit.probabilisticSharpeProbability)}</strong><small>考虑偏度与峰度后的统计把握</small></article></div>
      <div className="forecast-audit-ledger"><span>观察 Sharpe <b>{number(audit.observedSharpe)}</b></span><span>多试验噪声门槛 <b>{number(audit.expectedMaximumSharpe)}</b></span><span>最短记录要求 <b>{audit.minimumTrackRecordLength ?? '—'} 期</b></span><span>可用收益观察 <b>{audit.returnObservationCount} 期</b></span><span>CSCV 组合 <b>{audit.combinationCount}</b></span></div>
    </> : <p className="forecast-audit-empty">{audit.reason || '真实样本不足，暂不能量化多重试验造成的选择偏差。'}</p>}
  </section>;
}
