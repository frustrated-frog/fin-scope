# 纯投资数据认识 Agent 实施计划

> 本计划按可独立验证的批次执行，每批完成后提交并推送。实现采用测试先行，不迁移、不删除既有数据。

## 批次一：切断文章库到投资认识的前端链路

1. 修改 ArticleView 测试，断言页面没有“沉淀主题”，且不调用 `/api/topics/from-article/*`。
2. 删除 ArticleView 的沉淀状态、请求与按钮传参。
3. 修改 BriefsView、BriefReaderView 与 App 测试，断言简报仅阅读，不调用 `/api/topics/from-brief/*`。
4. 删除简报沉淀回调、状态和按钮。
5. 修改 TopicLibrary 测试，断言只展示认识，不提供“待提炼材料”Tab；保留手动新建。
6. 运行相关前端测试和生产构建，提交并推送。

## 批次二：建立独立 Agent 候选模型与 API

1. 在 domain 增加候选实体、状态和 API 视图模型。
2. 在 schema 初始化中新增独立候选表及索引，不触碰既有表数据。
3. 为候选 Repository 先写 DAO 测试，再实现保存、列表、状态流转和乐观锁。
4. 新增投资数据快照组装器，只读取 Watchlist、Quote 和已存在的结构化市场能力；快讯字段独立标记为 trigger。
5. 新增 Agent 服务测试，覆盖：
   - 有投资对象和结构化变化时生成候选；
   - 只有快讯时输出待补证据；
   - 非投资对象被拒绝；
   - Prompt/输入中不含文章内容；
   - LLM 失败时确定性降级。
6. 实现命题生成与反证审查，记录 Agent Run。
7. 新增 Controller API：运行、列表、接受、转待补证据、忽略/失效。
8. 增加 Controller 测试并运行后端相关模块测试，提交并推送。

## 批次三：重做投资认识页面

1. 为 Agent 工作台写组件测试：运行摘要、候选、待补证据、正式认识、失效状态及空状态。
2. 新增类型和 API client，接入候选列表和运行接口。
3. 将 TopicLibrary 首屏改为 Agent 工作台：
   - 顶部显示最近运行时间、检查对象数和数据健康度；
   - 主区域优先展示待处理候选；
   - 卡片展开后展示观察、机制、支持、反证、验证指标和失效条件；
   - 手动新建放在次级操作区。
4. 正式认识继续复用现有 TopicWorkspace；历史材料主题继续由准入分类隐藏。
5. 使用现有研究台视觉语言调整响应式布局，不引入装饰性大图或无意义动效。
6. 运行前端相关测试与构建，提交并推送。

## 批次四：整体验证

1. 搜索确认前端不再存在 `from-article`、`from-brief` 和文章沉淀按钮调用。
2. 运行全部前端测试与生产构建。
3. 运行全部后端测试。
4. 本地启动并检查真实页面：文章、简报、认识工作台和 Agent 空/失败状态。
5. 确认 git diff 只涉及证据事实知识工作台及必要支撑代码，提交并推送最后修正。

## 关键文件预期

- `frontend/src/features/articles/ArticleView.tsx`
- `frontend/src/features/briefs/BriefsView.tsx`
- `frontend/src/features/briefs/BriefReaderView.tsx`
- `frontend/src/features/knowledge/topics/TopicLibrary.tsx`
- `frontend/src/features/knowledge/KnowledgeView.tsx`
- `backend/finscope-domain/.../investmentrecognition/*`
- `backend/finscope-dao/.../investmentrecognition/*`
- `backend/finscope-service/.../investmentrecognition/*`
- `backend/finscope-web/.../InvestmentRecognitionController.java`
