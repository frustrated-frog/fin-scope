package com.finscope.service.fetch;

import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import com.finscope.rpc.source.SourceAdapter;
import com.finscope.rpc.source.SourceAdapterRegistry;
import com.finscope.service.article.ArticleIngestCoordinator;
import com.finscope.domain.article.ArticleIngestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class FetchService {

    @Resource
    private SourceRepository sourceRepository;
    @Resource
    private FetchRunRepository fetchRunRepository;
    @Resource
    private SourceAdapterRegistry adapterRegistry;
    @Resource
    private ArticleIngestCoordinator articleIngestCoordinator;

    public FetchRun fetch(Long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
        long start = System.currentTimeMillis();
        log.info("source fetch start sourceId={} sourceName={} type={}", source.getId(), source.getName(), source.getType());
        FetchRun run = fetchRunRepository.start(source.getId(), source.getName());
        int successCount = 0;
        int duplicateCount = 0;
        try {
            SourceAdapter adapter = adapterRegistry.get(source);
            List<RawItem> items = adapter.fetch(source);
            log.info("source fetch items sourceId={} itemCount={}", source.getId(), items.size());
            for (RawItem item : items) {
                ArticleIngestResult result = articleIngestCoordinator.ingest(source, item);
                if ("DUPLICATE".equals(result.getArticle().getNoveltyType())) {
                    duplicateCount++;
                } else {
                    successCount++;
                }
            }
            log.info("资源抓取成功： sourceId={} successCount={} duplicateCount={} durationMs={}",
                    source.getId(), successCount, duplicateCount, System.currentTimeMillis() - start);
            return fetchRunRepository.finish(run, "SUCCESS", successCount, duplicateCount, null);
        } catch (Exception ex) {
            log.error("source fetch failed sourceId={} successCount={} duplicateCount={} durationMs={}",
                    source.getId(), successCount, duplicateCount, System.currentTimeMillis() - start, ex);
            return fetchRunRepository.finish(run, "FAILED", successCount, duplicateCount, ex.getMessage());
        }
    }
}
