package com.finscope.service.research.agent;

import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.search.SearchResult;

import java.util.Collections;
import java.util.List;

/**
 * 研究分支编排缝。实现可以替换，但不能绕过 Research Runtime 的提交边界。
 */
public interface ResearchOrchestrator {
    List<BranchResult> execute(ResearchMode mode,
                               String query,
                               String intent,
                               BranchExecutor executor);

    interface BranchExecutor {
        List<SearchResult> search(String query, String intent) throws Exception;
    }

    final class BranchResult {
        private final String query;
        private final String intent;
        private final List<SearchResult> hits;
        private final String errorMessage;

        private BranchResult(String query, String intent, List<SearchResult> hits, String errorMessage) {
            this.query = query;
            this.intent = intent;
            this.hits = hits == null ? Collections.<SearchResult>emptyList() : hits;
            this.errorMessage = errorMessage;
        }

        public static BranchResult success(String query, String intent, List<SearchResult> hits) {
            return new BranchResult(query, intent, hits, null);
        }

        public static BranchResult failure(String query, String intent, String errorMessage) {
            return new BranchResult(query, intent, Collections.<SearchResult>emptyList(), errorMessage);
        }

        public String getQuery() { return query; }
        public String getIntent() { return intent; }
        public List<SearchResult> getHits() { return hits; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isSuccess() { return errorMessage == null; }
    }
}
