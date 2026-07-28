package com.finscope.rpc.acquisition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingAcquisitionRuntime implements AcquisitionRuntime {
    private final AcquisitionRuntime delegate;
    private final List<AcquisitionRequest> requests = new ArrayList<AcquisitionRequest>();

    public RecordingAcquisitionRuntime(AcquisitionRuntime delegate) {
        this.delegate = delegate;
    }

    @Override
    public AcquisitionResponse fetch(AcquisitionRequest request) {
        requests.add(request);
        return delegate.fetch(request);
    }

    public List<AcquisitionRequest> getRequests() {
        return Collections.unmodifiableList(requests);
    }
}
