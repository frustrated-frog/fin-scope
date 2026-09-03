package com.finscope.dao.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "finscope.ephemeral-content")
public class EphemeralContentCacheProperties {
    private int ttlHours = 36;
}
