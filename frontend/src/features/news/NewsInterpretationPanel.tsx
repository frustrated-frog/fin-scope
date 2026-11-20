import type { RefObject } from 'react';
import { DossierReading } from './DossierReading';
import { DossierPrimaryAction } from './NewsDossierDialog';
import type { NewsInterpretationResult, useNewsInterpretation } from './useNewsReportInterpretation';

type Interpretation = ReturnType<typeof useNewsInterpretation>;

export function NewsReadingAction({ interpretation, onShow, section, disabled }: {
  interpretation: Interpretation;
  onShow: () => void;
  section: RefObject<HTMLElement>;
  disabled?: boolean;
}) {
  const { view, loading, pending, error } = interpretation;
  const saved = view?.current?.result;
  const canRead = saved || view?.previous?.result;
  const label = error ? '重试读取解读' : pending ? '解读生成中…' : saved || (!view?.available && canRead)
    ? '查看 AI 解读' : view?.current?.status === 'FAILED' ? '重试 AI 解读' : '生成 AI 解读';
  function activate() {
    onShow();
    requestAnimationFrame(() => section.current?.scrollIntoView?.({ block: 'nearest' }));
    if (error) {
      interpretation.refresh();
    } else if (!saved && view?.available && !pending && !loading) {
      interpretation.start();
    }
  }
  return <DossierPrimaryAction label={label} onClick={activate}
    disabled={disabled || loading || (!error && (pending || (!view?.available && !canRead)))}/>;
}

export function NewsInterpretationPanel({ interpretation, heading, section, expandPrevious = false }: {
  interpretation: Interpretation;
  heading: string;
  section: RefObject<HTMLElement>;
  expandPrevious?: boolean;
}) {
  const { view, loading, pending, error } = interpretation;
  const current = view?.current;
  const result = current?.result;
  const failed = current?.status === 'FAILED';
  const status = result ? '解读完成' : pending ? '生成中' : failed ? '生成失败' : view?.available ? '未生成' : '未启用';
  return <section ref={section} className="radar-detail-section" aria-label="新闻 AI 解读">
    <div className="radar-detail-section-heading">
      <h3>{heading}</h3>
      {view && <span className={result ? 'is-success' : failed ? 'is-error' : undefined}>{status}</span>}
    </div>
    {error && <div className="radar-dossier-notice" role="alert">{error}</div>}
    {loading || (pending && !error) ? <p className="radar-dossier-notice" role="status">
      {pending || view ? '解读生成中…' : '正在读取解读状态…'}
    </p> : null}
    {view && !result && !pending && !loading && !error && <p className="radar-dossier-notice" role={failed ? 'alert' : undefined}>
      {!view.available ? '模型解读未启用或模型未配置。' : current?.failureMessage || '点击「生成 AI 解读」开始。'}
    </p>}
    {result && <NewsInterpretationContent result={result}/>}
    {view?.previous?.result && !result && <details className="radar-drawer-trace" open={expandPrevious || undefined}>
      <summary>查看旧解读 · 正文 v{view.previous.contentVersion}</summary>
      <NewsInterpretationContent result={view.previous.result}/>
    </details>}
  </section>;
}

function NewsInterpretationContent({ result }: { result: NewsInterpretationResult }) {
  if (!result.reading && result.formatVersion === 2) {
    return <DossierReading facts={result.facts} detailsLabel="延伸解读" impactChain={result.whyItMatters.split('\n\n').filter(Boolean)}/>;
  }
  return <DossierReading reading={result.reading} facts={result.facts} whyItMatters={result.whyItMatters}/>;
}
