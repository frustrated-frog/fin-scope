# Agent 驱动的实时资讯分类 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 News Wire 增加 Agent 驱动的动态分类，并让当前分类复用现有 45 秒轮询和“发现 N 条新资讯”交互。

**Architecture:** SQLite 保存分类目录和资讯分类状态；资讯服务获取实时材料后复用已有结果，并异步批量提交未分类条目给 LLM。Controller 提供分类目录和按分类查询，React 只维护一个随当前标签变化的轮询流程。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、SQLite、Jackson、JUnit 5、Mockito、React、TypeScript、Vitest、Testing Library。

---

### Task 1: 分类目录和分类结果持久化

**Files:**
- Modify: `backend/finscope-dao/src/main/java/com/finscope/dao/config/DatabaseInitializer.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/news/NewsCategory.java`
- Create: `backend/finscope-domain/src/main/java/com/finscope/domain/news/NewsItemClassification.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/news/NewsCategoryRepository.java`
- Create: `backend/finscope-dao/src/main/java/com/finscope/dao/news/NewsClassificationRepository.java`
- Test: `backend/finscope-dao/src/test/java/com/finscope/dao/news/NewsClassificationRepositoryTest.java`

- [ ] 编写失败测试：初始化后分类目录按顺序返回；同一 `item_id` 只能被认领一次；成功分类后可以批量查询；失败记录超过重试时间后可以重新认领。
- [ ] 运行 `cd backend && mvn -pl finscope-dao -am -Dtest=NewsClassificationRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认测试因表和 Repository 不存在而失败。
- [ ] 增加 `news_category` 与 `news_item_classification` 表、索引和幂等种子；实现两个小型 Repository。
- [ ] 重跑指定测试，确认全部通过。
- [ ] 使用 `feat: 增加资讯分类持久化` 提交并推送。

### Task 2: Agent 批量分类与失败隔离

**Files:**
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationCandidate.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationAgent.java`
- Create: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsClassificationCoordinator.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/config/AppConfig.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsClassificationAgentTest.java`
- Test: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsClassificationCoordinatorTest.java`

- [ ] 编写失败测试：Agent 只接受目录中的编码、解析 fenced JSON、拒绝未知条目；Coordinator 不重复调度已认领条目，并在模型异常时保存失败状态。
- [ ] 运行 `cd backend && mvn -pl finscope-service -am -Dtest=NewsClassificationAgentTest,NewsClassificationCoordinatorTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认测试因分类组件不存在而失败。
- [ ] 实现结构化提示词、JSON 校验、`news-classification` 轨迹记录和分批异步调度；在 `AppConfig` 增加单线程有界 `newsClassificationExecutor`。
- [ ] 重跑指定测试，确认全部通过。
- [ ] 使用 `feat: 增加资讯分类智能体` 提交并推送。

### Task 3: 动态分类和按分类资讯 API

**Files:**
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedItem.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/news/NewsFeedService.java`
- Modify: `backend/finscope-web/src/main/java/com/finscope/web/controller/NewsFeedController.java`
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/news/NewsFeedServiceTest.java`
- Create: `backend/finscope-web/src/test/java/com/finscope/web/controller/NewsFeedControllerTest.java`

- [ ] 扩展失败测试：`ALL` 立即返回未分类条目并调度 Agent；具体分类只返回匹配结果；未知分类被拒绝；分类目录端点返回启用目录。
- [ ] 运行 `cd backend && mvn -pl finscope-web -am -Dtest=NewsFeedServiceTest,NewsFeedControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认新增断言失败。
- [ ] 扩展快照映射和分类参数；批量附加已持久化分类；在返回前调度未分类条目；增加 `/api/news/categories`。
- [ ] 重跑指定测试，确认全部通过。
- [ ] 使用 `feat: 提供分类资讯查询接口` 提交并推送。

### Task 4: 分类标签与单轮询切换

**Files:**
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 编写失败测试：分类标签来自 API；点击标签立即请求 `category` 参数；切换后轮询请求当前分类；迟到响应不覆盖当前分类。
- [ ] 运行 `cd frontend && npm test -- NewsView.test.tsx`，确认新增测试失败。
- [ ] 实现分类目录加载、标签 UI、请求序号隔离和单一定时器；保留现有双栏、搜索、来源筛选、手动刷新与降级提示。
- [ ] 重跑指定测试，确认全部通过。
- [ ] 使用 `feat: 增加实时资讯分类切换` 提交并推送。

### Task 5: 自动刷新新资讯提示

**Files:**
- Modify: `frontend/src/features/news/NewsView.tsx`
- Modify: `frontend/src/features/news/NewsView.test.tsx`
- Modify: `frontend/src/styles.css`

- [ ] 编写失败测试：自动轮询发现新 ID 时旧列表保持不动并显示数量；点击提示后应用新快照；手动刷新仍立即更新。
- [ ] 运行 `cd frontend && npm test -- NewsView.test.tsx`，确认新资讯提示测试失败。
- [ ] 增加 `pendingSnapshot` 和新 ID 计数，分类切换时清理暂存结果，并补充可访问的提示按钮样式。
- [ ] 重跑指定测试，确认全部通过。
- [ ] 使用 `feat: 增加资讯更新提示` 提交并推送。

### Task 6: 回归验证与文档同步

**Files:**
- Modify: `README.md`
- Modify: `docs/架构说明.md`

- [ ] 在 README 和架构说明中记录 Agent 分类、动态目录、按分类轮询和故障降级边界，不记录任何 API Key。
- [ ] 运行 `cd backend && mvn test`，确认所有后端测试通过。
- [ ] 运行 `cd frontend && npm test -- --run`，确认所有前端测试通过。
- [ ] 运行 `cd frontend && npm run build`，确认 TypeScript 和生产构建通过。
- [ ] 检查 `git diff --check`、`git status --short` 和相对 `main` 的提交列表。
- [ ] 使用 `docs: 补充实时资讯分类说明` 提交并推送。
