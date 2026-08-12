# Industry Chain Wide Canvas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让产业图谱在宽屏中自动居中，并提高专题图层栏的桌面端文字可读性。

**Architecture:** 仅修改产业链 CSS。利用固定宽度画布的 `margin-inline: auto` 在有剩余空间时自然居中，在空间不足时保持既有横向滚动；图层栏通过明确的字号和行高下限提升可读性。

**Tech Stack:** React、TypeScript、CSS、Vitest、Vite

---

### Task 1: 锁定宽屏居中与字号契约

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] 新增测试，读取 `industry-chain.css` 并断言 `.ic-canvas` 包含 `margin-inline: auto`。
- [ ] 在同一测试中断言图层标题为 `14px`、图层名称为 `12px`、辅助说明与图例至少为 `9px`。
- [ ] 运行 `npm test -- --run src/features/industry-chain/IndustryChainCreateStyles.test.ts`，确认测试因现有样式不满足契约而失败。

### Task 2: 实现桌面宽屏布局

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] 为 `.ic-canvas` 增加 `margin-inline: auto`，不修改内联宽度或节点坐标。
- [ ] 提高 `.ic-layer-title`、`.ic-layer-options`、`.ic-layer-guide` 和 `.ic-layer-legend` 的字号与行高。
- [ ] 调整图层栏最小高度和按钮间距，使放大后的文本继续垂直居中。
- [ ] 运行样式测试确认通过。

### Task 3: 验证并交付

**Files:**
- Verify: `frontend/src/features/industry-chain/industry-chain.css`
- Verify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] 运行产业链样式与视图专项测试，预期全部通过。
- [ ] 运行 `npm run build`，预期 TypeScript 和 Vite 构建成功。
- [ ] 仅提交本任务文件，提交信息使用 `fix: 优化产业图谱宽屏布局`，推送当前 `main`。
