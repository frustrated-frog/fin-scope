package com.finscope.rpc.provider;

import java.time.Duration;

/** 外部数据源通用运行元数据；领域能力和返回类型由具体 Provider 接口定义。 */
public interface ExternalDataProvider {
    String providerCode();
    String providerFamily();
    int priority();
    int batchLimit();
    Duration minimumInterval();
    Duration timeout();
    default boolean isTerminalFallback() { return false; }
}
