package com.finscope.rpc.acquisition;

@FunctionalInterface
public interface AcquisitionObserver {
    void onSuccess(AcquisitionRequest request, AcquisitionResponse response);
}
