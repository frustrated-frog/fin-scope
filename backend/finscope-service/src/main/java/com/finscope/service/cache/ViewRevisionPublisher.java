package com.finscope.service.cache;

/** Web 层将生产完成版本广播给当前在线的页面。 */
public interface ViewRevisionPublisher {
    void publish(ViewRevision revision);
}
