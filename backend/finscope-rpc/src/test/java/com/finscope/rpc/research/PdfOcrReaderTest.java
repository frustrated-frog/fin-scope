package com.finscope.rpc.research;

import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PdfOcrReaderTest {
    @Test
    void rejectsInvalidInputAndDisabledOcrWithoutLaunchingNativeProcess() {
        PdfOcrReader reader = new PdfOcrReader();
        assertThrows(IllegalArgumentException.class, () -> reader.read(new byte[0]));
        ReflectionTestUtils.setField(reader, "enabled", false);
        ProviderContractException error = assertThrows(ProviderContractException.class, () -> reader.read(new byte[]{1}));
        assertEquals("PDF_OCR_UNAVAILABLE", error.getErrorType());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    void exhaustedProviderBudgetDoesNotLaunchOcr() {
        try (var ignored = ProviderCallDeadline.open(Duration.ofNanos(1))) {
            ProviderContractException error = assertThrows(ProviderContractException.class,
                    () -> new PdfOcrReader().read(new byte[]{1}));
            assertEquals("TIMEOUT", error.getErrorType());
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "finscope.test.ocr", matches = "true")
    void readsTheActualPreviouslyFailingPublicAnnouncements() throws Exception {
        for (String[] sample : new String[][]{
                {"/tmp/AN202611121831662728.pdf", "湖南天雁机械股份有限公司"},
                {"/tmp/AN202611161831751311.pdf", "杰克科技股份有限公司"}}) {
            String text = new PdfOcrReader().read(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(sample[0])));
            assertTrue(text.replaceAll("\\s+", "").contains(sample[1]));
            assertTrue(text.length() > 1000);
        }
    }
}
