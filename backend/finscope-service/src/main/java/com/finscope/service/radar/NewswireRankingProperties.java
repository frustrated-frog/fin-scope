package com.finscope.service.radar;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Data
@Component
@ConfigurationProperties(prefix = "finscope.newswire-ranking")
public class NewswireRankingProperties {
    public static final int DEFAULT_PERSONAL_BONUS = 12;
    private double importanceWeight = 0.45;
    private double noveltyWeight = 0.20;
    private double heatWeight = 0.20;
    private double freshnessWeight = 0.15;
    private int maxPersonalBonus = DEFAULT_PERSONAL_BONUS;

    @PostConstruct
    public void validate() {
        double sum = importanceWeight + noveltyWeight + heatWeight + freshnessWeight;
        if (!Double.isFinite(sum) || Math.abs(sum - 1) > 0.000001
                || importanceWeight < 0 || noveltyWeight < 0 || heatWeight < 0 || freshnessWeight < 0
                || maxPersonalBonus < 0 || maxPersonalBonus > 20) {
            throw new IllegalArgumentException("Newswire 评分权重须非负且和为 1，个人加分须在 0 至 20 之间");
        }
    }
}
