package com.finscope.service.globalexpectations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExpectationsCatalogTest {
    private final GlobalExpectationsCatalog catalog = new GlobalExpectationsCatalog();

    @Test
    void matchesKeywordsAsWordsInsteadOfSubstrings() {
        assertNull(catalog.match("Will Pierre Gasly be the 2026 F1 Drivers' Champion?"));
        assertEquals("能源资源", catalog.match("Will natural gas prices rise?").getTheme());
    }

    @Test
    void classifiesGlobalConflictsSeparatelyFromUsChinaRelations() {
        assertEquals("全球地缘", catalog.match("Putin out as President of Russia by December 31, 2026?").getTheme());
        assertEquals("中美关系", catalog.match("US x China tariff agreement by December 31?").getTheme());
    }
}
