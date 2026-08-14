package com.finscope.dao.cache;

import com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot;
import com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot;

import java.util.Optional;

public interface GlobalExpectationsCacheRepository {
    Optional<GlobalExpectationHistorySnapshot> getHistory(String tokenId);

    void putHistory(GlobalExpectationHistorySnapshot snapshot);

    Optional<GlobalExpectationsViewSnapshot> getView();

    void putView(GlobalExpectationsViewSnapshot snapshot);
}
