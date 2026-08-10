package com.finscope.service.research.agent;

import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedResearchOrchestratorTest {
    private final BoundedResearchOrchestrator orchestrator = new BoundedResearchOrchestrator();

    @Test
    void quickRunsOnlyTheRequestedBranch() {
        List<ResearchOrchestrator.BranchResult> results = orchestrator.execute(
                ResearchMode.QUICK, "AI 资本开支", "SUPPORT",
                (query, intent) -> Collections.singletonList(new SearchResult()));

        assertEquals(1, results.size());
        assertEquals("SUPPORT", results.get(0).getIntent());
        assertTrue(results.get(0).isSuccess());
    }

    @Test
    void deepRunsThreeComplementaryBranchesWithinConcurrencyLimit() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(3);

        List<ResearchOrchestrator.BranchResult> results = orchestrator.execute(
                ResearchMode.DEEP, "AI 资本开支", "SUPPORT", (query, intent) -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    entered.countDown();
                    entered.await(1, TimeUnit.SECONDS);
                    active.decrementAndGet();
                    return Collections.singletonList(new SearchResult());
                });

        Set<String> intents = new HashSet<String>();
        for (ResearchOrchestrator.BranchResult result : results) {
            intents.add(result.getIntent());
        }
        assertEquals(new HashSet<String>(java.util.Arrays.asList("SUPPORT", "COUNTER", "PRIMARY")), intents);
        assertEquals(3, maximum.get());
    }

    @Test
    void deepRunsBlockingBranchesOnNamedVirtualThreads() {
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        AtomicBoolean allVirtual = new AtomicBoolean(true);

        List<ResearchOrchestrator.BranchResult> results = orchestrator.execute(
                ResearchMode.DEEP, "AI 资本开支", "SUPPORT", (query, intent) -> {
                    Thread thread = Thread.currentThread();
                    threadNames.add(thread.getName());
                    allVirtual.compareAndSet(true, thread.isVirtual());
                    return Collections.singletonList(new SearchResult());
                });

        assertEquals(3, results.size());
        assertTrue(allVirtual.get());
        assertTrue(threadNames.stream()
                .allMatch(name -> name.startsWith("research-read-branch-")));
    }

    @Test
    void keepsSuccessfulBranchesWhenOneBranchFails() {
        List<ResearchOrchestrator.BranchResult> results = orchestrator.execute(
                ResearchMode.DEEP, "公司盈利", "COUNTER", (query, intent) -> {
                    if ("SUPPORT".equals(intent)) {
                        throw new IllegalStateException("provider timeout");
                    }
                    return Collections.singletonList(new SearchResult());
                });

        assertEquals(3, results.size());
        assertEquals(2, results.stream().filter(ResearchOrchestrator.BranchResult::isSuccess).count());
        assertEquals(1, results.stream().filter(result -> !result.isSuccess()).count());
    }
}
