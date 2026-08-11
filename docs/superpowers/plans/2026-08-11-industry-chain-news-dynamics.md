# 产业链新闻动态实施计划

> 对应设计：`docs/superpowers/specs/2026-08-11-industry-chain-news-dynamics-design.md`

**目标：** 复用 Research Radar 聚合事件，为产业链图谱增加可筛选、可追踪、可跳转原事件的“链上动态”视图。

**架构：** Radar 继续拥有新闻事实；产业链新增轻量关联表和分析服务。服务先用确定性文本召回候选，再生成受校验的节点影响路径；查询时将关联数据与实时 `RadarEvent` 组装。前端在现有画布上叠加事件计数和路径高亮，并用独立动态面板展示时间线。

**技术栈：** Java 21、Spring Boot 2.7、Spring JDBC、SQLite、JUnit 5、React、TypeScript、Vitest。

---

## 任务 1：定义新闻影响领域契约

**文件：**

- 新增：`backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEventImpact.java`
- 新增：`backend/finscope-domain/src/main/java/com/finscope/domain/industrychain/IndustryChainEventFeed.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainEventAnalyzerTest.java`

1. 先写失败测试，定义允许的方向、机制、周期、置信度和有序节点路径。
2. 运行 `mvn -pl finscope-service -am -Dtest=IndustryChainEventAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认因实现缺失失败。
3. 添加最小领域对象和严格枚举归一化。
4. 重跑测试，确认领域契约通过。

## 任务 2：建立只存关联的 SQLite 持久层

**文件：**

- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- 新增：`backend/finscope-dao/src/main/java/com/finscope/dao/industrychain/IndustryChainEventImpactRepository.java`
- 修改：`backend/finscope-dao/src/main/java/com/finscope/dao/radar/RadarRepository.java`
- 新增：`backend/finscope-dao/src/test/java/com/finscope/dao/industrychain/IndustryChainEventImpactRepositoryTest.java`

1. 写失败测试覆盖唯一约束、覆盖更新、路径顺序、时间窗和 Radar 事件字段实时组装。
2. 新增 `industry_chain_event_impact`、`industry_chain_event_path` 和索引；不增加标题、摘要、来源字段。
3. 为 Radar Repository 增加按最近时间窗读取聚合事件的方法。
4. 实现关联 upsert、路径替换和 feed 查询。
5. 运行 DAO 指定测试并修至通过。

## 任务 3：实现候选召回、影响分析和幂等回填

**文件：**

- 新增：`backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainEventAnalyzer.java`
- 新增：`backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainEventService.java`
- 修改：`backend/finscope-service/src/main/java/com/finscope/service/industrychain/IndustryChainService.java`
- 测试：`backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainEventAnalyzerTest.java`
- 新增：`backend/finscope-service/src/test/java/com/finscope/service/industrychain/IndustryChainEventServiceTest.java`

1. 写失败测试覆盖节点/公司/代码召回、弱相关跳过、路径推导、首次 30 天回填和第二次增量不重复。
2. 实现确定性候选评分和直接节点识别。
3. 利用现有图谱边构造从直接节点向上下游延伸的短路径；无法可靠判断时使用 `UNCERTAIN/LOW`，不虚构节点。
4. 实现查询、首次回填和显式刷新统计。
5. 跑 service 指定测试并修至通过。

## 任务 4：暴露产业链动态 API

**文件：**

- 修改：`backend/finscope-web/src/main/java/com/finscope/web/controller/IndustryChainController.java`
- 修改：`backend/finscope-web/src/test/java/com/finscope/web/controller/IndustryChainControllerTest.java`

1. 写失败测试覆盖 `GET /{id}/events?hours=` 和 `POST /{id}/events/refresh`。
2. 将查询窗口限制在 1 至 720 小时，默认 168 小时。
3. 返回 feed 和 refresh summary，保持统一 `ApiResponse`。
4. 跑 controller 测试并修至通过。
5. 运行后端全量 `mvn test`。

## 任务 5：定义前端 DTO 和动态交互测试

**文件：**

- 修改：`frontend/src/features/industry-chain/industryChainTypes.ts`
- 修改：`frontend/src/features/industry-chain/IndustryChainView.test.tsx`
- 新增：`frontend/src/features/industry-chain/IndustryChainDynamics.test.tsx`

1. 写失败测试覆盖全景/动态切换、24h/7d/30d、选中事件和打开 News Wire。
2. 添加 feed、impact、path、refresh summary 类型。
3. 运行 `npm test -- IndustryChainView IndustryChainDynamics`，确认测试先失败。

## 任务 6：实现高级链上动态 UI

**文件：**

- 新增：`frontend/src/features/industry-chain/IndustryChainDynamics.tsx`
- 修改：`frontend/src/features/industry-chain/IndustryChainView.tsx`
- 修改：`frontend/src/features/industry-chain/IndustryChainCanvas.tsx`
- 修改：`frontend/src/features/industry-chain/industry-chain.css`
- 修改：`frontend/src/App.tsx`

1. 添加“产业全景 / 链上动态”分段切换与时间窗。
2. 在画布节点上叠加事件数徽标，选中事件后高亮路径且保持现有布局不变。
3. 增加右侧时间线、影响标签、传播路径和空/错误/刷新状态。
4. 通过 App 现有 `initialRadarEventId` 机制跳转 News Wire 指定事件。
5. 运行前端指定测试和 `npm run build`。

## 任务 7：真实数据回填与端到端验收

**文件：**

- 不新增生产数据文件；只更新被 Git 忽略的本地 `data/finance.db`。

1. 启动后端与前端。
2. 对现有 AI 算力链调用动态刷新，完成最近 30 天事件回填。
3. 验证查询接口返回真实 Radar 事件 ID，且数据库关联表不含新闻正文副本。
4. 在浏览器验证视图切换、节点徽标、事件路径、详情和 News Wire 跳转。
5. 截图检查桌面宽度下无遮挡、无连线覆盖回归和合理空状态。

## 任务 8：最终质量门禁与交付

1. 运行 `git diff --check`。
2. 运行后端 `mvn test`。
3. 运行前端 `npm test` 与 `npm run build`。
4. 检查 `git status`，确保不提交 `output/` 和本地数据库。
5. 按可独立验证批次提交并推送：后端能力、前端体验、必要文档。
