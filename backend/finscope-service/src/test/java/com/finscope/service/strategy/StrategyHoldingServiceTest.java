package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.strategy.StrategyHolding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyHoldingServiceTest {
    private StrategyHoldingRepository repository;
    private StrategyInstrumentResolver resolver;
    private StrategyHoldingService service;

    @BeforeEach
    void setUp() {
        repository = mock(StrategyHoldingRepository.class);
        resolver = mock(StrategyInstrumentResolver.class);
        service = new StrategyHoldingService();
        ReflectionTestUtils.setField(service, "holdingRepository", repository);
        ReflectionTestUtils.setField(service, "instrumentResolver", resolver);
    }

    @Test
    void rejectsFundWithStockOnlyRole() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.add("020608", "FUND", "SIMULATED", 10, 0, ""));
        assertTrue(error.getMessage().contains("基金角色只能是核心、卫星、防守或观察"));
    }

    @Test
    void rejectsTargetWeightsAboveOneHundred() {
        Instrument instrument = new Instrument(); instrument.setId(1L); instrument.setCode("020608"); instrument.setType("FUND");
        when(resolver.resolve("020608", "FUND")).thenReturn(instrument);
        when(repository.sumTargetWeightExcluding(null)).thenReturn(91d);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.add("020608", "FUND", "CORE", 10, 0, ""));
        assertTrue(error.getMessage().contains("目标权重合计不能超过 100%"));
    }

    @Test
    void rejectsStaleUpdate() {
        StrategyHolding current = new StrategyHolding(); current.setId(8L); current.setType("FUND");
        when(repository.findById(8L)).thenReturn(java.util.Optional.of(current));
        when(repository.update(8L, "CORE", 60, 55, "", 2)).thenReturn(false);
        when(repository.sumTargetWeightExcluding(8L)).thenReturn(0d);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(8L, "CORE", 60, 55, "", 2));
        assertTrue(error.getMessage().contains("记录已被更新"));
    }
}
