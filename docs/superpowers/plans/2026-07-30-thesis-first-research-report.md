# Thesis-First Research Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将研究报告改造成直接研究命题的“完整事实—AI 解读”结构，隐藏执行过程和证据方向计数，同时保持引用审计与来源追溯。

**Architecture:** 在证据进入报告前使用统一的完整句子选择器，避免任何字符串硬截断。模型报告和确定性回退报告共同输出八段命题优先结构；质量校验器强制事实与 AI 解读成对、禁止旧执行摘要和截断标记，内部证据方向与反方覆盖继续参与安全审计但不展示。

**Tech Stack:** Java 8、Spring Boot 2.7、JUnit 5、OpenAI-compatible LLM、React、TypeScript、Vitest

---

### Task 1: 完整事实段落选择

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchFactText.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchFactTextTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchEvidenceSelector.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchEvidenceDossierBuilder.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchEvidenceSelectorTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchEvidenceDossierBuilderTest.java`

- [ ] **Step 1: 写完整句边界失败测试**

```java
@Test
void selectsCompleteSentencesWithoutEllipsis() {
    String text = "第一句说明事实。第二句补充影响。第三句不应进入。";
    String result = ResearchFactText.completeExcerpt(text, 18);
    assertEquals("第一句说明事实。第二句补充影响。", result);
    assertFalse(result.contains("…"));
}
```

同时将现有 selector 和 dossier 测试从“以省略号结尾”改为“以完整句末标点结尾且不含截断标记”。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchFactTextTest,ResearchEvidenceSelectorTest,ResearchEvidenceDossierBuilderTest test`

Expected: `ResearchFactText` 不存在或现有文本仍含省略号。

- [ ] **Step 3: 实现统一完整事实选择器**

`ResearchFactText.completeExcerpt` 清理 Markdown 链接、搜索排名标记和多余空白；文本超过预算时，优先返回预算内最后一个 `。！？.!?；;` 句末，必要时允许向后寻找最多 200 字的下一个句末，不添加省略号。

```java
final class ResearchFactText {
    static String completeExcerpt(String value, int preferredMaximum) {
        String clean = clean(value);
        if (clean.length() <= preferredMaximum) return clean;
        int boundary = lastBoundary(clean, preferredMaximum);
        if (boundary < Math.min(80, preferredMaximum / 2)) {
            boundary = nextBoundary(clean, preferredMaximum, Math.min(clean.length(), preferredMaximum + 200));
        }
        return boundary > 0 ? clean.substring(0, boundary).trim() : clean;
    }
}
```

Selector 使用 900 字预算，dossier 使用同一选择器；删除所有追加 `…` 的逻辑。

- [ ] **Step 4: 验证事实选择测试通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchFactTextTest,ResearchEvidenceSelectorTest,ResearchEvidenceDossierBuilderTest test`

Expected: 全部通过。

### Task 2: 模型报告改为事实—AI 解读结构

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/StructuredResearchReportAssembler.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportNarrativeAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportBlueprintAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportQualityValidator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchClaimExtractor.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/StructuredResearchReportPipelineTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportQualityValidatorTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchClaimAuditorTest.java`

- [ ] **Step 1: 写模型报告结构失败测试**

断言组装结果：

```java
assertTrue(markdown.contains("## 关键事实与 AI 解读"));
assertTrue(markdown.contains("**事实：**"));
assertTrue(markdown.contains("**AI 解读：**"));
assertTrue(markdown.contains("## 不同解释与不确定性"));
assertTrue(markdown.contains("## 资料来源"));
assertFalse(markdown.contains("## 执行摘要"));
assertFalse(markdown.contains("支持与反向证据比"));
```

质量校验测试要求缺少 AI 解读、含旧执行摘要或含 `（已截断）` 的报告分别返回 `FACT_INTERPRETATION_MISMATCH`、`LEGACY_META_SECTION_PRESENT`、`TRUNCATION_MARKER_PRESENT`。

- [ ] **Step 2: 运行定向测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=StructuredResearchReportPipelineTest,ResearchReportQualityValidatorTest,ResearchClaimAuditorTest test`

Expected: 旧结构仍包含执行摘要且没有事实—AI 解读章节。

- [ ] **Step 3: 重组模型报告与提示词**

Assembler 输出：`核心结论`、`关键事实与 AI 解读`、`命题拆解与综合判断`、`影响机制`、`不同解释与不确定性`、`情景推演`、`结论更新条件`、`资料来源`。每个 argument chain 固定输出：

```markdown
### 事实 1

**事实：** 完整事实段落 [E1]

**AI 解读：** 对应的 argumentAnalysis [E1]

**解释边界：** alternativeExplanation
```

Narrative prompt 要求 argumentAnalysis 逐条提供对象特定、详细且不新增事实的 AI 解读，禁止过程说明、支持/反对计数和截断标记。Blueprint prompt 要求 fact 是完整事实段落。

- [ ] **Step 4: 更新质量与审计边界**

质量校验器使用八个新必需章节，校验事实和 AI 解读数量相同且顺序成对，禁止旧元叙事和截断标记；反方覆盖改为检查“不同解释与不确定性”。Claim extractor 在 `## 资料来源` 前停止提取正文声明，并兼容旧 `## 证据附录`。

- [ ] **Step 5: 验证模型路径测试通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=StructuredResearchReportPipelineTest,ResearchReportQualityValidatorTest,ResearchClaimAuditorTest,ResearchReportBlueprintAgentTest,ResearchReportRepairAgentTest test`

Expected: 全部通过。

### Task 3: 确定性回退报告使用相同结构

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportGenerator.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportGeneratorTest.java`

- [ ] **Step 1: 写回退模板失败测试**

```java
assertTrue(report.getMarkdown().contains("## 关键事实与 AI 解读"));
assertEquals(evidence.size(), occurrences(report.getMarkdown(), "**事实：**"));
assertEquals(evidence.size(), occurrences(report.getMarkdown(), "**AI 解读：**"));
assertFalse(report.getMarkdown().contains("## 执行摘要"));
assertFalse(report.getMarkdown().contains("支持与反向证据比"));
assertFalse(report.getMarkdown().contains("（已截断）"));
assertFalse(report.getMarkdown().contains("…"));
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchReportGeneratorTest test`

Expected: 旧模板断言失败。

- [ ] **Step 3: 重写确定性 Markdown**

生成器沿用八个新章节。每条 evidence card 输出完整 claim、引用和安全的对象特定解读；内部 stance 只影响解读措辞，不显示立场、计数、比例、相关性或来源层级。资料来源只展示证据编号、标题、来源、日期和原文链接。

删除事实上的 `ResearchReportPolicy.bound` 调用以及整份 Markdown 的最终硬截断；长度通过最多 15 条、每条约 900 字的事实选择阶段控制。

- [ ] **Step 4: 验证回退模板测试通过**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchReportGeneratorTest,ResearchReportServiceTest,ResearchReportSynthesisAgentTest test`

Expected: 全部通过。

### Task 4: 前端目录与完整内容回归

**Files:**
- Modify: `frontend/src/features/research/ResearchReportReader.test.tsx`
- Modify: `frontend/src/features/research/researchReportPresentation.test.ts`

- [ ] **Step 1: 增加新章节显示测试**

使用命题优先 Markdown，断言目录包含“关键事实与 AI 解读”和“资料来源”，事实正文不含截断文本，E1 链接仍指向 `#evidence-e1`。

- [ ] **Step 2: 运行前端定向测试**

Run: `cd frontend && npm test -- --run src/features/research/ResearchReportReader.test.tsx src/features/research/researchReportPresentation.test.ts`

Expected: 全部通过；若仅测试数据需更新，不修改阅读器架构。

### Task 5: 全量和真实流程验证

**Files:**
- Verify all modified files

- [ ] **Step 1: 后端全量测试**

Run: `cd backend && mvn test`

Expected: 0 failures, 0 errors。

- [ ] **Step 2: 前端全量测试与生产构建**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 全部测试通过，生产构建成功。

- [ ] **Step 3: 真实报告再生成**

在隔离端口启动后端并重新生成现有已完成研究任务的报告，检查：八个章节齐全、每条事实后有 AI 解读、引用可点击、不含执行摘要、支持/反对计数或截断标记。

- [ ] **Step 4: 提交并推送**

```bash
git diff --check
git add backend frontend docs/superpowers/plans/2026-07-30-thesis-first-research-report.md
git commit -m "feat: 重构命题优先研究报告"
git push origin main
```

