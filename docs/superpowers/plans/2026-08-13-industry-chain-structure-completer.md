# 产业图谱通用结构补全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为所有产业链提供可解释的结构完整度、V2→V3/稀疏 V3 补全能力、Kafka 可重放异步编排和高级前端反馈。

**Architecture:** 领域层定义结构评估结果和版本化生成事件；service 层集中评估、任务编排与幂等执行；rpc 层仅实现 Kafka 发布；web 层仅消费消息并调用执行器。结构补全复用现有 evidence collector、synthesis agent 和 validator，数据库 revision 继续承担任务状态与原子发布边界。

**Tech Stack:** Java 21、Spring Boot 2.7、Spring Kafka、SQLite、React、TypeScript、Vitest、Vite

---

### Task 1: 通用结构完整度评估

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainStructureAssessment.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainStructureAssessor.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainStructureAssessorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainServiceTest.java`

- [ ] Write tests for BUILDING, V2 upgrade, sparse V3 and complete V3.
- [ ] Run `cd backend && mvn -pl finscope-service -am -Dtest=IndustryChainStructureAssessorTest,IndustryChainServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` and verify RED.
- [ ] Implement deterministic, industry-agnostic scoring and expose it on `Workspace`.
- [ ] Re-run the focused tests and verify GREEN.
- [ ] Commit and push: `feat: 增加产业图谱结构完整度评估`.

### Task 2: 版本化 Kafka 生成事件与可靠回退

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainGenerationMessage.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainGenerationPublisher.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/industrychain/KafkaIndustryChainGenerationPublisher.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/industrychain/KafkaIndustryChainGenerationPublisherTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainServiceTest.java`

- [ ] Write publisher contract tests for enabled success, disabled fallback and send failure.
- [ ] Write service tests proving Kafka success skips the local executor and failure falls back.
- [ ] Run focused RPC/service tests and verify RED.
- [ ] Implement `publish()` with bounded broker acknowledgement and boolean dispatch result.
- [ ] Re-run tests and verify GREEN.
- [ ] Commit and push: `feat: 使用Kafka派发产业图谱补全任务`.

### Task 3: 幂等消费者与结构补全执行器

**Files:**
- Create: `backend/finscope-web/src/main/java/com/finscope/web/messaging/IndustryChainGenerationListener.java`
- Create: `backend/finscope-web/src/test/java/com/finscope/web/messaging/IndustryChainGenerationListenerTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/industrychain/IndustryChainRepository.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGenerationExecutor.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGenerationExecutorTest.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] Write tests for valid consumption, invalid message rejection and duplicate READY revision skip.
- [ ] Run focused tests and verify RED.
- [ ] Add public revision lookup, ID-based executor entry and Kafka listener.
- [ ] Add industry-chain topic/group config and trusted message package without changing keys.
- [ ] Re-run focused tests and verify GREEN.
- [ ] Commit and push: `feat: 增加可重放的产业图谱补全消费链路`.

### Task 4: 复用归纳 Agent 完成 V2→V3 和稀疏 V3 补全

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainSynthesisAgent.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainSynthesisAgentTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainGenerationExecutor.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainGenerationExecutorTest.java`

- [ ] Write tests proving previous graph context is supplied and current evidence remains authoritative.
- [ ] Run focused tests and verify RED.
- [ ] Add optional previous graph payload, gap hints and completion-specific prompt constraints.
- [ ] Add `COMPLETING_STRUCTURE` and `VALIDATING_STRUCTURE` progress stages.
- [ ] Re-run all industry-chain backend tests and verify GREEN.
- [ ] Commit and push: `feat: 复用归纳智能体补全产业图谱结构`.

### Task 5: 高级结构仪表与异步状态反馈

**Files:**
- Modify: `frontend/src/features/industry-chain/industryChainTypes.ts`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.tsx`
- Modify: `frontend/src/features/industry-chain/IndustryChainView.test.tsx`
- Modify: `frontend/src/features/industry-chain/industry-chain.css`
- Modify: `frontend/src/features/industry-chain/IndustryChainCreateStyles.test.ts`

- [ ] Write component/style tests for score, status, gap hint, button label and completion stages.
- [ ] Run `cd frontend && npm test -- src/features/industry-chain/IndustryChainView.test.tsx src/features/industry-chain/IndustryChainCreateStyles.test.ts` and verify RED.
- [ ] Implement compact responsive meter, semantic action copy and stage-specific progress.
- [ ] Re-run focused tests and verify GREEN.
- [ ] Commit and push: `feat: 增加产业图谱结构补全仪表`.

### Task 6: 全量验证与本地 Kafka 烟雾测试

**Files:**
- Modify if needed: `README.md` or relevant setup documentation only when runtime behavior changed.

- [ ] Run `cd backend && mvn test`.
- [ ] Run `cd frontend && npm test && npm run build`.
- [ ] Run `git diff --check` and `git status --short`.
- [ ] Confirm local Kafka topic exists and run an application-level publish/consume smoke test without printing credentials.
- [ ] Verify desktop and 390px mobile presentation with a browser screenshot if the app can be started safely.
- [ ] Commit any verification-driven fixes separately and push the branch.

