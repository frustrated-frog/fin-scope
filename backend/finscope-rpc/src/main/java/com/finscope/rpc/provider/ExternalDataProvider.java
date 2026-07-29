package com.finscope.rpc.provider;

import java.time.Duration;

/** 外部数据源通用运行元数据；领域能力和返回类型由具体 Provider 接口定义。 */
public interface ExternalDataProvider {
    String providerCode();
    String providerFamily();
    /**
     * 可靠性故障域。默认与厂商家族一致；同厂商相互独立的端点可覆盖此值，
     * 同时仍通过 providerFamily 共享厂商级限流与数据来源标记。
     */
    default String reliabilityFamily() { return providerFamily(); }
    int priority();
    int batchLimit();
    Duration minimumInterval();
    Duration timeout();
    default boolean isTerminalFallback() { return false; }
}
