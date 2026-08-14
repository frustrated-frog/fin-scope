package com.finscope.service.globalexpectations;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 精选海外认知变化命题；关键词只用于从公共市场池中定位固定观察范围。 */
@Component
public class GlobalExpectationsCatalog {
    private final List<Definition> definitions = List.of(
            new Definition("科技供应链", List.of("chip", "semiconductor", "nvidia", "ai model", "export control", "taiwan"), "核验正式政策、限制范围与供应链实际传导。"),
            new Definition("中美关系", List.of("trade war", "sanction", "tariff", "iran", "russia", "ukraine", "nato"), "核验相关政策、外交表态与可确认的后续事实。"),
            new Definition("能源资源", List.of("oil", "crude", "gas", "opec", "energy", "uranium", "shipping"), "核验供给、航运、库存与主要产油国政策。"),
            new Definition("全球宏观", List.of("fed", "federal reserve", "inflation", "interest rate", "recession", "gdp"), "核验就业、通胀、流动性与央行正式表态。"),
            new Definition("中国相关", List.of("china", "chinese", "beijing", "hong kong", "yuan"), "核验双边政策、官方数据与实际执行进度。"));

    public Definition match(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (Definition definition : definitions) {
            for (String keyword : definition.keywords) {
                if (normalized.contains(keyword)) {
                    return definition;
                }
            }
        }
        return null;
    }

    public static final class Definition {
        private final String theme;
        private final List<String> keywords;
        private final String observation;

        private Definition(String theme, List<String> keywords, String observation) {
            this.theme = theme;
            this.keywords = keywords;
            this.observation = observation;
        }

        public String getTheme() {
            return theme;
        }

        public String getObservation() {
            return observation;
        }
    }
}
