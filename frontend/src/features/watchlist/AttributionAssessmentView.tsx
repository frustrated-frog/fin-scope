import { AttributionAssessment } from '../../shared/types';
import './attributionAssessment.css';

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

/** 研判是原报告的补充，不替代摘要、故事线和驱动卡片。 */
export function AttributionAssessmentView({ assessment }: { assessment: AttributionAssessment }) {
  const context = assessment.marketContext;
  const degraded = assessment.status === 'DEGRADED';
  const gaps = Array.from(new Set([...assessment.missingInformation, ...(context?.limitations || [])]));
  if (!degraded && !context && gaps.length === 0 && assessment.warnings.length === 0) {
    return null;
  }
  return <section className="attribution-research-details" aria-label="研判补充">
    {degraded && <div className="attribution-disclaimer" role="status">
      <strong>研判未完成</strong>
      <p>部分研究步骤未完成，已有分析和行情仍保留展示。可返回自选重新发起归因。</p>
      {assessment.warnings.map((item, index) => <p key={index}>{item}</p>)}
    </div>}
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
