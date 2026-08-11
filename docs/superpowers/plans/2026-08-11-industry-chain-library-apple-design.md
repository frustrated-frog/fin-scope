# Industry Chain Library Apple Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将产业链目录改造成具有 Apple 式材质层次、即时反馈和清晰状态表达的研究档案架。

**Architecture:** 保持 `IndustryChainView` 的数据和事件处理不变，仅为目录增加语义化展示信息并重写其局部 CSS。通过现有组件测试固定目录计数、状态文本、当前项语义和创建入口行为，不引入动画库或新依赖。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Testing Library、Playwright

---

### Task 1: 固定目录信息层级与状态契约

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.tsx`

- [ ] **Step 1: Write the failing test**

增加测试断言：目录展示“2 个图谱”、分组标题“我的图谱”、已生成状态“可查看链上动态”、未生成状态“等待首次生成”，当前产业链按钮具有 `aria-current="page"`。

```tsx
expect(screen.getByText('2 个图谱')).toBeInTheDocument();
expect(screen.getByText('我的图谱')).toBeInTheDocument();
expect(screen.getByText('可查看链上动态')).toBeInTheDocument();
expect(screen.getByText('等待首次生成')).toBeInTheDocument();
expect(screen.getByRole('button', { name: /AI算力/ })).toHaveAttribute('aria-current', 'page');
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- IndustryChainView`

Expected: FAIL，因为新层级、状态文案和 `aria-current` 尚不存在。

- [ ] **Step 3: Write minimal implementation**

在目录标题旁显示数量，在创建入口下增加“我的图谱”分组标题；每个按钮增加状态点、描述、版本胶囊和 chevron，并为当前按钮设置 `aria-current`。

```tsx
<div className="ic-library-heading">
  <div><strong>产业链目录</strong><span>跟踪产业脉络与实时变化</span></div>
  <small>{chains.length} 个图谱</small>
</div>
<div className="ic-library-section-title"><span>我的图谱</span><small>{chains.length}</small></div>
<button aria-current={active ? 'page' : undefined}>
  <i className="ic-chain-status" />
  <span className="ic-chain-copy"><strong>{chain.name}</strong><small>{ready ? '可查看链上动态' : '等待首次生成'}</small></span>
  <span className="ic-chain-revision">{ready ? `R${chain.currentRevisionId}` : '未生成'}</span>
  <span className="ic-chain-chevron" aria-hidden="true">›</span>
</button>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- IndustryChainView`

Expected: PASS。

### Task 2: 建立材质、排版和即时反馈

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] **Step 1: Implement the approved visual tokens**

将 `.ic-library` 改为 256px 半透明结构材质；把 `.ic-create` 改成一体化圆角控件；把 `.ic-chain-list button` 改成圆角档案卡，增加柔和阴影、状态点与右侧 chevron。

```css
.ic-workbench { grid-template-columns: 256px minmax(0, 1fr); }
.ic-library {
  background: rgba(9, 20, 27, .86);
  backdrop-filter: blur(24px) saturate(135%);
}
.ic-create { border-radius: 14px; background: rgba(23, 38, 47, .72); }
.ic-chain-list button { border-radius: 14px; background: rgba(18, 31, 40, .58); }
.ic-chain-list button.is-active {
  background: radial-gradient(circle at 15% 0, rgba(90, 216, 210, .13), transparent 54%), rgba(28, 54, 64, .88);
}
```

- [ ] **Step 2: Add physical feedback and accessibility fallbacks**

为按钮增加 `:active { transform: scale(.985) }`、可见 `:focus-visible`，并补充 `prefers-reduced-motion`、`prefers-reduced-transparency` 和 `prefers-contrast` 媒体查询。

```css
.ic-create button:active,
.ic-chain-list button:active { transform: scale(.985); }
.ic-create:focus-within,
.ic-chain-list button:focus-visible { box-shadow: 0 0 0 3px rgba(90, 216, 210, .14); }
@media (prefers-reduced-motion: reduce) {
  .ic-create button, .ic-chain-list button { transition: none; transform: none !important; }
}
@media (prefers-reduced-transparency: reduce) {
  .ic-library { background: #09141b; backdrop-filter: none; }
}
@media (prefers-contrast: more) {
  .ic-create, .ic-chain-list button { border-color: #7fa4ad; }
}
```

- [ ] **Step 3: Verify focused tests and build**

Run: `npm test -- IndustryChainView && npm run build`

Expected: 测试与构建均通过。

### Task 3: 真实页面视觉验收

**Files:**
- No production files expected

- [ ] **Step 1: Open the real Industry Graph page**

使用本地前后端进入 AI 算力图谱，确认创建入口、两种链状态和当前项都使用真实数据。

- [ ] **Step 2: Inspect desktop and narrow viewport screenshots**

桌面端检查材质层级、间距、对齐、hover/active；窄屏检查横向列表、点击区域和文字截断。

- [ ] **Step 3: Run full verification**

Run: `npm test -- --run && npm run build && git diff --check`

Expected: 全量测试、构建和 diff 检查通过。

- [ ] **Step 4: Commit and push**

```bash
git add frontend/src/features/industry-chain/IndustryChainView.tsx \
  frontend/src/features/industry-chain/IndustryChainView.test.tsx \
  frontend/src/features/industry-chain/industry-chain.css
git commit -m "feat: 优化产业链目录视觉体验"
git push
```
