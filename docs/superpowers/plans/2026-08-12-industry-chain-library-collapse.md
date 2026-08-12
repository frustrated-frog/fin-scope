# Industry Chain Library Collapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为产业链目录增加桌面端收起/展开能力，并把新建产业链表单改成清晰的单层控件。

**Architecture:** 在 `IndustryChainView` 内维护会话级折叠状态，通过根容器修饰类切换两列网格宽度；目录内容与窄轨道互斥渲染。视觉调整全部限定在产业链样式文件，不新增持久化或后端接口。

**Tech Stack:** React 18、TypeScript、CSS、Vitest、Testing Library

---

### Task 1: 用交互测试定义目录折叠行为

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] 新增测试，断言默认显示完整目录，点击“收起目录”后完整内容消失并出现“展开目录”，再次点击恢复。
- [ ] 运行 `npm test -- --run src/features/industry-chain/IndustryChainView.test.tsx`，确认测试因缺少折叠按钮而失败。

### Task 2: 实现折叠状态与单层新建控件

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainView.tsx`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] 在组件内增加 `libraryCollapsed` 状态、根修饰类和带可访问性属性的切换按钮。
- [ ] 折叠态仅渲染窄轨道；展开态保留标题、表单、列表和空状态。
- [ ] 重写 `.ic-create` 的输入和按钮边界，使表单只有一层外框，按钮用分隔线划区。
- [ ] 增加桌面宽度过渡、移动端强制展开、减少动画和高对比度适配。
- [ ] 重新运行产业链视图测试并确认通过。

### Task 3: 验证并提交

**Files:**
- Verify: `frontend/src/features/industry-chain/IndustryChainView.tsx`
- Verify: `frontend/src/features/industry-chain/industry-chain.css`
- Verify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] 运行 `npm test -- --run src/features/industry-chain/IndustryChainView.test.tsx`，预期全部通过。
- [ ] 运行 `npm run build`，预期 TypeScript 与 Vite 构建成功。
- [ ] 在真实浏览器中验证展开、收起、恢复和输入聚焦，并保存截图。
- [ ] 仅提交本任务文件，提交信息使用 `feat: 支持产业链目录收起展开`，随后推送当前分支。
