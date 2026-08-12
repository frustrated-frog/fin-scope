# Industry Chain Equal Lanes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 根据产业阶段数量动态等分桌面画布，并让节点、分割线和关系线共享一致的泳道坐标。

**Architecture:** `IndustryChainCanvas` 使用 `ResizeObserver` 测量滚动容器的实际宽度，并把该宽度传给纯函数 `layoutIndustryGraph`。布局函数计算不小于 `292px` 的动态 `laneWidth`，返回完整画布宽度与泳道宽度，Canvas 使用返回值渲染背景泳道和全部节点关系。

**Tech Stack:** React、TypeScript、ResizeObserver、Vitest、Testing Library、Vite

---

### Task 1: 用布局测试定义等分规则

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.test.ts`

- [ ] 新增三阶段 `1500px` 可用宽度测试，断言 `laneWidth` 为 `500`、画布宽度为 `1500`，三个阶段节点分别在泳道内居中。
- [ ] 新增四阶段 `1600px` 可用宽度测试，断言 `laneWidth` 为 `400`、画布宽度为 `1600`。
- [ ] 新增三阶段 `720px` 可用宽度测试，断言 `laneWidth` 保持 `292`、画布宽度为 `876`。
- [ ] 运行布局测试，确认测试因布局函数不接收可用宽度且不返回 `laneWidth` 而失败。

### Task 2: 实现动态泳道布局函数

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainLayout.ts`

- [ ] 将 `layoutIndustryGraph` 签名改为 `layoutIndustryGraph(graph, availableWidth = 0)`。
- [ ] 使用 `Math.max(292, availableWidth / stageCount)` 计算泳道宽度，空阶段按一个阶段处理。
- [ ] 使用 `(laneWidth - nodeWidth) / 2` 将阶段节点和语义节点放在所属泳道中央。
- [ ] 在布局结果中返回 `laneWidth`，并让画布宽度严格等于 `laneWidth * stageCount`。
- [ ] 运行布局测试，确认三阶段、四阶段和窄桌面测试通过。

### Task 3: 让 Canvas 响应容器宽度

**Files:**
- Modify: `frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`

- [ ] 为滚动容器增加 `ref` 与 `availableWidth` 状态。
- [ ] 在 `useLayoutEffect` 中创建 `ResizeObserver`，首次读取 `clientWidth`，后续使用 observer entry 的 `contentRect.width` 更新。
- [ ] ResizeObserver 不存在时仅使用首次 `clientWidth`，卸载时断开 observer。
- [ ] 将 `availableWidth` 传给布局函数，并使用 `layout.laneWidth` 设置每条泳道的 `left` 和 `width`。
- [ ] 在视图测试环境提供可控 ResizeObserver，验证宽度变化后产生三等分泳道。

### Task 4: 清理旧居中样式并验证

**Files:**
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Modify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] 移除 `.ic-canvas` 的 `margin-inline: auto`，因为画布现在直接铺满可用宽度。
- [ ] 更新样式测试，改为断言旧居中补丁已移除，保留图层栏字号契约。
- [ ] 运行产业链布局、视图和样式测试，预期全部通过。
- [ ] 运行 `npm run build`，预期 TypeScript 和 Vite 构建成功。
- [ ] 仅提交本任务文件，提交信息使用 `fix: 按产业阶段等分图谱画布`，推送当前 `main`。
