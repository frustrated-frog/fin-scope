# FinScope Plan A Research Hardening Design

## Goal

在保留 `fin-scope` 模块化单体结构的前提下，把当前“已跑通但仍偏原型”的事件研究系统升级为可长期维护的高质量实现。优先补齐 TRD 明确要求但尚未落地的主干能力，然后补生命周期闭环，最后做结构治理，避免继续把功能堆进单个大文件或弱约束接口里。

## Success Criteria

- 研究系统具备主题驱动研究运行主干：
  - `ThemeProfile`
  - `SourceProfile`
  - `SourcePlanner`
  - `ResearchRun`
  - `ResearchController`
- 可以按主题生成一次研究运行结果，而不只是被动等文章入库后做事件归并。
- 学习任务和内容选题具备完整状态闭环：
  - 请求 DTO 明确
  - 状态值校验明确
  - 前端可操作，不只是只读展示
- 本轮触达的 Spring 代码统一为构造注入，不保留字段注入混用。
- 前端不再继续把所有页面、状态、API、解析逻辑堆在 `App.tsx` 中；至少拆到 feature 级模块。
- 关键行为有测试覆盖，且最终以完整验证结果收尾。

## Non-Goals

- 不引入 MQ、Redis、向量库、工作流引擎或分布式调度。
- 不重做整个 UI 风格系统，只做保证可维护性的结构拆分和必要交互补强。
- 不在本轮引入复杂的 LLM 运行时依赖；研究运行仍以确定性规则和现有抓取链路为主。

## Architecture Decisions

### 1. Keep the modular monolith, but finish the missing research backbone

当前后端 Maven 分层方向是对的，问题不在“有没有模块”，而在研究域对象没有真正补齐。为此新增以下研究主干对象：

- `ThemeProfile`
  - 描述研究主题代码、名称、简报分区、来源偏好、创作偏好。
- `SourceProfile`
  - 作为 `Source` 面向研究编排的一层稳定视图，避免把研究调度逻辑直接塞进现有 `Source` 实体判断中。
- `SourcePlanner`
  - 输入日期、主题集合、来源数量限制，输出本次研究应抓取的来源集。
- `ResearchRun`
  - 记录一次研究运行的输入、筛选后的来源、结果摘要、错误信息和状态。
- `ResearchController`
  - 暴露研究运行入口和查询接口。

这些对象都落在现有 `domain / dao / service / web` 四层中，不跨层偷逻辑。

### 2. Unify dependency injection and touched-file code style

本轮涉及修改或新建的 Spring Bean 全部采用构造注入。原则：

- 不使用 `@Resource` / `@Autowired` 字段注入。
- Controller 只依赖 Service。
- Service 只依赖 Repository / RPC / 小算法组件。
- Repository 只写 SQL 和映射。
- 不在 Controller 中做状态校验分支，不在 Repository 中写业务状态机。

对于本轮直接修改到的旧类，也同步改成构造注入，避免新旧风格继续混杂。

### 3. Replace loose request shapes with explicit DTOs and validated state transitions

当前学习任务和内容选题更新接口直接接收 `Map<String, String>`，状态值可任意入库，这不符合长期维护要求。为此新增：

- `UpdateLearningTaskStatusRequest`
- `UpdateContentIdeaStatusRequest`

并在 Service 层增加：

- 枚举白名单校验
- 目标记录不存在时的明确异常
- 非法状态时的明确异常

前端相应改为显式下拉或按钮操作，而不是只读卡片。

### 4. Frontend split by feature, not by technical trivia

当前 `App.tsx` 既是壳层，又是 API 层，又是视图层，又是 Markdown 解析层，已经超过单文件可维护范围。本轮拆分为：

- `src/app/`
  - 顶层壳层、导航、共享消息状态
- `src/shared/`
  - 通用类型、API client、通用工具、共享展示组件
- `src/features/briefs/`
- `src/features/events/`
- `src/features/learning/`
- `src/features/content-studio/`
- `src/features/articles/`
- `src/features/topics/`
- `src/features/sources/`

拆分目标不是“拆得多”，而是做到：

- 一个 feature 的状态和交互只在自己的目录里维护
- `App.tsx` 只负责路由视图装配和全局壳层
- Markdown 解析、表格、卡片等共享组件不再嵌在页面文件底部

### 5. Scope of functional hardening in this round

本轮优先做这三条高价值闭环：

- 研究运行主干可用
- 学习任务 / 内容选题可查询、可更新、可校验
- 事件页 / 简报页 / 学习页 / 选题页具备真正可操作的体验

事件级 `InsightCard` 仍然是明确目标，但不作为本轮第一优先级阻塞项。若在实现主干与结构治理后还有空间，再继续把 `insight_card` 从纯 `article_id` 扩到可选 `event_id`。

## Data Flow

### Research run

`ResearchController`
-> `ResearchService`
-> `ThemeProfileService`
-> `SourcePlanner`
-> `SourceRepository` / `SourceProfileMapper`
-> `FetchService` or existing source fetch orchestration
-> article ingest mainline
-> event attach
-> evidence / learning / content generation
-> `ResearchRunRepository`

### Learning/content lifecycle

frontend action
-> typed REST request DTO
-> controller
-> validated service transition
-> repository update
-> refreshed list/detail view

## Testing Strategy

- 研究主干：
  - service 单测覆盖 `SourcePlanner`
  - web 集成测试覆盖研究运行入口
- 生命周期闭环：
  - 非法状态请求
  - 合法状态更新
  - 前端交互测试
- 结构治理：
  - 拆分后保持现有前端回归测试通过
  - 后端完整集成测试重跑

## Risks And Mitigations

- 风险：边补主干边重构前端，改动面大
  - 处理：按阶段推进，阶段间都有可运行验证
- 风险：研究运行若强行复用现有抓取链路，容易把 service 写胖
  - 处理：把“研究运行编排”单独收敛到 `ResearchService`
- 风险：前端拆分时功能回归
  - 处理：每拆完一个 feature 立即跑对应测试

## Implementation Order

1. 先补 `ThemeProfile / SourceProfile / SourcePlanner / ResearchRun / ResearchController`
2. 再补学习任务和内容选题的状态机、DTO、前端交互
3. 然后统一本轮触达后端类的构造注入风格
4. 最后拆分 `App.tsx` 并跑完整验证
