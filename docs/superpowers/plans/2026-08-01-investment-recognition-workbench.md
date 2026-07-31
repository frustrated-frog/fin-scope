# Investment Recognition Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把快讯、聚合变化和长期投资认识组合成每日可使用的研究闭环，并隔离单篇文章型伪主题。

**Architecture:** 前端通过现有研究雷达和知识接口并行读取数据，纯函数负责每日研究投影和知识条目分层，React 组件只负责交互与表达。保持所有后端、数据库结构和现有写入接口不变。

**Tech Stack:** React、TypeScript、Vitest、Testing Library、Vite

---

### Task 1: 每日研究投影

**Files:**
- Create: `frontend/src/features/knowledge/daily/dailyResearchProjection.ts`
- Create: `frontend/src/features/knowledge/daily/dailyResearchProjection.test.ts`

- [ ] 写失败测试，定义雷达变化按优先级排序、快讯按时间排序且限制数量。
- [ ] 运行 `npm test -- dailyResearchProjection.test.ts`，确认因模块缺失失败。
- [ ] 实现 `projectDailyResearch(snapshot)`，只返回用于工作台的变化、快讯和降级信息。
- [ ] 再次运行测试并确认通过。

### Task 2: 今日研究桌面

**Files:**
- Create: `frontend/src/features/knowledge/daily/DailyResearchDesk.tsx`
- Create: `frontend/src/features/knowledge/daily/DailyResearchDesk.test.tsx`
- Modify: `frontend/src/features/knowledge/KnowledgeView.tsx`
- Modify: `frontend/src/features/knowledge/KnowledgeNavigation.tsx`

- [ ] 写失败组件测试，要求同时出现“市场变化”“快讯流水”“尚待确认”“下一观察”。
- [ ] 运行定向测试确认失败。
- [ ] 实现研究桌面，并在 `KnowledgeView` 并行读取 `/api/knowledge/overview` 与 `/api/research-radar?category=ALL&watchlistOnly=false&limit=8`。
- [ ] 保证雷达请求失败时仍渲染知识概览和降级提示。
- [ ] 运行定向测试并确认通过。

### Task 3: 投资认识与待提炼材料分层

**Files:**
- Create: `frontend/src/features/knowledge/topics/knowledgeClassification.ts`
- Create: `frontend/src/features/knowledge/topics/knowledgeClassification.test.ts`
- Modify: `frontend/src/features/knowledge/topics/TopicLibrary.tsx`
- Modify: `frontend/src/features/knowledge/topics/TopicCard.tsx`
- Modify: `frontend/src/features/knowledge/topics/TopicLibrary.test.tsx`
- Modify: `frontend/src/features/knowledge/topics/TopicWorkspace.tsx`
- Modify: `frontend/src/features/knowledge/topics/TopicWorkspace.test.tsx`

- [ ] 写失败测试，定义单来源 EXPLORING 条目为材料，多来源或 BUILDING 条目为投资认识。
- [ ] 运行定向测试确认失败。
- [ ] 实现分类纯函数和双视图资料库。
- [ ] 详情页在没有知识记录时显示“尚未形成投资认识”。
- [ ] 运行定向测试并确认通过。

### Task 4: 研究账页视觉与响应式布局

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] 按 2:1 研究桌面实现布局，以分隔线和文字层级替代装饰卡片。
- [ ] 为键盘焦点、窄屏单列和降级状态添加样式。
- [ ] 运行相关组件测试，避免样式改动破坏语义结构。

### Task 5: 集成与验证

**Files:**
- Modify: `frontend/src/features/knowledge/KnowledgeView.test.tsx`
- Modify: `frontend/src/App.test.tsx`（仅在工作台入口断言需要更新时）

- [ ] 更新集成测试，验证工作台加载雷达、导航名称和失败降级。
- [ ] 运行 `npm test` 并确认全部测试通过。
- [ ] 运行 `npm run build` 并确认生产构建成功。
- [ ] 使用真实浏览器检查桌面与移动布局，修正信息层级和溢出问题。
- [ ] 按可独立验证批次提交并推送当前分支。
