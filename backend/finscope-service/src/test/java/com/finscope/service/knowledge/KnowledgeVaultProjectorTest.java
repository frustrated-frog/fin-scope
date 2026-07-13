package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.topic.Topic;
import com.finscope.service.vault.VaultWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeVaultProjectorTest {
    @TempDir
    Path tempDir;

    @Test
    void projectsOnceWhenDuplicateEventsArrive() throws Exception {
        KnowledgeProjectionJobRepository jobs = mock(KnowledgeProjectionJobRepository.class);
        KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        VaultWriter writer = new VaultWriter(tempDir);
        stubProjection(jobs, entries, topics);
        when(jobs.claim(5L)).thenReturn(true, false);
        KnowledgeVaultProjector projector = new KnowledgeVaultProjector(jobs, entries, topics, writer);

        projector.handle(new KnowledgeProjectionRequested(5L, 2L, 100L));
        projector.handle(new KnowledgeProjectionRequested(5L, 2L, 100L));

        String markdown = new String(
                Files.readAllBytes(tempDir.resolve("topics/ai-agents.md")),
                StandardCharsets.UTF_8
        );
        assertEquals(1, occurrences(markdown, "<!-- knowledge-entry:100 -->"));
        assertTrue(markdown.contains("final answer"));
        verify(jobs).markCompleted(5L);
        verify(jobs, never()).markFailed(eq(5L), anyString());
    }

    @Test
    void recordsFailureAndCompletesOnRetry() throws Exception {
        KnowledgeProjectionJobRepository jobs = mock(KnowledgeProjectionJobRepository.class);
        KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        VaultWriter writer = mock(VaultWriter.class);
        stubProjection(jobs, entries, topics);
        when(jobs.claim(5L)).thenReturn(true, true);
        doThrow(new IOException("disk unavailable"))
                .doReturn(tempDir.resolve("topics/ai-agents.md"))
                .when(writer).appendKnowledgeEntry(eq("ai-agents"), eq(100L), anyString());
        KnowledgeVaultProjector projector = new KnowledgeVaultProjector(jobs, entries, topics, writer);

        projector.project(5L);
        projector.project(5L);

        verify(jobs).markFailed(eq(5L), anyString());
        verify(jobs).markCompleted(5L);
        verify(writer, times(2)).appendKnowledgeEntry(eq("ai-agents"), eq(100L), anyString());
    }

    @Test
    void startupRecoveryLoadsOnlyOneBoundedBatch() {
        KnowledgeProjectionJobRepository jobs = mock(KnowledgeProjectionJobRepository.class);
        KnowledgeVaultProjector projector = mock(KnowledgeVaultProjector.class);
        KnowledgeProjectionJob first = job(5L);
        KnowledgeProjectionJob second = job(6L);
        when(jobs.findRecoverable(50)).thenReturn(Arrays.asList(first, second));
        KnowledgeProjectionRecovery recovery = new KnowledgeProjectionRecovery(jobs, projector);

        recovery.recover();

        verify(jobs).findRecoverable(50);
        verify(projector).project(5L);
        verify(projector).project(6L);
    }

    private void stubProjection(KnowledgeProjectionJobRepository jobs,
                                KnowledgeEntryRepository entries,
                                TopicRepository topics) {
        KnowledgeProjectionJob job = job(5L);
        when(jobs.findById(5L)).thenReturn(Optional.of(job));
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(100L);
        entry.setEntryType("ANSWER");
        entry.setQuestionSnapshot("What did we learn?");
        entry.setContentMarkdown("final answer");
        entry.setConfidence("HIGH");
        when(entries.findById(100L)).thenReturn(Optional.of(entry));
        Topic topic = new Topic();
        topic.setId(2L);
        topic.setSlug("ai-agents");
        when(topics.findById(2L)).thenReturn(Optional.of(topic));
    }

    private KnowledgeProjectionJob job(long id) {
        KnowledgeProjectionJob job = new KnowledgeProjectionJob();
        job.setId(id);
        job.setTopicId(2L);
        job.setEntryId(100L);
        job.setStatus("PENDING");
        return job;
    }

    private int occurrences(String value, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }
}
