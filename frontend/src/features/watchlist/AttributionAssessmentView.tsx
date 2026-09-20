import { AttributionAssessment } from '../../shared/types';
import './attributionAssessment.css';

const dispositions = {
  PREFERRED: '当前更倾向', COEXISTING: '同时起作用', NOT_ADOPTED: '暂不采用', UNRESOLVED: '尚待区分'
};
const statuses = { COMPLETE: '已形成研判', INSUFFICIENT_EVIDENCE: '保留分歧', DEGRADED: '研判未完成' };

function percent(value?: number | null, suffix = '%') {
  if (value == null || !Number.isFinite(value)) {
    return '暂无对照';
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}${suffix}`;
}

function sourceLink(value: string) {
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? value : undefined;
  } catch {
    return undefined;
  }
}

export function AttributionAssessmentView({ assessment }: { assessment: AttributionAssessment }) {
  const context = assessment.marketContext;
  const degraded = assessment.status === 'DEGRADED';
  const gaps = Array.from(new Set([...assessment.missingInformation, ...(context?.limitations || [])]));
  const accepted = assessment.hypotheses.filter(item => ['PREFERRED', 'COEXISTING'].includes(item.disposition));
  const paragraphs = assessment.commentary.filter(text => text !== assessment.mainJudgment && text !== assessment.pricingDebate && text !== assessment.unexplainedScope.join('；'));
  return (
    <article className="assessment-note" data-status={assessment.status} aria-label="股票异动研判">
      <header className="assessment-focus">
        <div className="assessment-kicker"><span>异动研判 · {context?.reportDate || '日期待确认'}</span><span>{statuses[assessment.status]}</span></div>
        {degraded ? <div className="assessment-failure" role="status">
          <h3>本次研判未完成</h3>
          <p>原因分析尚未生成，已保留获取到的行情与证据。这不代表已确认没有相关原因。</p>
          {assessment.warnings.map((warning, index) => <p className="assessment-caption" key={index}>{warning}</p>)}
          <p className="assessment-caption">可返回自选重新发起归因；本次报告仍保留在历史记录中。</p>
        </div> : <>
        <span className="assessment-label">本次研究焦点</span>
        <h3>{assessment.researchFocus}</h3>
        {assessment.focusReason && <p className="assessment-focus-reason">{assessment.focusReason}</p>}
        </>}
      </header>

      {!degraded && <section className="assessment-judgment" aria-label="当前判断">
        <h4>当前判断</h4>
        <p className="assessment-lead">{assessment.mainJudgment}</p>
        {assessment.pricingDebate && <p className="assessment-debate"><strong>关键分歧</strong>{assessment.pricingDebate}</p>}
      </section>}

      {context && (
        <details className="assessment-market" open>
          <summary>行情对照 <span>目标日快照</span></summary>
          <dl className="assessment-metrics">
            <div><dt>个股涨跌</dt><dd>{percent(context.stockChangePct)}</dd></div>
            <div><dt>{context.benchmarkName || '市场基准'}</dt><dd>{percent(context.benchmarkChangePct)}</dd></div>
            <div><dt>相对基准</dt><dd>{percent(context.relativeChangePct, ' 个百分点')}</dd></div>
            <div><dt>此前 5 个交易日</dt><dd>{percent(context.priorFiveSessionChangePct)}</dd></div>
            <div><dt>成交额 / 前 5 日均值</dt><dd>{context.amountRatio == null ? '暂无对照' : `${context.amountRatio.toFixed(2)} 倍`}</dd></div>
          </dl>
          <p className="assessment-caption">{context.source} · {context.capturedAt?.replace('T', ' ').slice(0, 19) || '采集时间未知'}</p>
          <p className="assessment-caption">相对表现不是收益归因；缺少行业数据时，不能推断领先或落后同行。</p>
        </details>
      )}

      {!degraded && paragraphs.length > 0 && <section className="assessment-prose" aria-label="研判短评">
        <h4>判断如何形成</h4>
        {paragraphs.map((text, index) => <p key={index}>{text}</p>)}
      </section>}

      {!degraded && (assessment.explainedScope.length > 0 || assessment.unexplainedScope.length > 0) && <aside className="assessment-boundary" aria-label="解释边界">
        <h4>这份判断解释到哪里</h4>
        {assessment.explainedScope.length > 0 && <p><strong>能够解释</strong>{assessment.explainedScope.join('；')}</p>}
        <p><strong>尚未解释</strong>{assessment.unexplainedScope.join('；') || '尚未确认可解释的范围。'}</p>
      </aside>}

      {!degraded && accepted.length > 0 && <section className="assessment-revision" aria-label="关键假设与改判条件">
        <h4>什么情况下需要改判</h4>
        {accepted.map(item => <div key={item.id}>
          <h5>{item.explanation}</h5>
          <p><strong>依赖的假设：</strong>{item.assumptions.join('；')}</p>
          <ul>{item.revisionConditions.map((condition, index) => <li key={index}>{condition}</li>)}</ul>
        </div>)}
      </section>}

      {!degraded && assessment.hypotheses.length > 0 && <details className="assessment-alternatives">
        <summary>为什么采用这个解释 <span>{assessment.hypotheses.length} 个候选</span></summary>
        {assessment.hypotheses.map(item => <section className="assessment-hypothesis" key={item.id}>
          <span className="assessment-label">{dispositions[item.disposition]}</span>
          <h5>{item.explanation}</h5>
          <p>{item.selectionReason}</p>
          <p><strong>定价逻辑：</strong>{item.pricingMechanism}</p>
          <p><strong>解释范围：</strong>{item.explains}</p>
          <p><strong>未能解释：</strong>{item.doesNotExplain}</p>
          <div className="assessment-source-links">{item.evidenceUrls.filter(url => sourceLink(url)).map((url, index) =>
            <a key={`${url}-${index}`} href={sourceLink(url)} target="_blank" rel="noreferrer">证据 {index + 1} ↗</a>)}
          </div>
        </section>)}
      </details>}
      {gaps.length > 0 && <details className="assessment-gaps">
        <summary>仍缺少哪些信息</summary>
        <ul>{gaps.map((item, index) => <li key={index}>{item}</li>)}</ul>
      </details>}
      {!degraded && assessment.warnings.map((warning, index) => <p className="assessment-warning" role="status" key={index}>{warning}</p>)}
    </article>
  );
}
