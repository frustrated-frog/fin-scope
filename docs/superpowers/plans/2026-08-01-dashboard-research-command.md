# Dashboard Research Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页改造成由真实工作区数据驱动的每日研究指挥台。

**Architecture:** `App` 保留完整的知识概览并把既有加载状态传入 `DashboardView`。`DashboardView` 只投影和排序数据，提供到既有工作区的导航回调，不增加后端接口或复制其他模块的完整功能。

**Tech Stack:** React 18、TypeScript、Vite、Vitest、React Testing Library、既有 CSS。

---

### Task 1: 将已有工作区状态接入 Dashboard

**Files:**
- Modify: `frontend/src/App.tsx:17-136,491`
- Modify: `frontend/src/App.test.tsx:1-80`

- [ ] **Step 1: Write the failing test**

```tsx
test('dashboard presents the research command sections from loaded workspace data', async () => {
  render(<App />);

  expect(await screen.findByRole('heading', { name: '今天的研究脉冲' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '优先处理' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '工作区概览' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '运行账本' })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- App.test.tsx`

Expected: FAIL because the existing Dashboard has no research-command sections.

- [ ] **Step 3: Preserve knowledge overview and pass the dashboard inputs**

```tsx
const [knowledgeOverview, setKnowledgeOverview] = useState<KnowledgeOverview | null>(null);

if (knowledgeOverviewData) {
  setKnowledgeOverview(knowledgeOverviewData);
  setActiveTopicCount(knowledgeOverviewData.activeTopicCount ?? 0);
}

<DashboardView
  dashboard={dashboard}
  articles={articles}
  events={events}
  learningTasks={learningTasks}
  contentIdeas={contentIdeas}
  researchRuns={researchRuns}
  researchTheses={researchTheses}
  agentRuns={agentRuns}
  intakeCandidates={intakeCandidates}
  knowledgeOverview={knowledgeOverview}
  onChangeView={setView}
/>
```

- [ ] **Step 4: Run test to verify it remains red for the missing view content**

Run: `npm test -- App.test.tsx`

Expected: FAIL only on the new Dashboard section assertions.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat: 接入首页研究工作区数据"
```

### Task 2: 建立数据驱动的研究指挥台

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardView.tsx:1-130`
- Modify: `frontend/src/App.test.tsx:1-80`

- [ ] **Step 1: Write the failing navigation test**

```tsx
test('dashboard directs a priority item to its owning workspace', async () => {
  render(<App />);

  await userEvent.click(await screen.findByRole('button', { name: '查看研究流' }));

  expect(screen.getByRole('heading', { name: '事件研究台' })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- App.test.tsx`

Expected: FAIL because Dashboard has no `查看研究流` command.

- [ ] **Step 3: Replace stock cards with command projections**

```tsx
const priorityEvents = [...events]
  .sort((left, right) => (right.importanceScore ?? 0) - (left.importanceScore ?? 0))
  .slice(0, 2);
const openTasks = learningTasks.filter((task) => task.status !== 'DONE').slice(0, 2);
const activeRuns = researchRuns.filter((run) => run.status === 'RUNNING' || run.status === 'QUEUED').slice(0, 2);

<section className="dashboard-pulse" aria-labelledby="dashboard-pulse-heading">
  <div><p>Today</p><h2 id="dashboard-pulse-heading">今天的研究脉冲</h2></div>
  {/* new information, pending work, due reviews, active runs */}
</section>
```

Render these four sections from existing props:

- `优先处理` uses `priorityEvents`, `openTasks`, and `activeRuns` with explicit workspace commands.
- `工作区概览` contains research flow, knowledge and judgment, investment research, and content output summaries using event, knowledge overview, thesis, and content-idea state.
- `运行账本` reuses `latestFetchRuns`, shows source name, status, effective additions, and duplicates.
- Empty data uses a short direction sentence and still exposes the owning workspace command.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `npm test -- App.test.tsx`

Expected: PASS, including the new Dashboard sections and navigation command.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/dashboard/DashboardView.tsx frontend/src/App.test.tsx
git commit -m "feat: 重构首页研究指挥台"
```

### Task 3: 实现研究指挥台的响应式视觉层级

**Files:**
- Modify: `frontend/src/styles.css:5074-5315,7312-7465`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write the failing stylesheet contract test**

```tsx
test('dashboard uses a responsive research command layout', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.dashboard-pulse\s*{[^}]*grid-template-columns:/s);
  expect(styles).toMatch(/\.dashboard-workspace-grid\s*{[^}]*grid-template-columns:/s);
  expect(styles).toMatch(/@media \(max-width: 760px\)[\s\S]*\.dashboard-pulse[\s\S]*grid-template-columns:\s*1fr/s);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- App.test.tsx`

Expected: FAIL because the new command-layout classes have no CSS.

- [ ] **Step 3: Add scoped dashboard styling**

```css
.dashboard-command { display: grid; gap: 18px; }
.dashboard-pulse { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(260px, .8fr); }
.dashboard-workspace-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
@media (max-width: 760px) {
  .dashboard-pulse,
  .dashboard-workspace-grid { grid-template-columns: 1fr; }
}
```

Use the design tokens from the approved spec. The pulse segments use status colors derived from real counts; workspace sections stay compact and use only restrained hover feedback. Respect `prefers-reduced-motion` by removing transition and transform effects.

- [ ] **Step 4: Run focused test, full suite, and production build**

Run: `npm test -- App.test.tsx && npm test && npm run build`

Expected: all tests and production build exit with status 0; the existing Vite bundle-size warning may remain.

- [ ] **Step 5: Inspect the Dashboard in a real browser**

Run: start the frontend dev server, open `http://127.0.0.1:5173/` with Playwright, and inspect desktop plus a narrow viewport screenshot.

Expected: no overlapping text, command buttons stay readable, and the mobile layout presents one column in priority order.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/styles.css frontend/src/App.test.tsx frontend/tsconfig.tsbuildinfo
git commit -m "style: 优化首页研究指挥台层级"
```
