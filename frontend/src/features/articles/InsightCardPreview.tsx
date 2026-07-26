import { InsightCard } from '../../shared/types';

type InsightSectionPreview = {
  title: string;
  content: string;
};

function firstText(...values: Array<string | undefined>) {
  return values.find((value) => value && value.trim())?.trim();
}

function addSection(sections: InsightSectionPreview[], title: string, content?: string) {
  if (content && content.trim()) {
    sections.push({ title, content: content.trim() });
  }
}

function hasLegacyAnalysisFields(card: InsightCard) {
  return [
    card.background,
    card.keyData,
    card.timeline,
    card.relatedParties,
    card.riskFactors,
    card.futureOutlook,
    card.impactOnInvestment,
    card.impactOnStartup,
    card.professionalInsight,
    card.facts,
    card.reasoning,
    card.opinions
  ].some((value) => value && value.trim());
}

function deriveSectionsFromLegacyFields(card: InsightCard, category?: string) {
  const normalizedCategory = (category || '').trim();
  if (!normalizedCategory || !hasLegacyAnalysisFields(card)) {
    return [];
  }

  const sections: InsightSectionPreview[] = [];
  if (normalizedCategory.includes('前沿') || normalizedCategory.includes('技术') || normalizedCategory.includes('科技')) {
    addSection(sections, '它做了什么', firstText(card.coreEvent, card.background, card.oneSentenceSummary));
    addSection(sections, '解决了什么问题', firstText(card.importance, card.professionalInsight));
    addSection(sections, '关键机制/技术路线', firstText(card.keyData, card.impactTargets, card.facts));
    addSection(sections, '风险与限制', card.riskFactors);
    addSection(sections, '我应该补什么知识', card.followUpQuestions);
    return sections;
  }

  if (normalizedCategory.includes('自我')) {
    addSection(sections, '核心观点', firstText(card.oneSentenceSummary, card.coreEvent));
    addSection(sections, '适用场景', firstText(card.background, card.importance));
    addSection(sections, '方法步骤', firstText(card.timeline, card.followUpQuestions));
    addSection(sections, '常见误区/边界', card.riskFactors);
    addSection(sections, '给自己的复盘问题', card.followUpQuestions);
    return sections;
  }

  if (normalizedCategory.includes('金融') || normalizedCategory.includes('宏观')) {
    addSection(sections, '发生了什么', firstText(card.coreEvent, card.background, card.oneSentenceSummary));
    addSection(sections, '关键数据/政策变量', firstText(card.keyData, card.facts, card.oneSentenceSummary));
    addSection(sections, '影响链条', firstText(card.importance, card.reasoning, card.professionalInsight));
    addSection(sections, '受影响资产', card.impactTargets);
    addSection(sections, '投资含义', firstText(card.impactOnInvestment, card.futureOutlook));
    addSection(sections, '反证与风险', card.riskFactors);
    addSection(sections, '下一观察窗口', card.followUpQuestions);
    return sections;
  }

  addSection(sections, '政策/事件脉络', firstText(card.coreEvent, card.background, card.oneSentenceSummary));
  addSection(sections, '发布会/公告要点', firstText(card.keyData, card.facts, card.oneSentenceSummary));
  addSection(sections, '市场反应', firstText(card.importance, card.reasoning));
  addSection(sections, '受影响方向', card.impactTargets);
  addSection(sections, '短期与中期影响', firstText(card.futureOutlook, card.impactOnInvestment, card.professionalInsight));
  addSection(sections, '拥挤度与风险点', card.riskFactors);
  addSection(sections, '下一观察窗口', card.followUpQuestions);
  return sections;
}

export function InsightCardPreview({ card, category }: { card: InsightCard; category?: string }) {
  const persistedSections = (card.analysisSections || []).filter((section) => section.title && section.content);
  const analysisSections = persistedSections.length > 0
    ? persistedSections
    : deriveSectionsFromLegacyFields(card, category);
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
      <div className="insight-source-row" aria-label="解读生成来源">
        <span className={`insight-source-badge source-${(card.interpretationSource || 'UNKNOWN').toLowerCase()}`}>
          {card.interpretationSource === 'LLM'
            ? 'Agent 模型解读'
            : card.interpretationSource === 'FALLBACK'
              ? '规则降级解读'
              : '历史解读'}
        </span>
      </div>
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
