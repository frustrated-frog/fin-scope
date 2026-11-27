package com.finscope.service.quant.study;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.StudyState;
import com.finscope.dao.quant.study.AutonomousStudyRepository;
import com.finscope.rpc.llm.LunaResponsesClient;
import com.finscope.rpc.quant.PythonPaperClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class AutonomousStudyServiceTest {
    @Test
    void reusedMaterialsAreRemovedBeforeAnyModelOrToolCall() throws Exception {
        var repo=mock(AutonomousStudyRepository.class);
        var client=mock(PythonPaperClient.class);
        var llm=mock(LunaResponsesClient.class);
        var json=new ObjectMapper();
        var service=new AutonomousStudyService();
        ReflectionTestUtils.setField(service,"studies",repo);
        ReflectionTestUtils.setField(service,"paper",client);
        ReflectionTestUtils.setField(service,"json",json);
        ReflectionTestUtils.setField(service,"llm",llm);
        when(repo.claim(anyString(),anyLong(),anyLong())).thenReturn(1L);
        when(repo.find(1)).thenReturn(Map.of("id",1,"state","PLANNING"));
        when(repo.usedSources()).thenReturn(List.of("frozen"));
        when(client.studySources()).thenReturn(json.readTree("[{\"source\":\"frozen\",\"tool\":\"CORRELATION\"}]"));
        service.workOne();
        verify(repo).finish(eq(1L),anyString(),eq(StudyState.WAITING_DATA),isNull(),anyString(),anyLong());
        verify(client,never()).computeStudy(any());
        verifyNoInteractions(llm);
    }

    @Test
    void resumesTeachingFromStoredEvidenceAndKeepsUnsuccessfulFindings() throws Exception {
        var repo = mock(AutonomousStudyRepository.class);
        var paper = mock(PythonPaperClient.class);
        var llm = mock(LunaResponsesClient.class);
        var json = new ObjectMapper();
        var service = new AutonomousStudyService();
        ReflectionTestUtils.setField(service, "studies", repo);
        ReflectionTestUtils.setField(service, "paper", paper);
        ReflectionTestUtils.setField(service, "json", json);
        ReflectionTestUtils.setField(service, "llm", llm);
        when(repo.claim(anyString(), anyLong(), anyLong())).thenReturn(9L);
        when(repo.find(9)).thenReturn(Map.of("id", 9, "state", "WRITING", "plan", "{\"title\":\"样本支持假设吗？\"}",
                "evidence", "{\"metrics\":{\"net\":-5},\"names\":{}}"));
        var report = json.createObjectNode();
        for (String field : List.of("finding", "background", "theory", "mathematics", "method", "interpretation",
                "limits", "nextQuestion", "reason")) {
            report.put(field, "本次计算的解释");
        }
        report.put("outcome", "UNSUPPORTED");
        report.put("value", "学习价值");
        var merits = report.putObject("merits");
        for (String key : List.of("学习价值","研究价值","投资价值","方法价值","实用价值","创新价值")) {
            merits.put(key, 1);
        }
        when(llm.isConfigured()).thenReturn(true);
        when(llm.completeText(anyString(), contains("\"net\":-5"), anyInt())).thenReturn(report.toString());
        service.workOne();
        verify(repo).finish(eq(9L), anyString(), eq(StudyState.PUBLISHED), contains("UNSUPPORTED"), isNull(), anyLong());
        verifyNoInteractions(paper);
    }
}
