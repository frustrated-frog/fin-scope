package com.finscope.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/quant-context-test/finance.db",
        "finscope.data-root=target/quant-context-test",
        "finscope.search.enabled=false",
        "finscope.llm.enabled=false"
})
class QuantPlatformContextTest {
    @Test
    void applicationContextLoadsWithQuantPlatformBeans() {
    }
}
