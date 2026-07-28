# Unified Acquisition Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立统一、可审计、可重放的采集运行时，并逐步让网页、RSS、X 与行情 Provider 获得一致的可靠性能力。

**Architecture:** `AcquisitionRuntime` 隐藏传输、重试、解码和响应限制，`SourceAdapter` 只保留内容语义转换；`RawSnapshotStore` 在 SQLite 与 `data/raw/` 之间形成可重放 Seam。行情仍由 `MarketDataGateway` 负责 Provider 路由，运行时只执行一次有界传输。

**Tech Stack:** Java 8、Spring Boot 2.7、JDK HTTP、Jsoup、Rome、SQLite、JUnit 5、Python 3、httpx、pytest。

---

### Task 1: Acquisition contracts and text decoder

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionRequest.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionResponse.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionErrorType.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionException.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/ResponseTextDecoder.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/acquisition/ResponseTextDecoderTest.java`

- [ ] Write decoder tests for UTF-8 BOM, response-header GBK, HTML meta GB2312 normalization and XML encoding.
- [ ] Run `cd backend && mvn -pl finscope-rpc -Dtest=ResponseTextDecoderTest test` and verify failure because the contracts do not exist.
- [ ] Implement immutable request/response contracts, stable error types and the decoder.
- [ ] Run the same test and verify it passes.
- [ ] Commit with `feat: 建立统一采集合同与字符集解码`.

### Task 2: Bounded HTTP acquisition runtime

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/AcquisitionRuntime.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/JdkAcquisitionRuntime.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/acquisition/JdkAcquisitionRuntimeTest.java`

- [ ] Write local-server tests proving 429/503 retry once, 404 no retry, response-size rejection, timeout classification and response hashing.
- [ ] Run the targeted test and verify missing runtime failures.
- [ ] Implement one bounded GET path with a total deadline, byte-counting read, explicit retry classification and safe headers.
- [ ] Run the targeted test and all `finscope-rpc` tests.
- [ ] Commit with `feat: 实现有界可靠 HTTP 采集运行时`.

### Task 3: Migrate Web and RSS adapters

**Files:**
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/WebSourceAdapter.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/WebListSourceAdapter.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/RssSourceAdapter.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/WebSourceAdapterTest.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/WebListSourceAdapterTest.java`
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/RssSourceAdapterTest.java`

- [ ] Add tests injecting a recording runtime and proving every list/detail/RSS request passes through it.
- [ ] Run the three Adapter tests and verify they fail against direct connections.
- [ ] Replace direct `Jsoup.connect`/`HttpURLConnection` calls with `AcquisitionRuntime` and parse returned text.
- [ ] Run Adapter tests and the full RPC module tests.
- [ ] Commit with `refactor: 统一网页与 RSS 采集出口`.

### Task 4: Raw snapshot persistence and replay

**Files:**
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/fetch/RawSnapshot.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/fetch/RawSnapshotRepository.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/fetch/RawSnapshotStore.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/fetch/FetchReplayService.java`
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/fetch/FetchService.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/fetch/RawSnapshotStoreTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/fetch/FetchReplayServiceTest.java`

- [ ] Write temporary-directory tests for metadata persistence, content-addressed files, secret redaction and offline replay.
- [ ] Verify the tests fail because snapshot storage is absent.
- [ ] Add the additive schema, repository, file store and replay application flow.
- [ ] Run service tests and verify existing article ingestion remains unchanged.
- [ ] Commit with `feat: 保存并重放原始采集快照`.

### Task 5: Complex web escalation

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/WebAcquisitionStrategy.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/source/EmbeddedDataExtractor.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/BrowserFetcher.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/acquisition/DisabledBrowserFetcher.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/WebAcquisitionStrategyTest.java`
- Test: `backend/finscope-rpc/src/test/java/com/finscope/rpc/source/EmbeddedDataExtractorTest.java`

- [ ] Write fixture tests for JSON-LD, `__NEXT_DATA__`, JavaScript shell detection and browser-disabled classification.
- [ ] Verify the targeted tests fail.
- [ ] Implement the static-to-embedded-to-browser decision state machine and disabled browser Adapter.
- [ ] Add the concrete browser Adapter behind `finscope.acquisition.browser.enabled`; keep browser capacity isolated.
- [ ] Run RPC tests and commit with `feat: 增强动态网页采集阶梯`.

### Task 6: Market-data transport ownership

**Files:**
- Modify: Java direct HTTP clients under `backend/finscope-rpc/src/main/java/com/finscope/rpc/quote/` and `marketintel/`.
- Modify: `market-data-service/src/finscope_market_data/providers/http.py`
- Modify: `market-data-service/src/finscope_market_data/router.py`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/marketdata/MarketDataGatewayReliabilityTest.java`
- Test: `market-data-service/tests/test_router.py`
- Test: `market-data-service/tests/test_providers.py`

- [ ] Add tests proving Java owns retries for Provider calls and Python provider mode performs one attempt.
- [ ] Add tests proving Python reuses one `AsyncClient` and closes it during application shutdown.
- [ ] Verify targeted tests fail against the current double-policy and per-request client behavior.
- [ ] Migrate transports without changing Provider parsing contracts.
- [ ] Run backend and Python suites.
- [ ] Commit with `refactor: 收敛行情采集可靠性职责`.

### Task 7: Final verification and publication

**Files:**
- Modify: `docs/架构说明.md`
- Modify: `docs/产品需求-统一采集与网页抓取能力.md`
- Modify: `docs/技术方案-统一采集与网页抓取能力.md`

- [ ] Document the final Module Seam, configuration and replay workflow.
- [ ] Run `cd backend && mvn test`.
- [ ] Run `cd market-data-service && uv run pytest -q`.
- [ ] Search production Source Adapter code and verify no direct `Jsoup.connect` or `HttpURLConnection` remains.
- [ ] Inspect `git diff`, commit documentation, push `codex/unified-acquisition-runtime`, and open a draft PR.
