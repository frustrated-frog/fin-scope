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
    @Test
    void bodySubjectAliasesAndMissingProviderHaveDistinctOutcomes() {
        var resolver = new ReactionStockResolver();
        var instruments = mock(InstrumentRepository.class);
        var names = mock(ReactionStockNameLookup.class);
        ReflectionTestUtils.setField(resolver, "instruments", instruments);
        ReflectionTestUtils.setField(resolver, "names", names);
        Instrument local = new Instrument();
        local.setType("STOCK");
        local.setName("示例设备");
        local.setAliases("示例设备股份有限公司,旧设备名称");
        local.setCode("600519");
        local.setMarket("SH");
        when(instruments.findAll()).thenReturn(List.of(local));
        var body = resolver.resolve("最新公告", "示例设备股份有限公司签订重大合同。客户提到了其他上市公司。");
        assertEquals(1, body.getMatches().size());
        assertEquals("600519.SH", body.getMatches().get(0).getCode());
        assertEquals(com.finscope.common.enums.investmentobservation.ReactionResolutionStatus.NO_SUBJECT,
                resolver.resolve("市场概览", "背景介绍中提及示例设备。").getStatus());
        Instrument ambiguous = new Instrument();
        ambiguous.setType("STOCK");
        ambiguous.setName("示例设备");
        ambiguous.setCode("600520");
        ambiguous.setMarket("SH");
        when(instruments.findAll()).thenReturn(List.of(local, ambiguous));
        var uncertain = resolver.resolve("示例设备签订合同", null);
        assertEquals(com.finscope.common.enums.investmentobservation.ReactionResolutionStatus.AMBIGUOUS, uncertain.getStatus());
        assertTrue(uncertain.getMatches().isEmpty());
        assertEquals(2, uncertain.getCandidates().size());
        when(names.search("另一公司")).thenThrow(new IllegalStateException("offline"));
        assertEquals(com.finscope.common.enums.investmentobservation.ReactionResolutionStatus.LOOKUP_UNAVAILABLE,
                resolver.resolve("另一公司签订重大合同", null).getStatus());
    }

}
