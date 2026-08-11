# PDFBox Damaged Font Warning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop recoverable `PDCIDFontType2` damaged-font warnings from printing stack traces while preserving PDFBox extraction and application-level failures.

**Architecture:** Keep PDF parsing unchanged and configure Spring Boot logging at the application boundary. A focused configuration test loads the real YAML through Spring Boot's YAML loader and locks the exact PDFBox logger level to `ERROR`.

**Tech Stack:** Java 21, Spring Boot 2.7, JUnit 5, Maven, YAML

---

### Task 1: Lock the PDFBox logger level with TDD

**Files:**
- Create: `backend/finscope-web/src/test/java/com/finscope/web/config/PdfBoxLoggingConfigurationTest.java`
- Modify: `backend/finscope-web/src/main/resources/application.yml:21-26`

- [ ] **Step 1: Write the failing configuration test**

```java
package com.finscope.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfBoxLoggingConfigurationTest {
    private static final String LOGGER_LEVEL_PROPERTY =
            "logging.level.org.apache.pdfbox.pdmodel.font.PDCIDFontType2";

    @Test
    void suppressesRecoverableDamagedEmbeddedFontWarnings() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        Object level = sources.stream()
                .map(source -> source.getProperty(LOGGER_LEVEL_PROPERTY))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertEquals("ERROR", level);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd backend && mvn -pl finscope-web -am -Dtest=PdfBoxLoggingConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the logger property is absent and the actual value is `null`.

- [ ] **Step 3: Add the minimal logger configuration**

Extend the existing `logging` block without changing its charset or pattern:

```yaml
logging:
  level:
    org.apache.pdfbox.pdmodel.font.PDCIDFontType2: ERROR
  charset:
    console: UTF-8
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
cd backend && mvn -pl finscope-web -am -Dtest=PdfBoxLoggingConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with zero test failures.

- [ ] **Step 5: Run module and backend regression verification**

Run:

```bash
cd backend && mvn -pl finscope-web -am test
cd backend && mvn test
```

Expected: both commands exit 0 with zero test failures.

- [ ] **Step 6: Review and commit the implementation**

```bash
git diff --check
git diff -- backend/finscope-web/src/main/resources/application.yml \
  backend/finscope-web/src/test/java/com/finscope/web/config/PdfBoxLoggingConfigurationTest.java
git add backend/finscope-web/src/main/resources/application.yml \
  backend/finscope-web/src/test/java/com/finscope/web/config/PdfBoxLoggingConfigurationTest.java
git commit -m "fix: 收敛PDFBox损坏字体告警"
git push
```
