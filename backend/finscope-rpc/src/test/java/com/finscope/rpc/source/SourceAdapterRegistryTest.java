package com.finscope.rpc.source;

import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceAdapterRegistryTest {
    @Test
    void urlAwareAdapterWinsWhenManualUrlIsSavedAsWebType() {
        Source source = new Source();
        source.setType("WEB");
        source.setUrl("https://x.com/justloveabit/status/2069292114794762335");

        SourceAdapterRegistry registry = new SourceAdapterRegistry();
        ReflectionTestUtils.setField(registry, "adapters", Arrays.asList(
                new WebSourceAdapter(),
                new XPostSourceAdapter("http://localhost:1", "http://localhost:1")));

        assertTrue(registry.get(source) instanceof XPostSourceAdapter);
    }

    @Test
    void xAdapterStillWinsWhenManualUrlIsSavedAsXType() {
        Source source = new Source();
        source.setType("X");
        source.setUrl("https://x.com/justloveabit/status/2069292114794762335");

        SourceAdapterRegistry registry = new SourceAdapterRegistry();
        ReflectionTestUtils.setField(registry, "adapters", Arrays.asList(
                new WebSourceAdapter(),
                new XPostSourceAdapter("http://localhost:1", "http://localhost:1")));

        assertTrue(registry.get(source) instanceof XPostSourceAdapter);
    }

    @Test
    void knownArticleSourceFallsBackToWebAdapterWhenNoDedicatedAdapterExists() {
        Source source = new Source();
        source.setType("SINA_FINANCE");
        source.setUrl("https://finance.sina.com.cn/stock/marketresearch/2026-06-20/doc-example.shtml");

        WebSourceAdapter webSourceAdapter = new WebSourceAdapter();
        SourceAdapterRegistry registry = new SourceAdapterRegistry();
        ReflectionTestUtils.setField(registry, "adapters", Arrays.asList(
                webSourceAdapter,
                new XPostSourceAdapter("http://localhost:1", "http://localhost:1")));

        assertSame(webSourceAdapter, registry.get(source));
    }
}
