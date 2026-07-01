package com.finscope.rpc.source;

import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

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
}
