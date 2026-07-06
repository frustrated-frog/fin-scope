# Topic Reader Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make topic detail open in a dedicated full-width reader page, while Learning remains focused on learning tasks and personal notes.

**Architecture:** Add an internal `topicReader` view that is not shown as a sidebar tab. `TopicsView` opens topic detail into that reader. `LearningView` keeps the note form but no longer renders raw Markdown, and the new reader renders `topicDetail.markdown` as readable Markdown using the same ReactMarkdown stack as Brief Reader.

**Tech Stack:** React 18, TypeScript, Vite, Vitest, Testing Library, react-markdown, remark-gfm, CSS responsive layout.

---

## File Structure

- Modify: `frontend/src/shared/types/index.ts`
  - Add `topicReader` to the `View` union.
- Modify: `frontend/src/App.tsx`
  - Split topic navigation into `openTopicReader(topicId)` and `openTopicForLearning(topicId)`.
  - Add `topicReader` title and render branch.
  - Pass the correct callbacks to `TopicsView`, `TopicReaderView`, and `LearningView`.
- Modify: `frontend/src/features/topics/TopicsView.tsx`
  - Keep the topic list page unchanged visually.
  - Rename callback intent to `onOpenTopicReader`.
- Create: `frontend/src/features/topics/TopicReaderView.tsx`
  - Full-width topic reading page.
  - Render Markdown as HTML, not raw `<pre>`.
  - Provide `返回主题库` and `记录理解` actions.
- Modify: `frontend/src/features/learning/LearningView.tsx`
  - Keep learning queue + topic note form.
  - Remove raw Markdown preview from the right detail panel.
  - Keep `记录理解` topic selection behavior.
- Modify: `frontend/src/styles.css`
  - Add Topic Reader layout and document typography.
  - Tighten Learning detail layout so it no longer fights with large content.
- Modify: `frontend/src/App.test.tsx`
  - Add regression coverage for topic detail reader.
  - Update or preserve existing learning note tests.

No commits will be made. The user will commit after review.

---

## Task 1: Add `topicReader` View Routing

**Files:**
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Extend the view type**

In `frontend/src/shared/types/index.ts`, add `topicReader` after `topics`:

```ts
export type View =
  | 'dashboard'
  | 'sources'
  | 'article'
  | 'briefs'
  | 'briefReader'
  | 'research'
  | 'events'
  | 'evidence'
  | 'topics'
  | 'topicReader'
  | 'learning'
  | 'contentStudio'
  | 'agents'
  | 'settings';
```

- [ ] **Step 2: Split topic open actions**

In `frontend/src/App.tsx`, replace the current `openTopic` function with two functions:

```ts
async function loadTopicDetail(topicId: number) {
  const detail = await api<TopicDetail>(`/api/topics/${topicId}`);
  setTopicDetail(detail);
  return detail;
}

async function openTopicReader(topicId: number) {
  await loadTopicDetail(topicId);
  setView('topicReader');
}

async function openTopicForLearning(topicId: number) {
  await loadTopicDetail(topicId);
  setView('learning');
}
```

- [ ] **Step 3: Add title mapping**

In `currentTitle`, add a branch for the internal reader page:

```ts
case 'topicReader':
  return 'Topic Reader';
```

- [ ] **Step 4: Wire render branches**

Add an import:

```ts
import { TopicReaderView } from './features/topics/TopicReaderView';
```

Update `TopicsView`:

```tsx
<TopicsView
  topics={topics}
  onChanged={refresh}
  onOpenTopicReader={openTopicReader}
/>
```

Add render branch before `learning`:

```tsx
{view === 'topicReader' && (
  <TopicReaderView
    topicDetail={topicDetail}
    onBack={() => setView('topics')}
    onRecordLearning={(topicId) => openTopicForLearning(topicId)}
  />
)}
```

Update `LearningView`:

```tsx
<LearningView
  topics={topics}
  learningTasks={learningTasks}
  topicDetail={topicDetail}
  onOpenTopic={openTopicForLearning}
  onOpenEvent={openEvent}
  onChanged={refresh}
  onTaskStatusChange={updateLearningTaskStatus}
  setMessage={setMessage}
  addToast={addToast}
/>
```

- [ ] **Step 5: Run type-aware test**

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm run build
```

Expected: build may fail until `TopicReaderView` is created. If it fails only because the new component does not exist, continue to Task 2.

---

## Task 2: Create Full-Width Topic Reader

**Files:**
- Create: `frontend/src/features/topics/TopicReaderView.tsx`

- [ ] **Step 1: Create the component**

Create `frontend/src/features/topics/TopicReaderView.tsx`:

```tsx
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import { markdownNodeText, slugify } from '../../shared/brief/markdown';
import { TopicDetail } from '../../shared/types';

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
              {(topic.terms || '').split(',').map((term) => term.trim()).filter(Boolean).map((term) => (
                <span key={term}>{term}</span>
              ))}
              {!topic.terms && <p className="muted">暂无关键术语。</p>}
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
```

- [ ] **Step 2: Run build**

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm run build
```

Expected: build fails only if App wiring from Task 1 is incomplete. Fix imports or prop names before moving on.

---

## Task 3: Update Topics and Learning Responsibilities

**Files:**
- Modify: `frontend/src/features/topics/TopicsView.tsx`
- Modify: `frontend/src/features/learning/LearningView.tsx`

- [ ] **Step 1: Rename Topics callback**

In `TopicsView`, change props to:

```ts
export function TopicsView({
  topics,
  onChanged,
  onOpenTopicReader
}: {
  topics: Topic[];
  onChanged: () => Promise<void>;
  onOpenTopicReader: (topicId: number) => Promise<void>;
}) {
```

Change the button:

```tsx
<button className="compact-button" type="button" onClick={() => onOpenTopicReader(topic.id)}>查看详情</button>
```

- [ ] **Step 2: Remove raw Markdown from Learning**

In `LearningView`, delete this line:

```tsx
<pre className="markdown-preview">{topicDetail.markdown}</pre>
```

Do not delete the note form. The right side should still show:

```tsx
<div className="panel-heading">
  <div>
    <h3>{activeTopic.name}</h3>
    <p className="muted">{activeTopic.markdownPath}</p>
  </div>
  <span className="badge">{activeTopic.status}</span>
</div>
<p>{activeTopic.description}</p>
<div className="topic-links">...</div>
<form className="note-form" onSubmit={appendNote}>...</form>
```

- [ ] **Step 3: Keep learning action behavior**

The learning list button should continue to use:

```tsx
<button className="compact-button learning-action-button" onClick={() => onOpenTopic(topic.id)}>记录理解</button>
```

This preserves the existing test and user workflow.

---

## Task 4: Add Reader Styles and Responsive Layout

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add topic reader base styles**

Add near the Brief Reader styles:

```css
.topic-reader,
.topic-reader-empty {
  display: grid;
  gap: 22px;
}

.topic-reader-hero {
  border: 1px solid var(--line);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(21, 148, 111, 0.12), transparent 42%),
    var(--surface);
  padding: 26px;
  box-shadow: var(--shadow-tight);
}

.topic-reader-kicker {
  color: var(--faint);
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0;
}

.topic-reader-hero h1 {
  max-width: 980px;
  margin: 8px 0 0;
  color: var(--ink);
  font-size: 34px;
  line-height: 1.12;
}

.topic-reader-hero p {
  max-width: 900px;
  margin: 12px 0 0;
  color: var(--muted);
}

.topic-reader-meta,
.topic-reader-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.topic-reader-meta span {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-2);
  padding: 5px 9px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 760;
}
```

- [ ] **Step 2: Add document layout styles**

```css
.topic-reader-layout {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.topic-reader-context,
.topic-reader-document {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow-tight);
}

.topic-reader-context {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 18px;
  padding: 18px;
}

.topic-reader-context section + section {
  border-top: 1px solid var(--line);
  padding-top: 16px;
}

.topic-reader-context ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

.topic-reader-context a,
.topic-reader-document a {
  color: var(--accent);
  font-weight: 720;
  text-underline-offset: 3px;
  overflow-wrap: anywhere;
}

.topic-reader-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}

.topic-reader-tags span {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-2);
  padding: 4px 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 720;
}

.topic-reader-document {
  padding: 28px;
}

.topic-reader-document h1,
.topic-reader-document h2,
.topic-reader-document h3 {
  color: var(--ink);
  line-height: 1.18;
}

.topic-reader-document h1 {
  margin: 0 0 18px;
  font-size: 30px;
}

.topic-reader-document h2 {
  margin: 30px 0 12px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  font-size: 22px;
}

.topic-reader-document h3 {
  margin: 22px 0 10px;
  font-size: 17px;
}

.topic-reader-document p,
.topic-reader-document li {
  color: var(--ink);
  line-height: 1.72;
}

.topic-reader-document ul,
.topic-reader-document ol {
  padding-left: 24px;
}

.topic-reader-document li + li {
  margin-top: 6px;
}
```

- [ ] **Step 3: Add responsive behavior**

Inside the existing responsive area:

```css
@media (max-width: 1120px) {
  .topic-reader-layout {
    grid-template-columns: 1fr;
  }

  .topic-reader-context {
    position: static;
  }
}

@media (max-width: 560px) {
  .topic-reader-hero,
  .topic-reader-document,
  .topic-reader-context {
    padding: 18px;
  }

  .topic-reader-hero h1 {
    font-size: 28px;
  }
}
```

---

## Task 5: Add Regression Tests

**Files:**
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Make topic markdown fixture representative**

In the `'/api/topics/1'` fixture, ensure `markdown` includes a heading, list, link, and article interpretation section:

```ts
markdown: '# 降息交易\n\n- 状态：LEARNING\n- 描述：跟踪利率预期如何影响黄金和风险资产。\n\n## 关键术语\n\n- 美联储\n- 实际利率\n- 黄金\n\n## 学习问题\n\n- 为什么降息会影响黄金？\n- 如何判断预期差？\n\n## 关联文章\n\n- [美联储释放降息信号 黄金走强](https://x.com/tester/status/123)\n\n## 文章解读\n\n### 一句话摘要\n\n美联储释放偏鸽信号，市场重新交易降息预期。'
```

- [ ] **Step 2: Add topic reader test**

Add this test after `topics show learning metadata and vault path`:

```ts
test('topics open a full-width markdown topic reader', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看详情' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/1', expect.anything());
  expect(await screen.findByText('Topic Reader')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '返回主题库' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '记录理解' })).toBeInTheDocument();
  expect(screen.getByRole('region', { name: '主题详情正文' })).toHaveClass('topic-reader-document');
  expect(screen.getByRole('complementary', { name: '主题上下文' })).toHaveClass('topic-reader-context');
  expect(screen.getAllByText('关键术语').length).toBeGreaterThan(0);
  expect(screen.getByText('文章解读')).toBeInTheDocument();
  expect(screen.queryByText((content, element) => element?.tagName === 'PRE' && content.includes('# 降息交易'))).not.toBeInTheDocument();
});
```

- [ ] **Step 3: Add reader-to-learning test**

Add:

```ts
test('topic reader can jump to the learning note form for the same topic', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看详情' }));
  await userEvent.click(await screen.findByRole('button', { name: '记录理解' }));

  expect(await screen.findByText('Learning')).toBeInTheDocument();
  expect(screen.getByLabelText('个人理解')).toBeInTheDocument();
  expect(screen.queryByRole('region', { name: '主题详情正文' })).not.toBeInTheDocument();
});
```

- [ ] **Step 4: Preserve existing learning test**

Run:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm test
```

Expected: all existing learning tests pass, including `learning view opens a topic and appends personal understanding`.

---

## Task 6: Browser QA and Final Verification

**Files:**
- No code changes unless QA finds a layout bug.

- [ ] **Step 1: Run unit tests**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm test
```

Expected: all tests pass.

- [ ] **Step 2: Run production build**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm run build
```

Expected: TypeScript build and Vite production build pass.

- [ ] **Step 3: Run whitespace check**

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 4: Run responsive browser smoke test**

Start the dev server:

```bash
cd /Users/machengqian.1/code/MyProject/fin-scope/frontend
pnpm exec vite --host 127.0.0.1 --port 5174
```

Using Chrome/Playwright, verify:

- `Topics` -> `查看详情` opens `Topic Reader`.
- The detail page does not show raw Markdown inside `<pre>`.
- `Topic Reader` has no whole-page horizontal overflow at `1512x900`, `1280x800`, and `390x844`.
- `记录理解` opens `Learning` with the same topic loaded.
- The `Learning` page does not include `.markdown-preview`.

- [ ] **Step 5: Stop dev server**

Send `Ctrl+C` to the Vite process and verify port `5174` is free:

```bash
lsof -i :5174 -sTCP:LISTEN -n -P || true
```

Expected: no listener output.

---

## Self-Review

- Spec coverage: The plan creates a dedicated topic detail page, removes Markdown source display from Learning, and preserves note-taking.
- Placeholder scan: No TBD/TODO placeholders are present.
- Type consistency: `topicReader`, `TopicReaderView`, `onOpenTopicReader`, and `onRecordLearning` names are used consistently.
- Test coverage: New tests cover Topics -> Reader, Markdown rendering, Reader -> Learning, and existing note behavior.
