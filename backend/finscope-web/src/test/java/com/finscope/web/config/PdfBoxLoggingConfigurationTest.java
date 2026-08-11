package com.finscope.web.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxLoggingConfigurationTest {
    @Test
    void suppressesRecoverableDamagedEmbeddedFontWarnings() throws Exception {
        String yaml = new String(Files.readAllBytes(
                Paths.get("src/main/resources/application.yml")), StandardCharsets.UTF_8);
        String logging = yaml.substring(yaml.indexOf("logging:"), yaml.indexOf("\nmanagement:"));

        assertTrue(logging.contains("org.apache.pdfbox.pdmodel.font.PDCIDFontType2: ERROR"));
    }
}
