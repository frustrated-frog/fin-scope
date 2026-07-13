import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { AttributionProgress, AttributionReport, AttributionResearchRunView } from '../../shared/types';

const stageLabels: Record<string, string> = {
  'question-plan': '拆解研究问题',
  'web-search': '全网搜索线索',
  'local-recall': '检索本地新闻',
  'chain-reason': '分析产业链关联',
  'evidence-rank': '整理证据',
  'attribution-synth': '综合生成归因'
};

const levelLabels: Record<string, string> = { HIGH: '高', MID: '中', LOW: '低' };
const trackLabels: Record<string, string> = {
  COMPANY: '公司事件', FUND_EXPOSURE: '基金暴露', INDUSTRY: '行业与产业链',
  MACRO: '宏观与政策', MARKET: '市场联动', COUNTER: '反证检查'
};
const stepStatusLabels: Record<string, string> = {
  COMPLETED: '已完成',
  PARTIAL: '部分完成',
  FAILED: '失败',
  SKIPPED: '跳过',
  RUNNING: '进行中',
  PLANNED: '待启动',
  PENDING: '待启动'
};
const ATTRIBUTION_POLL_INTERVAL_MS = 1200;
// 覆盖后台 90 秒研究预算，以及模型调用最长 300 秒超时。
const ATTRIBUTION_MAX_POLL_ATTEMPTS = 300;

function levelDots(level?: string) {
  const map: Record<string, string> = { HIGH: '●●●', MID: '●●○', LOW: '●○○' };
  return map[level || 'MID'] || '●●○';
}

export function AttributionReaderView({
  taskId,
  reportId,
  code,
  type,
  name,
  changePct,
  onBack
}: {
  taskId?: string;
  reportId: number;
  code: string;
  type?: 'STOCK' | 'FUND' | 'SECTOR';
  name?: string;
  changePct?: number;
  onBack: () => void;
}) {
  const [stages, setStages] = useState<string[]>([]);
  const [clues, setClues] = useState<string[]>([]);
  const [currentStage, setCurrentStage] = useState('question-plan');
  const [report, setReport] = useState<AttributionReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [researchRun, setResearchRun] = useState<AttributionResearchRunView | null>(null);
  const [history, setHistory] = useState<AttributionReport[]>([]);
  const esRef = useRef<EventSource | null>(null);

  const reachStage = (stage?: string) => {
    if (!stage) {
      return;
    }
    setCurrentStage(stage);
    setStages((prev) => (prev.includes(stage) ? prev : [...prev, stage]));
  };

  useEffect(() => {
    let disposed = false;
    let pollTimer: number | undefined;
    let pollAttempts = 0;
    const resolveReport = async () => {
      try {
        const next = await api<AttributionReport>(`/api/attribution/reports/${reportId}`);
        if (disposed) {
          return;
        }
        if (next.status === 'COMPLETED') {
          setReport(next);
          setDone(true);
          return;
        }
        if (next.status === 'FAILED') {
          setError(next.errorMessage || '归因失败');
          setDone(true);
          return;
        }
        try {
          const run = await api<AttributionResearchRunView>(`/api/attribution/reports/${reportId}/run`);
          if (!disposed) setResearchRun(run);
        } catch {
          // 运行记录可能仍在首次事务中创建，下一轮继续恢复。
        }
      } catch {
        // SSE 断开时报告可能仍在写入，继续有限次数轮询。
      }
      if (!disposed && pollAttempts++ < ATTRIBUTION_MAX_POLL_ATTEMPTS) {
        pollTimer = window.setTimeout(resolveReport, ATTRIBUTION_POLL_INTERVAL_MS);
      } else if (!disposed) {
        setError('研究进度连接已断开，请返回后重试。');
        setDone(true);
      }
    };
    if (!taskId) {
      resolveReport();
      return () => {
        disposed = true;
        if (pollTimer !== undefined) window.clearTimeout(pollTimer);
      };
    }
    const es = new EventSource(`/api/attribution/stream/${taskId}`);
    esRef.current = es;
    es.addEventListener('progress', (event) => {
      try {
        const data: AttributionProgress = JSON.parse((event as MessageEvent).data);
        if (data.type === 'STAGE') {
          reachStage(data.stage);
        } else if (data.type === 'CLUE') {
          reachStage(data.stage);
          setClues((prev) => [...prev, data.message || '']);
        } else if (data.type === 'DONE') {
          setDone(true);
          es.close();
        } else if (data.type === 'ERROR') {
          setError(data.message || '归因失败');
          setDone(true);
          es.close();
        }
      } catch (e) {
        // 忽略解析异常
      }
    });
    es.onerror = () => {
      es.close();
    };
    // 报告与 Harness 运行状态始终轮询；SSE 仅承担低延迟增量提示。
    resolveReport();
    return () => {
      disposed = true;
      if (pollTimer !== undefined) {
        window.clearTimeout(pollTimer);
      }
      es.close();
    };
  }, [taskId, reportId]);

  useEffect(() => {
    if (taskId) return undefined;
    let disposed = false;
    Promise.resolve(api<AttributionReport[]>(`/api/attribution/history?code=${encodeURIComponent(code)}&type=${type || 'STOCK'}&limit=50`))
      .then((items) => { if (!disposed) setHistory(Array.isArray(items) ? items : []); })
      .catch(() => { if (!disposed) setHistory([]); });
    return () => { disposed = true; };
  }, [code, type, taskId]);

  async function selectHistory(nextReportId: number) {
    if (nextReportId === report?.id) return;
    try {
      setError(null);
      const next = await api<AttributionReport>(`/api/attribution/reports/${nextReportId}`);
      setReport(next);
    } catch (historyError) {
      setError(historyError instanceof Error ? historyError.message : '历史归因读取失败');
    }
  }

  const historyGroups = history.reduce<Record<string, AttributionReport[]>>((groups, item) => {
    const day = item.reportDate || '日期未知';
    (groups[day] ||= []).push(item);
    return groups;
  }, {});

  const changeText = changePct === undefined || changePct === null
    ? ''
    : `${changePct > 0 ? '+' : ''}${changePct.toFixed(2)}%`;
  const changeCls = (changePct ?? 0) > 0 ? 'watchlist-up' : (changePct ?? 0) < 0 ? 'watchlist-down' : '';
  const stageKeys = Object.keys(stageLabels);
  const completedStageCount = stageKeys.filter((stage) => stages.includes(stage) || currentStage === stage).length;
  const latestClue = clues[clues.length - 1];
  const trackSteps = researchRun?.steps || [];
  const progress = researchRun?.progress;
  const isPlannedOnly = (status: string) => status === 'PLANNED' || status === 'PENDING';
  const visibleTrackSteps = trackSteps.filter((step) => !isPlannedOnly(step.status));
  const plannedTrackCount = progress?.plannedTracks ?? trackSteps.length;
  const activatedTrackCount = progress?.activatedTracks
    ?? visibleTrackSteps.length;
  const settledTrackCount = progress?.settledTracks
    ?? visibleTrackSteps.filter((step) => step.status !== 'RUNNING').length;
  const activeStageLabel = stageLabels[currentStage] || currentStage;
  const trackProgressText = plannedTrackCount === 0
    ? '等待轨道回传'
    : activatedTrackCount === 0
      ? '轨道准备中'
      : `已启动 ${activatedTrackCount}/${plannedTrackCount}，已结算 ${settledTrackCount}/${plannedTrackCount}`;

  return (
    <section className="panel wide attribution-panel">
      <div className="attribution-head">
        <button className="ghost-button" type="button" onClick={onBack}>← 返回自选</button>
        <div className="attribution-title">
          <strong>{name || code}</strong>
          <span className="watchlist-meta">{code}</span>
          {changeText && <span className={`attribution-change ${changeCls}`}>{changeText}</span>}
        </div>
      </div>

      {!report && !error && (
        <div className="attribution-progress">
          <h4>🔬 正在研究：{name || code} 的涨跌原因</h4>
          <div className="attribution-workbench">
            <div className="attribution-path-panel">
              <span className="watchlist-meta">研究路径</span>
              <ol className="attribution-steps">
                {stageKeys.map((key) => {
                  const reached = stages.includes(key) || currentStage === key;
                  const isCurrent = currentStage === key && !done;
                  return (
                    <li
                      key={key}
                      className={`attribution-step${reached ? ' reached' : ''}${isCurrent ? ' current' : ''}`}
                    >
                      <span className="attribution-step-mark">{reached ? (isCurrent ? '⟳' : '✓') : '○'}</span>
                      {stageLabels[key]}
                    </li>
                  );
                })}
              </ol>
              {clues.length > 0 && (
                <div className="attribution-clues">
                  <span className="watchlist-meta">实时线索</span>
                  <ul>
                    {clues.slice(-8).map((clue, index) => (
                      <li key={index}>· {clue}</li>
                    ))}
                  </ul>
                </div>
              )}
              {researchRun && visibleTrackSteps.length > 0 && (
                <div className="attribution-clues">
                  <span className="watchlist-meta">Harness 轨道恢复 · {researchRun.run.status}</span>
                  <ul>
                    {visibleTrackSteps.map((step) => (
                      <li key={step.stepId}>
                        {step.status === 'COMPLETED' ? '✓' : step.status === 'FAILED' ? '×' : '○'} {' '}
                        {trackLabels[step.track || ''] || step.track || step.stepId} · {step.status}
                        {step.outputSummary ? ` · ${step.outputSummary}` : ''}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <aside className="attribution-intel-panel" aria-label="研究态势">
              <div className="attribution-intel-head">
                <span className="watchlist-meta">研究态势</span>
                <strong>{activeStageLabel}</strong>
              </div>
              <div className="attribution-intel-metrics">
                <div>
                  <span>当前焦点</span>
                  <strong>{activeStageLabel}</strong>
                </div>
                <div>
                  <span>阶段进度</span>
                  <strong>{completedStageCount}/{stageKeys.length}</strong>
                </div>
                <div>
                  <span>已发现线索</span>
                  <strong>{clues.length} 条</strong>
                </div>
                <div>
                  <span>轨道进度</span>
                  <strong>{trackProgressText}</strong>
                </div>
              </div>
              <div className="attribution-latest-clue">
                <span className="watchlist-meta">最新线索</span>
                <p>{latestClue || '等待搜索与本地证据回传。'}</p>
              </div>
              <div className="attribution-track-grid">
                {visibleTrackSteps.length > 0 ? visibleTrackSteps.map((step) => (
                  <div
                    className={`attribution-track-card status-${step.status.toLowerCase()}`}
                    key={step.stepId}
                  >
                    <span>{trackLabels[step.track || ''] || step.track || step.stepId}</span>
                    <strong>{stepStatusLabels[step.status] || step.status}</strong>
                    {step.outputSummary && <small>{step.outputSummary}</small>}
                  </div>
                )) : (
                  <p className="muted">已规划 {plannedTrackCount} 条研究轨道，等待首条轨道开始执行。</p>
                )}
              </div>
            </aside>
          </div>
        </div>
      )}

      {error && (
        <div className="attribution-error">
          <p>归因未能完成：{error}</p>
          <button className="compact-button" type="button" onClick={onBack}>返回</button>
        </div>
      )}

      {report && (
        <div className="attribution-reader-grid">
        <div className="attribution-report">
          <div className="attribution-report-layout">
            <div className="attribution-report-main">
              <div className="attribution-summary">
                <span className="attribution-summary-label">📌 一句话归因</span>
                <p>{report.summary}</p>
              </div>

              {report.primaryDriver && (
                <div className="attribution-summary">
                  <span className="attribution-summary-label">🎯 首要驱动</span>
                  <p><strong>{report.primaryDriver.claim}</strong></p>
                  {report.primaryDriver.transmissionPath && <p>{report.primaryDriver.transmissionPath}</p>}
                </div>
              )}

              <h4 className="attribution-section-title">🔍 驱动因素</h4>
              {report.drivers && report.drivers.length > 0 ? (
                <div className="attribution-drivers">
                  {report.drivers.map((driver, index) => (
                    <div className="attribution-driver" key={index}>
                      <div className="attribution-driver-head">
                        <strong>{index + 1}. {driver.claim}</strong>
                        <span className="attribution-driver-meta">
                          影响 {levelDots(driver.impactLevel)} · 置信 {levelLabels[driver.confidence || 'MID']}
                        </span>
                      </div>
                      {driver.detail && <p className="attribution-driver-detail">{driver.detail}</p>}
                      {driver.facts && driver.facts.length > 0 && (
                        <ul>{driver.facts.map((fact, factIndex) => <li key={factIndex}>事实：{fact}</li>)}</ul>
                      )}
                      {driver.transmissionPath && <p className="attribution-driver-detail"><strong>传导链：</strong>{driver.transmissionPath}</p>}
                      {driver.counterEvidence && <p className="attribution-driver-detail"><strong>反证/局限：</strong>{driver.counterEvidence}</p>}
                      {driver.observationWindow && <p className="attribution-driver-detail"><strong>观察窗口：</strong>{driver.observationWindow}</p>}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted">未识别到明确驱动因素。</p>
              )}
            </div>

            <div className="attribution-report-support">
              {report.warningMessage && <p className="attribution-disclaimer">⚠ {report.warningMessage}</p>}
              {report.uncertainties && report.uncertainties.length > 0 && (
                <div>
                  <h4 className="attribution-section-title">⚖️ 不确定性</h4>
                  <ul>{report.uncertainties.map((item, index) => <li key={index}>{item}</li>)}</ul>
                </div>
              )}
              {report.observationWindows && report.observationWindows.length > 0 && (
                <div>
                  <h4 className="attribution-section-title">🕒 后续验证</h4>
                  <ul>{report.observationWindows.map((item, index) => <li key={index}>{item}</li>)}</ul>
                </div>
              )}

              {report.disclaimer && <p className="attribution-disclaimer">⚠ {report.disclaimer}</p>}

              <div>
                <h4 className="attribution-section-title">
                  📰 证据 {report.evidences ? `(${report.evidences.length})` : ''}
                  {report.durationMs ? <span className="watchlist-meta"> · 耗时 {Math.round(report.durationMs / 1000)}s</span> : null}
                </h4>
                <div className="attribution-evidences">
                  {(report.evidences || []).map((evidence, index) => (
                    <div className="attribution-evidence" key={evidence.id || evidence.eventKey || evidence.url || index}>
                      <span className={`attribution-tier attribution-tier-${evidence.sourceTier || 'T3'}`}>
                        {evidence.sourceTier || 'T3'}
                      </span>
                      {evidence.url ? (
                        <a href={evidence.url} target="_blank" rel="noreferrer">{evidence.title || evidence.url}</a>
                      ) : (
                        <span>{evidence.title}</span>
                      )}
                      {evidence.sourceDomain && <span className="watchlist-meta"> · {evidence.sourceDomain}</span>}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
        <aside className="attribution-history" aria-label="历史归因">
          <div className="attribution-history-head">
            <span className="watchlist-meta">RESEARCH ARCHIVE</span>
            <h4>历史归因</h4>
            <p>每次研究都是独立快照，新报告不覆盖旧结论。</p>
          </div>
          <div className="attribution-history-groups">
            {Object.entries(historyGroups).map(([day, versions]) => (
              <section className="attribution-history-day" key={day}>
                <div><time>{day}</time><span>{versions.length} 个版本</span></div>
                {versions.map((item, index) => (
                  <button
                    type="button"
                    key={item.id}
                    aria-current={item.id === report.id ? 'true' : undefined}
                    onClick={() => selectHistory(item.id)}
                  >
                    <span>{item.createdAt ? item.createdAt.slice(11, 16) : `版本 ${versions.length - index}`}</span>
                    <strong className={(item.changePct ?? 0) > 0 ? 'watchlist-up' : (item.changePct ?? 0) < 0 ? 'watchlist-down' : ''}>
                      {item.changePct == null ? '--' : `${item.changePct > 0 ? '+' : ''}${item.changePct.toFixed(2)}%`}
                    </strong>
                    <small>{item.summary}</small>
                  </button>
                ))}
              </section>
            ))}
          </div>
        </aside>
        </div>
      )}
    </section>
  );
}
