# 投资观察独立工作台实施计划

> **执行要求：** 按任务顺序实施，每个行为先写失败测试，再写最小实现；每一批通过对应测试后独立提交并推送。

**目标：** 新增独立一级 Tab“投资观察”，从持久化雷达事件生成一个零持仓也能工作的个人研究观察池，同时保持首页热点和新闻雷达逻辑不变。

**架构：** 在 common/domain/dao/service/web/frontend 中新增独立 `investmentobservation` 纵向业务域。观察刷新只读取 `RadarRepository` 的持久化事件，通过确定性评分生成独立观察对象和阶段转移；前端独立请求 `/api/investment-observations`，只在用户主动打开来源时跳转新闻雷达。

**技术栈：** Java 21、Spring Boot 2.7、JdbcTemplate、SQLite、JUnit 5、Mockito、React、TypeScript、Vitest、Testing Library、Vite。

---

## 任务一：建立稳定状态和领域契约

**文件：**

- 新建 `backend/finscope-common/src/main/java/com/finscope/common/enums/investmentobservation/InvestmentObservationStage.java`
- 新建 `backend/finscope-common/src/main/java/com/finscope/common/enums/investmentobservation/InvestmentObservationDisposition.java`
- 新建 `backend/finscope-common/src/main/java/com/finscope/common/enums/investmentobservation/InvestmentObservationSourceType.java`
- 新建 `backend/finscope-domain/src/main/java/com/finscope/domain/investmentobservation/InvestmentObservation.java`
- 新建 `backend/finscope-domain/src/main/java/com/finscope/domain/investmentobservation/InvestmentObservationTransition.java`
- 新建 `backend/finscope-domain/src/main/java/com/finscope/domain/investmentobservation/InvestmentObservationScoreDimension.java`
- 新建 `backend/finscope-domain/src/main/java/com/finscope/domain/investmentobservation/InvestmentObservationWorkspace.java`
- 测试 `backend/finscope-domain/src/test/java/com/finscope/domain/investmentobservation/InvestmentObservationContractTest.java`

**步骤：**

1. 写领域契约测试，覆盖枚举 JSON 取值、工作台统计、卡片所需字段和转移记录。
2. 运行 `cd backend && mvn -pl finscope-domain -am -Dtest=InvestmentObservationContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认失败。
3. 实现最小领域类型；跨层稳定状态使用独立枚举文件。
4. 再次运行测试并提交：`feat: 增加投资观察领域契约`。

## 任务二：实现 SQLite 持久化和幂等来源约束

**文件：**

- 修改 `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- 新建 `backend/finscope-dao/src/main/java/com/finscope/dao/investmentobservation/InvestmentObservationRepository.java`
- 新建 `backend/finscope-dao/src/test/java/com/finscope/dao/investmentobservation/InvestmentObservationRepositoryTest.java`

**步骤：**

1. 写 Repository 测试：`source_type + source_id` 唯一、upsert 不重复、用户状态不被自动刷新覆盖、阶段变化有 transition、revision 冲突拒绝更新。
2. 运行指定测试，确认因表和 Repository 缺失而失败。
3. 在 Schema 初始化器中增加两个独立表及索引；实现字段注入风格一致的 Repository。
4. 运行 DAO 指定测试和 `git diff --check`。
5. 提交并推送：`feat: 增加投资观察持久化`。

## 任务三：实现确定性评分与不空库分层

**文件：**

- 新建 `backend/finscope-service/src/main/java/com/finscope/service/investmentobservation/InvestmentObservationScoringService.java`
- 新建 `backend/finscope-service/src/test/java/com/finscope/service/investmentobservation/InvestmentObservationScoringServiceTest.java`

**步骤：**

1. 先写测试覆盖：高质量事件进入 FOCUS、普通事件进入 TRACKING、弱证据进入 LEARNING、字段缺失保守降级、无 70 分时从 50+ 中保底最多 3 个并标注证据不足。
2. 运行测试确认失败。
3. 用雷达已有字段实现六维评分；每个维度返回分数、上限和中文解释，不调用 LLM。
4. 运行测试，检查所有新增 `if/for` 使用完整大括号。
5. 提交并推送：`feat: 增加投资观察证据评分`。

## 任务四：实现刷新、生命周期和工作台服务

**文件：**

- 修改 `backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/investmentobservation/InvestmentObservationCandidateService.java`
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/investmentobservation/InvestmentObservationLifecycleService.java`
- 新建 `backend/finscope-service/src/main/java/com/finscope/service/investmentobservation/InvestmentObservationService.java`
- 新建 `backend/finscope-service/src/test/java/com/finscope/service/investmentobservation/InvestmentObservationServiceTest.java`

**步骤：**

1. 写服务测试覆盖：雷达为空、最多扫描 50 条并保留 20 个活跃对象、重复刷新幂等、旧数据在候选读取失败时保留、忽略/稍后状态不被刷新覆盖、详情含变化记录。
2. 运行测试确认失败。
3. 为 RadarRepository 增加只读候选查询；服务通过字段注入编排候选、评分、持久化和生命周期。
4. 刷新返回结构化统计，失败不删除现有观察对象。
5. 运行 service 指定测试并提交：`feat: 自动维护投资研究观察池`。

## 任务五：开放独立 REST 接口

**文件：**

- 新建 `backend/finscope-web/src/main/java/com/finscope/web/controller/InvestmentObservationController.java`
- 新建 `backend/finscope-web/src/main/java/com/finscope/web/request/UpdateInvestmentObservationStateRequest.java`
- 新建 `backend/finscope-web/src/test/java/com/finscope/web/controller/InvestmentObservationControllerTest.java`

**步骤：**

1. 写 MockMvc 测试覆盖工作台读取、刷新、详情、状态更新、归档和 revision 冲突映射。
2. 运行测试确认失败。
3. 实现字段注入 Controller 和请求对象，不允许 Controller 直接调用 Repository。
4. 运行 web 指定测试。
5. 提交并推送：`feat: 开放投资观察工作台接口`。

## 任务六：接入独立一级 Tab 和前端数据契约

**文件：**

- 修改 `frontend/src/shared/types/index.ts`
- 修改 `frontend/src/app/AppShell.tsx`
- 修改 `frontend/src/app/AppShell.test.tsx`
- 修改 `frontend/src/App.tsx`
- 修改 `frontend/src/App.test.tsx`
- 新建 `frontend/src/features/investment-observation/investmentObservationTypes.ts`
- 新建 `frontend/src/features/investment-observation/InvestmentObservationView.tsx`
- 新建 `frontend/src/features/investment-observation/InvestmentObservationView.test.tsx`

**步骤：**

1. 写导航和视图测试，确认“投资观察”是独立一级按钮，不依赖进入 News Wire。
2. 写页面测试：加载、空状态、今日重点、三层观察池、详情档案、状态操作、刷新失败保留旧界面。
3. 运行指定 Vitest，确认失败。
4. 实现 `View`、导航、App 分支、API 类型和页面交互；点击来源事件通过回调跳转现有新闻雷达详情。
5. 运行指定测试并提交：`feat: 增加投资观察独立页面`。

## 任务七：完成高质量响应式视觉

**文件：**

- 修改 `frontend/src/styles.css`
- 修改 `frontend/src/features/investment-observation/InvestmentObservationView.tsx`
- 修改 `frontend/src/features/investment-observation/InvestmentObservationView.test.tsx`

**步骤：**

1. 先补充可访问性和关键状态断言：语义标题、按钮名称、选中状态、警告与加载状态。
2. 实现暖白/墨色研究工作台、重点卡横向节奏、状态轨道、证据仪表和桌面双栏档案。
3. 增加 900px 与 600px 响应式规则；390px 下卡片单列、详情转为流式区域、无横向滚动。
4. 运行页面测试和 `npm run build`。
5. 启动本地前后端，使用真实浏览器检查 1440px 与 390px；修复溢出、层级和交互问题。
6. 提交并推送：`style: 完善投资观察工作台界面`。

## 任务八：回归、独立复审与交付

**文件：**

- 修改 `README.md`
- 按复审结果修改必要文件和测试

**步骤：**

1. 更新 README，说明独立边界、使用方法和“研究辅助、非交易建议”。
2. 运行后端全量：`cd backend && mvn test`。
3. 运行前端全量：`cd frontend && npm test -- --run && npm run build`。
4. 运行 `git diff --check`，确认工作区干净、分支与远端同步。
5. 请求独立代码复审，重点检查领域反向依赖、热点/雷达耦合、评分真实性、空库保底与 UI 状态竞态。
6. 修复所有 P1/P2，补充回归测试。
7. 提交并推送最终结果，保留 `codex/investment-observation` 分支供用户验收。
