package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeOverview;
import com.finscope.domain.knowledge.KnowledgeReviewResult;
import com.finscope.domain.knowledge.KnowledgeTopicWorkspace;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.topic.Topic;
import com.finscope.service.knowledge.KnowledgeLearningService;
import com.finscope.service.knowledge.KnowledgeContextService;
import com.finscope.service.knowledge.KnowledgeReviewService;
import com.finscope.service.knowledge.KnowledgeTopicService;
import com.finscope.service.knowledge.KnowledgeOverviewService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class KnowledgeControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private KnowledgeOverviewService overviewService;
    @MockBean
    private KnowledgeLearningService learningService;
    @MockBean
    private KnowledgeContextService contextService;
    @MockBean
    private KnowledgeTopicService topicService;
    @MockBean
    private KnowledgeReviewService reviewService;

    @Test
    void exposesOverviewAndBoundedPages() throws Exception {
        KnowledgeOverview overview = new KnowledgeOverview();
        overview.setAcceptedTaskCount(3);
        overview.setActions(Collections.emptyList());
        overview.setActiveTopics(Collections.emptyList());
        overview.setRecentEntries(Collections.emptyList());
        when(overviewService.load()).thenReturn(overview);
        when(overviewService.topicsPage(eq("ACTIVE"), eq("BUILDING"), eq(false),
                eq("agent"), eq(0), eq(20))).thenReturn(PageResponse.of(
                Collections.<Topic>emptyList(), 0, 0, 20));
        when(overviewService.tasksPage(eq("TODO"), eq(2L), eq("why"), eq(0), eq(20)))
                .thenReturn(PageResponse.of(Collections.<LearningTask>emptyList(), 0, 0, 20));

        mockMvc.perform(get("/api/knowledge/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedTaskCount").value(3));
        mockMvc.perform(get("/api/knowledge/topics")
                        .param("lifecycle", "ACTIVE").param("mastery", "BUILDING")
                        .param("query", "agent").param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageSize").value(20));
        mockMvc.perform(get("/api/knowledge/tasks")
                        .param("status", "TODO").param("topicId", "2")
                        .param("query", "why").param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void exposesDueReviewPage() throws Exception {
        when(overviewService.dueReviews(eq(0), eq(20))).thenReturn(
                PageResponse.of(Collections.<Topic>emptyList(), 0, 0, 20));

        mockMvc.perform(get("/api/knowledge/reviews/due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void exposesEvidenceBoundToTheSelectedTask() throws Exception {
        EvidenceItem item = new EvidenceItem();
        item.setId(11L);
        item.setClaim("一手回放显示任务可以稳定恢复");
        when(contextService.evidenceForTask(7L)).thenReturn(Collections.singletonList(item));

        mockMvc.perform(get("/api/knowledge/tasks/7/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(11));
    }

    @Test
    void exposesTopicWorkspaceAndRecordsReview() throws Exception {
        KnowledgeTopicWorkspace workspace = new KnowledgeTopicWorkspace();
        Topic topic = new Topic();
        topic.setId(2L);
        topic.setName("Agent 工程化");
        workspace.setTopic(topic);
        workspace.setEntries(Collections.emptyList());
        workspace.setTasks(Collections.emptyList());
        workspace.setEvents(Collections.emptyList());
        workspace.setEvidence(Collections.emptyList());
        when(topicService.load(2L)).thenReturn(workspace);
        KnowledgeReviewResult result = new KnowledgeReviewResult();
        result.setIntervalDays(30);
        when(reviewService.review(eq(2L), eq("结论更新"), eq("HIGH"),
                anyList(), eq(30), eq(4L))).thenReturn(result);

        mockMvc.perform(get("/api/knowledge/topics/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic.name").value("Agent 工程化"));
        mockMvc.perform(post("/api/knowledge/topics/2/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusion\":\"结论更新\",\"confidence\":\"HIGH\"," +
                                "\"evidenceIds\":[11],\"intervalDays\":30,\"expectedRevision\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intervalDays").value(30));
    }

    @Test
    void executesExplicitTaskCommandsWithRevision() throws Exception {
        LearningTask task = new LearningTask();
        task.setId(7L);
        task.setStatus("TODO");
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(9L);
        entry.setEntryStatus("DRAFT");
        when(learningService.acceptSuggestion(7L, 2L, 3L)).thenReturn(task);
        when(learningService.startTask(7L, 4L)).thenReturn(task);
        when(learningService.saveDraft(eq(7L), eq(2L), anyString(), eq("HIGH"),
                anyList(), eq(5L), eq(null))).thenReturn(entry);
        when(learningService.completeTask(eq(7L), eq(2L), anyString(), eq("HIGH"),
                anyList(), eq(5L), eq(null))).thenReturn(entry);
        when(learningService.dismissTask(7L, "later", 6L)).thenReturn(task);

        mockMvc.perform(post("/api/knowledge/tasks/7/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":2,\"expectedRevision\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7));
        mockMvc.perform(post("/api/knowledge/tasks/7/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":4}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/knowledge/tasks/7/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody(5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entryStatus").value("DRAFT"));
        mockMvc.perform(post("/api/knowledge/tasks/7/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryBody(5)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/knowledge/tasks/7/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"later\",\"expectedRevision\":6}"))
                .andExpect(status().isOk());
    }

    @Test
    void mapsInvalidMissingAndConflictRequests() throws Exception {
        when(learningService.startTask(eq(404L), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "学习任务不存在"));
        when(learningService.startTask(eq(7L), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_CONFLICT, "数据已更新"));

        mockMvc.perform(post("/api/knowledge/tasks/7/complete")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"));
        mockMvc.perform(post("/api/knowledge/tasks/404/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/knowledge/tasks/7/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/knowledge/tasks/7/start")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    private String entryBody(long revision) {
        return "{\"topicId\":2,\"markdown\":\"answer\",\"confidence\":\"HIGH\"," +
                "\"evidenceIds\":[11],\"expectedTaskRevision\":" + revision + "}";
    }
}
