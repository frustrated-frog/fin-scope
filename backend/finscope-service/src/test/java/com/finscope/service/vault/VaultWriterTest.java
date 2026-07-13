package com.finscope.service.vault;

import com.finscope.service.vault.VaultWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDailyBriefMarkdownIntoVault() throws Exception {
        VaultWriter writer = new VaultWriter(tempDir);

        Path written = writer.writeDailyBrief(LocalDate.of(2026, 6, 23), "# Brief");

        assertTrue(Files.exists(written));
        assertTrue(written.toString().endsWith("daily-briefs/2026-06-23.md"));
        assertEquals("# Brief", new String(Files.readAllBytes(written), "UTF-8"));
    }

    @Test
    void writesResearchReportOutsideDailyBriefDirectory() throws Exception {
        VaultWriter writer = new VaultWriter(tempDir);

        Path written = writer.writeResearchReport(3L, 14L, "# Research Report");

        assertTrue(written.toString().endsWith("research-reports/thesis-3/run-14.md"));
        assertEquals("# Research Report", new String(Files.readAllBytes(written), "UTF-8"));
        assertTrue(Files.notExists(tempDir.resolve("daily-briefs/2026-07-13.md")));
    }
}
