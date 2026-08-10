# 雷达事件日期时间展示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 雷达事件卡片将最后发现时间展示为“月日 + 时分”，让跨日热点可以直接辨认日期。

**Architecture:** 保持后端时间字段和接口契约不变，只调整 `RadarEventCard` 内已有的本地展示格式化函数。测试通过真实渲染 `NewsView` 验证用户看到的中文日期时间，非法或缺失时间继续沿用现有回退。

**Tech Stack:** React 18、TypeScript、Vitest、Testing Library

---

### Task 1: 雷达事件日期时间

**Files:**
- Modify: `frontend/src/features/news/RadarEventCard.tsx`
- Test: `frontend/src/features/news/NewsView.test.tsx`

- [x] **Step 1: Write the failing test**

在现有雷达视图测试中渲染默认事件，并断言页面显示 `7月31日 15:55`。

- [x] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

Expected: FAIL，因为当前卡片只渲染 `15:55`。

- [x] **Step 3: Write minimal implementation**

将 `RadarEventCard.tsx` 的 `Intl.DateTimeFormat` 选项增加 `month: 'long'` 和 `day: 'numeric'`，保持 `hour12: false` 与无效时间回退不变。

- [x] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- --run src/features/news/NewsView.test.tsx`

Expected: PASS。

- [x] **Step 5: Run frontend verification**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 全部测试通过且生产构建成功。

- [x] **Step 6: Commit and push**

```bash
git add docs/superpowers/plans/2026-08-10-radar-event-date-time.md frontend/src/features/news/NewsView.test.tsx frontend/src/features/news/RadarEventCard.tsx
git commit -m "fix: 补充雷达热点日期展示"
git push github main
```
