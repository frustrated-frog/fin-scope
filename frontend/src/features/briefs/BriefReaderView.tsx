import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import { markdownNodeText, parseBriefMarkdown, slugify, themeLabel } from '../../shared/brief/markdown';
import { Brief, BriefResearchContext } from '../../shared/types';

export function BriefReaderView({
  brief,
  researchContext,
  onBack,
  onCompound
}: {
  brief: Brief | null;
  researchContext: BriefResearchContext | null;
  onBack: () => void;
  onCompound: (date: string) => Promise<void>;
}) {
  const markdown = brief?.content ?? '';
  const metadata = parseBriefMarkdown(markdown, brief);

  if (!brief) {
    return (
      <section className="brief-reader-empty">
        <button className="ghost-button" type="button" onClick={onBack}>返回简报列表</button>
        <p className="muted">还没有选择简报。</p>
      </section>
    );
  }

  return (
    <article className="brief-reader">
      <header className="brief-reader-hero">
        <div className="brief-reader-kicker">FINANCE · INVESTMENT · STARTUP</div>
        <h1>{metadata.displayTitle}</h1>
        <div className="brief-reader-meta">
          <span>{brief.briefDate}</span>
          {metadata.generatedAt && <span>{metadata.generatedAt}</span>}
          <span>Local Vault</span>
        </div>
        {metadata.positioning && <p>{metadata.positioning}</p>}
        <div className="brief-reader-actions">
          <button className="ghost-button" type="button" onClick={onBack}>返回简报列表</button>
          <button className="primary-button" type="button" onClick={() => onCompound(brief.briefDate)}>沉淀主题</button>
        </div>
      </header>

      <div className="brief-reader-layout">
        <aside className="brief-reader-overview" aria-label="简报大纲概览">
          <div className="brief-reader-overview-head">
            <div className="brief-reader-overview-title">大纲概览</div>
            <span>{metadata.toc.length} 个章节</span>
          </div>
          {metadata.toc.length === 0 ? (
            <p>暂无目录</p>
          ) : (
            <nav className="brief-reader-overview-nav">
              {metadata.toc.map((heading) => (
                <a key={heading.slug} href={`#${heading.slug}`}>{heading.text}</a>
              ))}
            </nav>
          )}
          <div className="brief-reader-path">{brief.markdownPath}</div>
        </aside>

        <aside className="brief-reader-context" aria-label="研究上下文">
          <div className="brief-reader-context-title">研究上下文</div>
          {researchContext?.events?.length ? (
            <section className="context-block">
              <strong>今日新变量</strong>
              {researchContext.events.map((event) => (
                <p key={`event-${event.id}`}>
                  <span className="context-event-kicker">{themeLabel(event.themeCode)} / {event.noveltyState ?? 'NEW'}</span>
                  <br />
                  {event.canonicalTitle}
                </p>
              ))}
            </section>
          ) : null}
          {researchContext?.evidenceItems?.length ? (
            <section className="context-block">
              <strong>证据</strong>
              {researchContext.evidenceItems.map((item) => (
                <p key={`e-${item.id}`}>[{item.sourceTier}] {item.claim}</p>
              ))}
            </section>
          ) : null}
          {researchContext?.learningTasks?.length ? (
            <section className="context-block">
              <strong>学习任务</strong>
              {researchContext.learningTasks.map((task) => (
                <p key={`l-${task.id}`}>{task.question}</p>
              ))}
            </section>
          ) : null}
          {researchContext?.contentIdeas?.length ? (
            <section className="context-block">
              <strong>内容选题</strong>
              {researchContext.contentIdeas.map((idea) => (
                <p key={`c-${idea.id}`}>
                  {idea.title}
                  {idea.scoreReason ? <><br />{idea.scoreReason}</> : null}
                </p>
              ))}
            </section>
          ) : null}
          {!researchContext || (
            researchContext.evidenceItems.length === 0
            && researchContext.learningTasks.length === 0
            && researchContext.contentIdeas.length === 0
          ) ? <p className="muted">暂无研究补充信息。</p> : null}
        </aside>

        <section className="brief-reader-document" aria-label="简报正文">
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
            {metadata.bodyMarkdown}
          </ReactMarkdown>
        </section>
      </div>
    </article>
  );
}
