package com.finscope.service.financials;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerResearchDocumentParserTest {
    @Test
    void extractsTextAndPageCountFromARealPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Detailed research report learning material");
                content.endText();
            }
            document.save(output);
        }

        BrokerResearchDocumentParser.ParsedDocument parsed =
                new BrokerResearchDocumentParser().parse(output.toByteArray());

        assertEquals(1, parsed.getPageCount());
        assertEquals("PARSED", parsed.getParseStatus());
        assertTrue(parsed.getText().contains("Detailed research report"));
    }

    @Test
    void truncatesExtractedTextAtTheConfiguredSafetyLimit() throws Exception {
        byte[] pdf = pdfWithText("Detailed research report learning material");

        BrokerResearchDocumentParser.ParsedDocument parsed =
                new BrokerResearchDocumentParser(10, 20).parse(pdf);

        assertEquals(20, parsed.getText().length());
        assertEquals("PARSED_TRUNCATED", parsed.getParseStatus());
    }

    @Test
    void rejectsDocumentsAboveTheConfiguredPageLimit() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(output);
        }

        assertThrows(BrokerResearchDocumentParser.DocumentLimitException.class,
                () -> new BrokerResearchDocumentParser(1, 100).parse(output.toByteArray()));
    }

    private byte[] pdfWithText(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(output);
        }
        return output.toByteArray();
    }
}
