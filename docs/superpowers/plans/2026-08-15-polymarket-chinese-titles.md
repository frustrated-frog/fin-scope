# Polymarket Chinese Titles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让全球预期监控使用 Polymarket 官方中文市场标题。

**Architecture:** 仅修改 `finscope-rpc` 中 Gamma 市场列表请求的 locale 参数，现有 DTO、业务筛选、Redis 缓存和前端展示链路保持不变。通过客户端 URI 契约测试覆盖两个分页请求。

**Tech Stack:** Java 21、Spring Boot 2.7、JUnit 5、Maven

---

### Task 1: Gamma 中文市场请求

**Files:**
- Modify: `backend/finscope-rpc/src/test/java/com/finscope/rpc/polymarket/PolymarketPublicClientTest.java`
- Modify: `backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicClient.java`

- [ ] **Step 1: 写入失败测试**

将现有两个分页 URI 断言改为包含 `locale=zh`：

```java
assertEquals("closed=false&limit=100&offset=0&order=volumeNum&ascending=false&locale=zh",
        requested.get(0).getQuery());
assertEquals("closed=false&limit=100&offset=100&order=volumeNum&ascending=false&locale=zh",
        requested.get(1).getQuery());
```

- [ ] **Step 2: 验证测试因缺少中文参数而失败**

运行：

```bash
cd backend && JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home \
  mvn -pl finscope-rpc -Dtest=PolymarketPublicClientTest test
```

预期：分页 URI 断言失败，实际查询参数缺少 `locale=zh`。

- [ ] **Step 3: 实现最小改动**

在 Gamma 市场请求模板末尾追加官方语言参数：

```java
private static final String ACTIVE_MARKETS_URL =
        "https://gamma-api.polymarket.com/markets?closed=false&limit=100&offset=%d"
                + "&order=volumeNum&ascending=false&locale=zh";
```

- [ ] **Step 4: 验证相关测试与模块测试**

运行：

```bash
cd backend && JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home \
  mvn -pl finscope-rpc test
```

预期：`finscope-rpc` 全部测试通过。

- [ ] **Step 5: 提交并推送当前分支**

```bash
git add backend/finscope-rpc/src/main/java/com/finscope/rpc/polymarket/PolymarketPublicClient.java \
  backend/finscope-rpc/src/test/java/com/finscope/rpc/polymarket/PolymarketPublicClientTest.java
git commit -m "fix: 改用官方中文市场标题"
git push origin codex/global-expectations-monitor
```
