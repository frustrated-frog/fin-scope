import { AttributionAssessment } from '../../shared/types';
import './attributionAssessment.css';

const directions = { POSITIVE: '偏利好', NEGATIVE: '偏利空', MIXED: '多空兼有', NEUTRAL: '影响中性', UNCLEAR: '方向待判断' };

const dispositions = { PREFERRED: '当前更倾向', COEXISTING: '同时起作用', NOT_ADOPTED: '暂不采用', UNRESOLVED: '尚待区分' };

function readable(value: string) {
  return value.replace(/\bPREFERRED\b/g, '优先解释').replace(/\bCOEXISTING\b/g, '共同作用的解释')
    .replace(/\bNOT_ADOPTED\b/g, '暂不采用').replace(/\bUNRESOLVED\b/g, '尚待区分')
    .replace(/\bamountRatio\b/g, '成交额与前五日均值之比');
}

function percent(value?: number | null, suffix = '%') {
  if (value == null || !Number.isFinite(value)) {
    return '暂无对照';
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}${suffix}`;
}

function sourceLink(value: string) {
  try {
    return ['https:', 'http:'].includes(new URL(value).protocol);
  } catch {
    return false;
  }
}

/** 研判是原报告的补充，不替代摘要、故事线和驱动卡片。 */
export function AttributionAssessmentView({ assessment }: { assessment: AttributionAssessment }) {
  const context = assessment.marketContext;
  const degraded = assessment.status === 'DEGRADED';
  const gaps = Array.from(new Set([...assessment.missingInformation, ...(context?.limitations || [])]));
  return <section className="attribution-research-details" aria-label="研判补充">
    {degraded && <div className="attribution-disclaimer" role="status">
      <strong>研判未完成</strong>
      <p>部分研究步骤未完成，已有分析和行情仍保留展示。可返回自选重新发起归因。</p>
      {assessment.warnings.map((item, index) => <p key={index}>{item}</p>)}
    </div>}
    {assessment.hypotheses.length > 0 && <section aria-label="候选解释与改判条件">
      <div className="attribution-research-heading"><h4 className="attribution-section-title">候选解释与改判条件</h4><span>{assessment.hypotheses.length} 个候选 · 区分已采纳与待核验的解释</span></div>
      <div className="attribution-drivers">
        {assessment.hypotheses.map((rawItem, index) => {
          const item = { ...rawItem, explanation: readable(rawItem.explanation), selectionReason: readable(rawItem.selectionReason),
            pricingMechanism: readable(rawItem.pricingMechanism), explains: readable(rawItem.explains), doesNotExplain: readable(rawItem.doesNotExplain),
            assumptions: rawItem.assumptions.map(readable), revisionConditions: rawItem.revisionConditions.map(readable) };
          const separator = item.explanation.search(/[：:]/);
          const shortTitle = separator > 0 && separator <= 40 ? item.explanation.slice(0, separator) : item.explanation.length <= 40 ? item.explanation : `候选解释 ${index + 1}`;
          const description = separator > 0 && separator <= 40 ? item.explanation.slice(separator + 1).trim() : item.explanation.length > 40 ? item.explanation : '';
          return <div className="attribution-driver attribution-hypothesis-card" key={item.id}>
            <div className="attribution-driver-head">
              <div className="attribution-driver-title"><span className="attribution-hypothesis-number">{index + 1}</span><h4>{shortTitle}</h4></div>
              <span className={`attribution-hypothesis-status status-${item.disposition.toLowerCase()}`}>{dispositions[item.disposition]}</span>
            </div>
            {description && <p className="attribution-driver-plain">{description}</p>}
            {item.impactDirection && <div className={`attribution-impact impact-${item.impactDirection.toLowerCase()}`}>
              <strong>{directions[item.impactDirection]}</strong>
              <p>{readable(item.impactReason || '尚未提供方向判断理由。')}</p>
            </div>}
            {item.timeRelevance && <p className="attribution-impact-timing"><strong>作用时间</strong>{readable(item.timeRelevance)}</p>}
            <section className="attribution-driver-ai" aria-label="候选解释解读">
              <div className="attribution-driver-ai-heading"><span aria-hidden="true">AI</span><strong>解释与依据</strong></div>
              <div className="attribution-driver-ai-grid">
                <div className="attribution-research-field"><span>为什么采用或暂不采用</span><p>{item.selectionReason || '尚未提供可核验的依据。'}</p></div>
                <div className="attribution-research-field"><span>为什么可能影响股价</span><p>{item.pricingMechanism || '影响机制尚待核验。'}</p></div>
                <div className="attribution-research-field"><span>能够解释什么</span><p>{item.explains || '解释范围尚未确认。'}</p></div>
                <div className="attribution-research-field"><span>尚不能解释什么</span><p>{item.doesNotExplain || '仍需补充验证。'}</p></div>
              </div>
            </section>
            {(item.assumptions.length > 0 || item.revisionConditions.length > 0) && <div className="attribution-hypothesis-checks">
              {item.assumptions.length > 0 && <section><h5>关键假设</h5><ul>{item.assumptions.map((value, key) => <li key={key}>{value}</li>)}</ul></section>}
              {item.revisionConditions.length > 0 && <section><h5>什么情况下需要改判</h5><ul>{item.revisionConditions.map((value, key) => <li key={key}>{value}</li>)}</ul></section>}
            </div>}
            <div className="attribution-research-sources"><span>对应证据</span><div className="attribution-research-links">{item.evidenceUrls.filter(sourceLink).map((url, key) => <a key={`${url}-${key}`} href={url} target="_blank" rel="noreferrer">证据 {key + 1}<span aria-hidden="true"> ↗</span></a>)}{item.evidenceUrls.filter(sourceLink).length === 0 && <span className="muted">暂无可核验的来源链接</span>}</div></div>
          </div>;
        })}
      </div>
    </section>}
    {context && <details className="attribution-market-panel" open>
      <summary>行情对照 <span>{context.reportDate || '目标日快照'}</span></summary>
      <dl className="attribution-research-metrics">
        <div><dt>个股涨跌</dt><dd>{percent(context.stockChangePct)}</dd></div>
        <div><dt>{context.benchmarkName || '市场基准'}</dt><dd>{percent(context.benchmarkChangePct)}</dd></div>
        <div><dt>相对基准</dt><dd>{percent(context.relativeChangePct, ' 个百分点')}</dd></div>
        <div><dt>此前 5 个交易日</dt><dd>{percent(context.priorFiveSessionChangePct)}</dd></div>
        <div><dt>成交额 / 前 5 日均值</dt><dd>{context.amountRatio == null ? '暂无对照' : `${context.amountRatio.toFixed(2)} 倍`}</dd></div>
      </dl>
      <p className="attribution-research-caption">{context.source} · {context.capturedAt?.replace('T', ' ').slice(0, 19) || '采集时间未知'}</p>
      <p className="attribution-research-caption">相对表现不是收益归因；缺少行业数据时，不能推断领先或落后同行。</p>
    </details>}
    {gaps.length > 0 && <details className="attribution-research-gaps"><summary>证据缺口与研究限制</summary><ul>{gaps.map((item, index) => <li key={index}>{readable(item)}</li>)}</ul></details>}
    {!degraded && assessment.warnings.map((item, index) => <p className="attribution-disclaimer" key={index}>{item}</p>)}
  </section>;
}
