package com.finscope.web.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationLlmConfigurationTest {
    @Test
    void usesTheDocumentedGlm5ConfigurationWithoutEnvironmentPlaceholders() throws Exception {
        String yaml = new String(Files.readAllBytes(
                Paths.get("src/main/resources/application.yml")), StandardCharsets.UTF_8);
        String llm = yaml.substring(yaml.indexOf("  llm:"), yaml.indexOf("  search:"));

        assertTrue(llm.contains("base-url: https://modelservice.jdcloud.com/v1"));
        assertTrue(llm.contains("model: GLM-5"));
        assertTrue(llm.contains("timeout-ms: 300000"));
        assertTrue(llm.contains("temperature: 0.2"));
        assertTrue(Pattern.compile("(?m)^    api-key: pk-[A-Za-z0-9-]{20,}\\s*$").matcher(llm).find());
        assertFalse(llm.contains("${"));
    }
}
