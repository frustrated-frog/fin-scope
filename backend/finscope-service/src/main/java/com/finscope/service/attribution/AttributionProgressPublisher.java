package com.finscope.service.attribution;

/**
 * 归因进度发布器：service 层产出进度事件，由 web 层（SSE）实现订阅推送。
 * 以 taskId 为频道标识，实现发布订阅解耦。
 */
public interface AttributionProgressPublisher {

    /** 向指定任务频道推送一条进度事件。 */
    void publish(String taskId, AttributionProgressEvent event);

    /** 关闭指定任务频道（完成或失败后调用）。 */
    void complete(String taskId);
}