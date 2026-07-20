package com.finscope.service.financials;

import com.finscope.dao.financials.FinancialDocumentRepository;
import com.finscope.domain.financials.FinancialDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialDocumentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesPdfByHashAndExtractsPageText() throws Exception {
        FinancialDocumentRepository repository = mock(FinancialDocumentRepository.class);
        when(repository.findByHash(any())).thenReturn(java.util.Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            FinancialDocument value = invocation.getArgument(0);
            value.setId(11L);
            return value;
        });
        FinancialDocumentService service = new FinancialDocumentService(repository, tempDir);
        byte[] pdf = pdf("Revenue increased and cash flow improved.");

        FinancialDocument stored = service.store(
                7L, 9L, "2026-half-year.pdf",
                new ByteArrayInputStream(pdf), pdf.length);

        assertEquals(1, stored.getPageCount().intValue());
        assertEquals("PARSED", stored.getParseStatus());
        assertTrue(stored.getExtractedText().contains("Revenue increased"));
        assertTrue(Files.exists(tempDir.resolve(stored.getRelativePath())));
    }

    private byte[] pdf(String text) {
        String value = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj\n"
                + "4 0 obj << /Length 80 >> stream\n"
                + "BT /F1 12 Tf 72 720 Td (" + text + ") Tj ET\n"
                + "endstream endobj\n%%EOF";
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
