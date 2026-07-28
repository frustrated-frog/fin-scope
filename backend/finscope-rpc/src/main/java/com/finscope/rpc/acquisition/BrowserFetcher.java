package com.finscope.rpc.acquisition;

@FunctionalInterface
public interface BrowserFetcher {
    AcquisitionResponse fetch(AcquisitionRequest request);
}
