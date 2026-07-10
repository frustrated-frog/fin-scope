import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { AttributionProgress, AttributionReport } from '../../shared/types';

const stageLabels: Record<string, string> = {
  'question-plan': '拆解研究问题',
  'web-search': '全网搜索线索',
  'local-recall': '检索本地新闻',
  'chain-reason': '分析产业链关联',
  'evidence-rank': '整理证据',
  'attribution-synth': '综合生成归因'
};

const levelLabels: Record<string, string> = { HIGH: '高', MID: '中', LOW: '低' };

function levelDots(level?: string) {
  const map: Record<string, string> = { HIGH: '●●●', MID: '●●○', LOW: '●○○' };
  return map[level || 'MID'] || '●●○';
}

export function AttributionReaderView({
  taskId,
  code,
  name,
  changePct,
  onBack
}: {
  taskId: string;
  code: string;
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
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const es = new EventSource(`/api/attribution/stream/${taskId}`);
    esRef.current = es;
    es.addEventListener('progress', (event) => {
      try {
        const data: AttributionProgress = JSON.parse((event as MessageEvent).data);
        if (data.type === 'STAGE') {
          setCurrentStage(data.stage || '');
          setStages((prev) => (data.stage && !prev.includes(data.stage) ? [...prev, data.stage] : prev));
        } else if (data.type === 'CLUE') {
          setClues((prev) => [...prev, data.message || '']);
        } else if (data.type === 'DONE') {
          setDone(true);
          es.close();
          if (data.reportId) {
            api<AttributionReport>(`/api/attribution/reports/${data.reportId}`)
              .then(setReport)
              .catch((e) => setError(e instanceof Error ? e.message : '报告加载失败'));
          }
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
    return () => {
      es.close();
    };
  }, [taskId]);

  const changeText = changePct === undefined || changePct === null
    ? ''
    : `${changePct > 0 ? '+' : ''}${changePct.toFixed(2)}%`;
  const changeCls = (changePct ?? 0) > 0 ? 'watchlist-up' : (changePct ?? 0) < 0 ? 'watchlist-down' : '';

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
          <ol className="attribution-steps">
            {Object.keys(stageLabels).map((key) => {
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
        </div>
      )}

      {error && (
        <div className="attribution-error">
          <p>归因未能完成：{error}</p>
          <button className="compact-button" type="button" onClick={onBack}>返回</button>
        </div>
      )}

      {report && (
        <div className="attribution-report">
          <div className="attribution-summary">
            <span className="attribution-summary-label">📌 一句话归因</span>
            <p>{report.summary}</p>
          </div>

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
                </div>
              ))}
            </div>
          ) : (
            <p className="muted">未识别到明确驱动因素。</p>
          )}

          {report.disclaimer && <p className="attribution-disclaimer">⚠ {report.disclaimer}</p>}

          <h4 className="attribution-section-title">
            📰 证据 {report.evidences ? `(${report.evidences.length})` : ''}
            {report.durationMs ? <span className="watchlist-meta"> · 耗时 {Math.round(report.durationMs / 1000)}s</span> : null}
          </h4>
          <div className="attribution-evidences">
            {(report.evidences || []).map((evidence) => (
              <div className="attribution-evidence" key={evidence.id}>
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
      )}
    </section>
  );
}