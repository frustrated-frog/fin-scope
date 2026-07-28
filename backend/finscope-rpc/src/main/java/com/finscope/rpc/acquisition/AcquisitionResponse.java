package com.finscope.rpc.acquisition;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AcquisitionResponse {
    private final URI requestUri;
    private final URI finalUri;
    private final int httpStatus;
    private final Map<String, String> headers;
    private final byte[] bodyBytes;
    private final String bodyText;
    private final String contentType;
    private final String charsetName;
    private final String bodySha256;
    private final int attemptCount;
    private final long durationMs;
    private final Instant fetchedAt;

    public AcquisitionResponse(URI requestUri, URI finalUri, int httpStatus,
                               Map<String, String> headers, byte[] bodyBytes, String bodyText,
                               String contentType, String charsetName, String bodySha256,
                               int attemptCount, long durationMs, Instant fetchedAt) {
        this.requestUri = requestUri;
        this.finalUri = finalUri;
        this.httpStatus = httpStatus;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
        this.bodyBytes = bodyBytes.clone();
        this.bodyText = bodyText;
        this.contentType = contentType;
        this.charsetName = charsetName;
        this.bodySha256 = bodySha256;
        this.attemptCount = attemptCount;
        this.durationMs = durationMs;
        this.fetchedAt = fetchedAt;
    }

    public URI getRequestUri() { return requestUri; }
    public URI getFinalUri() { return finalUri; }
    public int getHttpStatus() { return httpStatus; }
    public Map<String, String> getHeaders() { return headers; }
    public byte[] getBodyBytes() { return bodyBytes.clone(); }
    public String getBodyText() { return bodyText; }
    public String getContentType() { return contentType; }
    public String getCharsetName() { return charsetName; }
    public String getBodySha256() { return bodySha256; }
    public int getAttemptCount() { return attemptCount; }
    public long getDurationMs() { return durationMs; }
    public Instant getFetchedAt() { return fetchedAt; }
}
