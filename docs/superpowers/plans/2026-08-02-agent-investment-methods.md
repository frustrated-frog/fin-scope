# Agent 原生投研方法体系第一阶段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让通用深度研究 Agent 能自动选择、校验、持久化并执行财报质量和公司质量两种首批投研方法，并在完成研究前检查方法要求。

**Architecture:** 在 `finscope-service` 增加类型化 `ResearchMethod` 注册表，方法只描述研究问题、证据、计算、反证和完成条件；`ResearchPlanningAgent` 仍是唯一规划入口，将方法编码写入现有 Mission。Mission 将方法蓝图持久化，Agent Context 和 Finish Verifier 消费同一蓝图，领域 Agent 工具化留到第二阶段。

**Tech Stack:** Java 8、Spring Boot 2.7、SQLite、Jackson、JUnit 5、Mockito、Maven

---

### Task 1: 建立首批投研方法注册表

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/ResearchMethod.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/ResearchMethodDefinition.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/FinancialStatementQualityMethod.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/CompanyQualityMethod.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/ResearchMethodRegistry.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/method/ResearchMethodRegistryTest.java`

- [x] **Step 1: 写失败测试**

测试财报问题自动返回 `FINANCIAL_STATEMENT_QUALITY` 和 `COMPANY_QUALITY`，普通股票公司判断只返回 `COMPANY_QUALITY`，主题研究不错误套用公司方法，并断言财报方法包含现金流、非经常性损益、反证和完成条件。

- [x] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchMethodRegistryTest test`

Expected: FAIL，原因是 `research.method` 类型尚不存在。

- [x] **Step 3: 最小实现方法模型与注册表**

`ResearchMethodDefinition` 使用不可变列表保存 `code`、`name`、`description`、`requiredQuestions`、`requiredEvidence`、`requiredCalculations`、`counterChecks`、`completionCriteria` 和 `requiredIntents`。`ResearchMethodRegistry#recommend(ResearchPlanningInput)` 使用对象类型与中英文关键词确定候选，并保证稳定顺序和编码唯一。

- [x] **Step 4: 运行测试并确认 GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchMethodRegistryTest test`

Expected: PASS，0 failures。

- [x] **Step 5: 提交并推送**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/research/method backend/finscope-service/src/test/java/com/finscope/service/research/method
git commit -m "feat: 增加首批投研方法注册表"
git push
```

### Task 2: 让规划 Agent 自动选择并校验方法

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionDraft.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanningAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchPlanValidator.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/DeterministicResearchPlanner.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchPlanningAgentTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchPlanValidatorTest.java`

- [x] **Step 1: 写失败测试**

增加以下行为测试：模型为股票财报研究选择合法方法后通过；输出未知方法、重复方法或不支持当前对象的方法时整份计划被拒绝；模型不可用时确定性规划器自动选择两个首批方法；系统 Prompt 和用户 Prompt 只暴露注册表中的方法编码及方法契约。

- [x] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchPlanningAgentTest,ResearchPlanValidatorTest test`

Expected: FAIL，原因是 Draft 没有方法蓝图字段，Validator 不认识方法注册表。

- [x] **Step 3: 扩展严格规划契约**

在 `ResearchMissionDraft` 增加 `researchType`、`methodCodes`、`requiredEvidence`、`requiredCalculations`、`counterChecks` 和 `completionCriteria`。`ResearchPlanningAgent` 将注册方法的编码、说明和要求加入提示词；`ResearchPlanValidator#validate(draft,input)` 校验编码白名单、重复项和对象支持性，并以注册表定义重新生成聚合要求，拒绝模型伪造的方法要求。确定性规划器使用同一个注册表生成相同蓝图。

- [x] **Step 4: 运行测试并确认 GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchPlanningAgentTest,ResearchPlanValidatorTest test`

Expected: PASS，0 failures。

- [x] **Step 5: 提交并推送**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/research/mission backend/finscope-service/src/test/java/com/finscope/service/research/mission
git commit -m "feat: 让研究Agent自动选择投研方法"
git push
```

### Task 7: 加固 Agent 决策与 Mission Task 执行闭环

- [x] **Step 1: 在 Agent 决策与持久化表中增加 `missionTaskKey`**
- [x] **Step 2: 校验任务归属、执行状态、依赖、工具、意图和查询参数**
- [x] **Step 3: Observation 仅按明确任务键回写，不再按同意图任务猜测匹配**
- [x] **Step 4: 证据充分时自动跳过剩余检索并完成评估任务**
- [x] **Step 5: 增加重复同意图任务、自动评估和端到端执行闭环测试**
- [x] **Step 6: 运行服务模块全量回归（670 tests，0 failures/errors）**

### Task 3: 持久化方法蓝图

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMission.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/research/mission/ResearchMissionRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/research/mission/ResearchMissionRepositoryTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchMissionServiceTest.java`

- [x] **Step 1: 写失败测试**

仓储测试保存并读取研究类型、方法编码、证据要求、计算要求、反证和完成条件；服务测试断言 `ResearchPlanningResult` 的完整蓝图传给仓储，重启读取后内容不丢失。

- [x] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -pl finscope-dao,finscope-service -Dtest=ResearchMissionRepositoryTest,ResearchMissionServiceTest test`

Expected: FAIL，原因是 Mission 和数据库尚无蓝图字段。

- [x] **Step 3: 增加向后兼容字段和仓储映射**

为 `research_mission` 增加 `research_type`、`method_codes`、`required_evidence`、`required_calculations`、`counter_checks`、`completion_criteria` 六列，并对旧数据库逐列执行 `ensureColumn`。Repository 使用现有单元分隔符保存列表，`replacePlan` 原子替换任务和方法蓝图；Service 从 Draft 传递全部字段。

- [x] **Step 4: 运行测试并确认 GREEN**

Run: `cd backend && mvn -pl finscope-dao,finscope-service -Dtest=ResearchMissionRepositoryTest,ResearchMissionServiceTest test`

Expected: PASS，0 failures。

- [x] **Step 5: 提交并推送**

```bash
git add backend/finscope-domain backend/finscope-dao backend/finscope-service
git commit -m "feat: 持久化研究方法蓝图"
git push
```

### Task 4: 将方法要求纳入 Agent 决策上下文和完成门禁

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/research/method/ResearchMethodCompletionPolicy.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchAgentContextBuilder.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/ResearchFinishVerifier.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchAgentContextBuilderTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchFinishVerifierTest.java`

- [x] **Step 1: 写失败测试**

上下文测试断言 Prompt 显示所选方法、必需证据、计算、反证和完成条件；完成门禁测试断言方法要求的 `PRIMARY`、`SUPPORT`、`COUNTER`、`ASSESS` 任务未终态时拒绝完成，全部完成或因证据充分跳过时允许继续。

- [x] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchAgentContextBuilderTest,ResearchFinishVerifierTest test`

Expected: FAIL，原因是 Context 和 Finish Verifier 尚未消费方法蓝图。

- [x] **Step 3: 实现方法完成策略**

`ResearchMethodCompletionPolicy` 从注册表读取每个方法的 `requiredIntents`，检查 Mission Task 是否存在对应意图且状态属于 `COMPLETED`，或以 `SUFFICIENT_EVIDENCE` 原因进入 `SKIPPED`；返回明确缺口文本。Context Builder 把完整方法蓝图加入受长度限制的 Prompt；Finish Verifier 将方法缺口与现有证据、活动任务和运行时缺口合并，并优先返回 `METHOD_INCOMPLETE`。

- [x] **Step 4: 运行测试并确认 GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchAgentContextBuilderTest,ResearchFinishVerifierTest test`

Expected: PASS，0 failures。

- [x] **Step 5: 提交并推送**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/research backend/finscope-service/src/test/java/com/finscope/service/research
git commit -m "feat: 增加投研方法完成门禁"
git push
```

### Task 6: 修复方法执行状态闭环

**Files:**
- Modify: `ResearchPlanningAgent`、`ResearchPlanValidator`、`DeterministicResearchPlanner`
- Modify: `ResearchAgentTurnService`、`ResearchMissionService`、`ResearchMethodCompletionPolicy`
- Test: 规划、Agent Loop、Mission Service、Finish Verifier 和旧库迁移测试

- [x] **Step 1: 复现 Agent 工具完成但 Mission Task 仍为 PENDING**

- [x] **Step 2: 将工具 Observation 匹配并回写对应 Mission Task**

- [x] **Step 3: 仅接受因证据充分产生的 SKIPPED 方法任务**

- [x] **Step 4: 向规划 Agent 暴露完整方法合同并校验必需意图**

- [x] **Step 5: 让确定性任务和检索词体现财报质量、公司质量方法要求**

- [x] **Step 6: 增加旧 SQLite 表升级回归并通过相关测试**

### Task 5: 全量回归与文档同步

**Files:**
- Modify: `docs/superpowers/specs/2026-08-01-agent-investment-methods-design.md`
- Modify: `docs/superpowers/plans/2026-08-02-agent-investment-methods.md`

- [x] **Step 1: 运行服务模块回归**

Run: `cd backend && mvn -pl finscope-service -am test`

Expected: BUILD SUCCESS，0 failures/errors。

- [x] **Step 2: 运行全部后端测试**

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS，0 failures/errors。

- [x] **Step 3: 检查工作树与敏感信息**

Run: `git diff --check && git status --short && git diff --cached --check`

Expected: 无空白错误；只有本功能明确修改的文件；配置中的固定密钥没有被读取、打印或移动。

- [x] **Step 4: 更新计划状态并提交推送**

将已执行步骤改为 `[x]`，在设计文档分阶段实施处标记第一阶段实际落地范围，不写未经验证的完成声明。

```bash
git add docs/superpowers/specs/2026-08-01-agent-investment-methods-design.md docs/superpowers/plans/2026-08-02-agent-investment-methods.md
git commit -m "docs: 记录投研方法第一阶段实现"
git push
```
