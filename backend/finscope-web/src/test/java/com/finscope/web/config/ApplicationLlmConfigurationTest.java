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
    void loadsLlmKeyFromLocalHomeWithoutStartingTheApplication(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) throws Exception {
        java.nio.file.Path local = home.resolve(".config/finscope/llm.local.properties");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "finscope.llm.api-key=test-local-key\n");
        org.springframework.core.env.StandardEnvironment environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "local-config-test", java.util.Map.of("user.home", home.toString())));
        org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertTrue("test-local-key".equals(environment.getProperty("finscope.llm.api-key")));
    }

    @Test
    void usesConcreteOpenAiCompatibleConfigurationWithoutProviderCoupling() throws Exception {
        String yaml = new String(Files.readAllBytes(
                Paths.get("src/main/resources/application.yml")), StandardCharsets.UTF_8);
        String llm = yaml.substring(yaml.indexOf("  llm:"), yaml.indexOf("  search:"));

        assertTrue(Pattern.compile("(?m)^\\s+base-url: https?://\\S+$").matcher(llm).find());
        assertTrue(Pattern.compile("(?m)^\\s+model: \\S+$").matcher(llm).find());
        assertTrue(llm.contains("timeout-ms: 300000"));
        assertTrue(llm.contains("temperature: 0.2"));
        assertTrue(llm.contains("api-key: \"\""));
        assertFalse(llm.contains("${"));
    }
}
