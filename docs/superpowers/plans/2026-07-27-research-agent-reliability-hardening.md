# Research Agent Reliability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让研究智能体在空来源、单源失败、连续自适应搜索无进展和证据不足时保持真实、可解释且一致的运行终态。

**Architecture:** 保留现有 `ResearchService -> ResearchRuntimeService -> ResearchMissionService -> ResearchReportService` 主链路，在边界处收紧语义：固定来源扫描不参与无进展预算，自适应搜索参与；Runtime 的硬终止原因不可被最终化覆盖；Mission 在结束时统一关闭遗留任务；报告生成必须至少选择一条有效证据；Planning 降级详情持久化并透传给前端。所有行为先由单元或集成测试复现，再做最小实现。

**Tech Stack:** Java 8、Spring Boot 2.7、JUnit 5、Mockito、SQLite、React、TypeScript、Vitest。

---

### Task 1: 修正无进展预算节点语义

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/runtime/ResearchRuntimeServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/ResearchRuntimeService.java`

- [x] **Step 1: 写固定来源不累计、自适应任务累计的失败测试**

```java
@Test
void configuredSourceDoesNotIncrementNoProgress() { /* collect_source same hash => 0 */ }

@Test
void adaptiveMissionSearchIncrementsNoProgress() { /* mission task same hash => previous + 1 */ }
```

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchRuntimeServiceTest test`

Expected: 固定来源测试收到旧值 `2`，自适应任务测试收到旧值 `0`。

- [x] **Step 3: 最小修改预算识别函数**

```java
private boolean isBudgetedNode(String nodeId) {
    return nodeId != null && (nodeId.startsWith("mission:") || nodeId.startsWith("expand_query:"));
}
```

- [x] **Step 4: 重跑定向测试并确认 GREEN**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchRuntimeServiceTest test`

Expected: PASS。

### Task 2: 统一 Runtime 与 Mission 终态

**Files:**
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/research/mission/ResearchMissionRepositoryTest.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/research/mission/ResearchMissionRepository.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/mission/ResearchMissionServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/runtime/ResearchRuntimeServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/runtime/ResearchRuntimeService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`

- [x] **Step 1: 写 Mission 终态关闭遗留任务的失败测试**

```java
service.completeMission(runId, true, "NO_PROGRESS");
verify(repository).skipUnfinishedTasks(runId, "RUNTIME_TERMINATED:NO_PROGRESS");
```

- [x] **Step 2: 写 Runtime 最终化保留终止原因的失败测试**

```java
assertEquals("NO_PROGRESS", service.complete(runId).getTerminationReason());
assertEquals("TERMINATED", service.complete(runId).getStatus());
```

- [x] **Step 3: 运行定向测试确认 RED**

Run: `cd backend && mvn -pl finscope-dao,finscope-service -am -Dtest=ResearchMissionRepositoryTest,ResearchMissionServiceTest,ResearchRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [x] **Step 4: 实现统一终态操作**

```java
public int skipUnfinishedTasks(Long runId, String reason) {
    return jdbcTemplate.update("UPDATE research_mission_task SET status='SKIPPED',skip_reason=? ... WHERE research_run_id=? AND status IN ('PENDING','RUNNING','INTERRUPTED')", reason, runId);
}
```

`ResearchService` 从 checkpoint 读取硬终止原因，报告最终化完成后保持 Run/Mission 为 `PARTIAL_SUCCESS`，并将未执行任务标记为 `SKIPPED`；无硬终止时按原成功语义完成。

- [x] **Step 5: 重跑定向测试确认 GREEN**

### Task 3: 增加报告零证据真实性门槛

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/report/ResearchReportServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/report/ResearchReportService.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/ResearchService.java`

- [x] **Step 1: 写零选中证据禁止落报告的失败测试**

```java
assertThrows(InsufficientResearchEvidenceException.class, () -> service.generate(runId));
verify(reportRepository, never()).upsert(any());
verify(vaultWriter, never()).writeResearchReport(any(), any(), any());
```

- [x] **Step 2: 运行报告测试确认 RED**

Run: `cd backend && mvn -pl finscope-service -Dtest=ResearchReportServiceTest test`

- [x] **Step 3: 在生成与落盘前执行证据门槛**

```java
if (evidence.isEmpty()) {
    throw new InsufficientResearchEvidenceException("研究运行没有可引用的有效证据，已阻止生成结论报告");
}
```

异常由编排层转化为真实的失败/部分成功和任务终态，不创建伪成功报告。

- [x] **Step 4: 重跑报告和编排测试确认 GREEN**

### Task 4: 持久化并展示 Planning 降级详情

**Files:**
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/research/mission/ResearchMission.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/research/mission/ResearchMissionRepository.java`
- Modify: `backend/finscope-dao/src/test/java/com/finscope/dao/research/mission/ResearchMissionRepositoryTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchMissionService.java`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/features/research/ResearchMissionMap.tsx`
- Modify: `frontend/src/features/research/ResearchMissionMap.test.tsx`

- [x] **Step 1: 写 Repository 和 UI 失败测试**

验证 `fallbackDetail` 能往返 SQLite，并在确定性降级提示中显示校验失败详情。

- [x] **Step 2: 分别运行后端与前端定向测试确认 RED**

Run: `cd backend && mvn -pl finscope-dao -am -Dtest=ResearchMissionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Run: `cd frontend && npm test -- ResearchMissionMap.test.tsx`

- [x] **Step 3: 增加兼容迁移与 API 字段**

```java
ensureColumn("research_mission", "fallback_detail", "TEXT");
repository.replacePlan(..., result.getFallbackReason(), result.getFallbackDetail());
```

- [x] **Step 4: 展示安全截断后的降级原因**

```tsx
{mission.fallbackDetail && <p className="research-mission-fallback-detail">{mission.fallbackDetail}</p>}
```

- [x] **Step 5: 重跑定向测试确认 GREEN**

### Task 5: 全量验证、文档同步、提交推送

**Files:**
- Modify: `docs/产品需求-自适应研究智能体与过程可视化.md`
- Modify: `docs/技术方案-自适应研究智能体与过程可视化.md`
- Modify: `docs/superpowers/plans/2026-07-27-research-agent-reliability-hardening.md`

- [x] **Step 1: 运行后端全量测试**

Run: `cd backend && mvn test`

Expected: 0 failures, 0 errors。

- [x] **Step 2: 运行前端全量测试与生产构建**

Run: `cd frontend && npm test`

Run: `cd frontend && npm run build`

Expected: 测试和构建退出码均为 0。

- [x] **Step 3: 检查差异、敏感信息和工作区状态**

Run: `git diff --check && git status --short && git diff --stat`

- [x] **Step 4: 更新计划勾选与设计文档实现状态**

记录终止语义、零证据门槛和降级详情字段。

- [x] **Step 5: 按约定提交并推送当前分支**

```bash
git add <本期相关文件>
git commit -m "fix: 完成研究智能体可靠性收口"
git push origin codex/adaptive-research-agent
```
