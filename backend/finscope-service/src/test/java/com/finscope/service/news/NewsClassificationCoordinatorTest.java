package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsCategory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsClassificationCoordinatorTest {
    @Test
    void springContainerSelectsTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NewsClassificationRepository.class,
                    () -> mock(NewsClassificationRepository.class));
            context.registerBean(NewsCategoryRepository.class, () -> mock(NewsCategoryRepository.class));
            context.registerBean(NewsClassificationAgent.class, () -> mock(NewsClassificationAgent.class));
            context.registerBean(AgentRunRepository.class, () -> mock(AgentRunRepository.class));
            context.registerBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(EphemeralContentCacheProperties.class,
                    () -> new EphemeralContentCacheProperties());
            context.registerBean("newsClassificationExecutor", Executor.class, () -> Runnable::run);
            context.registerBean(NewsWorkbenchCapabilities.class, () -> mock(NewsWorkbenchCapabilities.class));
            context.registerBean(NewsRuleClassifier.class, NewsRuleClassifier::new);
            context.registerBean(com.finscope.service.cache.ViewRevisionService.class,
                    () -> mock(com.finscope.service.cache.ViewRevisionService.class));
            context.register(NewsClassificationCoordinator.class);

            context.registerBean(com.finscope.rpc.llm.LlmChatClient.class,
                    () -> mock(com.finscope.rpc.llm.LlmChatClient.class));
            context.refresh();

            assertNotNull(context.getBean(NewsClassificationCoordinator.class));
        }
    }

    @Test
    void schedulesOnlyClaimedItemsAndPersistsAgentDecision() throws Exception {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationAgent agent = mock(NewsClassificationAgent.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        NewsClassificationCandidate first = candidate("CLS:1");
        NewsClassificationCandidate duplicate = candidate("THS:2");
        when(repository.claim(eq("CLS:1"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);
        when(repository.claim(eq("THS:2"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(categories.findEnabled()).thenReturn(Collections.singletonList(category()));
        Map<String, NewsClassificationAgent.Decision> decisions = new LinkedHashMap<String, NewsClassificationAgent.Decision>();
        decisions.put("CLS:1", new NewsClassificationAgent.Decision("CLS:1", "COMPANY", 0.88, "公司事件"));
        when(agent.classify(any(), any())).thenReturn(decisions);
        Executor direct = Runnable::run;
        NewsClassificationCoordinator coordinator = createNewsClassificationCoordinator(repository, categories,
                agent, runs, direct, fixedClock());

        int scheduled = coordinator.schedule(Arrays.asList(first, duplicate));

        assertEquals(1, scheduled);
        verify(repository).markClassified(eq("CLS:1"), eq("COMPANY"), eq(0.88), eq("公司事件"),
                anyString(), any(LocalDateTime.class));
        verify(repository, never()).markClassified(eq("THS:2"), anyString(), any(Double.class),
                anyString(), anyString(), any(LocalDateTime.class));
        verify(runs).record(eq("news-classification"), eq("SUCCESS"), anyString(), anyString(),
                eq(null), any(Long.class));
    }

    @Test
    void marksClaimedItemsFailedWhenAgentThrows() throws Exception {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationAgent agent = mock(NewsClassificationAgent.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(repository.claim(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);
        when(categories.findEnabled()).thenReturn(Collections.singletonList(category()));
        when(agent.classify(any(), any())).thenThrow(new RuntimeException("模型超时"));
        NewsClassificationCoordinator coordinator = createNewsClassificationCoordinator(repository, categories,
                agent, runs, Runnable::run, fixedClock());

        coordinator.schedule(Collections.singletonList(candidate("CLS:1")));

        verify(repository).markFailed(eq("CLS:1"), eq("模型超时"), anyString(), any(LocalDateTime.class));
        verify(runs).record(eq("news-classification"), eq("FAILED"), anyString(), eq(null),
                eq("模型超时"), any(Long.class));
    }

    @Test
    void disabledModelUsesRulesWithoutAgentCallsOrFailureRuns() throws Exception {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationAgent agent = mock(NewsClassificationAgent.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        when(repository.claim(anyString(), any(), any())).thenReturn(true);
        when(categories.findEnabled()).thenReturn(Collections.singletonList(category()));
        NewsClassificationCoordinator coordinator = createNewsClassificationCoordinator(repository, categories,
                agent, runs, Runnable::run, fixedClock());
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator, "capabilities", mock(NewsWorkbenchCapabilities.class));

        coordinator.schedule(Collections.singletonList(candidate("CLS:1")));

        verify(repository).markRuleResult(org.mockito.ArgumentMatchers.argThat(value ->
                "COMPANY".equals(value.getCategoryCode())), any());
        verify(agent, never()).classify(any(), any());
        org.mockito.Mockito.verifyNoInteractions(runs);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "\t\n"})
    void missingApiKeyUsesRulesWithoutSubmittingModelWork(String apiKey) throws Exception {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationAgent agent = mock(NewsClassificationAgent.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        Executor executor = mock(Executor.class);
        when(repository.claim(anyString(), any(), any())).thenReturn(true);
        when(categories.findEnabled()).thenReturn(Collections.singletonList(category()));
        NewsClassificationCoordinator coordinator = createNewsClassificationCoordinator(
                repository, categories, agent, runs, executor, fixedClock());
        NewsWorkbenchCapabilities capabilities = new NewsWorkbenchCapabilities();
        org.springframework.test.util.ReflectionTestUtils.setField(capabilities, "modelEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(capabilities, "llm",
                new com.finscope.rpc.llm.OpenAiCompatibleLlmClient(
                        true, "http://127.0.0.1:1", apiKey, "test-model", 100, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator, "capabilities", capabilities);

        assertEquals(1, coordinator.schedule(Collections.singletonList(candidate("CLS:1"))));

        verify(repository).markRuleResult(org.mockito.ArgumentMatchers.argThat(value ->
                "COMPANY".equals(value.getCategoryCode())), any());
        verify(repository, never()).markFailed(anyString(), anyString(), anyString(), any());
        org.mockito.Mockito.verifyNoInteractions(agent, runs, executor);
    }

    @Test
    void rejectedBatchReleasesClaimsForRetry() {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        when(repository.claim(anyString(), any(), any())).thenReturn(true);
        Executor rejected = task -> { throw new java.util.concurrent.RejectedExecutionException(); };
        NewsClassificationCoordinator coordinator = createNewsClassificationCoordinator(repository,
                mock(NewsCategoryRepository.class), mock(NewsClassificationAgent.class),
                mock(AgentRunRepository.class), rejected, fixedClock());
        coordinator.schedule(Collections.singletonList(candidate("CLS:1")));
        verify(repository).markFailed(eq("CLS:1"), eq("分类任务提交失败"), anyString(), any());
    }

    private static NewsClassificationCandidate candidate(String id) {
        return new NewsClassificationCandidate(id, "公司重大订单", "正文", "财联社",
                LocalDateTime.of(2026, 7, 31, 10, 0));
    }

    private static NewsCategory category() {
        return new NewsCategory("COMPANY", "公司动态", "公司经营变化", true, 10);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-31T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }

    private static NewsClassificationCoordinator createNewsClassificationCoordinator(NewsClassificationRepository repository,
                                         NewsCategoryRepository categories,
                                         NewsClassificationAgent agent,
                                         AgentRunRepository runs,
                                         Executor executor) {
        NewsClassificationCoordinator value = new NewsClassificationCoordinator();
        org.springframework.test.util.ReflectionTestUtils.setField(value, "repository", repository);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "categories", categories);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "agent", agent);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "runs", runs);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "executor", executor);
        com.finscope.service.news.NewsWorkbenchCapabilities capabilities = org.mockito.Mockito.mock(com.finscope.service.news.NewsWorkbenchCapabilities.class);
        org.mockito.Mockito.when(capabilities.isModelEnabled()).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "capabilities", capabilities);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "rules", new NewsRuleClassifier());
        org.springframework.test.util.ReflectionTestUtils.setField(value, "revisions", mock(com.finscope.service.cache.ViewRevisionService.class));
        return value;
    }

    private static NewsClassificationCoordinator createNewsClassificationCoordinator(NewsClassificationRepository repository,
                                  NewsCategoryRepository categories,
                                  NewsClassificationAgent agent,
                                  AgentRunRepository runs,
                                  Executor executor,
                                  Clock clock) {
        NewsClassificationCoordinator value = new NewsClassificationCoordinator();
        org.springframework.test.util.ReflectionTestUtils.setField(value, "repository", repository);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "categories", categories);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "agent", agent);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "runs", runs);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "executor", executor);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "clock", clock);
        com.finscope.service.news.NewsWorkbenchCapabilities capabilities = org.mockito.Mockito.mock(com.finscope.service.news.NewsWorkbenchCapabilities.class);
        org.mockito.Mockito.when(capabilities.isModelEnabled()).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "capabilities", capabilities);
        org.springframework.test.util.ReflectionTestUtils.setField(value, "rules", new NewsRuleClassifier());
        org.springframework.test.util.ReflectionTestUtils.setField(value, "revisions", mock(com.finscope.service.cache.ViewRevisionService.class));
        return value;
    }
}
