import { useEffect, useMemo, useState } from 'react';

import { ResearchRunDetail } from '../../shared/types';
import { presentResearchProgress } from './researchPresentation';

export function ResearchProgressPanel({ detail }: { detail: ResearchRunDetail }) {
  const isActive = ['RUNNING', 'PENDING'].includes(detail.run.status);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    if (!isActive) return undefined;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [isActive, detail.run.id]);

  const progress = useMemo(() => presentResearchProgress(detail, now), [detail, now]);

  return (
    <section className="research-progress" aria-label="研究运行进度">
      <div className="research-progress-head">
        <div>
          <span className={isActive ? 'research-live-indicator active' : 'research-live-indicator'}>
            {isActive ? '实时运行' : '运行结果'}
          </span>
          <h4>{progress.headline}</h4>
          <p>{progress.metrics}</p>
        </div>
        <div className="research-progress-count">
          <strong>{progress.completedSteps} / {progress.totalSteps}</strong>
          <span>步已完成</span>
          {isActive && <small>本步骤 {formatElapsed(progress.elapsedSeconds)}</small>}
        </div>
      </div>

      <div
        className="research-progress-track"
        role="progressbar"
        aria-label="研究完成进度"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={progress.percent}
      >
        <span style={{ width: `${progress.percent}%` }} />
      </div>

      <ol className="research-stage-rail">
        {progress.stages.map((stage, index) => (
          <li key={stage.id} data-status={stage.status} className={`research-stage ${stage.status.toLowerCase()}`}>
            <span className="research-stage-marker" aria-hidden="true">{stage.status === 'COMPLETED' ? '✓' : index + 1}</span>
            <span>{stage.label}</span>
          </li>
        ))}
      </ol>
      <span className="sr-only">{progress.completedSteps} / {progress.totalSteps} 步已完成</span>
    </section>
  );
}

function formatElapsed(seconds: number) {
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `${minutes} 分 ${remainder} 秒`;
}
