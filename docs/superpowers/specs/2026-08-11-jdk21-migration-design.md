# JDK 21 统一迁移设计

## 背景

FinScope 后端当前以 Java 8、Spring Boot 2.7.18 和 Maven 多模块为统一基线。根 `pom.xml`、后端 Dockerfile、README、协作说明和部分仍然有效的技术文档都显式声明了 Java 8。项目需要将编译、测试、运行和容器环境整体统一到 JDK 21，同时控制框架升级带来的无关风险。

Spring Boot 2.7.18 官方声明兼容 Java 21，因此本次不同时升级 Spring Boot。Spring Boot 3 及其 `javax` 到 `jakarta` 的迁移留作独立阶段。

## 目标

- 所有后端 Maven 模块使用 Java 21 编译、测试和运行。
- 构建在低于 Java 21 的 JDK 上快速失败并给出明确提示。
- 后端 Docker 构建与运行镜像统一为 Eclipse Temurin 21。
- 解决注解处理器和构建插件对 JDK 21 的兼容问题。
- 更新面向开发者的当前有效文档，使本地、IDE、Maven 和 Docker 指引一致。
- 在 JDK 21 环境完成全量测试、打包、启动冒烟和容器构建验证。

## 非目标

- 不升级 Spring Boot 2.7.18 或 Spring Framework 5.3.x。
- 不进行 `javax.*` 到 `jakarta.*` 的批量迁移。
- 不批量改写业务代码为 record、模式匹配、虚拟线程或其他 Java 21 新语法与运行模型。
- 不调整前端和 Python 市场数据服务的语言或运行时版本。
- 不借机进行与 JDK 迁移无关的代码重构。

## 方案选择

### 方案一：仅切换编译版本

只修改 `<java.version>`。改动最少，但 Docker、文档和开发机仍可能继续使用旧 JDK，且旧版注解处理器可能在 JDK 21 下失败，不能实现“全项目统一”。不采用。

### 方案二：JDK 21 与 Spring Boot 3 同时升级

一次完成现代化，但会同时引入 Servlet、Validation、Annotation 等 API 从 `javax` 到 `jakarta` 的迁移，以及依赖和测试行为变化。变更面过大，失败时难以区分 JDK 与框架原因。不采用。

### 方案三：分阶段迁移

本次完整统一 JDK 21，同时保留 Spring Boot 2.7.18；后续单独升级 Spring Boot 3。该方案能立即结束 Java 8 基线，又能保持现有 Web 契约和业务逻辑稳定，因此采用。

## 详细设计

### Maven 基线

根 `backend/pom.xml` 将 Java 版本设置为 21，并使用 Maven Enforcer 在构建早期要求 Maven 运行于 JDK 21。校验范围覆盖整个 reactor，避免某个模块或开发环境继续使用低版本 JDK。

Lombok 升级到明确支持 JDK 21 的稳定版本。其他依赖仅在实际编译、测试或运行验证证明不兼容时才做最小升级，并记录原因。

### 运行与容器

后端多阶段 Dockerfile 的构建镜像与 JRE 镜像统一切换为 Eclipse Temurin 21。镜像仍使用现有 Maven 构建命令、Jar 路径、数据目录和 Spring Profile，不改变部署拓扑。

本地开发说明统一要求 JDK 21，并提供 `java -version` 与 `mvn -version` 的核对方式。IDE 的 Project SDK 与 Maven Runner JRE 均指向 JDK 21。

### 源码兼容策略

现有 Java 8 风格源码可以直接由 JDK 21 编译，Spring Boot 2.7 使用的 `javax.validation`、`javax.servlet` 和 `javax.annotation` 保持不变。测试中的 `com.sun.net.httpserver.HttpServer` 继续使用 JDK 21 提供的 `jdk.httpserver` 模块，并通过全量测试验证。

为体现升级的实际工程收益，本次选择一个边界清楚的阻塞 I/O 并发场景做 Java 21 改造：`BoundedResearchOrchestrator` 的短生命周期只读研究分支改用具名虚拟线程。分支数量仍受 `ResearchMode` 预算限制，不改变结果顺序、失败隔离或持久化边界；使用 `ExecutorService` 的 try-with-resources 生命周期确保任务结束后回收执行器。内部不可变的 `BranchPlan` 改为 record，减少样板代码。测试必须证明深度研究分支运行在具名虚拟线程上，并保持原有并发上限和返回语义。

除上述受测试覆盖的示范点外，不做全项目语法现代化。尤其不把有界共享线程池无条件替换为无限制虚拟线程，不引入 JDK 21 预览特性，也不违反项目对 DTO、Spring Bean、线程命名和外部调用隔离的既有规范。

### 文档一致性

更新 README、AGENTS.md、CLAUDE.md 和仍作为当前实现约束的文档。历史设计文档保留其当时的 Java 8 描述，避免篡改历史决策；如有必要，仅在总览或现行规范中注明当前基线已变更。

## 验证

在干净的 JDK 21 环境依次执行：

1. 确认 `java -version` 与 `mvn -version` 均报告 Java 21。
2. 运行 `cd backend && mvn test`，覆盖全部 Maven 模块。
3. 运行 `cd backend && mvn -pl finscope-web -am package -DskipTests`，确认可执行 Jar 成功生成。
4. 使用生成的 Jar 启动后端，检查应用完成启动和健康端点可访问；不得在日志中输出固定 API Key。
5. 构建后端 Docker 镜像，确认构建阶段和运行阶段均使用 Java 21。
6. 以低于 JDK 21 的 Maven 运行一次校验，确认 Enforcer 在编译前给出明确失败信息。

若环境无法访问外部服务，相关业务调用不作为迁移失败；但应用上下文、数据库初始化、核心测试和健康检查必须成功。

## 提交策略

改动从已有的 `codex/fund-holdings-detail` 最新提交创建独立分支。设计文档、构建与容器修改、文档同步分别形成可独立验证的 Conventional Commit，提交主题使用中文。每批提交后推送当前分支；若远程网络不可用，则保留本地提交并明确报告未推送原因。

## 风险与回滚

- **注解处理器不兼容：** 先升级 Lombok，再用全量编译验证。
- **反射或强封装差异：** 通过应用启动和集成测试识别，仅对明确失败点做局部修正，不添加宽泛的 `--add-opens`。
- **依赖运行时差异：** 优先保持版本不变；只有证据表明依赖不支持 JDK 21 时才升级。
- **部署环境漂移：** Maven Enforcer、Docker 基础镜像和文档共同固定基线。

如需回滚，可逐批撤销 Maven/Docker 修改并恢复 Java 8 构建；本次不包含数据库结构或业务数据变更。
