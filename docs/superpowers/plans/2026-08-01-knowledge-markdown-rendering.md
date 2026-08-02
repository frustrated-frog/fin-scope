# Knowledge Markdown Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知识工作台的只读 Markdown 结论渲染为标题、段落和列表，消除页面上可见的 Markdown 格式符。

**Architecture:** 在知识功能目录新增一个受限的只读 Markdown 组件，只处理标题、段落和列表，始终返回 React 元素而不注入 HTML。主题工作台和知识脉络将复用该组件；输入编辑区继续保存原始 Markdown。

**Tech Stack:** React 18、TypeScript、Vitest、React Testing Library、既有 CSS。

---

### Task 1: 受限 Markdown 渲染组件

**Files:**
- Create: `frontend/src/features/knowledge/KnowledgeMarkdown.tsx`
- Create: `frontend/src/features/knowledge/KnowledgeMarkdown.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
test('renders headings, paragraphs, and lists without markdown markers', () => {
  render(<KnowledgeMarkdown value={'## 投资命题\n半导体景气回升。\n\n### 后续验证\n- 跟踪月度营收\n- 跟踪现货价格'} />);

  expect(screen.getByRole('heading', { name: '投资命题' })).toBeInTheDocument();
  expect(screen.getByText('半导体景气回升。')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '后续验证' })).toBeInTheDocument();
  expect(screen.getByRole('list')).toHaveTextContent('跟踪月度营收');
  expect(screen.queryByText(/##|###/)).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- KnowledgeMarkdown.test.tsx`

Expected: FAIL because `KnowledgeMarkdown` does not exist.

- [ ] **Step 3: Write minimal implementation**

```tsx
type MarkdownBlock =
  | { type: 'heading'; depth: number; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] };

export function KnowledgeMarkdown({ value }: { value: string }) {
  return <div className="knowledge-markdown">{/* blocks rendered as h4, p, ul, or ol */}</div>;
}
```

Parse input by line, grouping consecutive ordinary text and consecutive list items. Clamp headings to `h4` so callers never create a second page-level heading. Strip leading Markdown punctuation from unsupported constructs before rendering as text.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- KnowledgeMarkdown.test.tsx`

Expected: PASS with the heading, paragraph, list, and no-literal-marker assertions green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/knowledge/KnowledgeMarkdown.tsx frontend/src/features/knowledge/KnowledgeMarkdown.test.tsx
git commit -m "feat: 增加知识结论阅读渲染"
```

### Task 2: 在主题工作台复用阅读渲染

**Files:**
- Modify: `frontend/src/features/knowledge/topics/TopicWorkspace.tsx:37-48`
- Modify: `frontend/src/features/knowledge/topics/TopicWorkspace.test.tsx:14-58`
- Modify: `frontend/src/styles.css:2778-2790`

- [ ] **Step 1: Write the failing test**

```tsx
test('renders markdown conclusions as structured reading content', () => {
  render(<TopicWorkspace workspace={{
    ...workspace,
    entries: [{ ...workspace.entries[0], contentMarkdown: '## 投资命题\n景气度回升。\n\n- 跟踪营收' }]
  }} onBack={vi.fn()} onReview={vi.fn()} />);

  expect(screen.getByRole('heading', { name: '投资命题' })).toBeInTheDocument();
  expect(screen.getAllByText('景气度回升。').length).toBeGreaterThan(0);
  expect(screen.getAllByRole('list').length).toBeGreaterThan(0);
  expect(screen.queryByText(/## 投资命题/)).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- TopicWorkspace.test.tsx`

Expected: FAIL because the workspace still renders the full Markdown string inside `<p>` elements.

- [ ] **Step 3: Write minimal implementation**

```tsx
<section className="topic-current-judgment">
  <p className="knowledge-kicker">当前可检验结论</p>
  <h3>当前判断</h3>
  <KnowledgeMarkdown value={latest?.contentMarkdown || '还没有形成结论。先完成一个学习问题，让证据开始沉淀。'} />
</section>
```

Replace the same raw output in the review comparison with `KnowledgeMarkdown`. Add scoped CSS for `.knowledge-markdown` headings, paragraphs, and lists; preserve existing font scale and make nested headings compact inside `.topic-review-compare`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- TopicWorkspace.test.tsx`

Expected: PASS, including existing material-topic and review submission coverage.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/knowledge/topics/TopicWorkspace.tsx frontend/src/features/knowledge/topics/TopicWorkspace.test.tsx frontend/src/styles.css
git commit -m "fix: 优化知识判断阅读排版"
```

### Task 3: 在知识脉络复用阅读渲染并回归验证

**Files:**
- Modify: `frontend/src/features/knowledge/topics/TopicTimeline.tsx:3-20`
- Modify: `frontend/src/features/knowledge/topics/TopicWorkspace.test.tsx:14-58`
- Modify: `frontend/src/styles.css:2790-2800`

- [ ] **Step 1: Write the failing test**

```tsx
test('uses structured markdown rendering in the knowledge timeline', () => {
  render(<TopicWorkspace workspace={{
    ...workspace,
    entries: [{ ...workspace.entries[0], contentMarkdown: '## 支持数据\n- 净值上涨 2.41%' }]
  }} onBack={vi.fn()} onReview={vi.fn()} />);

  expect(screen.getByRole('heading', { name: '支持数据' })).toBeInTheDocument();
  expect(screen.getByText('净值上涨 2.41%')).toBeInTheDocument();
  expect(screen.queryByText(/## 支持数据/)).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- TopicWorkspace.test.tsx`

Expected: FAIL because `TopicTimeline` renders each value in a paragraph.

- [ ] **Step 3: Write minimal implementation**

```tsx
<div>
  <h3>{step.label}</h3>
  {step.values.slice(0, 4).map((value, index) => (
    <KnowledgeMarkdown key={`${value}-${index}`} value={value} />
  ))}
</div>
```

Update timeline CSS so a rendered block has predictable spacing and its `h4` does not compete with the step label.

- [ ] **Step 4: Run focused and full verification**

Run: `npm test -- TopicWorkspace.test.tsx KnowledgeMarkdown.test.tsx && npm test && npm run build`

Expected: focused tests, full suite, and production build all exit with status 0. Any existing Vite chunk-size warning may remain, but no new build errors are acceptable.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/knowledge/topics/TopicTimeline.tsx frontend/src/features/knowledge/topics/TopicWorkspace.test.tsx frontend/src/styles.css
git commit -m "fix: 统一知识脉络结论渲染"
```
