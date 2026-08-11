# 产业链研究内容丰富化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为已有产业链图谱生成并展示产业总览、环节画像、景气与供需、瓶颈变量和公司竞争格局。

**Architecture:** 用一个修订级 `IndustryChainResearchContent` 聚合承载研究内容，并以 JSON 随图谱修订原子发布。合成 Agent 一次生成图结构和研究内容，Validator 校验状态枚举与节点引用；前端新增独立研究视图，并在现有节点 Inspector 中复用画像摘要。

**Tech Stack:** Java 21、Spring Boot 2.7、Spring JDBC、SQLite、Jackson、JUnit 5、React 18、TypeScript、Vitest、CSS。

---

### Task 1: 定义研究内容领域契约

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainResearchContent.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainGraph.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGraphValidatorTest.java`

- [ ] **Step 1: Write the failing validator test**

在现有合法图谱中加入一个引用 `stage:chip` 的环节画像，断言合法内容通过；再使用不存在节点和错误类型，断言抛出 `IllegalArgumentException`。

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=IndustryChainGraphValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `IndustryChainResearchContent` and `setResearchContent` do not exist.

- [ ] **Step 3: Implement the minimal domain aggregate**

新类使用 Lombok `@Data`，定义 `Overview`、`StageProfile`、`CompanyProfile` 静态类，列表默认为空集合；`IndustryChainGraph` 增加默认非空 `researchContent`。

- [ ] **Step 4: Add validator rules and verify GREEN**

校验枚举、画像 `nodeKey` 唯一性，并确保环节画像只引用 `STAGE`、公司画像只引用 `COMPANY`。重跑上述命令，Expected: PASS.

- [ ] **Step 5: Commit and push**

```bash
git add backend/finscope-domain backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGraphValidatorTest.java
git commit -m "feat: 增加产业链研究内容契约"
git push
```

### Task 2: 将研究内容随修订持久化

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/industrychain/IndustryChainRepository.java`
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/industrychain/IndustryChainRepositoryTest.java`

- [ ] **Step 1: Write the failing repository round-trip test**

在测试图谱设置总览与环节画像，发布后重新读取，断言 `lifecycle` 和 `businessModel` 不变。

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=IndustryChainRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the repository does not persist `researchContent`.

- [ ] **Step 3: Add the compatible schema migration**

新建表语句包含 `research_content_json TEXT`，并调用 `ensureColumn("industry_chain_revision", "research_content_json", "TEXT")` 兼容本地旧库。

- [ ] **Step 4: Serialize and restore the aggregate**

使用现有 `ObjectMapper` 序列化聚合；空列、空字符串或旧修订返回新建空聚合，非法 JSON 抛出带业务上下文的 `IllegalStateException`。

- [ ] **Step 5: Run the repository test and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 6: Commit and push**

```bash
git add backend/finscope-dao
git commit -m "feat: 持久化产业链研究内容"
git push
```

### Task 3: 扩展产业链合成 Agent

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainSynthesisAgentTest.java`

- [ ] **Step 1: Write the failing parsing and prompt tests**

将测试 JSON 根契约扩展为 `researchContent`，断言能解析产业总览、环节画像和公司画像，以及 prompt 明确要求景气、供需、核心指标、瓶颈和竞争格局。

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=IndustryChainSynthesisAgentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `researchContent` is rejected by the strict root contract.

- [ ] **Step 3: Implement strict research-content parsing**

扩展根字段契约，添加限长字符串、枚举和去重限量短语列表解析器。每类列表上限 6 项，环节与公司画像数量不得超过对应节点数。

- [ ] **Step 4: Update the prompt and schema version**

要求模型仅为 `STAGE` 和 `COMPANY` 输出画像，未知信息使用空列表或“待观察”，禁止输出股价判断。设置 `INDUSTRY_CHAIN_V2`。

- [ ] **Step 5: Run service industry-chain tests and verify GREEN**

Run: `cd backend && mvn -pl finscope-service -am -Dtest='*IndustryChain*Test' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with zero failures.

- [ ] **Step 6: Commit and push**

```bash
git add backend/finscope-service
git commit -m "feat: 生成产业链景气与竞争格局"
git push
```

### Task 4: 先定义研究面板交互行为

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainTypes.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`
- Create: `frontend/src/features/industry-chain/IndustryChainResearchPanel.test.tsx`

- [ ] **Step 1: Add the TypeScript DTO contract**

添加 `IndustryChainResearchContent`、`IndustryChainResearchOverview`、`IndustryChainStageProfile`、`IndustryChainCompanyProfile` 及状态联合类型，`graph.researchContent` 保持可选以兼容 V1。

- [ ] **Step 2: Write failing view tests**

断言“研究面板”切换按钮、状态中文标签、需求驱动、产业瓶颈、环节商业模式和公司行业位置可见。

- [ ] **Step 3: Write failing empty-state test**

传入不含 `researchContent` 的 V1 图谱，断言显示“刷新图谱后生成研究内容”。

- [ ] **Step 4: Run tests and verify RED**

Run: `cd frontend && npm test -- IndustryChainView IndustryChainResearchPanel`

Expected: FAIL because the view switch and component do not exist.

### Task 5: 实现高级研究面板与 Inspector 摘要

**Files:**
- Create: `frontend/src/features/industry-chain/IndustryChainResearchPanel.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainInspector.tsx`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`

- [ ] **Step 1: Implement status translation helpers**

将 lifecycle、prosperity 和 supplyDemand 映射为中文标签与稳定 CSS modifier，未知值回退为“待观察”。

- [ ] **Step 2: Build the research content hierarchy**

实现状态带、驱动与变量、瓶颈与过剩风险、环节画像及公司矩阵。数据为空时不渲染空标题。

- [ ] **Step 3: Integrate the third view**

将 `viewMode` 扩展为 `panorama | research | dynamics`，研究视图不渲染画布和右侧栏，而是使用整个主内容宽度。

- [ ] **Step 4: Reuse profiles in the node inspector**

选中 `STAGE` 时展示景气、供需、商业模式、核心指标和瓶颈；选中 `COMPANY` 时展示产业位置、产品和竞争优势。

- [ ] **Step 5: Apply the visual system**

使用已有深色工业色板，新增稳定状态色、紧凑数据标签、有序环节卡和可水平滚动公司矩阵。支持 1100px 和 760px 断点、键盘焦点及 reduced motion。

- [ ] **Step 6: Run frontend tests and verify GREEN**

Run: `cd frontend && npm test -- IndustryChainView IndustryChainResearchPanel`

Expected: PASS with zero failures.

- [ ] **Step 7: Commit and push**

```bash
git add frontend/src/features/industry-chain
git commit -m "feat: 增加产业链高级研究面板"
git push
```

### Task 6: 全量验证与视觉验收

**Files:**
- Modify only if verification exposes an in-scope defect.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS, zero test failures.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend && npm test && npm run build`

Expected: all Vitest suites pass and Vite production build exits 0.

- [ ] **Step 3: Run repository quality checks**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended files are modified.

- [ ] **Step 4: Perform browser visual QA**

启动前后端，用实际产业链或测试工作区检查桌面和窄屏：状态带无截断，环节画像层级清晰，公司矩阵可滚动，空状态可理解，不影响产业全景与链上动态。

- [ ] **Step 5: Final commit and push if QA required fixes**

```bash
git add <verified-files>
git commit -m "fix: 优化产业链研究面板细节"
git push
```
