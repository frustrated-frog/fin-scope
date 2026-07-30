# Shared Search Evidence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Tavily 与 AnySearch 协同的多源搜索证据底座，并让研究智能体和归因智能体共同复用。

**Architecture:** `finscope-rpc` 只实现供应商协议，`finscope-service` 的 `SearchEvidenceGateway` 负责 Provider 选择、并发容错、URL 规范化、倒数排名融合、来源分层和正文获取。研究与归因只将标准证据映射到自己的领域模型，保留各自的查询规划、预算、持久化和报告逻辑。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、`HttpURLConnection`、JUnit 5、Mockito、React、TypeScript、Vitest。

---

## 文件结构

- `finscope-domain/search`：供应商无关的搜索请求、结果和 Provider 诊断 DTO。
- `finscope-rpc/search`：`WebSearchProvider` 合同、Tavily 与 AnySearch HTTP 适配器。
- `finscope-service/search/evidence`：标准证据模型、URL 规范化、结果融合和多源网关。
- `finscope-service/research/agent/tool`：研究工具到标准证据的映射与运行级持久化。
- `finscope-service/attribution`：归因任务到标准证据的映射，归因业务字段不下沉。
- `finscope-web/config`：Provider 与网关配置、Bean 和有界执行器。
- `frontend/src/features/research`：中性工具名称和 Provider 展示。

### Task 1: 建立搜索 Provider 合同并迁移 Tavily

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/search/WebSearchRequest.java`
- Modify: `backend/finscope-domain/src/main/java/com/finscope/domain/search/SearchResult.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/search/WebSearchProvider.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/search/TavilyWebSearchClient.java`
- Delete after consumer migration: `backend/finscope-rpc/src/main/java/com/finscope/rpc/search/WebSearchClient.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/search/TavilyWebSearchClientTest.java`

- [ ] **Step 1: 写失败的 Tavily 契约测试**

用本地 `HttpServer` 返回固定 JSON，断言 Provider 编码、Provider 内排名和原始分数：

```java
assertEquals("TAVILY", provider.providerCode());
List<SearchResult> results = provider.search(new WebSearchRequest("英伟达 财报", 3, "cn", "zh"));
assertEquals("TAVILY", results.get(0).getProviderCode());
assertEquals(1, results.get(0).getProviderRank());
assertEquals(0.91D, results.get(0).getScore(), 0.001D);
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=TavilyWebSearchClientTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，`WebSearchRequest` 或 `WebSearchProvider` 尚不存在。

- [ ] **Step 3: 实现最小 Provider 合同**

```java
public interface WebSearchProvider {
    String providerCode();
    boolean isConfigured();
    List<SearchResult> search(WebSearchRequest request) throws Exception;
}
```

`WebSearchRequest` 校验空查询并把 `maxResults` 限制到 1..20。`SearchResult` 增加 `providerCode` 和 `providerRank`。Tavily 客户端实现新合同，端点可由包级测试构造器注入，生产构造器仍使用官方端点。

- [ ] **Step 4: 运行契约测试与 RPC 模块测试**

Run: `cd backend && mvn -pl finscope-rpc -am test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-domain backend/finscope-rpc
git commit -m "refactor: 抽取统一搜索供应商合同"
git push
```

### Task 2: 接入 AnySearch 通用搜索

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/search/AnySearchWebSearchProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/search/WebSearchProviderException.java`
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/search/AnySearchWebSearchProviderTest.java`

- [ ] **Step 1: 写成功解析、空分数和错误脱敏测试**

```java
List<SearchResult> results = provider.search(new WebSearchRequest("NVIDIA latest news", 5, "intl", "en"));
assertEquals("ANYSEARCH", results.get(0).getProviderCode());
assertEquals(1, results.get(0).getProviderRank());
assertNull(results.get(0).getScore());
assertFalse(thrown.getMessage().contains("secret-key"));
assertEquals(401, thrown.getStatusCode());
```

同时捕获请求体，断言 `tag=general.general`、`format=json`、`zone`、`language` 和 `max_results`。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-rpc -am -Dtest=AnySearchWebSearchProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，AnySearch Provider 尚不存在。

- [ ] **Step 3: 实现 AnySearch Provider**

使用 Bearer Authorization，限制响应字节数，非 2xx 只抛出供应商编码、HTTP 状态和可重试标志：

```java
throw new WebSearchProviderException("ANYSEARCH", status,
        status == 429 || status >= 500,
        "ANYSEARCH request failed with HTTP " + status);
```

成功响应从根节点 `data` 结果数组读取 `title`、`url`、`snippet`、`content`，内容优先使用 `content`，其次 `snippet`；不生成虚假分数和发布时间。

- [ ] **Step 4: 运行 RPC 测试**

Run: `cd backend && mvn -pl finscope-rpc -am test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-rpc
git commit -m "feat: 接入AnySearch通用搜索"
git push
```

### Task 3: 实现标准证据融合与多源容错

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchDepth.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchEvidenceRequest.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchEvidence.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchProviderDiagnostic.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchEvidenceBatch.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchUrlCanonicalizer.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchResultFusionService.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchEvidenceGateway.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/search/evidence/SearchResultFusionServiceTest.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/search/evidence/SearchEvidenceGatewayTest.java`

- [ ] **Step 1: 写 URL 与融合失败测试**

覆盖移除 fragment/`utm_*`、默认端口、同规范 URL 合并 Provider、空分数保留、RRF 稳定顺序和单域名最多两条：

```java
assertEquals("https://example.com/news?id=7", canonicalizer.canonicalize(
        "https://EXAMPLE.com:443/news?utm_source=x&id=7#part"));
assertEquals(Arrays.asList("ANYSEARCH", "TAVILY"), evidence.getProviders());
assertEquals(2, batch.getEvidence().stream()
        .filter(item -> "example.com".equals(item.getSourceDomain())).count());
```

- [ ] **Step 2: 运行融合测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SearchResultFusionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，融合类型尚不存在。

- [ ] **Step 3: 实现规范化和倒数排名融合**

每个结果贡献 `1.0 / (60 + providerRank)`；合并时 Provider 使用排序后的去重集合，来源层级顺序为 T1、T2、T3，最终比较融合分数、层级、发布时间和规范 URL。无效或非 HTTP(S) URL 计为丢弃，不形成证据。

- [ ] **Step 4: 写网关 Provider 选择与部分失败测试**

```java
SearchEvidenceBatch quick = gateway.search(request(SearchDepth.QUICK));
verify(tavily).search(any(WebSearchRequest.class));
verifyNoInteractions(anySearch);

SearchEvidenceBatch deep = gateway.search(request(SearchDepth.DEEP));
assertEquals(1, deep.getEvidence().size());
assertTrue(deep.getDiagnostics().stream().anyMatch(SearchProviderDiagnostic::isFailed));
```

- [ ] **Step 5: 实现有界并发网关并运行测试**

网关接收 `List<WebSearchProvider>` 和共享 `ExecutorService`，按编码选择 Provider；每个 Future 受请求截止时间约束，失败转成诊断。所有目标 Provider 不可用时返回空批次和诊断，而不是暴露原始异常正文。

Run: `cd backend && mvn -pl finscope-service -am -Dtest=SearchResultFusionServiceTest,SearchEvidenceGatewayTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交并推送**

```bash
git add backend/finscope-service
git commit -m "feat: 实现多源搜索证据融合"
git push
```

### Task 4: 通用化正文获取并接入研究智能体

**Files:**
- Move: `backend/finscope-service/src/main/java/com/finscope/service/research/evidence/*` to `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/content/*`
- Move: `backend/finscope-service/src/test/java/com/finscope/service/research/evidence/ResearchEvidenceAcquisitionServiceTest.java` to `backend/finscope-service/src/test/java/com/finscope/service/search/evidence/content/SearchEvidenceContentServiceTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/search/evidence/SearchEvidenceGateway.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/PublicNewsSearchTool.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/tool/ResearchMaterialSearchTool.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/mission/ResearchToolRegistry.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/tool/PublicNewsSearchToolTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/tool/ResearchMaterialSearchToolTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/ResearchAgentContextBuilderTest.java`

- [ ] **Step 1: 先改研究工具测试为标准网关行为**

构造含 `TAVILY` 与 `ANYSEARCH` 的 `SearchEvidenceBatch`，断言同一证据只写一次、组合 Provider 被持久化、空原始分数仍进入证据、摘要使用“多源公开资料搜索”，网关全失败时错误类型为 `WEB_SEARCH_FAILED`。

- [ ] **Step 2: 运行研究工具测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=PublicNewsSearchToolTest,ResearchMaterialSearchToolTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，工具仍依赖 `WebSearchClient`。

- [ ] **Step 3: 重命名正文组件并让网关按预算获取正文**

采用 `SearchEvidenceContentService`、`SearchEvidenceContentResult`、`SearchEvidenceChunker` 和 `SearchEvidenceRanker`。正文失败保留摘要并设置 `SEARCH_SNIPPET`；只有进入融合候选且未超过 `fullTextBudget` 的证据尝试读取正文。

- [ ] **Step 4: 将研究工具改为标准证据消费者**

每个研究分支调用一次网关，模式映射为 QUICK/DEEP；`ResearchSearchEvidence.provider` 使用 `String.join("+", providers)`。删除 `MIN_RELEVANCE_SCORE` 与 Tavily 专属错误、状态哈希和文案，同时保留运行级 URL 去重、官方查询降级和研究编排。

- [ ] **Step 5: 运行相关 Service 测试**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=PublicNewsSearchToolTest,ResearchMaterialSearchToolTest,SearchEvidenceContentServiceTest,ResearchAgentContextBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 6: 提交并推送**

```bash
git add backend/finscope-service
git commit -m "refactor: 研究智能体复用搜索证据底座"
git push
```

### Task 5: 接入归因智能体

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionAgent.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/attribution/AttributionEvidenceGate.java`
- Create: `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentSearchEvidenceTest.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/attribution/AttributionAgentNarrativeTest.java`

- [ ] **Step 1: 写归因多源、正文和预算测试**

网关返回两条标准证据，其中一条由两个 Provider 共同命中。断言归因使用正文优先于摘要、每个计划问题只调用网关一次、公开证据仍保留归因的 track/stance/directness，Provider 部分失败不终止本地证据召回。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -pl finscope-service -am -Dtest=AttributionAgentSearchEvidenceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，归因仍直接依赖 `WebSearchClient`。

- [ ] **Step 3: 用 `SearchEvidenceGateway` 替换直接搜索**

归因请求使用 `SearchDepth.DEEP`，`maxResultsPerProvider=4`，最终结果上限沿用现有每问题证据预算，截止时间使用计划剩余时间。将标准证据映射为 `AttributionEvidence`，融合分数换算到现有相关性尺度，但不覆盖归因门控对立场与直接性的判断。

- [ ] **Step 4: 删除重复基础处理并运行归因测试**

删除 Agent 内只服务公开搜索的 URL 规范化和原始分数空值兜底；保留 `AttributionEvidenceGate` 的业务级去重、直接性排序和置信度封顶。

Run: `cd backend && mvn -pl finscope-service -am -Dtest='AttributionAgent*Test' -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

- [ ] **Step 5: 提交并推送**

```bash
git add backend/finscope-service
git commit -m "refactor: 归因智能体复用搜索证据底座"
git push
```

### Task 6: 配置装配与前端可见性

**Files:**
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`
- Modify: `backend/finscope-web/src/test/java/com/finscope/web/QuantPlatformContextTest.java`
- Modify: `frontend/src/features/research/ResearchAgentDecisionFlow.tsx`
- Modify: `frontend/src/features/research/ResearchAgentDecisionFlow.test.tsx`
- Modify: `docs/模型服务接入与配置说明.md`

- [ ] **Step 1: 写配置绑定与 Spring 装配失败测试**

断言 Tavily 和 AnySearch 可独立启用，网关执行器线程数有上限，AnySearch 未配置时应用仍启动。测试只使用虚假 Key，不读取或输出 `application.yml` 中的真实值。

- [ ] **Step 2: 运行 Web 测试并确认失败**

Run: `cd backend && mvn -pl finscope-web -am -Dtest=QuantPlatformContextTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，新配置结构和 Provider Bean 尚未装配。

- [ ] **Step 3: 实现配置和 Bean**

`FinScopeProperties.SearchProperties` 下增加 `tavily`、`anySearch` 和 `fusion` 子配置。`AppConfig` 创建两个 `WebSearchProvider` Bean 与名为 `searchEvidenceExecutor` 的有界执行器。`application.yml` 保留现有 Tavily Key 原值，并新增 AnySearch 固定配置；若没有可用 Key，先显式 `enabled: false`，不得猜测或复制浏览器凭据。

- [ ] **Step 4: 更新前端中性文案与测试**

```ts
public_news_search: '多源公开资料搜索'
```

证据或轨迹已有 Provider 字段时显示 `TAVILY`、`ANYSEARCH` 标签；没有字段的旧响应保持兼容。

Run: `cd frontend && npm test -- --run ResearchAgentDecisionFlow.test.tsx && npm run build`

Expected: PASS。

- [ ] **Step 5: 运行 Web 装配测试并提交推送**

Run: `cd backend && mvn -pl finscope-web -am test`

Expected: PASS。

```bash
git add backend/finscope-web frontend docs
git commit -m "feat: 配置多源搜索并展示供应商信息"
git push
```

### Task 7: 全量回归与真实质量评测

**Files:**
- Create: `docs/evaluations/2026-07-30-multi-search-evaluation.md`

- [ ] **Step 1: 执行静态和后端全仓验证**

Run: `git diff main...HEAD --check`

Expected: 无输出。

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 2: 执行前端全量验证**

Run: `cd frontend && npm test -- --run && npm run build`

Expected: 测试全部通过，Vite 生产构建成功。

- [ ] **Step 3: 执行二十个财经查询的只读对比**

在不打印 Key 的前提下，分别记录 Tavily 单源和 DEEP 多源的独立 URL、独立域名、T1 来源占比、重复率、失败率、P50/P95 延迟和 AnySearch 增量。评测不得把搜索内容写入文章库或用户正式研究数据。

- [ ] **Step 4: 记录结果和已知限制**

评测文档包含查询集合、指标定义、聚合结果和是否满足“增加证据广度且质量可控”的结论；不得写入请求头、API Key 或供应商完整错误响应。

- [ ] **Step 5: 提交并推送最终验证批次**

```bash
git add docs/evaluations/2026-07-30-multi-search-evaluation.md
git commit -m "test: 评测多源搜索证据质量"
git push
```

确认 `git status --short` 为空，并确认评测提交已经推送到当前分支。
