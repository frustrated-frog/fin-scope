import { AttributionAssessment } from '../../shared/types';
import './attributionAssessment.css';

const dispositions = { PREFERRED: '当前更倾向', COEXISTING: '同时起作用', NOT_ADOPTED: '暂不采用', UNRESOLVED: '尚待区分' };

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
      <p>这是生成失败，不代表没有相关原因。可返回自选重新发起归因。</p>
      {assessment.warnings.map((item, index) => <p key={index}>{item}</p>)}
    </div>}
    {!degraded && assessment.hypotheses.length > 0 && <details>
      <summary>候选解释与改判条件 <span>{assessment.hypotheses.length} 个候选</span></summary>
      {assessment.hypotheses.map(item => <div className="attribution-research-candidate" key={item.id}>
        <h4>{item.explanation} <small>{dispositions[item.disposition]}</small></h4>
        <p>{item.selectionReason}</p>
        <p><strong>可能的影响：</strong>{item.pricingMechanism}</p>
        <p><strong>解释边界：</strong>{item.doesNotExplain}</p>
        {item.assumptions.length > 0 && <p><strong>关键假设：</strong>{item.assumptions.join('；')}</p>}
        {item.revisionConditions.length > 0 && <><strong>什么情况下需要改判</strong><ul>{item.revisionConditions.map((condition, index) => <li key={index}>{condition}</li>)}</ul></>}
        <div className="attribution-research-links">{item.evidenceUrls.filter(sourceLink).map((url, index) => <a key={`${url}-${index}`} href={url} target="_blank" rel="noreferrer">证据 {index + 1} ↗</a>)}</div>
      </div>)}
    </details>}
    {context && <details>
      <summary>行情对照 <span>{context.reportDate || '目标日快照'}</span></summary>
      <dl className="attribution-research-metrics">
        <div><dt>个股涨跌</dt><dd>{percent(context.stockChangePct)}</dd></div>
        <div><dt>{context.benchmarkName || '市场基准'}</dt><dd>{percent(context.benchmarkChangePct)}</dd></div>
        <div><dt>相对基准</dt><dd>{percent(context.relativeChangePct, ' 个百分点')}</dd></div>
        <div><dt>此前 5 个交易日</dt><dd>{percent(context.priorFiveSessionChangePct)}</dd></div>
        <div><dt>成交额 / 前 5 日均值</dt><dd>{context.amountRatio == null ? '暂无对照' : `${context.amountRatio.toFixed(2)} 倍`}</dd></div>
      </dl>
      <p className="muted">{context.source} · {context.capturedAt?.replace('T', ' ').slice(0, 19) || '采集时间未知'}</p>
      <p className="muted">相对表现不是收益归因；缺少行业数据时，不能推断领先或落后同行。</p>
    </details>}
    {gaps.length > 0 && <details><summary>证据缺口与研究限制</summary><ul>{gaps.map((item, index) => <li key={index}>{item}</li>)}</ul></details>}
    {!degraded && assessment.warnings.map((item, index) => <p className="attribution-disclaimer" key={index}>{item}</p>)}
  </section>;
}
