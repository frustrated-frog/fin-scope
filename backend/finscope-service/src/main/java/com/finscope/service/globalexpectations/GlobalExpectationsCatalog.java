package com.finscope.service.globalexpectations;

import org.springframework.stereotype.Component;

import java.util.List;

/** 使用 Polymarket 官方标签定义稳定观察分类。 */
@Component
public class GlobalExpectationsCatalog {
    private final List<Definition> definitions = List.of(
            new Definition("政治", "politics", "核验选举、立法、政府人事与正式政策进展。"),
            new Definition("财务", "finance", "核验公司、资产价格、融资与监管披露。"),
            new Definition("地缘冲突", "geopolitics", "核验冲突进展、正式声明及对能源与供应链的潜在传导。"),
            new Definition("科技", "tech", "核验技术发布、产业政策、限制范围与供应链实际传导。"),
            new Definition("经济", "economy", "核验就业、通胀、增长、流动性与央行正式表态。"));

    public List<Definition> definitions() {
        return definitions;
    }

    public static final class Definition {
        private final String theme;
        private final String categorySlug;
        private final String observation;

        private Definition(String theme, String categorySlug, String observation) {
            this.theme = theme;
            this.categorySlug = categorySlug;
            this.observation = observation;
        }

        public String getTheme() {
            return theme;
        }

        public String getCategorySlug() {
            return categorySlug;
        }

        public String getObservation() {
            return observation;
        }
    }
}
