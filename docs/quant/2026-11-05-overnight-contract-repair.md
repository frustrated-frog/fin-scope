# 新版隔夜档案接口修复

2026-11-05，Python 历史接口正常返回，但 Java `/api/quant/overnight/history` 返回 500。新增 `acquisition` 字段未进入 Java DTO，Jackson 先报未知属性；移除该字段做内存诊断后，v6 模型版本又被旧白名单拒绝。此前只验证 Python 与前端，遗漏了新档案穿过 Java 的完整链路。

## 修复范围

- 在原有领域 DTO 保留行情采集状态、来源、时间与回退原因；Java 客户端接受 `overnight-v6-decision-direction`，继续拒绝未知版本、非法状态及缺失或非法审计指纹。
- 复用统一异常处理器及已有错误码：契约异常返回 `FS-3003` / 502，超时返回 `FS-3002` / 504，其余 Provider 不可用返回 `FS-3001` / 502。响应不泄露下游内部报错，日志保留原始异常链。
- 页面区分格式异常与临时连接问题；读取失败保留已有档案，服务修复后轮询恢复。
- 修正旧前端测试中 `getByRole` 不支持的 `exact` 参数；字符串名称本身已精确匹配。

## 回归与运行验证

`test-fixtures/overnight/history-v6.json` 是固定种子的合成分钟数据经 Python 实际 `OvernightService.generate` / `OvernightStore.history` 生成的尾盘、盘后档案，不包含用户持仓或真实运行数据。Python 测试锁定该输出；Java Web 测试使用同一文件，只替换 HTTP 传输，保留真实 RPC 解析、版本校验、业务服务与 Controller。

- Java：RPC 客户端 5 项、Web 跨层测试 4 项、统一异常处理 6 项通过。覆盖新旧混合档案、生成、结算，以及契约、超时和连接异常。
- Python：共享契约生成测试通过，数据库使用临时目录。
- 前端：两组相关测试共 13 项通过，包含故障保留档案和恢复；TypeScript / Vite 生产构建通过。现有大块体积提示不阻断构建。
- 已通过 IDEA 重新运行 `FinScopeApplication`。Java 8080 与 Vite 5173 代理的历史接口均返回 200，读取最近 50 份档案，其中 16 份为 v6。
- 浏览器实际验证：14:45 窗口展示 6 只候选及其判断和概率，不再显示档案读取失败。

本次修复恢复了已生成预测的可读性，不构成预测命中率提升的证据。旧原始档案、冻结参考价和模型验证资格均保持原有语义。

## 编码自检

复用现有 `@Resource` / `@Autowired` 注入、Lombok DTO、HTTP 客户端和统一异常体系。未新增业务 Bean、依赖、数据库变更或反向模块依赖；新增条件与循环均使用完整大括号。测试文件分别落在 Python、RPC 与 Web 测试目录，共享文件仅含可复现合成数据。
