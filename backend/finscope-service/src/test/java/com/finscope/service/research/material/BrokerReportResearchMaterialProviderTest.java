package com.finscope.service.research.material;

import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.financials.BrokerResearchSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerReportResearchMaterialProviderTest {
    @Test
    void reusesExistingBrokerResearchSourceWithoutDownloadingAnotherCatalog() {
        BrokerResearchSource source = mock(BrokerResearchSource.class);
        BrokerResearchCandidate candidate = new BrokerResearchCandidate();
        candidate.setSourceCode("EASTMONEY"); candidate.setExternalId("r1"); candidate.setStockCode("000001");
        candidate.setTitle("盈利预测上调"); candidate.setInstitution("示例证券"); candidate.setRating("增持");
        candidate.setPublishedDate(LocalDate.of(2026, 7, 20));
        candidate.setSourceUrl("https://pdf.dfcfw.com/pdf/H3_r1_1.pdf");
        when(source.sourceCode()).thenReturn("EASTMONEY");
        when(source.list(eq("000001"), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(Collections.singletonList(candidate));
        BrokerReportResearchMaterialProvider provider = new BrokerReportResearchMaterialProvider(source);

        List<ResearchMaterial> result = provider.fetch(ResearchMaterialType.BROKER_REPORT,
                new ResearchMaterialRequest("000001", "行业 盈利", 10)).getData();

        assertEquals(1, result.size());
        assertEquals("EASTMONEY", result.get(0).getProviderCode());
        assertEquals("T2", result.get(0).getSourceTier());
        assertEquals("盈利预测上调", result.get(0).getTitle());
    }
}
