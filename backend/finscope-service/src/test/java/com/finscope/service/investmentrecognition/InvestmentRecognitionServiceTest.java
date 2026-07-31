package com.finscope.service.investmentrecognition;

import com.finscope.dao.investmentrecognition.InvestmentRecognitionCandidateRepository;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.topic.Topic;
import com.finscope.service.knowledge.KnowledgeTopicService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class InvestmentRecognitionServiceTest {
    @Test
    void acceptingCandidateCreatesAFormalRecognitionWithoutArticleLinks() {
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        KnowledgeTopicService topics = mock(KnowledgeTopicService.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
        InvestmentRecognitionCandidate candidate = candidate();
        InvestmentRecognitionCandidate acceptedValue = candidate();
        acceptedValue.setTopicId(9L);
        acceptedValue.setStatus("ACCEPTED");
        acceptedValue.setRevision(1L);
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate), Optional.of(acceptedValue));
        Topic topic = new Topic();
        topic.setId(9L);
        topic.setRevision(0L);
        when(topics.create(any(), any())).thenReturn(topic);
        when(topicRepository.updateKnowledgeState(9L, "ACTIVE", "REVIEWING", 0L)).thenReturn(true);
        when(entries.saveDraft(any())).thenAnswer(invocation -> {
            KnowledgeEntry entry = invocation.getArgument(0);
            entry.setId(12L);
            entry.setRevision(0L);
            return entry;
        });
        when(entries.finalizeDraft(12L, 0L)).thenReturn(true);
        when(candidates.updateStatus(7L, "ACCEPTED", 0L, 9L)).thenReturn(true);
        InvestmentRecognitionCandidate accepted = new InvestmentRecognitionService(
                candidates, topics, topicRepository, entries).accept(7L, 0L);

        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(9L, accepted.getTopicId());
        verify(entries).saveDraft(any(KnowledgeEntry.class));
        verify(entries).finalizeDraft(12L, 0L);
    }

    @Test
    void rejectsARecognitionWhoseEvidenceStructureIsIncomplete() {
        InvestmentRecognitionCandidateRepository candidates = mock(InvestmentRecognitionCandidateRepository.class);
        KnowledgeTopicService topics = mock(KnowledgeTopicService.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
        InvestmentRecognitionCandidate candidate = candidate();
        candidate.setCounterData(Arrays.asList(" "));
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate));

        assertThrows(RuntimeException.class, () -> new InvestmentRecognitionService(
                candidates, topics, topicRepository, entries).accept(7L, 0L));

        verify(topics, never()).create(any(), any());
    }

    private InvestmentRecognitionCandidate candidate() {
        InvestmentRecognitionCandidate value = new InvestmentRecognitionCandidate();
        value.setId(7L);
        value.setRevision(0L);
        value.setStatus("CANDIDATE");
        value.setSubjectType("STOCK");
        value.setSubjectCode("600519");
        value.setSubjectName("贵州茅台");
        value.setThesis("盈利预期是否改善值得验证");
        value.setObservedChange("当日上涨 3.2%");
        value.setMechanism("盈利上修可能消化估值压力");
        value.setSupportingData(Arrays.asList("涨跌幅 +3.2%"));
        value.setCounterData(Arrays.asList("可能只是情绪"));
        value.setValidationMetrics(Arrays.asList("下一期收入增速"));
        value.setInvalidationConditions("收入增速未改善");
        value.setHorizon("下一财报期");
        value.setConfidence("MEDIUM");
        value.setEvidenceCompleteness("SUFFICIENT");
        return value;
    }
}
