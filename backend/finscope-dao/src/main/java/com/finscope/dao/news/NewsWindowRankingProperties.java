package com.finscope.dao.news;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "finscope.news-window-ranking")
public class NewsWindowRankingProperties {
    private int secondSource = 12;
    private int thirdSource = 6;
    private int fourthSource = 3;
    private int rapidMinutes = 60;
    private int rapidBonus = 4;
    private int progressBonus = 4;

    @PostConstruct
    public void validate() {
        if (secondSource < thirdSource || thirdSource < fourthSource || fourthSource < 0
                || secondSource > 100 || rapidMinutes < 1 || rapidMinutes > 1440
                || rapidBonus < 0 || progressBonus < 0 || rapidBonus > 100 || progressBonus > 100) {
            throw new IllegalArgumentException("实时资讯来源加分必须非负、逐级递减，集中报道窗口为1至1440分钟");
        }
    }

    List<Integer> parameters() {
        return List.of(secondSource, thirdSource, fourthSource, rapidMinutes, rapidBonus, progressBonus);
    }
}
