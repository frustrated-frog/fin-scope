package com.finscope.rpc.acquisition;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseTextDecoderTest {

    @Test
    void removesUtf8BomAndReportsDetectedCharset() {
        byte[] text = "研究正文".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(text, 0, bytes, 3, text.length);

        ResponseTextDecoder.DecodedText decoded = ResponseTextDecoder.decode(
                bytes, "text/html", true);

        assertEquals("研究正文", decoded.getText());
        assertEquals("UTF-8", decoded.getCharsetName());
    }

    @Test
    void honorsResponseHeaderCharset() {
        byte[] bytes = "宏观数据".getBytes(Charset.forName("GB18030"));

        ResponseTextDecoder.DecodedText decoded = ResponseTextDecoder.decode(
                bytes, "text/html; charset=gbk", true);

        assertEquals("宏观数据", decoded.getText());
        assertEquals("GB18030", decoded.getCharsetName());
    }

    @Test
    void normalizesHtmlMetaGb2312ToGb18030() {
        String html = "<html><head><meta charset=\"gb2312\"></head><body>基金公告</body></html>";
        byte[] bytes = html.getBytes(Charset.forName("GB18030"));

        ResponseTextDecoder.DecodedText decoded = ResponseTextDecoder.decode(
                bytes, "text/html", true);

        assertEquals(html, decoded.getText());
        assertEquals("GB18030", decoded.getCharsetName());
    }

    @Test
    void readsXmlDeclarationEncoding() {
        String xml = "<?xml version=\"1.0\" encoding=\"GBK\"?><rss><title>财经资讯</title></rss>";
        byte[] bytes = xml.getBytes(Charset.forName("GB18030"));

        ResponseTextDecoder.DecodedText decoded = ResponseTextDecoder.decode(
                bytes, "application/rss+xml", true);

        assertEquals(xml, decoded.getText());
        assertEquals("GB18030", decoded.getCharsetName());
    }
}
