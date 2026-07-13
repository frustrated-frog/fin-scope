import { useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import { ResearchReport } from '../../shared/types';

const CONFIDENCE_LABELS = { HIGH: '高', MEDIUM: '中', LOW: '低' } as const;

export function ResearchReportReader({ report, onBack }: { report: ResearchReport; onBack: () => void }) {
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    titleRef.current?.focus();
  }, [report.id]);

  return (
    <article className="research-report-reader standalone" aria-label="研究报告">
      <nav className="research-report-toolbar" aria-label="研究报告操作">
        <button className="ghost-button" type="button" onClick={onBack}>← 返回研究运行</button>
        <span>Run #{report.researchRunId} · {presentReportStatus(report.status)}</span>
      </nav>
      <header className="research-report-hero">
        <div>
          <p className="eyebrow">Research report</p>
          <h1 ref={titleRef} tabIndex={-1}>{report.title}</h1>
          <p className="research-report-conclusion">{report.conclusion}</p>
        </div>
        <div className="research-report-metrics" aria-label="报告质量信息">
          <span><small>置信度</small><strong>{CONFIDENCE_LABELS[report.confidence]}</strong></span>
          <span><small>有效证据</small><strong>{report.evidenceCount} 条</strong></span>
          <span><small>独立来源</small><strong>{report.sourceCount} 个</strong></span>
          <span><small>生成方式</small><strong>{presentGenerationMode(report.generationMode)}</strong></span>
        </div>
      </header>
      {report.warningMessage && (
        <div className="research-report-warning" role="note" aria-label="证据边界">
          <strong>证据边界</strong>
          <span>{report.warningMessage}</span>
        </div>
      )}
      <section className="research-report-document">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{report.contentMarkdown}</ReactMarkdown>
      </section>
    </article>
  );
}

function presentGenerationMode(mode: string) {
  if (mode === 'MODEL') return '模型综合生成';
  if (mode === 'HYBRID') return '模型与规则联合生成';
  if (mode === 'DETERMINISTIC') return '规则引擎保底生成';
  return mode || '系统生成';
}

function presentReportStatus(status: string) {
  if (status === 'COMPLETED') return '报告已完成';
  if (status === 'PARTIAL_SUCCESS') return '报告部分完成';
  return status;
}
