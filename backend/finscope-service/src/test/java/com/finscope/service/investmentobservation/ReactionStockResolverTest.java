package com.finscope.service.investmentobservation;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.rpc.investmentobservation.ReactionStockNameLookup;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactionStockResolverTest {
    @Test
    void rulesRejectCommentatorsAndResolveEverySubjectWithoutAnyModelDependency() {
        var resolver = new ReactionStockResolver();
        var instruments = mock(InstrumentRepository.class);
        var names = mock(ReactionStockNameLookup.class);
        ReflectionTestUtils.setField(resolver, "instruments", instruments);
        ReflectionTestUtils.setField(resolver, "names", names);
        Instrument local = new Instrument();
        local.setType("STOCK");
        local.setName("贵州茅台");
        local.setCode("600519");
        local.setMarket("SH");
        when(instruments.findAll()).thenReturn(List.of(local));
        ReactionStockMatch remote = new ReactionStockMatch();
        remote.setCode("300476");
        remote.setName("胜宏科技");
        when(names.search("胜宏科技")).thenReturn(List.of(remote));
        assertTrue(resolver.resolve("贵州茅台：看好胜宏科技业绩").isEmpty());
        assertEquals(2, resolver.resolve("贵州茅台与胜宏科技签订合同").size());
        assertTrue(java.util.Arrays.stream(ReactionStockResolver.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().contains("Llm")));
    }
}
