# Market Pulse Data Readiness Implementation Plan

> 使用 subagent-driven-development 实现独立Python数据任务；主任务处理Java和React；共享工作区不建立worktree。每批测试通过后提交推送。

**Goal:** 主题成员自动获取足够日K，重复进入日频研究快速命中缓存。
**Architecture:** Python SnapshotStore修订号＋持久日频缓存；ProviderRouter有界逐股补齐；Java字段注入适配服务和Web响应；React去重队列更新日频结果。
**Tech Stack:** 既有SQLite/Python/Pydantic/FastAPI、Spring/Java21、React/TypeScript，无新依赖。

- [ ] Python tests/test_daily_research_cache.py、test_research_members.py先写失败测试，覆盖设计验收矩阵。
- [ ] snapshot_store.py增加日K修订号和缓存持久化；daily_research.py/cache服务合并同键计算；app.py共享服务生命周期与成员POST路由；research_members.py实现本地检查、复用router、并发/超时/冷却。
- [ ] Python相关pytest通过后独立审查并提交推送。
- [ ] Java新增MemberDataStatus/MemberDataReason独立枚举、内部DTO和WebResponse；PythonDailyResearchSource增加POST映射及缓存元数据；DailyResearchService/controller增加成员补齐。先写RPC和controller测试验证请求/响应边界及异常。
- [ ] Maven使用JDK21运行 *DailyResearch*、*ResearchMember*、*MarketPulse* 相关测试，审查后提交推送。
- [ ] React新增useThemeMemberData队列hook和状态展示；ThemeResearchPanel上报已保存成员、DailyResearchPanel监听并在补齐结束刷新；cache_hit/calculated_at显示缓存时效。先测试重复主题去重、最多2并发、加载/保存/切日取消、失败重试，运行全前端测试和build。
- [ ] 用真实行情库测缓存前后延迟；检查正在运行服务的启动配置，安全重启更新的Java/Python并执行HTTP与浏览器验收。
- [ ] 更新使用说明、核对开发规范/原始未提交改动、独立审查后提交推送交付。
