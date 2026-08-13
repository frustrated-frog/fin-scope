package com.finscope.service.research.agent.tool;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.common.enums.research.ResearchMaterialType;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchMaterialSearchToolTest {
    @Test
    void storesStructuredMaterialAsRunScopedEvidence() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        ResearchSearchEvidenceRepository repository = mock(ResearchSearchEvidenceRepository.class);
        ResearchMaterial material = new ResearchMaterial();
        material.setMaterialType(ResearchMaterialType.ANNOUNCEMENT); material.setExternalId("a1");
        material.setStockCode("000001"); material.setTitle("半年度报告"); material.setContent("营业收入增长。\n利润改善。");
        material.setUrl("https://www.cninfo.com.cn/new/disclosure/detail?annoId=a1");
        material.setPublishedAt(LocalDateTime.of(2026, 7, 20, 0, 0));
        material.setProviderCode("CNINFO"); material.setProviderFamily("CNINFO"); material.setSourceTier("T1");
        when(gateway.search(any(), any())).thenReturn(new ResearchMaterialGatewayResult(
                Collections.singletonList(material), Collections.emptyList()));
        when(repository.findByRunIdAndUrl(7L, material.getUrl())).thenReturn(Optional.empty());
        when(repository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> {
            ResearchSearchEvidence value = invocation.getArgument(0); value.setId(91L); return value;
        });
        ResearchMaterialSearchTool tool = new ResearchMaterialSearchTool(gateway, repository);

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(7L, 3L), arguments());

        assertEquals("SUCCESS", observation.getStatus());
        assertEquals(1, observation.getEvidenceDelta());
        assertEquals(Collections.singletonList("search-evidence:91"), observation.getDataRefs());
        ArgumentCaptor<ResearchSearchEvidence> captor = ArgumentCaptor.forClass(ResearchSearchEvidence.class);
        verify(repository).save(captor.capture());
        assertEquals("STRUCTURED_MATERIAL", captor.getValue().getContentOrigin());
        assertEquals("T1", captor.getValue().getSourceTier());
        assertTrue(captor.getValue().getContent().contains("利润改善"));
    }

    @Test
    void rejectsUnknownArgumentsAndUnsupportedStockCodes() {
        ResearchMaterialSearchTool tool = new ResearchMaterialSearchTool(
                mock(ResearchMaterialGateway.class), mock(ResearchSearchEvidenceRepository.class));
        Map<String, Object> extra = arguments(); extra.put("limit", 100);
        assertThrows(BusinessException.class, () -> tool.validate(extra));
        Map<String, Object> invalid = arguments(); invalid.put("stockCode", "NVDA");
        assertThrows(BusinessException.class, () -> tool.validate(invalid));
    }

    @Test
    void readsAnnouncementPdfBeforePersistingEvidence() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        ResearchSearchEvidenceRepository repository = mock(ResearchSearchEvidenceRepository.class);
        ResearchEvidenceAcquisitionService acquisition = mock(ResearchEvidenceAcquisitionService.class);
        ResearchMaterial material = new ResearchMaterial();
        material.setMaterialType(ResearchMaterialType.ANNOUNCEMENT);
        material.setStockCode("000001"); material.setTitle("半年度报告");
        material.setContent("公告：半年度报告");
        material.setUrl("https://static.cninfo.com.cn/finalpage/report.pdf");
        material.setProviderCode("CNINFO"); material.setProviderFamily("CNINFO"); material.setSourceTier("T1");
        when(gateway.search(any(), any())).thenReturn(new ResearchMaterialGatewayResult(
                Collections.singletonList(material), Collections.emptyList()));
        when(repository.findByRunIdAndUrl(7L, material.getUrl())).thenReturn(Optional.empty());
        when(acquisition.acquire(material.getUrl(), "半年度报告", material.getContent(), "000001"))
                .thenReturn(new ResearchEvidenceAcquisitionResult("[S1] 营业收入增长12%", material.getContent(),
                        "FULL_TEXT", "pdf:pdfbox", "FETCHED", 8000));
        when(repository.save(any(ResearchSearchEvidence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new ResearchMaterialSearchTool(gateway, repository, acquisition)
                .execute(new ResearchAgentToolContext(7L, 3L), arguments());

        ArgumentCaptor<ResearchSearchEvidence> captor = ArgumentCaptor.forClass(ResearchSearchEvidence.class);
        verify(repository).save(captor.capture());
        assertEquals("FULL_TEXT", captor.getValue().getContentOrigin());
        assertTrue(captor.getValue().getContent().contains("12%"));
    }

    private Map<String, Object> arguments() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("stockCode", "000001");
        value.put("materialType", "ANNOUNCEMENT");
        value.put("query", "半年度报告");
        return value;
    }
}
