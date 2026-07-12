package com.finscope.service.attribution;

/** Agent 上报真实研究过程，Harness 负责状态持久化。 */
public interface AttributionResearchProgressListener {
    AttributionResearchProgressListener NO_OP = new AttributionResearchProgressListener() { };

    default void stageStarted(String stage) { }
    default void trackStarted(AttributionResearchExecution.TrackResult result) { }
    default void trackUpdated(AttributionResearchExecution.TrackResult result) { }
    default void trackFinished(AttributionResearchExecution.TrackResult result) { }
}
