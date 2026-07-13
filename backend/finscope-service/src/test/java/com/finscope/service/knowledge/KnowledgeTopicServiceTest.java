package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.knowledge.TopicReviewStateRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeTopicWorkspace;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeTopicServiceTest {
    @Test
    void loadsABoundedTopicWorkspaceWithBulkEventAndEvidenceQueries() {
        TopicRepository topics = mock(TopicRepository.class);
        TopicReviewStateRepository reviews = mock(TopicReviewStateRepository.class);
        KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
        LearningTaskRepository tasks = mock(LearningTaskRepository.class);
        TopicEventRepository links = mock(TopicEventRepository.class);
        EventClusterRepository events = mock(EventClusterRepository.class);
        EvidenceItemRepository evidence = mock(EvidenceItemRepository.class);
        Topic topic = new Topic();
        topic.setId(2L);
        when(topics.findById(2L)).thenReturn(Optional.of(topic));
        when(reviews.findByTopicId(2L)).thenReturn(Optional.empty());
        when(entries.findFinalByTopicId(2L, 50, 0)).thenReturn(Collections.emptyList());
        when(tasks.findPage(null, 2L, null, 0, 50)).thenReturn(Collections.emptyList());
        when(links.findEventIds(2L)).thenReturn(Arrays.asList(9L, 8L));
        when(events.findByIds(Arrays.asList(9L, 8L), 20)).thenReturn(Collections.emptyList());
        when(evidence.findByEventIds(Arrays.asList(9L, 8L), 50)).thenReturn(Collections.emptyList());

        KnowledgeTopicWorkspace result = new KnowledgeTopicService(
                topics, reviews, entries, tasks, links, events, evidence).load(2L);

        assertEquals(topic, result.getTopic());
        verify(events).findByIds(Arrays.asList(9L, 8L), 20);
        verify(evidence).findByEventIds(Arrays.asList(9L, 8L), 50);
    }
}
