# Research Evidence Anchor Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 隐藏研究报告中的原始 HTML 证据锚点，同时保持新旧报告的 E1 引用跳转有效。

**Architecture:** 后端停止生成重复的 HTML 锚点；前端对历史 Markdown 做窄范围兼容清理，并沿用现有 H3 标题 ID 生成逻辑。无需数据库迁移或开放原始 HTML 渲染。

**Tech Stack:** Java 8、JUnit 5、React、TypeScript、react-markdown、Vitest、Testing Library

---

### Task 1: 锁定后端新报告格式

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportGeneratorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportGenerator.java`

- [ ] **Step 1: 写失败测试**

在完整报告测试中加入：

```java
assertFalse(report.getMarkdown().contains("<a id=\"evidence-e1\"></a>"));
assertTrue(report.getMarkdown().contains("### E1 ·"));
```

- [ ] **Step 2: 验证测试因现有 HTML 锚点失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchReportGeneratorTest test`

Expected: `generatesAPlannedBoundedChineseReport` 在原始 HTML 锚点断言处失败。

- [ ] **Step 3: 最小化修复生成器**

删除证据附录循环中的：

```java
out.append("<a id=\"evidence-e").append(index + 1).append("\"></a>\n")
```

直接从 `### E...` 标题开始生成。

- [ ] **Step 4: 验证后端测试通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchReportGeneratorTest test`

Expected: 全部通过。

### Task 2: 兼容已保存旧报告

**Files:**
- Modify: `frontend/src/features/research/researchReportPresentation.ts`
- Modify: `frontend/src/features/research/ResearchReportReader.tsx`
- Modify: `frontend/src/features/research/ResearchReportReader.test.tsx`

- [ ] **Step 1: 写失败测试**

构造包含以下 Markdown 的报告：

```markdown
[E1](#evidence-e1)

<a id="evidence-e1"></a>
### E1 · 示例证据
```

验证页面不存在锚点源码文本，`E1` 引用的 `href` 为 `#evidence-e1`，证据标题的 `id` 为 `evidence-e1`。

- [ ] **Step 2: 验证前端测试失败**

Run: `cd frontend && npm test -- --run src/features/research/ResearchReportReader.test.tsx`

Expected: 页面仍能查询到 `<a id="evidence-e1"></a>` 文本。

- [ ] **Step 3: 实现窄范围历史清理**

在展示工具中增加：

```ts
export function sanitizeLegacyEvidenceAnchors(markdown: string) {
  return markdown.replace(/^<a id=["']evidence-e\d+["']><\/a>\s*$/gim, '');
}
```

阅读器使用清理后的 Markdown 提取目录并传给 `ReactMarkdown`；保留现有 H3 ID 渲染。

- [ ] **Step 4: 验证前端定向测试通过**

Run: `cd frontend && npm test -- --run src/features/research/ResearchReportReader.test.tsx`

Expected: 全部通过。

### Task 3: 全量验证并发布

**Files:**
- Verify all modified files

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn test`

Expected: 0 failures, 0 errors。

- [ ] **Step 2: 前端全量测试与构建**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 测试全部通过，生产构建成功。

- [ ] **Step 3: 检查差异并提交推送**

```bash
git diff --check
git add docs backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportGenerator.java backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportGeneratorTest.java frontend/src/features/research/researchReportPresentation.ts frontend/src/features/research/ResearchReportReader.tsx frontend/src/features/research/ResearchReportReader.test.tsx
git commit -m "fix: 修复研究报告证据锚点显示"
git push origin main
```

