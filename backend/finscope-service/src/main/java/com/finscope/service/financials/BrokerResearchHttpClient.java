package com.finscope.service.financials;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public interface BrokerResearchHttpClient {
    Response get(URI uri, Map<String, String> headers, int maxBytes) throws IOException;

    final class Response {
        private final int status;
        private final String contentType;
        private final URI finalUri;
        private final byte[] body;

        public Response(int status, String contentType, URI finalUri, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.finalUri = finalUri;
            this.body = body;
        }

        public int getStatus() { return status; }
        public String getContentType() { return contentType; }
        public URI getFinalUri() { return finalUri; }
        public byte[] getBody() { return body; }
    }
}
