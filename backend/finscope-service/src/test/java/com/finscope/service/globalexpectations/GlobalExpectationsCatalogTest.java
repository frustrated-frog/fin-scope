package com.finscope.service.globalexpectations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExpectationsCatalogTest {
    private final GlobalExpectationsCatalog catalog = new GlobalExpectationsCatalog();

    @Test
    void exposesFiveOfficialPolymarketCategoriesInProductOrder() {
        List<GlobalExpectationsCatalog.Definition> definitions = catalog.definitions();

        assertEquals(List.of("政治", "财务", "地缘冲突", "科技", "经济"), definitions.stream()
                .map(GlobalExpectationsCatalog.Definition::getTheme)
                .collect(Collectors.toList()));
        assertEquals(List.of("politics", "finance", "geopolitics", "tech", "economy"), definitions.stream()
                .map(GlobalExpectationsCatalog.Definition::getCategorySlug)
                .collect(Collectors.toList()));
    }
}
