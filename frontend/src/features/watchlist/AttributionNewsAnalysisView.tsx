import { AttributionNewsAnalysis, NewsInterpretationType } from '../../shared/types';

const typeLabels: Record<NewsInterpretationType, string> = {
  EARNINGS: '业绩与盈利', ORDER: '订单兑现', PRICE_CHANGE: '价格与供需', POLICY: '政策传导',
  PRODUCT_TECHNOLOGY: '技术与产品', CORPORATE_ACTION: '资本事项', CLARIFICATION: '公告澄清',
  TRADING: '交易与资金', OTHER: '事件分析'
};
const directionLabels = {
  POSITIVE: '偏利好', NEGATIVE: '偏利空', MIXED: '多空交织', NEUTRAL: '影响中性', UNCLEAR: '方向待明'
};

export function hasNewsAnalysis(analysis?: AttributionNewsAnalysis) {
  return Boolean(analysis && (analysis.keyChange?.trim()
    || analysis.businessImpacts?.some(point => point.label?.trim() && point.analysis?.trim())
    || analysis.shortTermImpact?.trim() || analysis.mediumTermImpact?.trim()
    || analysis.longTermImpact?.trim() || analysis.chainReaction?.trim()));
}

export function AttributionNewsAnalysisView({ analysis }: { analysis?: AttributionNewsAnalysis }) {
  if (!analysis || !hasNewsAnalysis(analysis)) {
    return null;
  }
  const impacts = analysis.businessImpacts?.filter(point => point.label?.trim() && point.analysis?.trim()) || [];
  const horizons = [
    { label: '短期 · 交易反应', text: analysis.shortTermImpact },
    { label: '中期 · 经营兑现', text: analysis.mediumTermImpact },
    { label: '长期 · 竞争格局', text: analysis.longTermImpact }
  ].filter(item => item.text?.trim());

  return (
    <section className="attribution-news-depth" aria-label="新闻深读">
      <header className="attribution-news-heading">
        <strong>新闻深读</strong>
        <div className="attribution-news-tags">
          {analysis.types?.map(type => typeLabels[type] && <span key={type}>{typeLabels[type]}</span>)}
          {analysis.direction && directionLabels[analysis.direction] && (
            <span className={`attribution-news-direction direction-${analysis.direction.toLowerCase()}`}>
              {directionLabels[analysis.direction]}
            </span>
          )}
        </div>
      </header>
      {analysis.keyChange && (
        <div className="attribution-news-key-change"><span>关键变化</span><p>{analysis.keyChange}</p></div>
      )}
      {impacts.length > 0 && (
        <div className="attribution-news-findings">
          {impacts.map((point, index) => (
            <article className="attribution-news-point" key={index}><h5>{point.label}</h5><p>{point.analysis}</p></article>
          ))}
        </div>
      )}
      {horizons.length > 0 && (
        <div className="attribution-news-horizons">
          {horizons.map(item => (
            <article className="attribution-news-point" key={item.label}><h5>{item.label}</h5><p>{item.text}</p></article>
          ))}
        </div>
      )}
      {analysis.chainReaction && (
        <article className="attribution-news-point attribution-news-chain">
          <h5>产业链传导</h5><p>{analysis.chainReaction}</p>
        </article>
      )}
    </section>
  );
}
