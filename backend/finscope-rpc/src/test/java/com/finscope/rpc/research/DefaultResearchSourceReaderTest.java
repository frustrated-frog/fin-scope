package com.finscope.rpc.research;

import com.finscope.domain.research.ResearchSourceDocument;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultResearchSourceReaderTest {

    @Test
    void extractsReadableHtmlWithAcquisitionMetadata() {
        String html = "<html><head><title>公告标题</title></head><body><article><h1>公告标题</h1>"
                + "<p>募集资金将用于研发和产能建设，重点支持先进制程、核心设备验证和技术平台升级，"
                + "项目建设周期及市场价格波动风险已在公告中同步披露。</p></article></body></html>";
        DefaultResearchSourceReader reader = reader(runtime(
                html.getBytes(StandardCharsets.UTF_8), html, "text/html;charset=UTF-8"));

        ResearchSourceDocument document = reader.read("https://example.com/notice");

        assertEquals("FETCHED", document.getFetchStatus());
        assertEquals("WEB_PAGE", document.getContentType());
        assertTrue(document.getBody().contains("募集资金将用于研发和产能建设"));
        assertTrue(document.getExtractionMethod().startsWith("web:"));
    }

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf = pdf("Revenue grew by 18 percent in 2025.");
        DefaultResearchSourceReader reader = reader(runtime(
                pdf, "", "application/pdf"));

        ResearchSourceDocument document = reader.read("https://example.com/report.pdf");

        assertEquals("PDF", document.getContentType());
        assertEquals("pdf:pdfbox", document.getExtractionMethod());
        assertTrue(document.getBody().contains("Revenue grew by 18 percent"));
    }

    @Test
    void rejectsPrivateNetworkUrlsBeforeCallingRuntime() {
        DefaultResearchSourceReader reader = reader(request -> {
            throw new AssertionError("私网 URL 不应发起请求");
        });

        assertThrows(IllegalArgumentException.class, () -> reader.read("http://127.0.0.1/admin"));
    }

    @Test
    void disablesAutomaticRedirectsToPreventPublicToPrivateUrlHops() {
        AtomicReference<com.finscope.rpc.acquisition.AcquisitionRequest> captured = new AtomicReference<>();
        String html = "<article>这是一份足够长的公开公司公告正文，用于验证研究材料读取请求不会自动跟随到未经校验的新地址。</article>";
        DefaultResearchSourceReader reader = reader(request -> {
            captured.set(request);
            return runtime(html.getBytes(StandardCharsets.UTF_8), html, "text/html").fetch(request);
        });

        reader.read("https://example.com/notice");

        assertFalse(captured.get().isFollowRedirects());
    }

    private DefaultResearchSourceReader reader(AcquisitionRuntime runtime) {
        DefaultResearchSourceReader reader = new DefaultResearchSourceReader();
        org.springframework.test.util.ReflectionTestUtils.setField(reader, "acquisitionRuntime", runtime);
        org.springframework.test.util.ReflectionTestUtils.setField(reader, "pdfOcrReader", org.mockito.Mockito.mock(PdfOcrReader.class));
        return reader;
    }

    @Test
    void fallsBackToOcrForImagePdfAndPreservesPdfIdentity() throws Exception {
        DefaultResearchSourceReader reader = reader(runtime(pdf(""), "", "application/pdf"));
        PdfOcrReader ocr = org.mockito.Mockito.mock(PdfOcrReader.class);
        org.mockito.Mockito.when(ocr.read(org.mockito.ArgumentMatchers.any())).thenReturn("证券代码：600698 公司回购股份的完整公告正文，识别结果需要继续核验发行人。");
        org.springframework.test.util.ReflectionTestUtils.setField(reader, "pdfOcrReader", ocr);
        ResearchSourceDocument result = reader.read("https://example.com/report.pdf");
        assertEquals("pdf:vision-ocr", result.getExtractionMethod());
        assertEquals("PDF", result.getContentType());
        assertEquals("https://example.com/report.pdf", result.getFinalUrl());
        assertTrue(result.getBody().contains("600698"));
    }

    private AcquisitionRuntime runtime(byte[] bytes, String text, String contentType) {
        return request -> new AcquisitionResponse(request.getUri(), URI.create(request.getUri().toString()), 200,
                Collections.<String, String>emptyMap(), bytes, text, contentType, "UTF-8", "hash", 1, 5,
                Instant.parse("2026-07-29T00:00:00Z"));
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
