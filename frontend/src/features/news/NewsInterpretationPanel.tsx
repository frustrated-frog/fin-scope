import { conceptLine, interpretationText, sectionLine } from './newsReading';
import type { NewsInterpretationResult, useNewsInterpretation } from './useNewsReportInterpretation';

export function NewsInterpretationPanel({ interpretation, heading, expandPrevious = false }: {
  interpretation: ReturnType<typeof useNewsInterpretation>;
  heading: string;
  expandPrevious?: boolean;
}) {
  return (
    <section className="news-version-history" aria-label="新闻 AI 解读">
      <h3>{heading}</h3>
      {interpretation.error ? <div className="news-window-error" role="alert">
        {interpretation.error}<button type="button" onClick={interpretation.refresh}>重试读取</button>
      </div> : null}
      {interpretation.loading || (interpretation.pending && !interpretation.error) ? (
        <p role="status">{interpretation.pending || interpretation.view ? '解读生成中…' : '正在读取解读状态…'}</p>
      ) : null}
      {interpretation.view && !interpretation.view.available && !interpretation.view.current?.result ? (
        <p>模型解读未启用或模型未配置。</p>
      ) : null}
      {interpretation.view?.available && !interpretation.view.current?.result && !interpretation.pending
        && !interpretation.loading && !interpretation.error ? <>
          {interpretation.view.current?.failureMessage ? <p role="alert">{interpretation.view.current.failureMessage}</p> : null}
          <div className="news-report-actions"><button type="button" onClick={interpretation.start}>
            {interpretation.view.current?.status === 'FAILED' ? '重试 AI 解读' : '生成 AI 解读'}
          </button></div>
        </> : null}
      {interpretation.view?.current?.result ? <NewsInterpretationContent result={interpretation.view.current.result} /> : null}
      {interpretation.view?.previous?.result && !interpretation.view.current?.result ? <details open={expandPrevious || undefined}>
        <summary>查看旧解读 · 正文 v{interpretation.view.previous.contentVersion}</summary>
        <NewsInterpretationContent result={interpretation.view.previous.result} />
      </details> : null}
    </section>
  );
}

function NewsInterpretationContent({ result }: { result: NewsInterpretationResult }) {
  const reading = result.reading;
  if (reading || result.formatVersion === 2) {
    const lead = reading?.lead ?? result.facts[0] ?? '';
    const paragraphs = reading
      ? [...reading.concepts.map(conceptLine), result.whyItMatters, ...reading.sections.map(sectionLine)].filter(Boolean)
      : result.whyItMatters.split('\n\n').filter(Boolean);
    return <article><p><strong>{interpretationText(lead)}</strong></p>
      {paragraphs.map((paragraph, index) => <p key={index}>{interpretationText(paragraph)}</p>)}
    </article>;
  }
  return <>
    <article><h4>报道披露的事实</h4><ul>{result.facts.map((value, index) => <li key={index}>{value}</li>)}</ul></article>
    <article><h4>为什么值得关注</h4><p>{result.whyItMatters}</p></article>
  </>;
}
