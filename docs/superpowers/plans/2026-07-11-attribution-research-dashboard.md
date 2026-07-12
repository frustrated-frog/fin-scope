# Attribution Research Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将归因详情页从单列进度页升级为双栏研究驾驶舱，让大屏右侧承载“当前研究态势、证据来源、轨道状态”。

**Architecture:** 只改前端归因详情页，不新增后端接口。`AttributionReaderView` 继续消费现有 `stages`、`clues`、`researchRun.steps` 和 `report` 数据，在渲染层派生右侧态势摘要；`styles.css` 负责双栏、雷达矩阵、响应式回落和暗色主题质感。

**Tech Stack:** React 18, TypeScript, Vitest, Testing Library, CSS variables.

---

## Scope

本次改动只覆盖等待归因和报告展示的页面结构。它不改变归因轮询、SSE、Harness 恢复、报告生成接口，也不改变自选列表入口。

## Files

- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`
  - 新增研究态势派生逻辑。
  - 将等待态改为双栏布局：左侧研究路径与实时线索，右侧研究态势面板。
  - 将报告态改为主结论 + 辅助证据侧栏的双栏布局。
- Modify: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`
  - 新增测试锁定等待态右侧面板和 Harness 轨道映射。
- Modify: `frontend/src/styles.css`
  - 新增 `attribution-workbench`、`attribution-path-panel`、`attribution-intel-panel`、`attribution-track-grid`、`attribution-report-layout` 等样式。
  - 保持 8px 左右圆角、深色金融终端风格、移动端单列回落。
- Create: `docs/superpowers/plans/2026-07-11-attribution-research-dashboard.md`
  - 记录本方案与执行任务。

## Design Tokens

- Base: use existing `--base`, `--surface`, `--surface-2`, `--surface-3`.
- Text: use existing `--ink`, `--muted`, `--faint`.
- Progress green: use existing `--accent` / `--success`.
- Uncertainty amber: use existing `--accent-2`.
- Danger: use existing `--danger`.

不新增一套独立色板，避免页面脱离当前 FinScope 视觉系统。

## Data Mapping

右侧态势面板从现有状态派生，不增加接口字段：

- `completedStageCount`: `stages.length`
- `totalStageCount`: `Object.keys(stageLabels).length`
- `latestClue`: `clues.at(-1)`
- `latestClueCount`: `clues.length`
- `activeStageLabel`: `stageLabels[currentStage]`
- `trackRows`: `researchRun.steps` 映射为 `trackLabels[step.track] || step.track || step.stepId`
- `completedTrackCount`: `researchRun.steps` 中 `status === 'COMPLETED'` 的数量
- `totalTrackCount`: `researchRun.steps.length`

状态文案：

- 当前焦点：`stageLabels[currentStage]`
- 阶段进度：`${completedStageCount}/${totalStageCount}`
- 已发现线索：`${latestClueCount} 条`
- 轨道进度：无 `researchRun` 时显示“等待 Harness 回传”；有数据时显示 `${completedTrackCount}/${totalTrackCount}`

## Layout

等待态：

```text
attribution-progress
└── attribution-workbench
    ├── attribution-path-panel
    │   ├── title
    │   ├── attribution-steps
    │   ├── attribution-clues
    │   └── harness recovery details
    └── attribution-intel-panel
        ├── current focus
        ├── progress metrics
        ├── latest clue
        └── track matrix
```

报告态：

```text
attribution-report
└── attribution-report-layout
    ├── attribution-report-main
    │   ├── summary
    │   ├── primary driver
    │   └── drivers
    └── attribution-report-side
        ├── uncertainties
        ├── observation windows
        ├── disclaimer
        └── evidences
```

CSS 网格：

```css
.attribution-workbench,
.attribution-report-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(360px, 1.1fr);
  gap: 18px;
  align-items: start;
}
```

移动端在 `max-width: 900px` 回落单列。

## Task 1: Add Failing Dashboard Test

**Files:**
- Modify: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [ ] **Step 1: Write the failing test**

Add this test after the existing Harness recovery test:

```tsx
test('renders a research dashboard side panel while attribution is running', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ id: 104, status: 'GENERATING' })
    .mockResolvedValueOnce({
      run: { id: 8, reportId: 104, status: 'RUNNING' },
      steps: [
        { stepId: 'company', track: 'COMPANY', status: 'COMPLETED', outputSummary: '公司线索 2 条' },
        { stepId: 'industry', track: 'INDUSTRY', status: 'PENDING' }
      ]
    })
    .mockResolvedValue({ id: 104, status: 'GENERATING' });

  render(
    <AttributionReaderView
      taskId="task-4"
      reportId={104}
      code="021894"
      name="易方达半导体设备ETF联接C"
      changePct={-6.5}
      onBack={vi.fn()}
    />
  );

  eventSource.emit('progress', {
    type: 'CLUE',
    stage: 'evidence-rank',
    message: '找到：半导体设备链波动扩大（T2）'
  });

  expect(await screen.findByText('研究态势')).toBeInTheDocument();
  expect(screen.getByText('当前焦点')).toBeInTheDocument();
  expect(screen.getByText('整理证据')).toBeInTheDocument();
  expect(screen.getByText('已发现线索')).toBeInTheDocument();
  expect(screen.getByText('1 条')).toBeInTheDocument();
  expect(screen.getByText('轨道进度')).toBeInTheDocument();
  expect(screen.getByText('1/2')).toBeInTheDocument();
  expect(screen.getByText(/半导体设备链波动扩大/)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd frontend && npm test -- --run src/features/watchlist/AttributionReaderView.test.tsx
```

Expected: the new test fails because `研究态势` is not rendered yet.

## Task 2: Implement Derived Dashboard State

**Files:**
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`

- [ ] **Step 1: Add derived constants inside the component**

```tsx
const stageKeys = Object.keys(stageLabels);
const completedStageCount = stageKeys.filter((stage) => stages.includes(stage)).length;
const latestClue = clues[clues.length - 1];
const trackSteps = researchRun?.steps || [];
const completedTrackCount = trackSteps.filter((step) => step.status === 'COMPLETED').length;
```

- [ ] **Step 2: Render waiting state as `attribution-workbench`**

Replace the single-column waiting content with:

```tsx
<div className="attribution-progress">
  <h4>🔬 正在研究：{name || code} 的涨跌原因</h4>
  <div className="attribution-workbench">
    <div className="attribution-path-panel">
      ...
    </div>
    <aside className="attribution-intel-panel" aria-label="研究态势">
      ...
    </aside>
  </div>
</div>
```

- [ ] **Step 3: Keep existing progress, clues, and Harness details visible**

Move existing `attribution-steps`、`attribution-clues`、Harness 恢复列表 into the left panel without changing their text semantics.

- [ ] **Step 4: Add right-side dashboard content**

Render:

```tsx
<div className="attribution-intel-head">
  <span className="watchlist-meta">研究态势</span>
  <strong>{stageLabels[currentStage] || currentStage}</strong>
</div>
<div className="attribution-intel-metrics">
  <div><span>阶段进度</span><strong>{completedStageCount}/{stageKeys.length}</strong></div>
  <div><span>已发现线索</span><strong>{clues.length} 条</strong></div>
  <div><span>轨道进度</span><strong>{trackSteps.length ? `${completedTrackCount}/${trackSteps.length}` : '等待'}</strong></div>
</div>
```

Track matrix:

```tsx
<div className="attribution-track-grid">
  {trackSteps.length > 0 ? trackSteps.map((step) => (
    <div className={`attribution-track-card status-${step.status.toLowerCase()}`} key={step.stepId}>
      <span>{trackLabels[step.track || ''] || step.track || step.stepId}</span>
      <strong>{step.status}</strong>
      {step.outputSummary && <small>{step.outputSummary}</small>}
    </div>
  )) : <p className="muted">等待 Harness 回传轨道状态。</p>}
</div>
```

## Task 3: Implement Report Two-Column Layout

**Files:**
- Modify: `frontend/src/features/watchlist/AttributionReaderView.tsx`

- [ ] **Step 1: Wrap report content in main and side columns**

Keep summary, primary driver and drivers in `attribution-report-main`; move uncertainties, observation windows, disclaimer and evidences into `attribution-report-side`.

- [ ] **Step 2: Preserve existing section text**

Do not rename:

- `一句话归因`
- `首要驱动`
- `驱动因素`
- `不确定性`
- `后续验证`
- `证据`

Existing tests rely on these user-facing labels.

## Task 4: Add CSS

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add layout and panel styles near the existing attribution block**

Add classes for:

- `.attribution-workbench`
- `.attribution-path-panel`
- `.attribution-intel-panel`
- `.attribution-intel-head`
- `.attribution-intel-metrics`
- `.attribution-latest-clue`
- `.attribution-track-grid`
- `.attribution-track-card`
- `.attribution-report-layout`
- `.attribution-report-main`
- `.attribution-report-side`

- [ ] **Step 2: Add status colors**

Use existing CSS variables:

- completed: `var(--accent)`
- running/current/partial: `var(--accent-2)`
- failed: `var(--danger)`
- pending/skipped: `var(--muted)`

- [ ] **Step 3: Add responsive fallback**

```css
@media (max-width: 900px) {
  .attribution-workbench,
  .attribution-report-layout {
    grid-template-columns: 1fr;
  }
}
```

## Task 5: Verify

**Files:**
- Test: `frontend/src/features/watchlist/AttributionReaderView.test.tsx`

- [ ] **Step 1: Run targeted test**

```bash
cd frontend && npm test -- --run src/features/watchlist/AttributionReaderView.test.tsx
```

Expected: all tests in `AttributionReaderView.test.tsx` pass.

- [ ] **Step 2: Run production build**

```bash
cd frontend && npm run build
```

Expected: TypeScript and Vite build exit with code 0.

- [ ] **Step 3: Check touched-file whitespace**

```bash
git diff --check -- frontend/src/features/watchlist/AttributionReaderView.tsx frontend/src/features/watchlist/AttributionReaderView.test.tsx frontend/src/styles.css docs/superpowers/plans/2026-07-11-attribution-research-dashboard.md
```

Expected: no whitespace errors.

## Self-Review

- Spec coverage: waiting-state right-side emptiness is addressed by the research dashboard; completed report state also gains a useful right column.
- Placeholder scan: no `TBD` or deferred implementation steps remain.
- Type consistency: all fields come from existing `AttributionResearchRunView`, `AttributionResearchStep`, `AttributionReport`, and local component state.
