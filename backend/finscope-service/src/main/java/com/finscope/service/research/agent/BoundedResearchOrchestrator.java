package com.finscope.service.research.agent;

import com.finscope.common.enums.research.ResearchMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * 仅并行只读分支；返回值保持计划顺序，调用方负责顺序提交持久化结果。
 */
@Component
public class BoundedResearchOrchestrator implements ResearchOrchestrator {
    @Override
    public List<BranchResult> execute(ResearchMode requestedMode,
                                      String query,
                                      String intent,
                                      BranchExecutor executor) {
        ResearchMode mode = ResearchMode.defaultIfNull(requestedMode);
        List<BranchPlan> plans = plans(mode, query, intent);
        if (plans.size() == 1) {
            return Collections.singletonList(run(plans.get(0), executor));
        }
        ThreadFactory threadFactory = Thread.ofVirtual()
                .name("research-read-branch-", 0)
                .factory();
        try (ExecutorService pool = Executors.newThreadPerTaskExecutor(threadFactory)) {
            List<Future<BranchResult>> futures = new ArrayList<>();
            for (BranchPlan plan : plans) {
                futures.add(pool.submit(() -> run(plan, executor)));
            }
            return collect(plans, futures);
        }
    }

    private List<BranchResult> collect(List<BranchPlan> plans, List<Future<BranchResult>> futures) {
        List<BranchResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            BranchPlan plan = plans.get(i);
            try {
                results.add(futures.get(i).get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                results.add(BranchResult.failure(plan.query(), plan.intent(), safe(ex.getMessage())));
            } catch (Exception ex) {
                results.add(BranchResult.failure(plan.query(), plan.intent(), safe(ex.getMessage())));
            }
        }
        return results;
    }

    private BranchResult run(BranchPlan plan, BranchExecutor executor) {
        try {
            return BranchResult.success(plan.query(), plan.intent(),
                    executor.search(plan.query(), plan.intent()));
        } catch (Exception ex) {
            return BranchResult.failure(plan.query(), plan.intent(), safe(ex.getMessage()));
        }
    }

    private List<BranchPlan> plans(ResearchMode mode, String query, String intent) {
        List<BranchPlan> values = new ArrayList<BranchPlan>();
        values.add(new BranchPlan(limit(query), intent));
        if (mode == ResearchMode.QUICK) {
            return values;
        }
        addIfMissing(values, query, "SUPPORT", " 支持证据 数据");
        addIfMissing(values, query, "COUNTER", " 反方风险 下修");
        addIfMissing(values, query, "PRIMARY", " 官方公告 财报 监管");
        return values.subList(0, Math.min(mode.getMaxConcurrency(), values.size()));
    }

    private void addIfMissing(List<BranchPlan> values, String query, String intent, String suffix) {
        for (BranchPlan value : values) {
            if (intent.equals(value.intent())) {
                return;
            }
        }
        values.add(new BranchPlan(limit(query + suffix), intent));
    }

    private String limit(String value) {
        String compact = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return compact.length() <= 180 ? compact : compact.substring(0, 180);
    }

    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "研究分支执行失败";
        }
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240);
    }

    private record BranchPlan(String query, String intent) {
    }
}
