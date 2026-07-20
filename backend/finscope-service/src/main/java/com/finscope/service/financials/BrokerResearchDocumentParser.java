package com.finscope.service.financials;

import lombok.Data;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;

@Component
public class BrokerResearchDocumentParser {
    private static final int DEFAULT_MAX_PAGES = 500;
    private static final int DEFAULT_MAX_CHARS = 2_000_000;
    private final int maxPages;
    private final int maxChars;

    public BrokerResearchDocumentParser() {
        this(DEFAULT_MAX_PAGES, DEFAULT_MAX_CHARS);
    }

    BrokerResearchDocumentParser(int maxPages, int maxChars) {
        this.maxPages = maxPages;
        this.maxChars = maxChars;
    }

    public ParsedDocument parse(byte[] content) throws IOException {
        ParsedDocument result = new ParsedDocument();
        try (PDDocument document = Loader.loadPDF(content)) {
            result.setPageCount(document.getNumberOfPages());
            if (document.getNumberOfPages() > maxPages) {
                throw new DocumentLimitException("研报页数不能超过 " + maxPages + " 页");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            BoundedWriter writer = new BoundedWriter(maxChars);
            stripper.writeText(document, writer);
            String text = writer.text().replace('\u0000', ' ').trim();
            result.setText(text);
            result.setParseStatus(writer.isTruncated() ? "PARSED_TRUNCATED"
                    : text.length() < 20 ? "OCR_REQUIRED" : "PARSED");
        }
        return result;
    }

    static class DocumentLimitException extends IOException {
        DocumentLimitException(String message) { super(message); }
    }

    private static final class BoundedWriter extends Writer {
        private final int limit;
        private final StringBuilder value = new StringBuilder();
        private boolean truncated;

        private BoundedWriter(int limit) { this.limit = limit; }

        @Override
        public void write(char[] characters, int offset, int length) {
            int remaining = limit - value.length();
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            int accepted = Math.min(remaining, length);
            value.append(characters, offset, accepted);
            if (accepted < length) truncated = true;
        }

        @Override public void flush() { }
        @Override public void close() { }
        private String text() { return value.toString(); }
        private boolean isTruncated() { return truncated; }
    }

    @Data
    public static class ParsedDocument {
        private String text = "";
        private Integer pageCount;
        private String parseStatus;
    }
}
