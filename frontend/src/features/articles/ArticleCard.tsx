import { Article } from '../../shared/types';
import { InsightCardPreview } from './InsightCardPreview';

type SourceTag = {
  label: string;
  tone: 'social' | 'news' | 'finance' | 'research' | 'general';
};

function detectSourceTag(article: Pick<Article, 'url' | 'sourceName'>): SourceTag | null {
  const sourceName = article.sourceName?.trim().toLowerCase() ?? '';
  const sourceUrl = article.url?.trim().toLowerCase() ?? '';
  const combined = `${sourceName} ${sourceUrl}`;

  if (combined.includes('x.com') || combined.includes('twitter.com')) {
    return { label: 'X（推特）', tone: 'social' };
  }
  if (combined.includes('xinhuanet.com') || combined.includes('news.cn')) {
    return { label: '新华网', tone: 'news' };
  }
  if (combined.includes('people.com.cn')) {
    return { label: '人民网', tone: 'news' };
  }
  if (combined.includes('caixin.com')) {
    return { label: '财新网', tone: 'finance' };
  }
  if (combined.includes('10jqka.com.cn') || combined.includes('hexun.com')) {
    return { label: '同花顺', tone: 'finance' };
  }
  if (combined.includes('eastmoney.com') || combined.includes('guba.eastmoney.com')) {
    return { label: '东方财富', tone: 'finance' };
  }
  if (combined.includes('finance.sina.com.cn')) {
    return { label: '新浪财经', tone: 'finance' };
  }
  if (combined.includes('finance.qq.com')) {
    return { label: '腾讯财经', tone: 'finance' };
  }
  if (combined.includes('arxiv.org')) {
    return { label: 'arXiv', tone: 'research' };
  }
  if (combined.includes('manual') || combined.includes('手动研究') || combined.includes('example.com')) {
    return { label: '网页', tone: 'general' };
  }

  return null;
}

export function ArticleCard({
  article,
  isExpanded,
  onToggle,
  onCompound,
  onDelete,
  categoryColor,
  isHighlighted = false,
  isCompounding = false
}: {
  article: Article;
  isExpanded: boolean;
  onToggle: () => void;
  onCompound: () => void;
  onDelete: () => void;
  categoryColor: string;
  isHighlighted?: boolean;
  isCompounding?: boolean;
}) {
  const detectedSourceTag = detectSourceTag(article);

  return (
    <div className={`article-card${isHighlighted ? ' article-card-highlight' : ''}`}>
      <button
        type="button"
        className="article-card-header"
        onClick={onToggle}
        aria-expanded={isExpanded}
        aria-label={`${isExpanded ? '收起' : '展开'}文章：${article.title}`}
      >
        <div className="article-tag-group">
          {detectedSourceTag && (
            <span className={`article-source-tag article-source-tag-${detectedSourceTag.tone}`}>
              {detectedSourceTag.label}
            </span>
          )}
          <span className="article-category-tag" style={{ backgroundColor: categoryColor }}>
            {article.category || '市场'}
          </span>
        </div>
        <div className="article-card-main">
          <h4 className="article-title">{article.title}</h4>
          <div className="article-meta">
            <span className="source-badge">{article.sourceName}</span>
            <span>·</span>
            {article.noveltyType && (
              <span className={`badge ${article.noveltyType.toLowerCase()}`}>{article.noveltyType}</span>
            )}
          </div>
          {article.summary && <p className="article-summary">{article.summary}</p>}
        </div>
        <span className="article-expand-icon">
          {isExpanded ? '▼' : '▶'}
        </span>
      </button>

      {isExpanded && (
        <div className="article-card-expanded">
          {article.body && (
            <div className="article-body-content">
              <h5>原文内容</h5>
              <div className="markdown-content">
                {article.body}
              </div>
            </div>
          )}

          {article.insightCard && (
            <div className="insight-section">
              <h5>AI 解读</h5>
              <InsightCardPreview card={article.insightCard} category={article.category || '市场'} />
            </div>
          )}

          <div className="article-card-actions">
            <button
              className="primary-button"
              type="button"
              aria-busy={isCompounding}
              disabled={isCompounding}
              onClick={(event) => { event.stopPropagation(); onCompound(); }}
            >
              {isCompounding ? '沉淀中...' : '沉淀到主题库'}
            </button>
            {article.url && (
              <a href={article.url} target="_blank" rel="noopener noreferrer" className="link-button">
                查看原文
              </a>
            )}
            <button
              className="danger-button article-danger-button"
              onClick={(event) => { event.stopPropagation(); onDelete(); }}
              style={{ marginLeft: 'auto' }}
            >
              删除
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
