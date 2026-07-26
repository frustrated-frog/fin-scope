import { ResearchMissionGap, ResearchMissionTask, ResearchMissionView } from '../../shared/types';

const INTENT_LABELS: Record<string, string> = {
  BASELINE: '基线',
  SUPPORT: '支持',
  COUNTER: '反方',
  PRIMARY: '一手',
  BREADTH: '扩展',
  ASSESS: '判断',
  SYNTHESIS: '合成'
};

const MISSION_STATUS: Record<string, string> = {
  PENDING: '等待规划',
  PLANNING: '正在规划',
  RUNNING: '研究进行中',
  COMPLETED: '研究已收束',
  PARTIAL_SUCCESS: '带缺口收束',
  FAILED: '研究失败',
  INTERRUPTED: '研究已中断'
};

export function ResearchMissionMap({ mission }: { mission?: ResearchMissionView }) {
  if (!mission) return null;

  const latestGap = mission.gaps[mission.gaps.length - 1];
  const completedTasks = mission.tasks.filter((task) => ['COMPLETED', 'SKIPPED'].includes(task.status)).length;
  const progress = mission.tasks.length ? Math.round((completedTasks / mission.tasks.length) * 100) : 0;
  const activeTask = mission.tasks.find((task) => task.taskKey === mission.mission.activeTaskKey);
  const tools = new Map(mission.tools.map((tool) => [tool.code, tool]));

  return (
    <section className="research-mission-map" aria-label="研究作战图">
      <header className="research-mission-contract">
        <div className="research-mission-title">
          <div className="research-mission-kicker">
            <span>Mission control</span>
            <span className={`research-mission-mode ${mission.mission.planningMode.toLowerCase()}`}>
              {mission.mission.planningMode === 'LLM_VALIDATED' ? 'Agent 计划' : '规则计划'}
            </span>
          </div>
          <h4>{mission.mission.goal}</h4>
          <p>{mission.mission.scopeSummary}</p>
        </div>
        <div className="research-mission-state">
          <span>{MISSION_STATUS[mission.mission.status] || mission.mission.status}</span>
          <strong>{completedTasks}<small> / {mission.tasks.length}</small></strong>
          <small>{mission.mission.subject || '研究对象'} · V{mission.mission.planVersion}</small>
        </div>
        <div className="research-mission-criteria">
          <span>成功条件</span>
          <ul>
            {mission.mission.successCriteria.slice(0, 3).map((criterion) => <li key={criterion}>{criterion}</li>)}
          </ul>
        </div>
      </header>

      <div className="research-mission-body">
        <div className="research-mission-graph">
          <div className="research-mission-section-head">
            <div>
              <span>Task graph</span>
              <strong>{activeTask ? activeTask.title : '任务图谱'}</strong>
            </div>
            <small>{activeTask ? taskStatusLabel(activeTask) : `${progress}% 已完成`}</small>
          </div>
          <div
            className={`research-evidence-pulse ${mission.mission.status === 'RUNNING' ? 'running' : ''}`}
            role="progressbar"
            aria-label="研究任务完成度"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress}
          >
            <span style={{ width: `${progress}%` }} />
            {mission.mission.status === 'RUNNING' && <i aria-hidden="true" style={{ left: `${Math.max(2, progress)}%` }} />}
          </div>
          <ol className="research-mission-tasks" aria-label="研究任务依赖顺序">
            {mission.tasks.map((task, index) => (
              <li
                className={`research-mission-task ${task.status.toLowerCase()}`}
                data-testid="mission-task"
                data-status={task.status}
                key={task.taskKey}
              >
                <span className="research-mission-task-index">{String(index + 1).padStart(2, '0')}</span>
                <article>
                  <div className="research-mission-task-head">
                    <span className={`research-intent intent-${task.intent.toLowerCase()}`}>
                      {INTENT_LABELS[task.intent] || task.intent}
                    </span>
                    <span className="research-mission-task-status">{taskStatusLabel(task)}</span>
                  </div>
                  <strong>{task.title}</strong>
                  <p>{task.status === 'RUNNING'
                    ? task.queryText || task.question
                    : task.outputSummary || task.rationale || task.question}</p>
                  <footer>
                    <span>{tools.get(task.toolCode)?.name || task.toolCode}</span>
                    {(task.evidenceDelta > 0 || task.sourceDelta > 0) && (
                      <small>+{task.evidenceDelta} 证据 · +{task.sourceDelta} 来源</small>
                    )}
                    {task.skipReason && <small>{presentSkipReason(task.skipReason)}</small>}
                  </footer>
                </article>
              </li>
            ))}
          </ol>
        </div>

        <aside className="research-gap-ledger" aria-label="证据缺口账本">
          <div className="research-mission-section-head">
            <div><span>Evidence gate</span><strong>证据缺口账本</strong></div>
            <small>{latestGap?.sufficient ? '门槛满足' : '持续校验'}</small>
          </div>
          {latestGap ? (
            <>
              <div className="research-gap-metrics">
                <GapMetric label="有效证据" value={latestGap.evidenceCount} target={6} />
                <GapMetric label="独立来源" value={latestGap.sourceCount} target={2} />
                <GapMetric label="支持证据" value={latestGap.supportCount} target={1} />
                <GapMetric label="反方证据" value={latestGap.counterCount} target={1} />
              </div>
              <div className={`research-gap-next ${latestGap.sufficient ? 'sufficient' : ''}`}>
                <span>{latestGap.sufficient ? '停止条件' : '下一证据意图'}</span>
                <strong>{latestGap.sufficient ? '证据门槛已满足' : presentIntent(latestGap.recommendedIntent)}</strong>
              </div>
              {latestGap.warnings.length ? (
                <ul className="research-gap-warnings">
                  {latestGap.warnings.map((warning) => <li key={warning}>{warning}</li>)}
                </ul>
              ) : <p className="research-gap-clear">数量、来源和正反覆盖均已达到合同标准。</p>}
              <GapHistory gaps={mission.gaps} />
            </>
          ) : (
            <p className="research-gap-empty">完成基线来源扫描后，这里会记录第一份证据缺口快照。</p>
          )}
        </aside>
      </div>

      <footer className="research-mission-tools">
        <span>本次能力边界</span>
        <div>
          {mission.tools.map((tool) => (
            <span key={tool.code} title={tool.description}>
              <i aria-hidden="true" className={tool.budgetType === 'EXTERNAL_ACTION' ? 'external' : ''} />
              {tool.name}<small>{tool.budgetType === 'EXTERNAL_ACTION' ? '动作预算' : '内部只读'}</small>
            </span>
          ))}
        </div>
      </footer>
    </section>
  );
}

function GapMetric({ label, value, target }: { label: string; value: number; target: number }) {
  const percent = Math.min(100, Math.round((value / target) * 100));
  return (
    <article>
      <span>{label}</span>
      <strong>{value} / {target}</strong>
      <div aria-hidden="true"><span style={{ width: `${percent}%` }} /></div>
    </article>
  );
}

function GapHistory({ gaps }: { gaps: ResearchMissionGap[] }) {
  const recent = gaps.slice(-3).reverse();
  if (recent.length < 2) return null;
  return (
    <div className="research-gap-history">
      <span>最近判断</span>
      {recent.map((gap) => (
        <div key={`${gap.assessmentIndex}-${gap.stateHash}`}>
          <small>#{gap.assessmentIndex} · {gap.afterTaskKey || 'baseline'}</small>
          <strong>{gap.evidenceCount} 证据 / {gap.sourceCount} 来源</strong>
        </div>
      ))}
    </div>
  );
}

function taskStatusLabel(task: ResearchMissionTask) {
  if (task.status === 'RUNNING') {
    if (task.toolCode === 'public_news_search') return '正在取证';
    if (task.toolCode === 'source_scan') return '正在扫描';
    if (task.toolCode === 'evidence_assess') return '正在判断';
    if (task.toolCode === 'report_synthesis') return '正在合成';
    return '执行中';
  }
  const labels: Record<string, string> = {
    PENDING: '等待执行',
    COMPLETED: '已完成',
    SKIPPED: '已跳过',
    FAILED: '执行失败',
    INTERRUPTED: '已中断'
  };
  return labels[task.status] || task.status;
}

function presentIntent(intent: string) {
  const labels: Record<string, string> = {
    SUPPORT: '补充支持证据',
    COUNTER: '寻找反方与风险证据',
    PRIMARY: '补充一手来源',
    BREADTH: '扩大证据覆盖',
    NONE: '无需继续搜索'
  };
  return labels[intent] || intent;
}

function presentSkipReason(reason: string) {
  return reason === 'SUFFICIENT_EVIDENCE' ? '证据已充分' : reason;
}
