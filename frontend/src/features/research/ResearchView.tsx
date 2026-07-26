import { useEffect, useMemo, useState } from 'react';
import { ResearchReport, ResearchRun, ResearchRunDetail, ResearchThesis, ResearchThesisDetail } from '../../shared/types';
import { api } from '../../shared/api/client';
import { ResearchProgressPanel } from './ResearchProgressPanel';
import { ResearchReportReader } from './ResearchReportReader';
import {
  groupThesisFindings,
  PresentedFindingLane,
  presentAgentRun,
  presentConfidence,
  presentThesisStage,
  summarizeResearchDiagnostics
} from './researchPresentation';

export function ResearchView({
  runs,
  theses,
  detail,
  report,
  busy,
  reportBusy,
  onRun,
  onCreateThesis,
  onOpenRun,
  onOpenReport,
  onRegenerateReport,
  onResumeRun,
  onEvaluateRun,
  onCloseReport
}: {
  runs: ResearchRun[];
  theses: ResearchThesis[];
  detail: ResearchRunDetail | null;
  report: ResearchReport | null;
  busy: boolean;
  reportBusy: boolean;
  onRun: (input: {
    thesisId?: number;
    runDate: string;
    themeCodes: string[];
    maxSourcesPerTheme: number;
    includeDisabled: boolean;
  }) => Promise<void>;
  onCreateThesis: (input: Omit<ResearchThesis, 'id' | 'status' | 'createdAt' | 'updatedAt'>) => Promise<ResearchThesis>;
  onOpenRun: (id: number) => Promise<void | ResearchRunDetail>;
  onOpenReport: (runId: number) => Promise<void>;
  onRegenerateReport: (runId: number) => Promise<void>;
  onResumeRun: (runId: number) => Promise<void>;
  onEvaluateRun: (runId: number) => Promise<void>;
  onCloseReport: () => void;
}) {
  const today = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const [runDate, setRunDate] = useState(today);
  const [themeCodes, setThemeCodes] = useState<string[]>(['china_macro', 'ai_startup', 'company_ipo']);
  const [maxSourcesPerTheme, setMaxSourcesPerTheme] = useState(3);
  const [includeDisabled, setIncludeDisabled] = useState(false);
  const [selectedThesisId, setSelectedThesisId] = useState<number | null>(null);
  const [question, setQuestion] = useState('');
  const [subjectType, setSubjectType] = useState<ResearchThesis['subjectType']>('COMPANY');
  const [subjectName, setSubjectName] = useState('');
  const [subjectCode, setSubjectCode] = useState('');
  const [thesisDetail, setThesisDetail] = useState<ResearchThesisDetail | null>(null);
  const [thesisDetailLoading, setThesisDetailLoading] = useState(false);
  const [creatingThesis, setCreatingThesis] = useState(false);
  const [thesisFormError, setThesisFormError] = useState('');
  const [runtimeAction, setRuntimeAction] = useState<'resume' | 'evaluate' | null>(null);

  async function runRuntimeAction(action: 'resume' | 'evaluate', runId: number) {
    if (runtimeAction) return;
    setRuntimeAction(action);
    try {
      await (action === 'resume' ? onResumeRun(runId) : onEvaluateRun(runId));
    } finally {
      setRuntimeAction(null);
    }
  }

  useEffect(() => {
    if (!selectedThesisId) {
      setThesisDetail(null);
      setThesisDetailLoading(false);
      return undefined;
    }
    let cancelled = false;
    setThesisDetailLoading(true);
    api<ResearchThesisDetail>(`/api/research/theses/${selectedThesisId}`)
      .then((nextDetail) => {
        if (!cancelled) setThesisDetail(nextDetail);
      })
      .catch(() => {
        if (!cancelled) setThesisDetail(null);
      })
      .finally(() => {
        if (!cancelled) setThesisDetailLoading(false);
      });
    return () => { cancelled = true; };
  }, [selectedThesisId]);

  async function submit() {
    await onRun({ thesisId: selectedThesisId || undefined, runDate, themeCodes, maxSourcesPerTheme, includeDisabled });
  }

  async function createThesis() {
    if (!question.trim() || !subjectName.trim() || creatingThesis) return;
    setCreatingThesis(true);
    setThesisFormError('');
    try {
      const thesis = await onCreateThesis({
        question: question.trim(),
        subjectType,
        subjectName: subjectName.trim(),
        subjectCode: subjectCode.trim() || undefined,
        conclusion: undefined,
        confidence: undefined,
        nextValidation: undefined
      });
      setSelectedThesisId(thesis.id);
      setQuestion('');
      setSubjectName('');
      setSubjectCode('');
    } catch (error) {
      setThesisFormError(error instanceof Error ? error.message : '命题创建失败，请稍后重试');
    } finally {
      setCreatingThesis(false);
    }
  }

  if (report) {
    return <ResearchReportReader report={report} onBack={onCloseReport} />;
  }

  const openThesisCount = theses.filter((item) => item.status === 'OPEN').length;
  const selectedThesis = theses.find((thesis) => thesis.id === selectedThesisId);
  const latestRun = runs[0];

  return (
    <section className="research-workbench research-apple-workbench">
      <header className="research-command-hero">
        <div className="research-command-copy">
          <p className="research-command-kicker">Research intelligence</p>
          <h2>把判断变成可验证的研究</h2>
          <p>先写下值得验证的命题，再决定研究范围。每次运行都留下证据、过程和下一步，而不是只给一个答案。</p>
        </div>
        <div className="research-command-metrics" aria-label="研究工作台概览">
          <article>
            <span>开放命题</span>
            <strong>{openThesisCount}</strong>
            <small>持续验证</small>
          </article>
          <article>
            <span>研究运行</span>
            <strong>{runs.length}</strong>
            <small>完整留痕</small>
          </article>
          <article>
            <span>最近状态</span>
            <strong className={`research-command-status ${latestRun ? runStatusTone(latestRun.status) : 'neutral'}`}>
              {latestRun ? presentRunStatus(latestRun.status) : '待启动'}
            </strong>
            <small>{latestRun?.runDate || '尚无运行'}</small>
          </article>
        </div>
      </header>

      <ol className="research-workflow-rail" aria-label="研究流程">
        <li className="active">
          <span>01</span>
          <div><strong>定义命题</strong><small>明确要验证什么</small></div>
        </li>
        <li className={selectedThesisId ? 'active' : ''}>
          <span>02</span>
          <div><strong>执行研究</strong><small>配置范围并收集证据</small></div>
        </li>
        <li className={detail ? 'active' : ''}>
          <span>03</span>
          <div><strong>形成判断</strong><small>阅读过程、报告与反证</small></div>
        </li>
      </ol>

      <div className="research-thesis-layout research-material" aria-label="研究命题工作区">
        <div className="research-thesis-panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">01 · Research thesis</p>
              <h3>研究命题</h3>
            </div>
            <span className="subtle-badge">{openThesisCount} open</span>
          </div>
          <p className="research-thesis-hint">把公司、行业或自选组合的核心判断写成可验证的问题；一次运行只是为命题补充证据，不等于直接给出结论。</p>
          <div className="research-thesis-form">
            <label>
              <span>研究问题</span>
              <input value={question} placeholder="例如：英伟达盈利预期是否仍在上修？" onChange={(event) => setQuestion(event.target.value)} />
            </label>
            <div className="research-thesis-fields">
              <label>
                <span>对象类型</span>
                <select value={subjectType} onChange={(event) => setSubjectType(event.target.value as ResearchThesis['subjectType'])}>
                  <option value="COMPANY">公司</option>
                  <option value="INDUSTRY">行业</option>
                  <option value="WATCHLIST">自选组合</option>
                </select>
              </label>
              <label>
                <span>研究对象</span>
                <input value={subjectName} placeholder="英伟达 / 半导体设备" onChange={(event) => setSubjectName(event.target.value)} />
              </label>
              <label>
                <span>代码（可选）</span>
                <input value={subjectCode} placeholder="NVDA" onChange={(event) => setSubjectCode(event.target.value)} />
              </label>
            </div>
            {thesisFormError && <p className="research-form-error" role="alert">{thesisFormError}</p>}
            <button
              className="secondary-button research-create-thesis-button"
              type="button"
              aria-busy={creatingThesis}
              disabled={creatingThesis || !question.trim() || !subjectName.trim()}
              onClick={createThesis}
            >
              {creatingThesis ? '正在建立命题…' : '新建命题'}
            </button>
          </div>
        </div>
        <div className="research-thesis-list" aria-label="研究命题列表">
          <div className="research-thesis-list-heading">
            <span>最近命题</span>
            <small>选择后查看判断并继续研究</small>
          </div>
          {theses.length ? theses.slice(0, 5).map((thesis) => {
            const findingCount = thesisDetail?.thesis.id === thesis.id ? thesisDetail.findings.length : 0;
            const stage = presentThesisStage(thesis, findingCount);
            return (
              <button
                key={thesis.id}
                className={selectedThesisId === thesis.id ? 'research-thesis-card active' : 'research-thesis-card'}
                type="button"
                aria-pressed={selectedThesisId === thesis.id}
                onClick={() => setSelectedThesisId(thesis.id)}
              >
                <span>{thesis.subjectType === 'COMPANY' ? '公司' : thesis.subjectType === 'INDUSTRY' ? '行业' : '自选'} · {thesis.subjectName}</span>
                <strong>{thesis.question}</strong>
                <small className={`research-thesis-stage ${stage.tone}`}>{stage.label}</small>
              </button>
            );
          }) : <p className="empty-state compact">还没有命题。先把你想验证的公司或行业判断写下来。</p>}
        </div>
      </div>
      {thesisDetailLoading && (
        <div className="research-selection-feedback" role="status">
          <span aria-hidden="true" />
          正在读取命题判断与证据…
        </div>
      )}
      {thesisDetail && <ThesisDecisionSummary detail={thesisDetail} />}
      <div className="research-control-panel research-material">
        <div className="research-run-heading">
          <div>
            <p className="eyebrow">02 · Research execution</p>
            <h3>{selectedThesisId ? '为命题补充证据' : '启动探索性研究'}</h3>
          </div>
          <div className="research-run-context">
            <span>当前命题</span>
            <strong>{selectedThesis?.subjectName || '未绑定命题'}</strong>
          </div>
        </div>
        <div className="research-execution-scope">
          <span className="scope-label">执行范围</span>
          <div className="research-controls">
          <label className="inline-select">
            <span>日期</span>
            <input type="date" value={runDate} onChange={(event) => setRunDate(event.target.value)} />
          </label>
          <label className="inline-select">
            <span>每主题来源数</span>
            <input
              min={1}
              max={8}
              type="number"
              value={maxSourcesPerTheme}
              onChange={(event) => setMaxSourcesPerTheme(Number(event.target.value))}
            />
          </label>
          <label className="research-toggle">
            <input
              type="checkbox"
              checked={includeDisabled}
              onChange={(event) => setIncludeDisabled(event.target.checked)}
            />
            <span>包含停用来源</span>
          </label>
          </div>
        </div>
        <p className="research-scope-note">默认覆盖：中国宏观、AI 创业、公司 / IPO。研究范围将随命题和来源配置逐步细化。</p>
        <div className="research-run-action">
          <div>
            <span>{selectedThesisId ? '本次运行将写回当前命题' : '本次运行将作为探索档案保存'}</span>
            <small>启动后可离开页面，运行进度会持续记录。</small>
          </div>
          <button
            className="primary-button research-start-button"
            type="button"
            aria-busy={busy}
            disabled={busy || themeCodes.length === 0}
            onClick={submit}
          >
            <span aria-hidden="true">{busy ? '◌' : '→'}</span>
            {busy ? '正在启动研究' : selectedThesisId ? '开始补充研究' : '开始探索研究'}
          </button>
        </div>
      </div>

      <div className="research-grid">
        <section className="panel research-archive-panel research-material" aria-label="研究运行档案">
          <div className="panel-head research-archive-head">
            <div>
              <p className="eyebrow">研究档案</p>
              <h3>历次研究运行</h3>
            </div>
            <span className="research-run-count">共 {runs.length} 次</span>
          </div>
          <ResearchRunList runs={runs} theses={theses} selectedRunId={detail?.run.id} onOpenRun={onOpenRun} />
        </section>

        <aside className="research-detail-panel research-material">
          <div className="panel-head research-detail-head">
            <div>
              <p className="eyebrow">Trace</p>
              <h3>运行细节</h3>
            </div>
            {detail?.reportAvailable && (
              <button className="primary-button compact" type="button" disabled={reportBusy} onClick={() => onOpenReport(detail.run.id)}>
                {reportBusy ? '正在打开' : '阅读研究报告'}
              </button>
            )}
            {detail?.canRegenerateReport && !detail.reportAvailable && (
              <button className="secondary-button compact" type="button" disabled={reportBusy} onClick={() => onRegenerateReport(detail.run.id)}>
                {reportBusy ? '正在补建报告' : '补建研究报告'}
              </button>
            )}
            {detail?.run.status === 'RUNNING' && !detail.reportAvailable && (
              <span className="research-report-pending">报告将在研究完成后提供</span>
            )}
          </div>
          {detail ? (
            <>
              <ResearchProgressPanel detail={detail} />
              <div className="run-summary">
                <strong>{presentRunStatus(detail.run.status)}</strong>
                <span>{detail.run.summary || '-'}</span>
              </div>
              <ResearchRuntimeEvaluationPanel
                detail={detail}
                busyAction={runtimeAction}
                onResume={() => runRuntimeAction('resume', detail.run.id)}
                onEvaluate={() => runRuntimeAction('evaluate', detail.run.id)}
              />
              <ResearchDiagnostics detail={detail} />
            </>
          ) : (
            <p className="empty-state">选择一次运行查看 agent trace。</p>
          )}
        </aside>
      </div>
    </section>
  );
}

function ResearchRuntimeEvaluationPanel({
  detail,
  busyAction,
  onResume,
  onEvaluate
}: {
  detail: ResearchRunDetail;
  busyAction: 'resume' | 'evaluate' | null;
  onResume: () => void;
  onEvaluate: () => void;
}) {
  const runtime = detail.runtime;
  const evaluation = detail.latestEvaluation;
  const checkpoint = runtime?.checkpoint;
  const budgetPercent = checkpoint?.maxActions
    ? Math.min(100, Math.round((checkpoint.consumedActions / checkpoint.maxActions) * 100)) : 0;

  return (
    <section className="research-runtime-eval" aria-label="研究运行时与评测">
      <header>
        <div><span>Runtime &amp; eval</span><strong>可恢复、可评测的研究执行</strong></div>
        {evaluation && <span className={`research-eval-gate ${evaluation.gateStatus.toLowerCase()}`}>{evaluation.gateStatus}</span>}
      </header>
      <div className="research-runtime-eval-grid">
        <article>
          <span>动作预算</span>
          <strong>{checkpoint ? `${checkpoint.consumedActions} / ${checkpoint.maxActions}` : '未记录'}</strong>
          <div className="research-runtime-budget" aria-label="动作预算使用率"><span style={{ width: `${budgetPercent}%` }} /></div>
          <small>{checkpoint ? `${checkpoint.phase} · ${checkpoint.currentNode}` : '旧运行暂无检查点'}</small>
        </article>
        <article>
          <span>离线评测</span>
          <strong>{evaluation ? evaluation.score : '—'}</strong>
          <small>{evaluation ? `${evaluation.metrics.length} 项指标 · ${evaluation.evaluatorVersion}` : '尚未建立评测基线'}</small>
        </article>
      </div>
      {evaluation?.criticalIssues.length ? (
        <p className="research-eval-issues">关键问题：{evaluation.criticalIssues.join('、')}</p>
      ) : checkpoint?.lastError ? <p className="research-eval-issues">最近中断：{checkpoint.lastError}</p> : null}
      <div className="research-runtime-detail-grid">
        {evaluation?.metrics.length ? (
          <section aria-label="评测指标">
            <strong>评测指标</strong>
            {evaluation.metrics.map((metric) => (
              <div className="research-eval-metric" key={metric.metricCode}>
                <span>{metric.label}</span>
                <strong>{metric.score}/{metric.maxScore}</strong>
              </div>
            ))}
          </section>
        ) : null}
        {runtime?.events.length ? (
          <section aria-label="最近运行事件">
            <strong>最近事件</strong>
            {[...runtime.events].slice(-3).reverse().map((event) => (
              <div className="research-runtime-event" key={`${event.sequenceNo}-${event.eventType}-${event.nodeId || ''}`}>
                <span>{presentRuntimeEvent(event.eventType)}</span>
                <small>#{event.sequenceNo} · {event.nodeId || 'runtime'}</small>
              </div>
            ))}
          </section>
        ) : null}
      </div>
      <footer>
        {runtime?.recoverable && (
          <button className="secondary-button compact" type="button" disabled={Boolean(busyAction)} onClick={onResume}>
            {busyAction === 'resume' ? '正在恢复…' : '从检查点恢复'}
          </button>
        )}
        <button className="secondary-button compact" type="button" disabled={Boolean(busyAction)} onClick={onEvaluate}>
          {busyAction === 'evaluate' ? '正在评测…' : evaluation ? '重新评测' : '运行离线评测'}
        </button>
      </footer>
    </section>
  );
}

function presentRuntimeEvent(type: string) {
  const labels: Record<string, string> = {
    RUN_CREATED: '运行创建', NODE_STARTED: '节点开始', NODE_COMPLETED: '节点完成', NODE_FAILED: '节点失败',
    RESUMED: '恢复执行', GUARD_TRIGGERED: '触发安全护栏', TERMINATED: '运行结束', RUNTIME_INTERRUPTED: '进程中断'
  };
  return labels[type] || type;
}

function presentRunStatus(status: string) {
  const labels: Record<string, string> = {
    RUNNING: '运行中', COMPLETED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败',
    PENDING: '等待中', SKIPPED: '已跳过', FALLBACK: '保底完成'
  };
  return labels[status] || status;
}

function runStatusTone(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'PARTIAL_SUCCESS' || status === 'RUNNING') return 'active';
  if (status === 'FAILED') return 'danger';
  return 'neutral';
}

function ResearchRunList({
  runs,
  theses,
  selectedRunId,
  onOpenRun
}: {
  runs: ResearchRun[];
  theses: ResearchThesis[];
  selectedRunId?: number;
  onOpenRun: (id: number) => Promise<void | ResearchRunDetail>;
}) {
  if (runs.length === 0) {
    return <p className="muted">还没有研究运行</p>;
  }

  return (
    <div className="research-run-list" aria-label="研究运行列表">
      <div className="research-run-list-head">
        <span>日期</span>
        <span>命题</span>
        <span>状态</span>
        <span>来源</span>
        <span>事件</span>
        <span>证据</span>
        <span>学习/选题</span>
      </div>
      <div className="research-run-list-body">
        {runs.map((run) => (
          <ResearchRunRow
            key={run.id}
            run={run}
            thesisName={researchRunThesisName(run, theses)}
            isSelected={selectedRunId === run.id}
            onOpen={() => onOpenRun(run.id)}
          />
        ))}
      </div>
    </div>
  );
}

function ResearchRunRow({
  run,
  thesisName,
  isSelected,
  onOpen
}: {
  run: ResearchRun;
  thesisName: string;
  isSelected: boolean;
  onOpen: () => void;
}) {
  const fetchedSources = run.fetchedSourceCount ?? 0;
  const sourceCount = run.sourceCount || 0;
  const sourcePercent = sourceCount ? Math.min(100, Math.round((fetchedSources / sourceCount) * 100)) : 0;
  const tone = runStatusTone(run.status);

  return (
    <button
      className={`research-run-row ${tone}${isSelected ? ' selected' : ''}`}
      type="button"
      onClick={onOpen}
      aria-current={isSelected ? 'true' : undefined}
      aria-label={`打开研究运行 ${run.runDate} ${thesisName} ${presentRunStatus(run.status)}`}
    >
      <span className="research-run-date-cell">
        <strong>{run.runDate}</strong>
        <small>RUN #{run.id}</small>
      </span>
      <span className="research-run-thesis-cell">
        <strong>{thesisName}</strong>
        <small>{run.summary || '等待打开运行细节查看 agent trace'}</small>
      </span>
      <span className="research-run-status-cell">
        <span className={`research-status-chip ${tone}`}>{presentRunStatus(run.status)}</span>
      </span>
      <span className="research-source-cell">
        <strong>{fetchedSources}/{sourceCount}</strong>
        <span className="research-source-mini-meter" aria-label={`来源进度 ${fetchedSources}/${sourceCount}`}>
          <span style={{ width: `${sourcePercent}%` }} />
        </span>
      </span>
      <span className="research-run-number-cell">{run.eventCount ?? 0}</span>
      <span className="research-run-number-cell">{run.evidenceCount ?? 0}</span>
      <span className="research-run-number-cell">{run.learningTaskCount ?? 0}/{run.contentIdeaCount ?? 0}</span>
    </button>
  );
}

function researchRunThesisName(run: ResearchRun, theses: ResearchThesis[]) {
  if (!run.thesisId) return '探索';
  return theses.find((thesis) => thesis.id === run.thesisId)?.subjectName || `命题 #${run.thesisId}`;
}

function presentDuration(durationMs: number) {
  if (durationMs < 1000) return `${durationMs} 毫秒`;
  return `${(durationMs / 1000).toFixed(durationMs < 10_000 ? 1 : 0)} 秒`;
}

function formatAgentDiagnostic(run: ResearchRunDetail['agentRuns'][number]) {
  return [
    `节点：${run.nodeName}`,
    `状态：${run.status}`,
    run.errorType ? `错误类型：${run.errorType}` : '',
    run.fallbackReason ? `保底原因：${run.fallbackReason}` : '',
    run.terminationReason ? `停止原因：${run.terminationReason}` : '',
    run.output ? `原始输出：\n${run.output}` : ''
  ].filter(Boolean).join('\n');
}

function ThesisDecisionSummary({ detail }: { detail: ResearchThesisDetail }) {
  const stage = presentThesisStage(detail.thesis, detail.findings.length);
  const findings = groupThesisFindings(detail.findings, 2);
  const latestRunDate = [...detail.runs]
    .sort((left, right) => right.runDate.localeCompare(left.runDate))[0]?.runDate;

  return (
    <section className="research-decision-summary" aria-label="命题决策摘要">
      <header className="research-decision-hero">
        <div>
          <div className="research-decision-stage-row">
            <span className={`research-decision-stage ${stage.tone}`}>{stage.label}</span>
            <span>{stage.description}</span>
          </div>
          <p className="eyebrow">当前研究判断</p>
          <h3>{detail.thesis.conclusion || '尚未形成稳定结论'}</h3>
        </div>
        <div className="research-decision-confidence">
          <span>判断置信度</span>
          <strong>{presentConfidence(detail.thesis.confidence)}</strong>
        </div>
      </header>

      <div className="research-next-validation">
        <span>下一验证点</span>
        <strong>{detail.thesis.nextValidation || '补充关键经营或行业数据后再次验证'}</strong>
      </div>

      <div className="research-finding-lanes">
        <FindingLane
          title="支持判断"
          description="哪些事实支持当前方向"
          lane={findings.SUPPORT}
          empty="尚无直接支持当前判断的证据"
          tone="support"
        />
        <FindingLane
          title="反向信号"
          description="什么变化可能推翻判断"
          lane={findings.COUNTER}
          empty="尚未发现足以推翻判断的信号"
          tone="counter"
        />
        <FindingLane
          title="仍待确认"
          description="还缺哪些关键变量"
          lane={findings.UNKNOWN}
          empty="关键变量已覆盖，继续关注新变化"
          tone="unknown"
        />
      </div>

      <footer className="research-decision-meta">
        <span>{detail.runs.length} 次研究</span>
        <span>{detail.outputs.length} 项关联产物</span>
        <span>{latestRunDate ? `最近研究 ${latestRunDate}` : '尚未绑定研究运行'}</span>
      </footer>
    </section>
  );
}

function FindingLane({
  title,
  description,
  lane,
  empty,
  tone
}: {
  title: string;
  description: string;
  lane: PresentedFindingLane;
  empty: string;
  tone: 'support' | 'counter' | 'unknown';
}) {
  return (
    <section className={`research-finding-lane ${tone}`} aria-label={title}>
      <header>
        <div><strong>{title}</strong><span>{description}</span></div>
        <small>{lane.total}</small>
      </header>
      {lane.items.length ? (
        <ul>{lane.items.map((finding) => <li key={finding.id}>{finding.summary}</li>)}</ul>
      ) : <p className="research-finding-empty">{empty}</p>}
      {lane.remaining > 0 && <span className="research-finding-remainder">另有 {lane.remaining} 条</span>}
    </section>
  );
}

function ResearchDiagnostics({ detail }: { detail: ResearchRunDetail }) {
  const summary = summarizeResearchDiagnostics(detail);
  const visibleSources = detail.plannedSources.slice(0, 4);
  const remainingSources = Math.max(0, detail.plannedSources.length - visibleSources.length);

  return (
    <details className="research-diagnostics">
      <summary>
        <div>
          <strong>研究过程与来源</strong>
          <span>{summary.label}</span>
        </div>
        <span className="research-diagnostics-toggle" aria-hidden="true" />
      </summary>

      <div className="research-diagnostics-content">
        <section className="research-diagnostics-section" aria-label="执行步骤">
          <header><strong>执行步骤</strong><span>{detail.planSteps.length} 步</span></header>
          <div className="research-plan-list">
            {detail.planSteps.length ? detail.planSteps.map((step) => (
              <div className="research-plan-row" key={step.stepId}>
                <div>
                  <strong>{step.title}</strong>
                  <span>{step.executor || '系统任务'}</span>
                  {(step.outputSummary || step.errorMessage || step.fallbackReason) && (
                    <small>{step.errorMessage || step.fallbackReason || step.outputSummary}</small>
                  )}
                </div>
                <div className="research-plan-meta">
                  <span>{presentRunStatus(step.status)}</span>
                  <small>{step.attempt ?? 0}/{step.maxAttempts ?? 1} 次尝试</small>
                </div>
              </div>
            )) : <p className="empty-state compact">本次运行没有步骤快照</p>}
          </div>
        </section>

        <section className="research-diagnostics-section" aria-label="来源快照">
          <header><strong>来源快照</strong><span>仅展示前 4 个</span></header>
          <div className="research-source-preview">
            {visibleSources.length ? visibleSources.map((source) => (
              <div className="planned-source-row" data-testid="planned-source" key={`${source.sourceId}-${source.sourceName}`}>
                <strong>{source.sourceName}</strong>
                <span>{presentSourceTier(source.sourceTier)} · 可信度 {source.credibility ?? '-'}{source.enabled === false ? ' · 已停用' : ''}</span>
              </div>
            )) : <p className="empty-state compact">本次运行没有来源快照</p>}
          </div>
          {remainingSources > 0 && <p className="research-source-remainder">另有 {remainingSources} 个来源未展开</p>}
        </section>

        <section className="research-diagnostics-section" aria-label="智能处理记录">
          <header><strong>智能处理记录</strong><span>{detail.agentRuns.length} 个节点</span></header>
          <div className="agent-trace-list">
            {detail.agentRuns.length ? detail.agentRuns.map((run) => {
              const presented = presentAgentRun(run);
              return (
                <div className="trace-row" key={run.id}>
                  <div>
                    <strong>{presented.label}</strong>
                    <span>{presentRunStatus(run.status)}</span>
                    <small>{presented.summary}</small>
                    {(run.output || run.errorType || run.terminationReason) && (
                      <details className="trace-diagnostic">
                        <summary>诊断信息</summary>
                        <pre>{formatAgentDiagnostic(run)}</pre>
                      </details>
                    )}
                  </div>
                  <small>{presentDuration(run.durationMs)}</small>
                </div>
              );
            }) : <p className="empty-state compact">本次运行没有智能处理记录</p>}
          </div>
        </section>
      </div>
    </details>
  );
}

function presentSourceTier(value?: string) {
  const labels: Record<string, string> = {
    REGULATOR: '监管机构',
    COMPANY: '公司来源',
    MEDIA: '媒体来源',
    CURATED_AI: '精选研究',
    RESEARCH: '研究机构'
  };
  return value ? labels[value] || value : '未分类来源';
}
