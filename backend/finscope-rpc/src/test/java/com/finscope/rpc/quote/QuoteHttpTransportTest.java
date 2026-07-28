package com.finscope.rpc.quote;

import com.finscope.rpc.acquisition.JdkAcquisitionRuntime;
import com.finscope.rpc.acquisition.RecordingAcquisitionRuntime;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuoteHttpTransportTest {
    @Test
    void usesUnifiedRuntimeWithoutTransportRetriesAndHonorsProviderCharset() throws Exception {
        Charset gbk = Charset.forName("GBK");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quote", exchange -> {
            byte[] body = "贵州茅台".getBytes(gbk);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RecordingAcquisitionRuntime runtime = new RecordingAcquisitionRuntime(new JdkAcquisitionRuntime());
            QuoteHttpTransport transport = new QuoteHttpTransport(runtime);
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/quote");

            String body = transport.get("SINA_STOCK", uri, 1000, 1024,
                    Collections.singletonMap("Referer", "https://finance.sina.com.cn"), gbk);

            assertEquals("贵州茅台", body);
            assertEquals(1, runtime.getRequests().size());
            assertEquals("MARKET_PROVIDER:SINA_STOCK", runtime.getRequests().get(0).getPurpose());
            assertEquals(0, runtime.getRequests().get(0).getMaxRetries());
            assertEquals("https://finance.sina.com.cn",
                    runtime.getRequests().get(0).getHeaders().get("Referer"));
        } finally {
            server.stop(0);
        }
    }
}
