import { useEffect, useState } from 'react';

import {
  agentWaitButtonLabel,
  agentWaitMessage,
  capitalAgentStatusMessage
} from './agentWaitPresentation';
import {
  CapitalFactorObservation,
  CapitalInterpretation,
  CapitalSignalEvaluation,
  CapitalWatchCondition
} from './marketIntelTypes';

const confidenceLabels = { LOW: '低置信度', MID: '中置信度', HIGH: '高置信度' } as const;
const statusLabels = {
  PENDING: '等待分析',
  RUNNING: '模型分析中',
  SUCCEEDED: '模型解读',
  FALLBACK: '规则兜底',
  INSUFFICIENT_DATA: '数据不足',
  FAILED: '解读失败'
} as const;
const marketStateLabels: Record<string, string> = {
  VOLUME_EXPANSION_OUTFLOW: '放量资金流出',
  VOLUME_EXPANSION_INFLOW: '放量资金流入',
  PRICE_FLOW_DIVERGENCE: '价格与资金背离',
  MIXED: '多维信号分化',
  NEUTRAL: '暂未形成一致方向',
  INTRADAY_REVERSAL: '日内资金反转',
  INSUFFICIENT_DATA: '证据覆盖不足'
};
const dimensionLabels: Record<string, string> = {
  VOLUME: '量能',
  TURNOVER: '换手活跃度',
  FLOW: '资金方向',
  ORDER_STRUCTURE: '订单结构',
  INTRADAY: '日内节奏',
  MULTI_PERIOD: '多周期趋势'
};
const hypothesisLabels: Record<string, string> = {
  ACCUMULATION: '资金积累',
  DISTRIBUTION: '资金派发',
  ORDER_SPLITTING: '疑似拆单',
  HIDDEN_FLOW: '疑似隐藏资金',
  LIQUIDITY_SHIFT: '流动性变化'
};
function formatEvidence(value: number, unit: string) {
  if (unit === '元') {
    if (Math.abs(value) >= 1e8) return (value >= 0 ? '+' : '') + (value / 1e8).toFixed(2) + ' 亿元';
    if (Math.abs(value) >= 1e4) return (value >= 0 ? '+' : '') + (value / 1e4).toFixed(2) + ' 万元';
  }
  if (unit === '%') return value.toFixed(2) + '%';
  if (unit === '比例') return (value * 100).toFixed(2) + '%';
  return value.toFixed(2) + ' ' + unit;
}

function percent(value?: number | null) {
  if (value == null) return '--';
  const amount = value * 100;
  return `${amount > 0 ? '+' : ''}${amount.toFixed(2)}%`;
}

export function CapitalAgentInterpretationPanel({
  interpretation,
  factorObservations,
  historicalEvaluations = [],
  evaluationVersion = 'capital-evaluation-v2',
  watchConditions,
  busy,
  onRun
}: {
  interpretation: CapitalInterpretation | null;
  factorObservations: CapitalFactorObservation[];
  historicalEvaluations?: CapitalSignalEvaluation[];
  evaluationVersion?: string;
  watchConditions: CapitalWatchCondition[];
  busy: boolean;
  onRun: () => void;
}) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    if (!busy) {
      setElapsedSeconds(0);
      return undefined;
    }
    const startedAt = Date.now();
    setElapsedSeconds(0);
    const timer = window.setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [busy]);

  const factorByRef = new Map(factorObservations.map((factor) => [
    factor.factorRef ?? 'factor:' + factor.factorCode + ':' + factor.observedAt.replace(/:00$/, ''),
    factor
  ]));
  const evidenceByRef = new Map((interpretation?.evidenceRefs ?? []).map((evidence) => [evidence.ref, evidence]));
  const evaluationByRef = new Map(historicalEvaluations.map((evaluation) => [
    `evaluation:${evaluationVersion}:${evaluation.signalType}:${evaluation.horizonDays}d`,
    evaluation
  ]));
  const watchById = new Map(watchConditions.map((condition) => [condition.id, condition]));
  const reason = interpretation?.fallbackReason
    ? capitalAgentStatusMessage(interpretation.fallbackReason)
    : null;
  const observations = interpretation?.observations ?? [];
  const counterEvidence = interpretation?.counterEvidence ?? [];
  const watchRefs = interpretation?.watchConditionRefs ?? [];

  return (
    <section className="market-intel-agent" aria-labelledby="capital-agent-heading">
      <header>
        <div>
          <p className="market-intel-kicker">Evidence-bound Agent</p>
          <h3 id="capital-agent-heading">Agent 深度解读</h3>
        </div>
        <button className="primary-button" type="button" disabled={busy} onClick={onRun}>
          {busy ? agentWaitButtonLabel(elapsedSeconds) : interpretation ? '重新运行 Agent 解读' : '运行 Agent 解读'}
        </button>
      </header>

      {!interpretation && !busy && (
        <div className="market-intel-agent-primer">
          <span aria-hidden="true">AI</span>
          <p>点击后才调用模型。Agent 只组织已登记因子和可追溯指标，拆单、吸筹、出货始终作为待验证假设。</p>
        </div>
      )}
      {busy && <p className="market-intel-agent-running" role="status">{agentWaitMessage(elapsedSeconds)}</p>}
      {interpretation && (
        <div className={'market-intel-agent-report' + (busy && interpretation.status === 'PENDING' ? ' is-pending' : '')}>
          <div className={'market-intel-agent-verdict status-' + interpretation.status.toLowerCase()} role={interpretation.status === 'FAILED' ? 'alert' : 'status'}>
            <span>{statusLabels[interpretation.status]}</span>
            <strong>{marketStateLabels[interpretation.marketState ?? ''] ?? '资金行为待确认'}</strong>
            {interpretation.confidence && <small>{confidenceLabels[interpretation.confidence]}</small>}
          </div>
          {reason && <p className="market-intel-agent-notice">{reason}</p>}
          <p className="market-intel-agent-summary">{interpretation.executiveSummary ?? interpretation.plainSummary}</p>

          {observations.length > 0 && (
            <section className="market-intel-agent-observations" aria-label="分维度观察">
              {observations.map((observation, index) => (
                <article key={observation.dimension + '-' + index}>
                  <header>
                    <span>{dimensionLabels[observation.dimension] ?? observation.dimension}</span>
                    <small>{observation.factorRefs.length} 个因子</small>
                  </header>
                  <p>{observation.claim}</p>
                  <ul className="market-intel-agent-evidence">
                    {observation.factorRefs.map((ref) => {
                      const factor = factorByRef.get(ref);
                      return factor ? (
                        <li key={ref}>
                          <span>因子</span>
                          <strong>{factor.label}</strong>
                          <small>{factor.value.toFixed(4)}{factor.state ? ' · ' + factor.state : ''}</small>
                        </li>
                      ) : null;
                    })}
                    {observation.metricRefs.map((ref) => {
                      const evidence = evidenceByRef.get(ref);
                      return evidence ? (
                        <li key={ref}>
                          <span>指标</span>
                          <strong>{evidence.label}</strong>
                          <small>{formatEvidence(evidence.value, evidence.unit)}</small>
                        </li>
                      ) : null;
                    })}
                    {(observation.evaluationRefs ?? []).map((ref) => {
                      const evaluation = evaluationByRef.get(ref);
                      return evaluation ? (
                        <li key={ref}>
                          <span>历史评价</span>
                          <strong>{evaluation.signalLabel} · {evaluation.horizonDays} 日</strong>
                          <small>{evaluation.sampleCount} 个样本 · {evaluation.excessAverageReturn != null
                            ? `超额 ${percent(evaluation.excessAverageReturn)}`
                            : `平均 ${percent(evaluation.averageReturn)}`}</small>
                        </li>
                      ) : null;
                    })}
                  </ul>
                </article>
              ))}
            </section>
          )}

          {interpretation.hypotheses.map((hypothesis, index) => (
            <article className="market-intel-hypothesis" key={hypothesis.type + '-' + index}>
              <header>
                <span>{hypothesisLabels[hypothesis.type] ?? hypothesis.type.replace(/_/g, ' ')}</span>
                <strong className={'confidence-' + hypothesis.confidence.toLowerCase()}>
                  {confidenceLabels[hypothesis.confidence]}
                </strong>
              </header>
              <p>{hypothesis.claim}</p>
              <dl>
                <div>
                  <dt>支撑证据</dt>
                  <dd>{hypothesis.supportingMetricRefs.map((ref) => evidenceByRef.get(ref))
                    .filter(Boolean)
                    .map((evidence) => evidence!.label + ' ' + formatEvidence(evidence!.value, evidence!.unit))
                    .join('；') || '有效引用已由服务端校验'}</dd>
                </div>
                <div><dt>反证 / 限制</dt><dd>{hypothesis.counterEvidence.join('；') || '暂无'}</dd></div>
              </dl>
            </article>
          ))}

          <div className="market-intel-agent-columns">
            <section>
              <h4>反向证据</h4>
              <ul>{counterEvidence.length
                ? counterEvidence.map((item) => <li key={item}>{item}</li>)
                : <li>当前没有额外反向证据</li>}</ul>
            </section>
            <section>
              <h4>下一步观察</h4>
              <ul>{watchRefs.length
                ? watchRefs.map((ref) => watchById.get(ref)).filter(Boolean)
                  .map((condition) => <li key={condition!.id}>{condition!.label}</li>)
                : (interpretation.observationPoints ?? []).map((point) => <li key={point}>{point}</li>)}</ul>
            </section>
          </div>

          {interpretation.dataGaps.length > 0 && (
            <details className="market-intel-agent-audit">
              <summary>数据缺口与审计信息</summary>
              <ul>{interpretation.dataGaps.map((gap) => <li key={gap}>{gap}</li>)}</ul>
              {interpretation.rejectedOutputCount > 0 && (
                <>
                  <p>{interpretation.rejectedOutputCount} 项模型输出未通过证据门禁</p>
                  {(interpretation.rejectionReasons ?? []).length > 0 && (
                    <ul className="market-intel-agent-rejections">
                      {(interpretation.rejectionReasons ?? []).map((item, index) => (
                        <li key={index + '-' + item}>{item}</li>
                      ))}
                    </ul>
                  )}
                </>
              )}
              <small>因子 {interpretation.factorVersion ?? '--'} · 信号 {interpretation.signalVersion ?? '--'}</small>
            </details>
          )}
          <p className="market-intel-disclaimer">{interpretation.disclaimer}</p>
        </div>
      )}
    </section>
  );
}
