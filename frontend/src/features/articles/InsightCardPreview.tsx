import { InsightCard } from '../../shared/types';

export function InsightCardPreview({ card }: { card: InsightCard }) {
  const analysisSections = (card.analysisSections || []).filter((section) => section.title && section.content);
  const basicFields = [
    ['一句话摘要', card.oneSentenceSummary],
    ['核心事件', card.coreEvent],
    ['为什么重要', card.importance],
    ['影响对象', card.impactTargets]
  ].filter(([, value]) => Boolean(value));

  const deepFields = [
    ['背景是什么', card.background],
    ['关键数据', card.keyData],
    ['时间线', card.timeline],
    ['相关方', card.relatedParties],
    ['风险因素', card.riskFactors],
    ['未来展望', card.futureOutlook],
    ['对投资的影响', card.impactOnInvestment],
    ['对创业的影响', card.impactOnStartup],
    ['专业解读', card.professionalInsight]
  ].filter(([, value]) => Boolean(value));

  const frpFields = [
    ['事实', card.facts],
    ['推理', card.reasoning],
    ['观点', card.opinions]
  ].filter(([, value]) => Boolean(value));

  return (
    <div className="insight-card-preview">
      {basicFields.map(([label, value]) => (
        <div className="insight-field-item" key={label}>
          <div className="insight-field-label">{label}</div>
          <div className="insight-field-value">{value}</div>
        </div>
      ))}

      {analysisSections.length > 0 && (
        <div className="insight-deep-section">
          <div className="insight-section-title">分类解读</div>
          {analysisSections.map((section) => (
            <div className="insight-field-item" key={section.title}>
              <div className="insight-field-label">{section.title}</div>
              <div className="insight-field-value">{section.content}</div>
            </div>
          ))}
        </div>
      )}

      {analysisSections.length === 0 && deepFields.length > 0 && (
        <div className="insight-deep-section">
          <div className="insight-section-title">深度解读</div>
          {deepFields.map(([label, value]) => (
            <div className="insight-field-item" key={label}>
              <div className="insight-field-label">{label}</div>
              <div className="insight-field-value">{value}</div>
            </div>
          ))}
        </div>
      )}

      {frpFields.length > 0 && (
        <div className="insight-frp-section">
          <div className="insight-section-title">事实、推理与观点</div>
          {frpFields.map(([label, value]) => (
            <div className="insight-field-item" key={label}>
              <div className="insight-field-label">{label}</div>
              <div className="insight-field-value">{value}</div>
            </div>
          ))}
        </div>
      )}

      {card.followUpQuestions && (
        <div className="insight-field-item">
          <div className="insight-field-label">后续观察</div>
          <div className="insight-field-value">{card.followUpQuestions}</div>
        </div>
      )}
    </div>
  );
}
