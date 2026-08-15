# Global Expectations Dialog Overflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复全球预期详情弹窗被大量历史柱子撑宽而越过边界的问题。

**Architecture:** 仅调整全局预期弹窗的 CSS 尺寸契约，不改变组件结构或历史数据。通过静态 CSS 回归测试和真实浏览器边界测量共同验证。

**Tech Stack:** React、TypeScript、CSS Grid/Flexbox、Vitest、Vite

---

### Task 1: 锁定弹窗收缩契约

**Files:**
- Create: `frontend/src/features/global-expectations/GlobalExpectationsResponsive.test.ts`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写入失败测试**

创建 CSS 契约测试，断言弹窗使用 `grid-template-columns: minmax(0, 1fr)`、`box-sizing: border-box`、视口最大高度和纵向滚动，图表使用 `min-width: 0`，柱子使用 `flex: 1 1 0` 与 `min-width: 0`。

```ts
expect(dialogRule).toContain('grid-template-columns:minmax(0,1fr)');
expect(dialogRule).toContain('box-sizing:border-box');
expect(dialogRule).toContain('max-height:calc(100dvh - 40px)');
expect(dialogRule).toContain('overflow-y:auto');
expect(historyRule).toContain('min-width:0');
expect(barRule).toContain('flex:1 1 0');
expect(barRule).toContain('min-width:0');
```

- [ ] **Step 2: 验证测试按预期失败**

运行：

```bash
cd frontend && npm test -- GlobalExpectationsResponsive.test.ts
```

预期：测试失败，指出当前弹窗和柱子缺少收缩约束。

- [ ] **Step 3: 实现最小 CSS 修复**

将弹窗改为零最小宽度网格轨道，并允许历史图表和柱子收缩：

```css
.expectation-dialog {
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr);
  max-height: calc(100dvh - 40px);
  overflow-y: auto;
}
.expectation-dialog > *, .expectation-dialog header > div, .detail-history { min-width: 0; }
.expectation-dialog header button { flex: 0 0 28px; }
.detail-history i { flex: 1 1 0; min-width: 0; }
```

- [ ] **Step 4: 运行测试与构建**

```bash
cd frontend && npm test -- GlobalExpectationsResponsive.test.ts GlobalExpectationsView.test.tsx
cd frontend && npm run build
```

预期：相关测试和生产构建成功。

- [ ] **Step 5: 真实页面验证**

在 1280px 和窄屏视口打开详情弹窗，读取 `scrollWidth/clientWidth` 与元素矩形。弹窗无水平溢出，关闭按钮、指标栏、图表全部位于弹窗左右边界内。

- [ ] **Step 6: 提交并推送**

```bash
git add frontend/src/styles.css frontend/src/features/global-expectations/GlobalExpectationsResponsive.test.ts
git commit -m "fix: 修复全球预期详情弹窗溢出"
git push origin codex/global-expectations-monitor
```
