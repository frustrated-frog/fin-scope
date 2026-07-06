import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import { markdownNodeText, slugify } from '../../shared/brief/markdown';
import { TopicDetail } from '../../shared/types';

function topicTerms(terms?: string) {
  return (terms || '')
    .split(/[,，\n]/)
    .map((term) => term.trim())
    .filter(Boolean);
}

export function TopicReaderView({
  topicDetail,
  onBack,
  onRecordLearning
}: {
  topicDetail: TopicDetail | null;
  onBack: () => void;
  onRecordLearning: (topicId: number) => Promise<void>;
}) {
  if (!topicDetail) {
    return (
      <section className="topic-reader-empty">
        <button className="ghost-button" type="button" onClick={onBack}>返回主题库</button>
        <p className="muted">还没有选择主题。</p>
      </section>
    );
  }

  const { topic, linkedArticles, linkedBriefs, markdown } = topicDetail;
  const terms = topicTerms(topic.terms);

  return (
    <article className="topic-reader">
      <header className="topic-reader-hero">
        <div className="topic-reader-kicker">TOPIC MEMORY</div>
        <h1>{topic.name}</h1>
        <div className="topic-reader-meta">
          <span>{topic.status}</span>
          {topic.markdownPath && <span>{topic.markdownPath}</span>}
        </div>
        {topic.description && <p>{topic.description}</p>}
        <div className="topic-reader-actions">
          <button className="ghost-button" type="button" onClick={onBack}>返回主题库</button>
          <button className="primary-button" type="button" onClick={() => onRecordLearning(topic.id)}>记录理解</button>
        </div>
      </header>

      <div className="topic-reader-layout">
        <aside className="topic-reader-context" aria-label="主题上下文">
          <section>
            <strong>关键术语</strong>
            <div className="topic-reader-tags">
              {terms.length > 0 ? (
                terms.map((term) => <span key={term}>{term}</span>)
              ) : (
                <p className="muted">暂无关键术语。</p>
              )}
            </div>
          </section>
          <section>
            <strong>关联文章</strong>
            {linkedArticles.length === 0 ? (
              <p className="muted">暂无关联文章。</p>
            ) : (
              <ul>
                {linkedArticles.map((article) => (
                  <li key={article.id}>
                    {article.url ? (
                      <a href={article.url} target="_blank" rel="noopener noreferrer">{article.title}</a>
                    ) : article.title}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section>
            <strong>关联简报</strong>
            {linkedBriefs.length === 0 ? (
              <p className="muted">暂无关联简报。</p>
            ) : (
              <ul>
                {linkedBriefs.map((brief) => (
                  <li key={brief.id}>{brief.title}</li>
                ))}
              </ul>
            )}
          </section>
        </aside>

        <section className="topic-reader-document" aria-label="主题详情正文">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              h2: ({ children }) => {
                const text = markdownNodeText(children);
                return <h2 id={slugify(text)}>{children}</h2>;
              },
              a: ({ children, href }) => (
                <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
              )
            }}
          >
            {markdown || '# 暂无主题详情'}
          </ReactMarkdown>
        </section>
      </div>
    </article>
  );
}
