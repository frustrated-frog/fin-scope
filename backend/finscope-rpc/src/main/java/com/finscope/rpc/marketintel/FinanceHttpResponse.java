package com.finscope.rpc.marketintel;

import java.time.Instant;

public class FinanceHttpResponse {
    private final int status;
    private final String body;
    private final Instant retrievedAt;
    private final String payloadHash;
    public FinanceHttpResponse(int status, String body, Instant retrievedAt, String payloadHash) {
        this.status=status; this.body=body; this.retrievedAt=retrievedAt; this.payloadHash=payloadHash;
    }
    public int getStatus(){return status;} public String getBody(){return body;}
    public Instant getRetrievedAt(){return retrievedAt;} public String getPayloadHash(){return payloadHash;}
}
