# 财报解读 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 A 股非金融企业财报增加可追溯、受数字与引用门禁约束、失败可降级的一键 Agent 解读。

**Architecture:** 先由 Java 确定性计算生成不可变分析快照和稳定证据 ID，再由单个 LLM Agent 输出固定 JSON；Parser 和 Trust Gate 校验后异步保存，失败时生成同协议的规则化结果。React 页面只渲染服务端已验收 DTO，并通过证据标签展示来源。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、JUnit 5、React 18、TypeScript、Vitest。

---

## 文件结构

- `backend/finscope-domain/.../financials/FinancialAnalysisSnapshot.java`：不可变分析输入元数据。
- `backend/finscope-domain/.../financials/FinancialEvidence.java`：统一指标、科目、规则与缺口证据。
- `backend/finscope-domain/.../financials/FinancialInterpretation.java`：任务状态、六维结果与历史 DTO。
- `backend/finscope-dao/.../financials/FinancialSchemaMigrator.java`：迁移 302。
- `backend/finscope-dao/.../financials/FinancialAnalysisSnapshotRepository.java`：快照幂等保存与读取。
- `backend/finscope-dao/.../financials/FinancialInterpretationRepository.java`：追加式任务历史与状态更新。
- `backend/finscope-service/.../financials/FinancialAnalysisEngine.java`：V2 指标与规则。
- `backend/finscope-service/.../financials/FinancialTrendEngine.java`：年度与单季度同口径趋势。
- `backend/finscope-service/.../financials/FinancialEvidencePacketAssembler.java`：规范化快照、指纹和证据索引。
- `backend/finscope-service/.../financials/FinancialInterpretationResponseParser.java`：提取模型 JSON。
- `backend/finscope-service/.../financials/FinancialInterpretationGate.java`：结构、引用、数字、置信度与禁止表达校验。
- `backend/finscope-service/.../financials/FinancialInterpretationFallbackBuilder.java`：确定性降级输出。
- `backend/finscope-service/.../financials/FinancialInterpretationAgent.java`：主请求、一次修复与降级。
- `backend/finscope-service/.../financials/FinancialInterpretationFacade.java`：快照、幂等、异步状态和 Trace 编排。
- `backend/finscope-web/.../controller/FinancialsController.java`：生成、详情、最新和历史接口。
- `backend/finscope-web/.../config/AppConfig.java`：有界财报 Agent 线程池。
- `frontend/src/features/financials/financialTypes.ts`：解读与证据类型。
- `frontend/src/features/financials/FinancialInterpretationPanel.tsx`：解读、历史和证据抽屉。
- `frontend/src/features/financials/FinancialsView.tsx`：加载与挂载 Agent 区域。
- `frontend/src/styles.css`：解读区视觉样式。

### Task 1: 扩展确定性指标与规则

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialAnalysisEngineTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialAnalysisEngine.java`

- [ ] **Step 1: 写失败测试**

加入用例，输入营收、归母净利润、经营现金流、流动资产、流动负债、货币资金、存货、短期和长期借款、资本开支，断言：

```java
assertEquals(new BigDecimal("25.000000"), metric(result, "NET_PROFIT_PARENT_YOY"));
assertEquals(new BigDecimal("150.000000"), metric(result, "CURRENT_RATIO"));
assertEquals(new BigDecimal("120.000000"), metric(result, "QUICK_RATIO"));
assertEquals(new BigDecimal("500.000000"), metric(result, "INTEREST_BEARING_DEBT"));
assertEquals(new BigDecimal("60.000000"), metric(result, "FREE_CASH_FLOW"));
```

再加入缺少资本开支时不生成自由现金流的断言。

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl finscope-service -am -Dtest=FinancialAnalysisEngineTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，提示新指标不存在。

- [ ] **Step 3: 最小实现**

在 `FinancialAnalysisEngine` 增加稳定指标代码、V2 公式版本、空值/零分母保护，并仅在输入完整时输出指标。保留现有 V1 行为和规则代码兼容性。

- [ ] **Step 4: 验证 GREEN**

运行 Step 2 命令，Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialAnalysisEngine.java backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialAnalysisEngineTest.java
git commit -m "feat: 扩展财报确定性分析指标"
```

### Task 2: 建立快照、证据与持久化

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/financials/FinancialAnalysisSnapshot.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/financials/FinancialEvidence.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/financials/FinancialInterpretation.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/financials/FinancialSchemaMigrator.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/financials/FinancialAnalysisSnapshotRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/financials/FinancialInterpretationRepository.java`
- Create: `backend/finscope-dao/src/test/java/com/finscope/dao/financials/FinancialInterpretationPersistenceTest.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidencePacket.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialTrendEngine.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialEvidencePacketAssembler.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialEvidencePacketAssemblerTest.java`

- [ ] **Step 1: 写迁移与仓储失败测试**

测试迁移两次后 `schema_migration.version=302` 只有一条，保存相同 `(reportId, algorithmVersion, inputHash)` 复用快照，解读历史按 ID 倒序且失败记录不删除成功记录。

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl finscope-dao -am -Dtest=FinancialInterpretationPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，类或表不存在。

- [ ] **Step 3: 实现迁移与仓储**

迁移 302 创建 `financial_analysis_snapshot` 和 `financial_interpretation`；快照唯一键为 `(report_id,algorithm_version,input_hash)`，解读仅建普通历史、快照状态和生成键索引。`FinancialInterpretationRepository` 提供：

```java
FinancialInterpretation save(FinancialInterpretation value);
void update(FinancialInterpretation value);
Optional<FinancialInterpretation> findById(Long id);
Optional<FinancialInterpretation> findLatestDisplayable(Long reportId);
Optional<FinancialInterpretation> findReusable(String generationKey);
List<FinancialInterpretation> findHistory(Long reportId, int limit);
```

- [ ] **Step 4: 写证据包失败测试**

断言同一 `FinancialReportView` 两次组装得到相同 `inputHash` 和证据 ID；更改一个指标值后指纹变化；科目数据库 ID 改变但内容相同不改变证据 ID。趋势用例断言年度序列只包含年报、单季度序列只包含 `CURRENT_QUARTER`，累计季度不会混入两者。

- [ ] **Step 5: 验证证据测试 RED**

Run: `mvn -pl finscope-service -am -Dtest=FinancialEvidencePacketAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，assembler 不存在。

- [ ] **Step 6: 实现证据包**

组装器按稳定键排序，用 Jackson 规范化后计算 SHA-256。证据 ID 使用 `M_`、`L_`、`F_`、`T_`、`G_` 前缀，并返回不可变证据列表、引用索引、允许数字集合和 `qualityCeiling`。趋势引擎分别产生最多 5 个年度点和 8 个单季度点，不对累计季度序列贴单季度标签。

- [ ] **Step 7: 验证 GREEN 并提交**

运行 Step 2 和 Step 5 命令，Expected: PASS；提交：

```bash
git add backend/finscope-domain backend/finscope-dao backend/finscope-service
git commit -m "feat: 建立财报分析快照与证据包"
```

### Task 3: 实现 Parser、Trust Gate、Agent 与降级

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationResponseParser.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationGate.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationFallbackBuilder.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationAgent.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialInterpretationGateTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialInterpretationAgentTest.java`

- [ ] **Step 1: 写 Gate 失败测试**

分别断言合法六维 JSON 通过，不存在的引用、证据外数字、超过质量上限的置信度、买卖建议、重复/缺失维度被拒绝。

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl finscope-service -am -Dtest=FinancialInterpretationGateTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，Gate 不存在。

- [ ] **Step 3: 实现 Parser 与 Gate**

Parser 从 Markdown 包裹中提取首个完整 JSON 对象。Gate 将 JSON 映射为 `FinancialInterpretation.Result`，校验六个固定维度、枚举、引用、数字白名单和禁止表达；返回所有校验错误供修复请求使用。

- [ ] **Step 4: 写 Agent 失败测试**

使用可编程 `LlmChatClient` 验证：合法首答只调用一次；非法首答、合法修复调用两次且 `generationMode=REPAIRED`；两次非法得到 `DETERMINISTIC_FALLBACK`；未配置和超时均降级。

- [ ] **Step 5: 验证 Agent RED**

Run: `mvn -pl finscope-service -am -Dtest=FinancialInterpretationAgentTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，Agent 不存在。

- [ ] **Step 6: 实现 Agent 与降级**

主请求超时 60 秒，修复请求 30 秒。模型只得到证据包 JSON；修复输入包含截断后的错误与原输出。降级构造器从指标、发现和缺口生成统一结果，明确 `confidence=LOW` 和免责声明。

- [ ] **Step 7: 验证 GREEN 并提交**

运行 Step 2 和 Step 5 命令，Expected: PASS；提交：

```bash
git add backend/finscope-service backend/finscope-domain
git commit -m "feat: 实现受证据约束的财报解读 Agent"
```

### Task 4: 异步 Facade、Trace 与 REST API

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/financials/FinancialInterpretationFacade.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/financials/FinancialInterpretationFacadeTest.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/FinancialsController.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/request/financials/FinancialInterpretationRequest.java`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/FinancialsApiIntegrationTest.java`

- [ ] **Step 1: 写 Facade 失败测试**

断言普通请求复用同生成键记录，`force=true` 创建新版本，同报告已有运行任务时复用运行记录，完成后写入终态且 Trace 主题为 `FINANCIAL_INTERPRETATION`。

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl finscope-service -am -Dtest=FinancialInterpretationFacadeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，Facade 不存在。

- [ ] **Step 3: 实现异步编排**

请求阶段读取 `FinancialQueryService.view(reportId)`、组装/保存快照、计算 `generationKey=inputHash|promptVersion|model`、保存 `QUEUED` 后提交有界线程池。执行阶段依次更新 `RUNNING`、`VALIDATING` 和终态，不在数据库事务中调用 LLM。

- [ ] **Step 4: 写 API 失败测试**

断言 POST 返回 202；latest、history、detail 和 evidence 返回统一 `ApiResponse`；不存在的报告/解读返回既有错误契约。

- [ ] **Step 5: 验证 API RED**

Run: `mvn -pl finscope-web -am -Dtest=FinancialsApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新路由为 404。

- [ ] **Step 6: 实现 API 和线程池**

新增 `financialInterpretationExecutor`，核心线程 1、最大线程 2、队列 20；Controller 暴露已确认的五个接口，证据接口仅返回当前结果引用到的证据。

- [ ] **Step 7: 验证 GREEN 并提交**

运行 Step 2 和 Step 5 命令，Expected: PASS；提交：

```bash
git add backend/finscope-service backend/finscope-web
git commit -m "feat: 接入财报解读异步 API"
```

### Task 5: React 解读区、证据抽屉与历史

**Files:**
- Modify: `frontend/src/features/financials/financialTypes.ts`
- Create: `frontend/src/features/financials/FinancialInterpretationPanel.tsx`
- Modify: `frontend/src/features/financials/FinancialsView.tsx`
- Modify: `frontend/src/features/financials/FinancialsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 写失败组件测试**

mock latest 为 404/空时断言出现“生成 Agent 解读”；点击后 POST，轮询到成功，展示经营状态、六维标题和证据标签；点击证据显示来源；重新生成发送 `{force:true}`；失败任务仍展示旧结果。

- [ ] **Step 2: 验证 RED**

Run: `npm test -- FinancialsView.test.tsx`

Expected: FAIL，页面没有 Agent 解读入口。

- [ ] **Step 3: 实现类型和面板**

`FinancialInterpretationPanel` 接受 `reportId`、`interpretation`、`busy`、`onGenerate`、`onSelectHistory`，固定渲染六维、摘要、风险、观察项和限制；证据标签通过 evidence 接口打开侧边详情。

- [ ] **Step 4: 接入 FinancialsView**

加载报告时并行请求 latest；报告切换时清理旧解读与轮询；非终态按 2 秒轮询；生成失败时只更新任务错误，不清空已有 displayable 结果。

- [ ] **Step 5: 验证 GREEN 与构建**

Run: `npm test -- FinancialsView.test.tsx && npm run build`

Expected: tests PASS，TypeScript 和 Vite 构建成功。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/features/financials frontend/src/styles.css
git commit -m "feat: 增加财报 Agent 解读工作区"
```

### Task 6: 全量回归与可信样本验证

**Files:**
- Modify only when a failing regression has a dedicated test first.

- [ ] **Step 1: 后端全量验证**

Run: `mvn test`

Expected: 所有模块测试 PASS。

- [ ] **Step 2: 前端全量验证**

Run: `npm test && npm run build`

Expected: 所有 Vitest 用例 PASS，生产构建成功。

- [ ] **Step 3: 迁移与工作树检查**

Run: `git diff --check && git status --short && git log --oneline -8`

Expected: 无空白错误；只有本功能已知改动或工作树干净；实现提交顺序清晰。

- [ ] **Step 4: 修复回归**

任何失败先添加或收紧可复现测试，确认 RED，再修改最小生产代码并重跑相关测试和全量测试。

- [ ] **Step 5: 确认提交状态**

若 Step 4 没有触发修复，保持工作树干净且不创建空提交；若触发修复，则在对应 RED/GREEN 循环结束时只提交该失败涉及的测试和生产文件，提交信息使用 `fix: 修复财报解读回归问题`。
