package com.finscope.domain.industrychain;

/** 将已持久化的产业图谱生成任务派发到异步执行通道。 */
public interface IndustryChainGenerationPublisher {
    boolean publish(IndustryChainGenerationMessage message);
}
