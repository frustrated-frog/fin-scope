# FinScope Project Foundation Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全量统一 FinScope 普通 JSON API 的响应、中文错误码、异常处理、请求日志和前端解包，并清除配置文件中的硬编码密钥。

**Architecture:** `finscope-common` 提供无 Spring 依赖的响应与异常契约；`finscope-web` 使用 `ResponseBodyAdvice` 和 `RestControllerAdvice` 统一 Web 出入口；前端 `api<T>` 严格解析统一信封并只向业务代码返回 `data`。SSE、204、流式和二进制响应保持原协议。

**Tech Stack:** Java 8、Spring Boot 2.7.18、Spring MVC、SLF4J/MDC、JUnit 5、MockMvc、React 18、TypeScript 5.6、Vitest

---

## File Structure

### Backend common contract

- Create `backend/finscope-common/src/main/java/com/finscope/common/api/ApiResponse.java`: 统一成功/失败响应实体。
- Modify `backend/finscope-common/src/main/java/com/finscope/common/exception/ErrorCode.java`: 中文分类错误码和 HTTP 状态数值。
- Modify `backend/finscope-common/src/main/java/com/finscope/common/exception/BusinessException.java`: 用户消息、错误码和 cause。
- Create `backend/finscope-common/src/main/java/com/finscope/common/exception/ResourceNotFoundException.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/exception/BusinessConflictException.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/exception/ExternalServiceException.java`.
- Create `backend/finscope-common/src/main/java/com/finscope/common/exception/InfrastructureException.java`.
- Create `backend/finscope-common/src/test/java/com/finscope/common/api/ApiResponseTest.java`.
- Create `backend/finscope-common/src/test/java/com/finscope/common/exception/ErrorCodeTest.java`.

### Backend Web boundary

- Create `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiResponseBodyAdvice.java`: 普通 JSON 成功响应自动包装。
- Replace `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiErrorResponse.java`: 删除双轨错误实体。
- Modify `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiExceptionHandler.java`: 全面异常翻译。
- Create `backend/finscope-web/src/test/java/com/finscope/web/handler/ApiResponseBodyAdviceTest.java`.
- Create `backend/finscope-web/src/test/java/com/finscope/web/handler/ApiExceptionHandlerTest.java`.

### Logging and configuration

- Modify `backend/finscope-web/src/main/java/com/finscope/web/config/RequestLoggingFilter.java`: 单条结构化完成日志、慢请求分级和安全 traceId。
- Create `backend/finscope-web/src/main/java/com/finscope/web/config/LogSanitizer.java`: 去除换行并限制字段长度。
- Create `backend/finscope-web/src/test/java/com/finscope/web/config/RequestLoggingFilterTest.java`.
- Modify `backend/finscope-web/src/main/resources/application.yml`: 密钥改环境变量，补充慢请求阈值。
- Modify `backend/finscope-web/src/main/java/com/finscope/web/config/FinScopeProperties.java`: 如需要，增加日志阈值配置。

### Existing exception call sites

- Modify not-found and infrastructure boundaries under:
  - `backend/finscope-web/src/main/java/com/finscope/web/controller/`
  - `backend/finscope-service/src/main/java/com/finscope/service/`
  - `backend/finscope-rpc/src/main/java/com/finscope/rpc/`
- Priority replacements:
  - `ArticleDeletionService`
  - `TopicService`
  - `ResearchService`
  - `ResearchRunPlanService`
  - `ResearchRunPlanRepository`
  - `MarketIntelController`
  - `QuantDatasetService`
  - `UrlIngestService`

### Frontend contract

- Modify `frontend/src/shared/api/client.ts`: 严格统一响应解包。
- Modify `frontend/src/shared/api/client.test.ts`: 成功、失败、204 和协议错误测试。
- Update frontend fetch fixtures that exercise the real client, especially `frontend/src/App.test.tsx`.

### Web API tests

- Update all `backend/finscope-web/src/test/java/**/*.java` successful JSON assertions from `$.field` to `$.data.field`.
- Preserve error assertions at root: `$.success`, `$.code`, `$.message`, `$.traceId`, `$.timestamp`.
- Update response-body parsing in `MarketIntelApiIntegrationTest` and other tests to read `data.id`.

## Task 1: Common response and error contract

**Files:**
- Create: `backend/finscope-common/src/test/java/com/finscope/common/api/ApiResponseTest.java`
- Create: `backend/finscope-common/src/test/java/com/finscope/common/exception/ErrorCodeTest.java`
- Create: `backend/finscope-common/src/main/java/com/finscope/common/api/ApiResponse.java`
- Modify: `backend/finscope-common/src/main/java/com/finscope/common/exception/ErrorCode.java`
- Modify: `backend/finscope-common/src/main/java/com/finscope/common/exception/BusinessException.java`
- Create: four semantic exception classes listed above

- [ ] **Step 1: Write failing contract tests**

```java
@Test
void buildsChineseSuccessEnvelope() {
    ApiResponse<String> response = ApiResponse.success("ok", "trace-1");
    assertTrue(response.isSuccess());
    assertEquals("FS-0000", response.getCode());
    assertEquals("成功", response.getMessage());
    assertEquals("ok", response.getData());
    assertEquals("trace-1", response.getTraceId());
    assertNotNull(response.getTimestamp());
}

@Test
void allDefaultMessagesAreChineseAndCodesAreUnique() {
    Set<String> codes = new HashSet<String>();
    for (ErrorCode value : ErrorCode.values()) {
        assertTrue(codes.add(value.getCode()));
        assertFalse(value.getDefaultMessage().matches("^[\\x00-\\x7F]+$"));
    }
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl finscope-common test
```

Expected: compilation fails because `ApiResponse` and new error codes do not exist.

- [ ] **Step 3: Implement the response and error model**

`ApiResponse<T>` must expose:

```java
private boolean success;
private String code;
private String message;
private T data;
private String traceId;
private Instant timestamp;
```

Factories:

```java
public static <T> ApiResponse<T> success(T data, String traceId)
public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, String traceId)
```

`ErrorCode` must contain at least:

```java
SUCCESS("FS-0000", "成功", 200),
REQUEST_PARAMETER_MISSING("FS-1001", "缺少必要的请求参数", 400),
REQUEST_PARAMETER_INVALID("FS-1002", "请求参数不合法", 400),
REQUEST_BODY_INVALID("FS-1003", "请求体格式错误", 400),
REQUEST_METHOD_NOT_SUPPORTED("FS-1004", "请求方法不支持", 405),
MEDIA_TYPE_NOT_SUPPORTED("FS-1005", "请求内容类型不支持", 415),
UNAUTHORIZED("FS-1101", "请先登录", 401),
FORBIDDEN("FS-1102", "无权执行该操作", 403),
RATE_LIMITED("FS-1103", "请求过于频繁，请稍后重试", 429),
RESOURCE_NOT_FOUND("FS-2001", "请求的资源不存在", 404),
BUSINESS_CONFLICT("FS-2002", "当前业务状态不允许该操作", 409),
DUPLICATE_OPERATION("FS-2003", "请勿重复操作", 409),
DATA_VERSION_CONFLICT("FS-2004", "数据已被更新，请刷新后重试", 409),
EXTERNAL_SERVICE_UNAVAILABLE("FS-3001", "外部服务暂不可用，请稍后重试", 502),
EXTERNAL_SERVICE_TIMEOUT("FS-3002", "外部服务响应超时，请稍后重试", 504),
EXTERNAL_RESPONSE_INVALID("FS-3003", "外部服务返回数据异常", 502),
MARKET_DATA_UNAVAILABLE("FS-3004", "市场数据暂不可用，请稍后重试", 502),
LLM_SERVICE_ERROR("FS-3005", "模型服务暂不可用，请稍后重试", 502),
DATABASE_ERROR("FS-4001", "数据库操作失败，请稍后重试", 500),
FILE_OPERATION_ERROR("FS-4002", "文件操作失败，请稍后重试", 500),
ASYNC_TASK_ERROR("FS-4003", "异步任务执行失败，请稍后重试", 500),
INTERNAL_ERROR("FS-5000", "系统繁忙，请稍后重试", 500);
```

- [ ] **Step 4: Verify GREEN**

Run `mvn -pl finscope-common test`.

Expected: all common tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-common
git commit -m "feat: 建立统一响应与中文错误码"
```

## Task 2: Automatic success response wrapping

**Files:**
- Create: `backend/finscope-web/src/test/java/com/finscope/web/handler/ApiResponseBodyAdviceTest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiResponseBodyAdvice.java`

- [ ] **Step 1: Write failing MockMvc tests**

Cover:

```java
mockMvc.perform(get("/test/value").header("X-Request-Id", "trace-success"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.code").value("FS-0000"))
    .andExpect(jsonPath("$.message").value("成功"))
    .andExpect(jsonPath("$.data.name").value("value"))
    .andExpect(jsonPath("$.traceId").value("trace-success"));
```

Also assert:

- `ResponseEntity.created(location).body(value)` keeps 201 and `Location`.
- Existing `ApiResponse` is not nested.
- `SseEmitter` support returns false.
- `ResponseEntity<Void>` with 204 remains empty.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl finscope-web -am -Dtest=ApiResponseBodyAdviceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test fails because responses are still raw.

- [ ] **Step 3: Implement `ApiResponseBodyAdvice`**

Use `@RestControllerAdvice(basePackages = "com.finscope.web.controller")` and `ResponseBodyAdvice<Object>`.

`supports` must return false for:

```java
ApiResponse.class.isAssignableFrom(returnType.getParameterType())
SseEmitter.class.isAssignableFrom(returnType.getParameterType())
StreamingResponseBody.class.isAssignableFrom(returnType.getParameterType())
Resource.class.isAssignableFrom(returnType.getParameterType())
byte[].class.equals(returnType.getParameterType())
```

`beforeBodyWrite` must skip non-JSON media types and return:

```java
ApiResponse.success(body, TraceId.current())
```

- [ ] **Step 4: Verify GREEN**

Run the focused test and then `mvn -pl finscope-web -am -DskipTests package`.

- [ ] **Step 5: Commit**

```bash
git add backend/finscope-web/src/main/java/com/finscope/web/handler/ApiResponseBodyAdvice.java \
        backend/finscope-web/src/test/java/com/finscope/web/handler/ApiResponseBodyAdviceTest.java
git commit -m "feat: 统一包装 Web 成功响应"
```

## Task 3: Comprehensive exception translation

**Files:**
- Create: `backend/finscope-web/src/test/java/com/finscope/web/handler/ApiExceptionHandlerTest.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiExceptionHandler.java`
- Delete: `backend/finscope-web/src/main/java/com/finscope/web/handler/ApiErrorResponse.java`
- Modify: `backend/finscope-web/pom.xml`

- [ ] **Step 1: Write failing exception mapping tests**

Create test endpoints that throw each exception and assert:

```java
.andExpect(status().isNotFound())
.andExpect(jsonPath("$.success").value(false))
.andExpect(jsonPath("$.code").value("FS-2001"))
.andExpect(jsonPath("$.message").value("主题不存在"))
.andExpect(jsonPath("$.data").doesNotExist())
.andExpect(jsonPath("$.traceId").isNotEmpty())
.andExpect(jsonPath("$.timestamp").exists());
```

Cover missing parameter, type mismatch, invalid JSON, method not allowed, unsupported media type, business conflict, data access failure and unknown exception.

- [ ] **Step 2: Verify RED**

Run the focused handler test. Expected: missing handlers and legacy fields fail.

- [ ] **Step 3: Add validation dependency**

Add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

- [ ] **Step 4: Implement the handler**

Every handler returns:

```java
ResponseEntity.status(errorCode.getHttpStatus())
    .body(ApiResponse.failure(errorCode, safeUserMessage, traceId));
```

Remove `isNotFound(message)`. Unknown exceptions return only `ErrorCode.INTERNAL_ERROR.getDefaultMessage()` to users and log the full exception.

- [ ] **Step 5: Verify GREEN**

Run focused tests and all `finscope-web` handler tests.

- [ ] **Step 6: Commit**

```bash
git add backend/finscope-web/pom.xml backend/finscope-web/src/main/java/com/finscope/web/handler \
        backend/finscope-web/src/test/java/com/finscope/web/handler
git commit -m "feat: 完善 Web 全局异常处理"
```

## Task 4: Request logging and secret-safe configuration

**Files:**
- Create: `backend/finscope-web/src/test/java/com/finscope/web/config/RequestLoggingFilterTest.java`
- Create: `backend/finscope-web/src/main/java/com/finscope/web/config/LogSanitizer.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/RequestLoggingFilter.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml`

- [ ] **Step 1: Write failing filter tests**

Assert:

- Incoming safe `X-Request-Id` is reused.
- Blank/oversized/newline-containing request IDs are replaced by UUID.
- Response always includes `X-Request-Id`.
- MDC is empty after the filter finishes.
- `LogSanitizer.clean("a\\nb", 20)` returns `"a b"`.
- Overlong values are truncated with `...`.

- [ ] **Step 2: Verify RED**

Run `mvn -pl finscope-web -am -Dtest=RequestLoggingFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`.

- [ ] **Step 3: Implement the filter**

Emit one completion log with:

```text
请求完成 method={} path={} query={} status={} durationMs={} remote={}
```

Use warn for `durationMs >= 2000` or 4xx, info otherwise. Do not log bodies or sensitive headers.

- [ ] **Step 4: Externalize configuration secrets**

Use:

```yaml
finscope:
  llm:
    enabled: ${FINSCOPE_LLM_ENABLED:false}
    base-url: ${FINSCOPE_LLM_BASE_URL:https://api.openai.com/v1}
    api-key: ${FINSCOPE_LLM_API_KEY:}
    model: ${FINSCOPE_LLM_MODEL:}
  search:
    enabled: ${FINSCOPE_SEARCH_ENABLED:false}
    provider: ${FINSCOPE_SEARCH_PROVIDER:tavily}
    api-key: ${FINSCOPE_SEARCH_API_KEY:}
```

- [ ] **Step 5: Verify GREEN**

Run the focused test and `rg -n 'api-key: \"[^$]' backend/finscope-web/src/main/resources`.

Expected: test passes and search returns no hard-coded keys.

- [ ] **Step 6: Commit**

```bash
git add backend/finscope-web/src/main/java/com/finscope/web/config \
        backend/finscope-web/src/main/resources/application.yml \
        backend/finscope-web/src/test/java/com/finscope/web/config
git commit -m "feat: 规范请求日志并移除硬编码密钥"
```

## Task 5: Migrate exception call sites

**Files:**
- Modify targeted Service, DAO, RPC and Controller files listed in File Structure.
- Modify their existing tests where applicable.

- [ ] **Step 1: Add failing tests for typed not-found and conflict behavior**

Examples:

```java
assertThrows(ResourceNotFoundException.class, () -> topicService.get(999L));
assertEquals(ErrorCode.DATA_VERSION_CONFLICT,
    assertThrows(BusinessException.class, staleUpdate).getErrorCode());
```

- [ ] **Step 2: Verify RED**

Run focused module tests and confirm current generic exceptions fail expectations.

- [ ] **Step 3: Replace brittle exceptions**

Required replacements:

```java
throw new ResourceNotFoundException("主题不存在");
throw new BusinessConflictException(ErrorCode.DATA_VERSION_CONFLICT, "记录已被更新，请刷新后再试");
throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "文章抓取服务暂不可用", ex);
```

Convert current English user-facing messages in migrated paths to Chinese. Preserve technical provider codes in domain data where they are part of persisted machine-readable contracts.

- [ ] **Step 4: Verify no message-based not-found routing remains**

Run:

```bash
rg -n 'isNotFound|toLowerCase\\(\\).*not found' backend
```

Expected: no matches in Web exception handling.

- [ ] **Step 5: Run module tests**

Run:

```bash
cd backend
mvn -pl finscope-service,finscope-web -am test
```

Compare failures with the three recorded baseline failures.

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "refactor: 统一业务异常抛出语义"
```

## Task 6: Frontend unified response client

**Files:**
- Modify: `frontend/src/shared/api/client.test.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: real-fetch fixtures in `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing client tests**

Success:

```ts
new Response(JSON.stringify({
  success: true,
  code: 'FS-0000',
  message: '成功',
  data: { id: 7 },
  traceId: 'trace-7',
  timestamp: '2026-07-16T00:00:00Z'
}), { status: 200 })
```

Assert `api<{id:number}>()` resolves to `{ id: 7 }`.

Failure:

```ts
{
  success: false,
  code: 'FS-2004',
  message: '数据已被更新，请刷新后重试',
  data: null,
  traceId: 'trace-conflict',
  timestamp: '...'
}
```

Assert `ApiError` contains status, code and traceId.

Also assert raw JSON on a 200 response throws `ApiError` with code `API_PROTOCOL_ERROR`, while 204 resolves `undefined`.

- [ ] **Step 2: Verify RED**

Run `npm test -- src/shared/api/client.test.ts`.

- [ ] **Step 3: Implement strict envelope parsing**

Define:

```ts
export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
  traceId: string;
  timestamp: string;
}
```

Extend `ApiError` with `traceId?: string`. Parse a body once, validate envelope fields, throw on HTTP failure or `success=false`, and return `envelope.data as T`.

- [ ] **Step 4: Update real-fetch test fixtures**

Wrap backend mock bodies used through the actual `api` client with a shared test helper:

```ts
function success<T>(data: T) {
  return {
    success: true,
    code: 'FS-0000',
    message: '成功',
    data,
    traceId: 'test-trace',
    timestamp: '2026-07-16T00:00:00Z'
  };
}
```

- [ ] **Step 5: Verify GREEN**

Run focused client tests, then `npm test`.

Expected: no new failures beyond the recorded `App.test.tsx` baseline unless that fixture is directly corrected during wrapping.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/shared/api frontend/src/App.test.tsx
git commit -m "feat: 前端统一解析 API 响应"
```

## Task 7: Migrate all Web tests to the envelope

**Files:**
- Modify all Java tests under `backend/finscope-web/src/test/java`.

- [ ] **Step 1: Add a contract assertion to an integration test**

For one representative endpoint, assert:

```java
.andExpect(jsonPath("$.success").value(true))
.andExpect(jsonPath("$.code").value("FS-0000"))
.andExpect(jsonPath("$.message").value("成功"))
.andExpect(jsonPath("$.data").exists())
.andExpect(jsonPath("$.traceId").isNotEmpty())
.andExpect(jsonPath("$.timestamp").exists());
```

- [ ] **Step 2: Run Web tests to capture RED**

Run `mvn -pl finscope-web -am test`.

Expected: legacy success JSON paths fail because data is nested.

- [ ] **Step 3: Update success assertions**

For every successful JSON response:

```text
$.id              -> $.data.id
$.items            -> $.data.items
$.length()         -> $.data.length()
$[*].field         -> $.data[*].field
```

Do not prefix root error metadata assertions.

When parsing IDs:

```java
objectMapper.readTree(body).path("data").path("id").asLong();
```

- [ ] **Step 4: Ensure advice is imported in sliced tests**

Add `ApiResponseBodyAdvice.class` to relevant `@Import` declarations where the slice does not auto-discover it.

- [ ] **Step 5: Run Web tests**

Compare results to baseline:

- `eventGovernanceMovesArticleIntoNewEvent`
- `generatedResearchArtifactsUseEventContext`

No additional failures are acceptable.

- [ ] **Step 6: Commit**

```bash
git add backend/finscope-web/src/test/java
git commit -m "test: 迁移 Web 接口统一响应断言"
```

## Task 8: Final verification and documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/架构说明.md`
- Modify: `docs/superpowers/plans/2026-07-16-project-foundation-infrastructure.md`

- [ ] **Step 1: Document the API contract and environment variables**

Add a concise response example, error example, error-code categories, `X-Request-Id`, and required environment variables.

- [ ] **Step 2: Run static checks**

```bash
git diff --check
rg -n 'Bad request|Resource not found|External service error|Internal server error' backend
rg -n 'api-key: \"[^$]' backend/finscope-web/src/main/resources
```

Expected: no legacy English default errors and no hard-coded keys.

- [ ] **Step 3: Run backend verification**

```bash
cd backend
mvn test
```

Expected: no new failures beyond the two recorded baseline integration failures.

- [ ] **Step 4: Run frontend verification**

```bash
cd frontend
npm test
npm run build
```

Expected: no new test failures beyond the recorded baseline if it remains; production build passes.

- [ ] **Step 5: Review the diff**

```bash
git status --short
git diff --stat main...HEAD
git diff --check main...HEAD
```

Confirm SSE methods, 204 responses, status codes and `Location` headers remain intact.

- [ ] **Step 6: Commit final docs**

```bash
git add README.md docs/架构说明.md docs/superpowers/plans/2026-07-16-project-foundation-infrastructure.md
git commit -m "docs: 补充统一基建使用说明"
```
