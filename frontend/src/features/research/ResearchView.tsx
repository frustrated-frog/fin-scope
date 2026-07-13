import { useEffect, useMemo, useState } from 'react';
import { Table } from '../../shared/components/Table';
import { ResearchReport, ResearchRun, ResearchRunDetail, ResearchThesis, ResearchThesisDetail } from '../../shared/types';
import { api } from '../../shared/api/client';
import { ResearchProgressPanel } from './ResearchProgressPanel';
import { ResearchReportReader } from './ResearchReportReader';
import { presentAgentRun } from './researchPresentation';

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
  useEffect(() => { if (!selectedThesisId) { setThesisDetail(null); return; } api<ResearchThesisDetail>(`/api/research/theses/${selectedThesisId}`).then(setThesisDetail).catch(() => setThesisDetail(null)); }, [selectedThesisId]);

  async function submit() {
    await onRun({ thesisId: selectedThesisId || undefined, runDate, themeCodes, maxSourcesPerTheme, includeDisabled });
  }

  async function createThesis() {
    if (!question.trim() || !subjectName.trim()) return;
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
  }

  if (report) {
    return <ResearchReportReader report={report} onBack={onCloseReport} />;
  }

  return (
    <section className="research-workbench">
      <div className="research-thesis-layout">
        <div className="research-thesis-panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Research thesis</p>
              <h3>研究命题</h3>
            </div>
            <span className="subtle-badge">{theses.filter((item) => item.status === 'OPEN').length} open</span>
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
            <button className="secondary-button" type="button" disabled={!question.trim() || !subjectName.trim()} onClick={createThesis}>新建命题</button>
          </div>
        </div>
        <div className="research-thesis-list" aria-label="研究命题列表">
          {theses.length ? theses.slice(0, 5).map((thesis) => (
            <button
              key={thesis.id}
              className={selectedThesisId === thesis.id ? 'research-thesis-card active' : 'research-thesis-card'}
              type="button"
              onClick={() => setSelectedThesisId(thesis.id)}
            >
              <span>{thesis.subjectType === 'COMPANY' ? '公司' : thesis.subjectType === 'INDUSTRY' ? '行业' : '自选'} · {thesis.subjectName}</span>
              <strong>{thesis.question}</strong>
              <small>{thesis.status === 'OPEN' ? '待验证' : thesis.status}</small>
            </button>
          )) : <p className="empty-state compact">还没有命题。先把你想验证的公司或行业判断写下来。</p>}
        </div>
      </div>
      {thesisDetail && <div className="research-thesis-detail">
        <div><p className="eyebrow">当前判断</p><strong>{thesisDetail.thesis.conclusion || '尚未形成结论'}</strong><p>{thesisDetail.thesis.nextValidation || '下一验证窗口尚未设置'}</p></div>
        {['SUPPORT','COUNTER','UNKNOWN'].map((stance) => <div key={stance}><p className="eyebrow">{stance === 'SUPPORT' ? '支持证据' : stance === 'COUNTER' ? '反证' : '未知项'}</p>{thesisDetail.findings.filter((item) => item.stance === stance).map((item) => <p key={item.id}>{item.summary}</p>) || <p>暂无</p>}</div>)}
        <div><p className="eyebrow">本次研究产物</p><strong>{thesisDetail.outputs.length} 项</strong><p>{thesisDetail.runs.length} 次绑定运行</p></div>
      </div>}
      <div className="research-control-panel">
        <div className="research-run-heading">
          <div>
            <p className="eyebrow">研究执行台</p>
            <h3>{selectedThesisId ? '为命题补充证据' : '启动探索性研究'}</h3>
          </div>
          <div className="research-run-context">
            <span>当前命题</span>
            <strong>{selectedThesisId ? theses.find((thesis) => thesis.id === selectedThesisId)?.subjectName || '已选命题' : '未绑定命题'}</strong>
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
        <button
          className="primary-button"
          type="button"
          disabled={busy || themeCodes.length === 0}
          onClick={submit}
        >
          {busy ? '正在启动研究' : selectedThesisId ? '开始补充研究' : '开始探索研究'}
        </button>
      </div>

      <div className="research-grid">
        <div className="panel">
          <div className="panel-head research-archive-head">
            <div>
              <p className="eyebrow">研究档案</p>
              <h3>历次研究运行</h3>
            </div>
            <span className="research-run-count">共 {runs.length} 次</span>
          </div>
          {runs[0] && <button className="research-latest-run" type="button" onClick={() => onOpenRun(runs[0].id)}>
            <span>最近一次 · {runs[0].runDate} · {runs[0].status}</span>
            <strong>新增 {runs[0].evidenceCount ?? 0} 条证据 →</strong>
          </button>}
          <Table
            headers={['日期', '命题', '状态', '来源', '事件', '证据', '学习/选题']}
            rows={runs.map((run) => [
              <button className="link-button" type="button" onClick={() => onOpenRun(run.id)}>{run.runDate}</button>,
              run.thesisId ? theses.find((thesis) => thesis.id === run.thesisId)?.subjectName || `#${run.thesisId}` : '探索',
              run.status,
              `${run.fetchedSourceCount ?? 0}/${run.sourceCount}`,
              `${run.eventCount ?? 0}`,
              `${run.evidenceCount ?? 0}`,
              `${run.learningTaskCount ?? 0}/${run.contentIdeaCount ?? 0}`
            ])}
            empty="还没有研究运行"
          />
        </div>

        <aside className="research-detail-panel">
          <div className="panel-head">
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
              <details className="research-diagnostic-section">
                <summary>查看执行步骤诊断</summary>
                <div className="research-plan-list">
                  {(detail.planSteps || []).length ? detail.planSteps.map((step) => (
                    <div className="research-plan-row" key={step.stepId}>
                      <div>
                        <strong>{step.title}</strong>
                        <span>{step.stepId} · {step.executor || 'system'}</span>
                        {(step.outputSummary || step.errorMessage || step.fallbackReason) && (
                          <small>{step.errorMessage || step.fallbackReason || step.outputSummary}</small>
                        )}
                      </div>
                      <div className="research-plan-meta">
                        <span>{presentRunStatus(step.status)}</span>
                        <small>{step.attempt ?? 0}/{step.maxAttempts ?? 1} · Δ{step.progressDelta ?? 0}</small>
                      </div>
                    </div>
                  )) : <p className="empty-state compact">没有计划步骤</p>}
                </div>
              </details>
              <div className="planned-source-list">
                <div className="section-kicker">计划来源</div>
                {(detail.plannedSources || []).length ? detail.plannedSources.map((source) => (
                  <div className="planned-source-row" key={`${source.sourceId}-${source.sourceName}`}>
                    <strong>{source.sourceName}</strong>
                    <span>{source.sourceTier || 'UNKNOWN'} · C{source.credibility ?? '-'} · {source.enabled ? 'ON' : 'OFF'}</span>
                  </div>
                )) : (
                  <p className="empty-state compact">没有来源快照</p>
                )}
              </div>
              <div className="agent-trace-list">
                <div className="section-kicker">智能处理记录</div>
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
                }) : <p className="empty-state compact">暂无智能处理记录</p>}
              </div>
            </>
          ) : (
            <p className="empty-state">选择一次运行查看 agent trace。</p>
          )}
        </aside>
      </div>
    </section>
  );
}

function presentRunStatus(status: string) {
  const labels: Record<string, string> = {
    RUNNING: '运行中', COMPLETED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败',
    PENDING: '等待中', SKIPPED: '已跳过', FALLBACK: '保底完成'
  };
  return labels[status] || status;
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
