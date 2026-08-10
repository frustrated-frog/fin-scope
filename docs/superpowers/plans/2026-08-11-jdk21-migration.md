# JDK 21 统一迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FinScope 后端的编译、测试、运行与容器基线统一到 JDK 21，并用具名虚拟线程精简有界研究分支的阻塞 I/O 并发实现。

**Architecture:** 保留 Spring Boot 2.7.18 和现有 `javax` API，只在父 Maven 配置、后端容器及开发文档中切换 JDK 基线。Java 21 代码改造限制在 `BoundedResearchOrchestrator`：研究模式继续决定最多三个只读分支，执行器改为每任务一个具名虚拟线程，结果仍按计划顺序收集，内部计划值对象改为 record。

**Tech Stack:** Java 21、Spring Boot 2.7.18、Maven 3.9、Maven Enforcer、Lombok 1.18.30、JUnit 5、Eclipse Temurin 21、Docker

---

### Task 1: 统一 Maven 与本地工具链基线

**Files:**
- Create: `.java-version`
- Modify: `backend/pom.xml`

- [ ] **Step 1: 将 Maven 编译目标设置为 Java 21**

在父 POM 中设置统一属性，并让显式 Lombok 依赖使用已支持 JDK 21 的版本：

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
    <version>1.18.30</version>
</dependency>
```

- [ ] **Step 2: 增加构建 JDK 校验**

在父 POM 的 `build/plugins` 中配置 Maven Enforcer，要求 Maven 本身运行在 JDK 21：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <id>enforce-java-version</id>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireJavaVersion>
                        <version>[21,22)</version>
                        <message>FinScope backend requires JDK 21 for Maven builds.</message>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: 固定版本管理器提示**

创建 `.java-version`，内容为：

```text
21
```

- [ ] **Step 4: 验证旧 JDK 会快速失败**

Run: `cd backend && mvn -pl finscope-common validate`

Expected: 当前 JDK 8 环境在 Enforcer 阶段失败，错误信息包含 `FinScope backend requires JDK 21`。

- [ ] **Step 5: 使用 JDK 21 验证 Maven 配置**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 \
  mvn -Dmaven.repo.local=/tmp/m2 -pl finscope-common test
```

Expected: `BUILD SUCCESS`，编译器使用 release/source 21。

- [ ] **Step 6: 提交 Maven 基线**

```bash
git add .java-version backend/pom.xml
git commit -m "build: 统一后端JDK二十一基线"
git push -u origin codex/jdk21-migration
```

### Task 2: 用 Java 21 虚拟线程精简研究分支并发

**Files:**
- Modify: `backend/finscope-service/src/test/java/com/finscope/service/research/agent/BoundedResearchOrchestratorTest.java`
- Modify: `backend/finscope-service/src/main/java/com/finscope/service/research/agent/BoundedResearchOrchestrator.java`

- [ ] **Step 1: 写入虚拟线程行为测试**

在 `BoundedResearchOrchestratorTest` 增加测试，验证深度研究的阻塞分支运行在具名虚拟线程上：

```java
@Test
void deepRunsBlockingBranchesOnNamedVirtualThreads() {
    Set<String> threadNames = ConcurrentHashMap.newKeySet();
    AtomicBoolean allVirtual = new AtomicBoolean(true);

    List<ResearchOrchestrator.BranchResult> results = orchestrator.execute(
            ResearchMode.DEEP, "AI 资本开支", "SUPPORT", (query, intent) -> {
                Thread thread = Thread.currentThread();
                threadNames.add(thread.getName());
                allVirtual.compareAndSet(true, thread.isVirtual());
                return Collections.singletonList(new SearchResult());
            });

    assertEquals(3, results.size());
    assertTrue(allVirtual.get());
    assertTrue(threadNames.stream().allMatch(name -> name.startsWith("research-read-branch-")));
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 \
  mvn -Dmaven.repo.local=/tmp/m2 -pl finscope-service -am \
  -Dtest=BoundedResearchOrchestratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 新测试失败于 `allVirtual` 断言，因为现有执行器创建平台线程。

- [ ] **Step 3: 替换为具名虚拟线程执行器**

将多分支执行代码改为 try-with-resources 管理的虚拟线程执行器：

```java
ThreadFactory factory = Thread.ofVirtual()
        .name("research-read-branch-", 0)
        .factory();
try (ExecutorService executorService = Executors.newThreadPerTaskExecutor(factory)) {
    List<Future<BranchResult>> futures = new ArrayList<>();
    for (BranchPlan plan : plans) {
        futures.add(executorService.submit(() -> run(plan, executor)));
    }
    return collect(plans, futures);
}
```

保持分支总数由 `plans()` 和 `ResearchMode.maxConcurrency` 限制，抽取 `collect()` 时继续按计划顺序返回结果并隔离单分支异常。

- [ ] **Step 4: 将内部不可变计划改为 record**

用 record 替换只承载查询和意图的内部类：

```java
private record BranchPlan(String query, String intent) {
}
```

同步将内部字段访问改为 `query()` 和 `intent()`；公开 DTO、领域实体和 Spring Bean 不改为 record。

- [ ] **Step 5: 运行测试并确认 GREEN**

Run: Task 2 Step 2 的同一条 Docker Maven 命令。

Expected: `BoundedResearchOrchestratorTest` 全部通过，包括虚拟线程、并发上限、顺序和失败隔离测试。

- [ ] **Step 6: 运行 service 模块测试**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 \
  mvn -Dmaven.repo.local=/tmp/m2 -pl finscope-service -am test
```

Expected: reactor 中截至 `finscope-service` 的测试全部通过。

- [ ] **Step 7: 提交 Java 21 改造**

```bash
git add backend/finscope-service/src/main/java/com/finscope/service/research/agent/BoundedResearchOrchestrator.java \
  backend/finscope-service/src/test/java/com/finscope/service/research/agent/BoundedResearchOrchestratorTest.java
git commit -m "refactor: 使用虚拟线程执行研究分支"
git push
```

### Task 3: 统一后端容器运行时

**Files:**
- Modify: `backend/finscope-web/Dockerfile`

- [ ] **Step 1: 切换构建和运行镜像**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
...
FROM eclipse-temurin:21-jre
```

保留现有工作目录、复制路径、环境变量、端口和入口命令。

- [ ] **Step 2: 构建后端镜像**

Run: `docker build -f backend/finscope-web/Dockerfile -t finscope-backend:jdk21 .`

Expected: 镜像构建成功，Maven 打包输出 `BUILD SUCCESS`。

- [ ] **Step 3: 核对镜像运行时**

Run: `docker run --rm --entrypoint java finscope-backend:jdk21 -version`

Expected: 输出 Eclipse Temurin/OpenJDK 21。

- [ ] **Step 4: 提交容器改动**

```bash
git add backend/finscope-web/Dockerfile
git commit -m "build: 升级后端容器至JDK二十一"
git push
```

### Task 4: 同步当前开发文档

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: 更新项目总览与环境要求**

将当前后端技术栈、JDK 安装、多 JDK 切换、IDE SDK 与 Maven Runner 指引全部从 Java 8 改为 JDK 21。保留 Spring Boot 2.7.18 和 `javax.annotation` 兼容说明，并明确该组合由 Spring Boot 官方支持。

- [ ] **Step 2: 记录 Java 21 使用边界**

在 README 的后端说明中注明：当前只在有界、阻塞 I/O 型研究分支使用具名虚拟线程；共享线程池仍遵循显式容量、队列、拒绝策略和命名规范；不启用预览特性。

- [ ] **Step 3: 检查当前文档不再声明 Java 8 基线**

Run:

```bash
rg -n "Java 8|JDK 8|java 8|jdk 8|1\\.8|temurin-8|:8-jre" \
  README.md AGENTS.md CLAUDE.md backend --glob '!**/target/**'
```

Expected: 无匹配。历史产品/技术方案文档不做追溯性改写。

- [ ] **Step 4: 提交文档改动**

```bash
git add README.md AGENTS.md CLAUDE.md
git commit -m "docs: 更新JDK二十一开发说明"
git push
```

### Task 5: 全量验证与交付

**Files:**
- Verify only; no expected source changes

- [ ] **Step 1: 在 JDK 21 下运行全量后端测试**

Run:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 \
  mvn -Dmaven.repo.local=/tmp/m2 test
```

Expected: 全部六个后端模块 `BUILD SUCCESS`，无测试失败。

- [ ] **Step 2: 打包可执行 Jar**

Run: 同一 JDK 21 容器执行 `mvn -Dmaven.repo.local=/tmp/m2 -pl finscope-web -am package -DskipTests`。

Expected: `backend/finscope-web/target/finscope-web-0.1.0-SNAPSHOT.jar` 生成且打包成功。

- [ ] **Step 3: 启动应用并执行健康检查**

Run: 使用后端 JDK 21 镜像启动临时容器，挂载临时数据目录并访问 `/actuator/health`；检查日志时不得打印 `application.yml` 中的任何密钥值。

Expected: 健康端点返回 HTTP 200 和 `UP`；若 Redis 或市场数据服务未启动，非核心下游不得阻止应用上下文启动。

- [ ] **Step 4: 检查改动范围与格式**

Run: `git diff --check codex/fund-holdings-detail...HEAD && git status --short --branch`

Expected: 无空白错误，只包含 JDK 21 迁移设计、计划、构建、虚拟线程测试与实现、Docker 和当前开发文档改动。

- [ ] **Step 5: 推送最终分支**

Run: `git push -u origin codex/jdk21-migration`

Expected: 推送成功；若 SSH 网络仍不可用，记录本地提交哈希和失败原因，不改用可能泄露凭据的方式。
