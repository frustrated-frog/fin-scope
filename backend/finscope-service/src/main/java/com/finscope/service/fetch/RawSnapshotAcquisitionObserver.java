package com.finscope.service.fetch;

import com.finscope.rpc.acquisition.AcquisitionContext;
import com.finscope.rpc.acquisition.AcquisitionObserver;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import org.springframework.stereotype.Component;

@Component
public class RawSnapshotAcquisitionObserver implements AcquisitionObserver {
    private final RawSnapshotStore snapshotStore;

    public RawSnapshotAcquisitionObserver(RawSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    @Override
    public void onSuccess(AcquisitionRequest request, AcquisitionResponse response) {
        AcquisitionContext.Value context = AcquisitionContext.current().orElse(null);
        snapshotStore.save(request, response,
                context == null ? null : context.getFetchRunId(),
                context == null ? null : context.getSourceId());
    }
}
