# 同花顺快讯接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将同花顺实时快讯和要闻精华作为两个可靠、可隔离的 `NEWS_FLASH` 来源接入研究 Agent。

**Architecture:** 两个 Spring Provider 共享一个只负责请求、解析和标准化的抽象基类，通过既有 `ResearchMaterialGateway` 自动装配。所有外部调用继续经过 `AcquisitionRuntime` 与 `ProviderRequestGuard`，结构漂移转为明确的 Provider 契约错误。

**Tech Stack:** Java 8、Spring Boot 2.7、Jackson、JUnit 5、Maven

---

### Task 1: 固化同花顺响应契约

**Files:**
- Create: `backend/finscope-rpc/src/test/java/com/finscope/rpc/research/material/ThsNewsResearchMaterialProviderTest.java`

- [ ] 编写实时快讯解析、关键词过滤、完整正文、无效条目过滤和请求元数据测试。
- [ ] 编写要闻精华端点与 Provider 元数据测试。
- [ ] 编写非法包装、缺少条目数组和陈旧数据告警测试。
- [ ] 运行目标测试，确认因 Provider 尚不存在而失败。

### Task 2: 实现共享解析器与两个来源

**Files:**
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/AbstractThsNewsResearchMaterialProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/ThsRealtimeNewsResearchMaterialProvider.java`
- Create: `backend/finscope-rpc/src/main/java/com/finscope/rpc/research/material/ThsHeadlineNewsResearchMaterialProvider.java`

- [ ] 实现受控请求、JavaScript 对象解包和宽松字段名解析。
- [ ] 实现单条数据容错、标准化、过滤、条数上限及 HTTPS 地址转换。
- [ ] 实现根数据时间的新鲜度告警和明确的契约异常。
- [ ] 运行目标测试直至通过，再运行 RPC 模块测试。
- [ ] 使用 `feat: 接入同花顺实时快讯与要闻精华` 提交并推送。

### Task 3: 验证研究网关与真实端点

- [ ] 运行研究资料网关与研究工具相关测试，确认 Spring Provider 自动进入现有来源列表。
- [ ] 对 `realtimenews.js` 和 `ywjh.js` 执行只读验活，检查包装结构、条目数和最新发布时间。
- [ ] 运行后端全量测试，检查工作区差异与敏感信息。
- [ ] 若验证产生修复，使用 `fix: 加固同花顺快讯数据契约` 提交并推送。
