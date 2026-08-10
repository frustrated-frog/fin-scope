# 雷达线程池注入冲突故障记录

## 现象

应用启动时，Spring 在创建 `RadarHotspotProductionPipeline` 的依赖链时失败，提示需要注入一个 `Executor`，但容器中存在多个线程池 Bean（如 `radarAgentExecutor`、`radarRefreshExecutor` 等）。

## 根因

`RadarEventEnhancementScheduler` 依赖 `Executor` 执行后台增强任务。原来的注入没有指定 Bean 名称，Spring 无法在多个 `Executor` 类型 Bean 中确定应该使用哪一个，因而启动失败。

报错中显示的 `RadarHotspotProductionPipeline` 构造器第 6 个参数只是上层依赖链入口，实际冲突点在 `RadarEventEnhancementScheduler` 的 `Executor` 注入。

## 修复方式

为该依赖显式指定雷达 Agent 线程池：

```java
@Qualifier("radarAgentExecutor") Executor executor
```

这样标题规范化和证据增强任务会固定由 `AppConfig` 中的 `radarAgentExecutor` 执行，不会再与其他业务线程池产生装配歧义。

## 验证

`RadarEventEnhancementSchedulerTest` 已覆盖 Spring 容器按生产构造器创建该调度器，并注册 `radarAgentExecutor` 的场景。
