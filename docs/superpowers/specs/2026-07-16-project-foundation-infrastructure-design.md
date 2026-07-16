# FinScope 项目基建设计

## 1. 背景与目标

FinScope 已具备基础的 `BusinessException`、少量错误码、全局异常处理和请求 `traceId`，但存在以下问题：

- 成功接口直接返回业务对象，失败接口返回另一套结构，前后端契约不统一。
- 错误码只有少量通用英文项，无法准确区分参数、资源、状态冲突、外部依赖和系统故障。
- `IllegalArgumentException` 通过英文消息中是否包含 `not found` 判断 HTTP 状态，异常语义不稳定。
- Controller、Service、DAO、RPC 中混用多种运行时异常，用户提示、技术原因和日志级别没有明确边界。
- 请求日志只有简单的开始/结束记录，缺少统一字段、慢请求分级和安全约束。
- 前端只依据 HTTP 状态读取错误，不理解统一业务响应。
- 应用配置中存在硬编码 API Key，必须改为环境变量注入。

本次目标是全量迁移所有普通 JSON REST API，形成稳定、中文、可追踪、可测试的项目基础设施，同时保留既有 HTTP 状态、`Location` 响应头、SSE 和无响应体接口语义。

## 2. 方案比较与决策

### 方案 A：逐个 Controller 显式返回统一响应

每个接口将返回类型改为 `ApiResponse<T>` 或 `ResponseEntity<ApiResponse<T>>`。

优点是接口签名本身就是可见、可检查的统一契约，IDE、测试和 OpenAPI 都不会把裸实体误认为真实 HTTP 响应。通过 `ApiResponses.success(...)` 统一补充 `traceId`，可以控制重复代码；通过契约测试扫描全部 Controller，可以防止遗漏。

### 方案 B：使用 `ResponseBodyAdvice` 集中包装普通 JSON 响应

Controller 继续返回业务对象或 `ResponseEntity<T>`，Web 出口统一包装为 `ApiResponse<T>`。异常由 `RestControllerAdvice` 直接返回同一结构。

优点是迁移集中，但 Controller 方法签名仍然暴露裸实体，统一响应只在运行时隐式发生，代码审查、静态分析和接口文档无法直接识别真实契约；同时必须维护 SSE、流式响应、文件下载和 204 响应的复杂豁免规则。

### 方案 C：只改前端客户端和错误响应

保留后端成功响应原样，前端兼容两种结构。

改动最小，但不能形成真正统一的接口契约，也会长期保留双轨逻辑。

### 决策

采用方案 A。所有普通 JSON Controller 方法必须显式声明 `ApiResponse<T>`；需要保留状态码或响应头时声明 `ResponseEntity<ApiResponse<T>>`。统一使用 `ApiResponses.success(...)` 创建成功信封，并使用 `ControllerResponseContractTest` 扫描全部映射方法，阻止裸实体返回重新进入代码库。SSE 和无响应体的 204 接口保留原协议。

## 3. 统一响应契约

统一响应结构位于 `finscope-common`，不依赖 Spring：

```json
{
  "success": true,
  "code": "FS-0000",
  "message": "成功",
  "data": {},
  "traceId": "6f8f...",
  "timestamp": "2026-07-16T10:00:00Z"
}
```

规则：

- `success=true` 时 `code` 固定为 `FS-0000`，`message` 固定为“成功”。
- `success=false` 时 `data=null`，`code` 和 `message` 来自错误码或受控业务异常。
- `traceId` 与响应头 `X-Request-Id` 一致。
- `timestamp` 使用 UTC `Instant`。
- `ResponseEntity` 的 HTTP 状态和响应头保持不变。
- SSE、流式、二进制、文件下载和 204 No Content 保持原协议。

## 4. 错误码体系

错误码使用稳定的字符串编码，所有默认用户文案必须为中文。编码按类别分段：

| 类别 | 范围 | 示例 |
| --- | --- | --- |
| 成功 | `FS-0000` | 成功 |
| 请求与协议 | `FS-1001`～`FS-1099` | 请求参数不合法、请求体格式错误、请求方法不支持 |
| 认证与权限 | `FS-1101`～`FS-1199` | 未登录、无权访问、请求过于频繁 |
| 资源与业务状态 | `FS-2001`～`FS-2099` | 资源不存在、业务状态不允许、数据版本冲突、重复操作 |
| 外部依赖 | `FS-3001`～`FS-3099` | 外部服务不可用、调用超时、响应协议异常、市场数据不可用、模型服务异常 |
| 基础设施 | `FS-4001`～`FS-4099` | 数据库操作失败、文件操作失败、异步任务执行失败 |
| 未知系统错误 | `FS-5000` | 系统繁忙，请稍后重试 |

首期错误码覆盖当前项目已出现的异常场景，并预留认证、权限、限流等基础类别。枚举常量名使用英文，编码稳定，默认消息全部使用中文。

## 5. 异常模型与抛出规范

保留 `BusinessException` 作为预期业务失败的统一异常，并补充语义明确的子类：

- `ResourceNotFoundException`：资源不存在，映射 404。
- `BusinessConflictException`：状态冲突、重复操作、乐观锁冲突，映射 409。
- `ExternalServiceException`：外部数据源、搜索或模型服务失败，映射 502/504。
- `InfrastructureException`：数据库、文件和异步基础设施失败，映射 500。

规范：

- Controller 只负责协议参数和 HTTP 语义，不再依据字符串猜测异常类型。
- Service 对可预期业务失败抛出带错误码的异常。
- DAO/RPC 保留底层原因作为 `cause`，在边界处转换为基础设施或外部服务异常。
- 用户可见消息必须为中文；技术细节只进入日志，不直接返回前端。
- 领域对象内部用于保护不变量的 `IllegalArgumentException` 可以保留，但 Web 层统一映射为中文“请求参数不合法”。
- 逐步替换当前以 `not found` 英文消息驱动状态码的代码。

## 6. Web 统一入口与出口

### 6.1 成功响应

所有普通 JSON Controller 显式返回统一响应：

- 普通成功结果声明为 `ApiResponse<T>`，并通过 `ApiResponses.success(data)` 构造。
- 需要保留 HTTP 状态或响应头时声明为 `ResponseEntity<ApiResponse<T>>`。
- `ApiResponses` 自动读取 MDC 中的 `traceId`，避免各 Controller 重复处理链路字段。
- `ControllerResponseContractTest` 扫描全部 `@RestController` 映射方法，任何裸实体或裸泛型返回都会导致测试失败。
- `ControllerResponseProtocolTest` 固化统一信封、traceId、201/202、`Location`、SSE 和 204 空响应等 Web 行为。
- `SseEmitter` 和 `ResponseEntity<Void>` 的 204 响应作为明确的协议例外保留。

### 6.2 异常响应

重构 `ApiExceptionHandler`，覆盖：

- 业务异常和语义子类。
- Bean Validation 字段校验与约束校验。
- 缺少参数、参数类型错误、请求体无法解析。
- 不支持的 HTTP 方法和媒体类型。
- 数据访问异常。
- 异步请求超时。
- 未知异常兜底。

4xx 预期失败使用 `warn`，5xx 使用带堆栈的 `error`。错误响应与成功响应使用完全相同的字段结构。

## 7. 日志与链路追踪

重构 `RequestLoggingFilter`：

- 接收合法的 `X-Request-Id`，否则生成 UUID。
- 将 `traceId` 写入 MDC 和响应头，并在请求完成后清理。
- 每个请求输出一条结构化完成日志：`method`、`path`、`query`、`status`、`durationMs`、`remote`、`traceId`。
- 正常请求使用 `info`，慢请求和 4xx 使用 `warn`，5xx 的堆栈由异常处理器记录。
- 不记录请求体、响应体、Authorization、Cookie、API Key 等敏感信息。
- 为日志参数提供截断和换行清理工具，防止日志注入和超大字段。

业务代码继续使用 SLF4J 参数化日志，统一采用“中文事件 + `key=value` 字段”的格式，禁止字符串拼接和直接打印完整外部响应。

## 8. 前端迁移

`frontend/src/shared/api/client.ts` 定义与后端一致的 `ApiResponse<T>`：

- 所有普通 JSON 成功响应必须解析统一结构并返回 `data`。
- HTTP 非 2xx 或 `success=false` 都抛出 `ApiError`。
- `ApiError` 保留 `status`、`code`、`traceId`，用户界面展示中文 `message`。
- 204 返回 `undefined`。
- 非统一 JSON 响应视为接口协议错误，避免静默接受后端回归。
- SSE 继续使用原有 `EventSource`，不经过普通 API 解包。

前端业务组件无需改为访问 `response.data`，统一客户端负责解包，因此业务调用的泛型仍表示最终数据类型。

## 9. 配置安全

应用配置中的模型和搜索 API Key 改为环境变量：

- `FINSCOPE_LLM_API_KEY`
- `FINSCOPE_SEARCH_API_KEY`
- 其他可变连接信息继续使用环境变量默认值。

配置文件不再包含真实密钥，日志也不得输出配置对象或密钥值。已有密钥应在代码合并后由使用者完成轮换，因为删除当前文件内容不能清除 Git 历史。

## 10. 测试策略

采用测试优先迁移：

1. 为统一响应实体和错误码编写单元测试。
2. 为成功响应自动包装、`ResponseEntity` 状态/头保留、重复包装和豁免规则编写 MockMvc 测试。
3. 为各类异常到 HTTP 状态、中文错误码和 `traceId` 的映射编写测试。
4. 为请求日志的 traceId 透传、生成、响应头和 MDC 清理编写测试。
5. 更新全部 Web Controller/集成测试，将成功业务字段断言迁移到 `$.data.*`。
6. 更新前端 API 客户端测试，覆盖成功、业务失败、HTTP 失败、协议错误和 204。
7. 运行后端全量 Maven 测试、前端全量 Vitest 和前端生产构建。

当前 `main` 基线已有三个无关失败：

- 前端 `App.test.tsx` 的“宏观”文本单元素查询匹配到两个节点。
- 后端 `FinScopeApiIntegrationTest.eventGovernanceMovesArticleIntoNewEvent` 依赖固定自增 ID。
- 后端 `FinScopeApiIntegrationTest.generatedResearchArtifactsUseEventContext` 的概念文本断言与当前生成结果不一致。

本次验收将记录这些基线失败，并确保不新增失败；与统一响应迁移直接重叠的断言会按新契约更新。

## 11. 验收标准

- 所有普通 JSON API 返回同一响应结构。
- 所有默认错误提示均为中文，错误码分类可辨识且稳定。
- 不再通过异常消息字符串判断 404。
- 前端业务调用获得解包后的数据，并能展示后端中文错误。
- 每个请求均可通过响应头和响应体 `traceId` 关联日志。
- SSE、204、HTTP 状态和 `Location` 头不被破坏。
- 配置文件不包含真实 API Key。
- 后端和前端除已记录的 `main` 基线失败外不新增失败，生产构建通过。
