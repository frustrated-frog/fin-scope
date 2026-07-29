import {
  ResearchAgentDecision,
  ResearchAgentTraceView,
  ResearchToolObservation
} from '../../shared/types';

const DECISION_LABELS: Record<string, string> = {
  TOOL_CALL: '调用研究工具',
  PLAN_PATCH: '局部重规划',
  FINISH: '完成校验',
  ABORT: '终止研究'
};

const TOOL_LABELS: Record<string, string> = {
  public_news_search: 'Tavily 公开资料搜索',
  evidence_assess: '证据缺口评估',
  source_scan: '来源扫描',
  report_synthesis: '报告合成'
};

export function ResearchAgentDecisionFlow({
  agentCore,
  remainingActions,
  planVersion
}: {
  agentCore?: ResearchAgentTraceView;
  remainingActions: number;
  planVersion: number;
}) {
  if (!agentCore) return null;

  const { state, decisions, observations, trajectoryMetrics } = agentCore;
  const observationByDecision = new Map(observations.map((item) => [item.decisionId, item]));
  const evidenceDelta = observations.reduce((total, item) => total + item.evidenceDelta, 0);
  const sourceDelta = observations.reduce((total, item) => total + item.sourceDelta, 0);
  const retryCount = observations.reduce((total, item) => total + Math.max(0, (item.attemptCount || 1) - 1), 0);
  const activeGap = [...decisions].reverse().find((item) => item.targetGap)?.targetGap
    || '等待下一轮证据评估确认缺口';
  const convergence = presentConvergence(state.status, state.finishRejectionCount, state.noProgressCount);
  const evidencePulse = Math.min(100, 20 + evidenceDelta * 14 + sourceDelta * 10);

  return (
    <section className="research-agent-flow" aria-label="Agent 决策流">
      <header className="research-agent-flow-head">
        <div className="research-agent-flow-title">
          <span className="research-agent-flow-kicker"><i aria-hidden="true" />Agent flight recorder</span>
          <h3>{state.currentSubgoal || '正在判断下一步研究动作'}</h3>
          <p>{state.planSummary || 'Agent 将根据最新观察持续选择工具、评估缺口并决定是否收束。'}</p>
        </div>
        <dl className="research-agent-flow-stats" aria-label="Agent 当前状态">
          <div><dt>决策轮次</dt><dd>{state.decisionCount}</dd></div>
          <div><dt>计划版本</dt><dd>V{planVersion}</dd></div>
          <div><dt>剩余搜索</dt><dd>{Math.max(0, remainingActions)} 次</dd></div>
          <div><dt>轨迹质量</dt><dd>{trajectoryMetrics?.qualityScore ?? '—'}</dd></div>
        </dl>
      </header>

      <div className="research-agent-health" aria-label="研究证据健康">
        <article className="evidence">
          <span>证据健康</span>
          <strong>+{evidenceDelta} 证据 · +{sourceDelta} 来源</strong>
          <p>{state.evidenceSummary || '等待第一条可验证证据进入研究账本。'}</p>
          <i className="research-agent-evidence-pulse" aria-hidden="true"><b style={{ width: `${evidencePulse}%` }} /></i>
        </article>
        <article className="gap">
          <span>当前缺口</span>
          <strong>{activeGap}</strong>
          <p>{state.replanCount > 0 ? `已重规划 ${state.replanCount} 次，继续寻找独立证据。` : 'Agent 将优先处理这个缺口。'}</p>
        </article>
        <article className={`convergence ${convergence.tone}`}>
          <span>收敛状态</span>
          <strong>{convergence.label}</strong>
          <p>{convergence.description}</p>
        </article>
        <div className="research-agent-health-signals">
          <span>{retryCount} 次自动重试</span>
          <span>{state.fallbackCount} 次规则降级</span>
          <span>{state.noProgressCount} 次无新增</span>
          {trajectoryMetrics && <span className="quality">轨迹质量 {trajectoryMetrics.qualityScore}</span>}
        </div>
      </div>

      {decisions.length ? (
        <ol className="research-agent-timeline" aria-label="Agent 决策与观察时间线">
          {decisions.map((decision) => (
            <DecisionEntry
              decision={decision}
              observation={observationByDecision.get(decision.id)}
              key={decision.id}
            />
          ))}
        </ol>
      ) : (
        <p className="research-agent-empty">任务合同已建立，Agent 正在生成第一步可执行决策。</p>
      )}
    </section>
  );
}

function DecisionEntry({
  decision,
  observation
}: {
  decision: ResearchAgentDecision;
  observation?: ResearchToolObservation;
}) {
  const rejectedFinish = decision.decisionType === 'FINISH' && decision.status === 'REJECTED';
  const modelTimeoutFallback = decision.decisionMode === 'DETERMINISTIC'
    && decision.validationError?.startsWith('MODEL_TIMEOUT');
  const tone = rejectedFinish || decision.status === 'FAILED'
    ? 'warning'
    : decision.decisionType === 'PLAN_PATCH'
      ? 'replan'
      : decision.decisionType.toLowerCase();

  return (
    <li className={`research-agent-entry ${tone}`} data-testid={`agent-decision-${decision.id}`}>
      <div className="research-agent-entry-marker" aria-hidden="true">
        <span>{String(decision.iteration).padStart(2, '0')}</span>
      </div>
      <article className="research-agent-decision-card">
        <header>
          <div>
            <span className="research-agent-decision-type">
              {rejectedFinish ? '完成校验未通过' : DECISION_LABELS[decision.decisionType] || decision.decisionType}
            </span>
            {decision.toolCode && <strong>{TOOL_LABELS[decision.toolCode] || decision.toolCode}</strong>}
          </div>
          <div className="research-agent-decision-badges">
            {decision.decisionMode === 'DETERMINISTIC' && <span className="fallback">规则降级</span>}
            <span>{Math.round(decision.confidence * 100)}% 置信</span>
          </div>
        </header>
        <p className="research-agent-decision-summary">{decision.decisionSummary || decision.currentSubgoal || '已记录决策'}</p>
        {(decision.targetGap || decision.expectedObservation) && (
          <div className="research-agent-expectation">
            {decision.targetGap && <p><span>针对缺口</span>{decision.targetGap}</p>}
            {decision.expectedObservation && <p><span>预期观察</span>{decision.expectedObservation}</p>}
          </div>
        )}
        {decision.validationError && (
          <p
            className={modelTimeoutFallback ? 'research-agent-fallback-detail' : 'research-agent-validation'}
            role="status"
          >
            {decision.validationError}
          </p>
        )}
        {observation ? <ObservationCard observation={observation} /> : (
          decision.decisionType === 'TOOL_CALL' && decision.status !== 'FAILED'
            ? <p className="research-agent-observation-pending">等待工具返回 Observation…</p>
            : null
        )}
      </article>
    </li>
  );
}

function ObservationCard({ observation }: { observation: ResearchToolObservation }) {
  const noProgress = observation.status === 'NO_PROGRESS';
  const retryFailed = observation.status === 'RETRYABLE_ERROR';
  const failed = retryFailed || observation.status === 'FAILED' || observation.status === 'TERMINAL_ERROR';
  const label = retryFailed ? '重试未恢复' : noProgress ? '无新增' : failed ? '工具失败' : '获得新观察';
  return (
    <section className={`research-agent-observation ${noProgress ? 'no-progress' : failed ? 'failed' : 'success'}`} aria-label={`Observation：${label}`}>
      <header>
        <span>Observation</span>
        <strong>{label}</strong>
        {(observation.evidenceDelta > 0 || observation.sourceDelta > 0) && (
          <small>+{observation.evidenceDelta} 证据 · +{observation.sourceDelta} 来源</small>
        )}
      </header>
      <p>{observation.observationSummary || '工具已返回结构化观察。'}</p>
      {observation.newInformation && observation.newInformation !== observation.observationSummary && (
        <small>{observation.newInformation}</small>
      )}
      {observation.errorType && (
        <small>
          错误类型：{observation.errorType}
          {observation.retryable ? (observation.attemptCount && observation.attemptCount > 1
            ? ' · 已完成自动重试' : ' · 可恢复后重试') : ''}
        </small>
      )}
    </section>
  );
}

function presentConvergence(status: string, finishRejections: number, noProgressCount: number) {
  if (status === 'COMPLETED') {
    return { tone: 'complete', label: '完成门槛通过', description: '正反证据与关键缺口已通过收束校验。' };
  }
  if (status === 'FAILED' || status === 'INTERRUPTED') {
    return { tone: 'blocked', label: '等待恢复', description: '轨迹已保存，可从最近检查点继续。' };
  }
  if (noProgressCount >= 2) {
    return { tone: 'warning', label: '需要更换取证路径', description: '连续查询没有带来新的独立信息。' };
  }
  if (finishRejections > 0) {
    return { tone: 'active', label: '继续取证', description: '完成校验未通过，Agent 正补齐关键证据。' };
  }
  return { tone: 'active', label: '证据积累中', description: '研究尚未满足最终收束条件。' };
}
