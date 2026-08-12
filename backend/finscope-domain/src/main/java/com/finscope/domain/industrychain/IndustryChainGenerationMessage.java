package com.finscope.domain.industrychain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** 请求生成或补全一个产业图谱修订的版本化事件。 */
@Data
public class IndustryChainGenerationMessage {
    public static final int CURRENT_VERSION = 1;
    public static final String REQUESTED = "INDUSTRY_CHAIN_STRUCTURE_COMPLETION_REQUESTED";

    private String eventId;
    private int eventVersion;
    private String eventType;
    private Long chainId;
    private Long revisionId;
    private LocalDateTime requestedAt;

    public static IndustryChainGenerationMessage requested(Long chainId, Long revisionId) {
        IndustryChainGenerationMessage message = new IndustryChainGenerationMessage();
        message.setEventId(UUID.randomUUID().toString());
        message.setEventVersion(CURRENT_VERSION);
        message.setEventType(REQUESTED);
        message.setChainId(chainId);
        message.setRevisionId(revisionId);
        message.setRequestedAt(LocalDateTime.now());
        return message;
    }

    public boolean isValid() {
        return eventId != null && !eventId.trim().isEmpty()
                && eventVersion == CURRENT_VERSION
                && REQUESTED.equals(eventType)
                && chainId != null && chainId > 0
                && revisionId != null && revisionId > 0
                && requestedAt != null;
    }
}
