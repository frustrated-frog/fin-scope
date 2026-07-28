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
  public_news_search: '公开新闻检索',
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
          <div><dt>剩余动作</dt><dd>{Math.max(0, remainingActions)} 次</dd></div>
          <div><dt>轨迹质量</dt><dd>{trajectoryMetrics?.qualityScore ?? '—'}</dd></div>
        </dl>
      </header>

      <div className="research-agent-memory" aria-label="Agent 工作记忆">
        <article>
          <span>工作记忆</span>
          <p>{state.memorySummary || '等待第一条工具观察写入工作记忆。'}</p>
        </article>
        <article>
          <span>证据状态</span>
          <p>{state.evidenceSummary || '尚未形成证据摘要。'}</p>
        </article>
        <div className="research-agent-memory-signals">
          <span>重规划 <strong>{state.replanCount}</strong></span>
          <span>无新增 <strong>{state.noProgressCount}</strong></span>
          <span>完成拒绝 <strong>{state.finishRejectionCount}</strong></span>
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
  const failed = observation.status === 'FAILED';
  const label = noProgress ? '无新增' : failed ? '工具失败' : '获得新观察';
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
      {observation.errorType && <small>错误类型：{observation.errorType}{observation.retryable ? ' · 可调整后重试' : ''}</small>}
    </section>
  );
}
